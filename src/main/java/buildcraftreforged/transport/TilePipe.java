package buildcraftreforged.transport;

import buildcraftreforged.api.transport.IPipe;
import buildcraftreforged.api.transport.IPipeConnection;
import buildcraftreforged.api.transport.IPipeConnection.ConnectOverride;
import buildcraftreforged.api.transport.IPipeTile;
import buildcraftreforged.core.TileBuildCraft;
import buildcraftreforged.transport.graph.PipeNetworks;
import buildcraftreforged.transport.graph.PipeWorld;
import buildcraftreforged.transport.route.PipeBudget;
import buildcraftreforged.transport.route.PipeItemHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.model.data.ModelData;
import net.neoforged.neoforge.model.data.ModelProperty;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * The block entity of every pipe (docs/TRANSPORT.md 3.1). It holds the connection mask, the plugged sides and the
 * module, and recomputes the mask (3.2) on the server tick after something changed: the pipe was placed or loaded, a
 * neighbour block changed, a neighbour's capability was invalidated, or a plug or module changed here or on a
 * neighbouring pipe. The mask is not saved; it is synced to the client, where it selects the cached model.
 *
 * <p>Loading, removal (including chunk unload), a mask change and a plug or module change invalidate the cached
 * network around the pipe ({@link PipeNetworks}, docs/TRANSPORT.md 3.4).
 *
 * <p>Item pipes take pushes through the item capability and route them at once ({@link PipeItemHandler},
 * docs/TRANSPORT.md 6.2); every pipe keeps its throughput {@link PipeBudget} (5). Fluid locks and flow records of 3.1
 * belong to #51 and #59. The overflow stack of 3.1 is not needed: a transfer is one transaction (see
 * {@link buildcraftreforged.transport.route.ItemRouting}).
 */
public class TilePipe extends TileBuildCraft implements IPipeTile, PipeWorld.Pipe {

    /** The connection mask, for the client model ({@code PipeBlockStateModel}). */
    public static final ModelProperty<Byte> CONNECTIONS = new ModelProperty<>();

    public final PipeType kind;
    private byte connectionMask;
    private byte pluggedMask;
    private @Nullable PipeModuleType moduleType;
    private boolean connectionsDirty = true;
    private final BlockCapabilityCache<?, ?>[] endpointCaches = new BlockCapabilityCache<?, ?>[6];
    /** The gate and pipe wire (#60). */
    public final PipeGateHolder gateHolder = new PipeGateHolder(this);
    private final PipeBudget budget = new PipeBudget();
    private final PipeItemHandler[] itemHandlers = new PipeItemHandler[6];

    public TilePipe(BlockPos pos, BlockState state) {
        super(TransportRegistry.pipeTile.get(), pos, state);
        kind = state.getBlock() instanceof BlockPipe pipe ? pipe.kind : PipeType.ITEM;
    }

    public byte getConnectionMask() {
        return connectionMask;
    }

    public byte getPluggedMask() {
        return pluggedMask;
    }

    public @Nullable PipeModuleType getModuleType() {
        return moduleType;
    }

    public int getTier() {
        return getBlockState().getBlock() instanceof BlockPipe pipe ? pipe.getTier() : 0;
    }

    /** The caps of this pipe's tier in use now ({@link BlockPipe#getTierStats}). */
    public PipeTierStats getTierStats() {
        return getBlockState().getBlock() instanceof BlockPipe pipe ? pipe.getTierStats()
                : PipeTierStats.defaults(PipeMaterial.WOOD);
    }

    /** The throughput budget (docs/TRANSPORT.md 5), in the kind's unit. Not saved. */
    public PipeBudget getBudget() {
        return budget;
    }

    public PipeConnections.PipeEnd end() {
        return new PipeConnections.PipeEnd(kind, pluggedMask, moduleType);
    }

    /** Plugs (#61) call this. A plugged side never connects, on either pipe. */
    public void setPlugged(Direction side, boolean plugged) {
        byte mask = PipeConnections.with(pluggedMask, side, plugged);
        if (mask != pluggedMask) {
            pluggedMask = mask;
            onConfigurationChanged();
        }
    }

    /** Module fitting (#55) calls this. The module decides Isolator and ban rules. */
    public void setModuleType(@Nullable PipeModuleType module) {
        if (module != moduleType) {
            moduleType = module;
            onConfigurationChanged();
        }
    }

    private void onConfigurationChanged() {
        setChanged();
        scheduleConnectionUpdate();
        // The module is part of the graph node even when the mask stays the same (docs/TRANSPORT.md 3.4).
        PipeNetworks.invalidate(level, worldPosition);
        // The neighbouring pipes look at our plugs and module too.
        for (Direction side : Direction.values()) {
            TilePipe neighbour = neighbourPipe(side);
            if (neighbour != null)
                neighbour.scheduleConnectionUpdate();
        }
    }

    /** Recompute the mask on the next server tick. Safe to call from a capability invalidation listener. */
    public void scheduleConnectionUpdate() {
        connectionsDirty = true;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        scheduleConnectionUpdate();
        PipeNetworks.invalidate(level, worldPosition);
        if (level != null)
            budget.start(level.getGameTime());
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        // Also covers chunk unload: the pipe leaves its network, splitting it.
        PipeNetworks.invalidate(level, worldPosition);
    }

    @Override
    public void updateEntity() {
        super.updateEntity();
        if (connectionsDirty)
            updateConnections();
        gateHolder.updateEntity();
        // Sources are ticked per network (PipeSources), so a source makes sure its network exists.
        if (moduleType != null && moduleType.isSource() && level instanceof ServerLevel serverLevel)
            PipeNetworks.get(serverLevel, worldPosition);
    }

    /** Server side: applies the connection rule on all six sides. */
    public void updateConnections() {
        if (level == null || level.isClientSide())
            return;
        connectionsDirty = false;

        byte mask = 0;
        for (Direction side : Direction.values()) {
            if (computeConnection(side))
                mask = PipeConnections.with(mask, side, true);
        }

        if (mask != connectionMask) {
            connectionMask = mask;
            sendNetworkUpdate();
            PipeNetworks.invalidate(level, worldPosition);
            gateHolder.onConnectionsChanged();
        }

        // The rule is symmetric for two pipes, so a neighbour that disagrees missed a change (it was unloaded, or
        // was placed after us in the same tick): let it look again.
        for (Direction side : Direction.values()) {
            TilePipe neighbour = neighbourPipe(side);
            if (neighbour != null && PipeConnections.hasSide(neighbour.connectionMask, side.getOpposite())
                    != PipeConnections.hasSide(mask, side))
                neighbour.scheduleConnectionUpdate();
        }
    }

    private boolean computeConnection(Direction side) {
        BlockPos neighbourPos = worldPosition.relative(side);
        if (!level.isLoaded(neighbourPos))
            return false;
        BlockEntity tile = level.getBlockEntity(neighbourPos);
        ConnectOverride override = tile instanceof IPipeConnection connection
                ? connection.overridePipeConnection(kind, side.getOpposite()) : ConnectOverride.DEFAULT;
        PipeConnections.PipeEnd neighbour = tile instanceof TilePipe pipe && !pipe.isRemoved() ? pipe.end() : null;
        return PipeConnections.canConnect(end(), side, override, neighbour,
                () -> getBlockState().getBlock() instanceof BlockPipe block && block.isEndpoint(this, side));
    }

    private @Nullable TilePipe neighbourPipe(Direction side) {
        if (level == null)
            return null;
        BlockPos neighbourPos = worldPosition.relative(side);
        if (!level.isLoaded(neighbourPos))
            return null;
        return level.getBlockEntity(neighbourPos) instanceof TilePipe pipe && !pipe.isRemoved() ? pipe : null;
    }

    /**
     * The capability the block on {@code side} exposes on its face towards this pipe, through a cache whose
     * invalidation schedules a connection update (docs/TRANSPORT.md 3.2). One cache per side; a pipe asks for only
     * its own kind's capability.
     */
    @SuppressWarnings("unchecked")
    public <T> @Nullable T getEndpointCapability(BlockCapability<T, Direction> capability, Direction side) {
        BlockPos neighbourPos = worldPosition.relative(side);
        if (!(level instanceof ServerLevel serverLevel))
            return level == null ? null : level.getCapability(capability, neighbourPos, side.getOpposite());
        int index = side.get3DDataValue();
        if (endpointCaches[index] == null) {
            endpointCaches[index] = BlockCapabilityCache.create(capability, serverLevel, neighbourPos,
                    side.getOpposite(), () -> !isRemoved(), this::scheduleConnectionUpdate);
        }
        return (T) endpointCaches[index].getCapability();
    }

    // IPipeTile

    @Override
    public PipeType getPipeType() {
        return kind;
    }

    @Override
    public boolean isPipeConnected(Direction with) {
        return PipeConnections.hasSide(connectionMask, with);
    }

    /** 4.2.2's deprecated pipe object; the pipe is this block entity now, and its gate and wire half answer here. */
    @Deprecated
    @Override
    public IPipe getPipe() {
        return gateHolder;
    }

    /** Pushes entering on {@code from} route at once (docs/TRANSPORT.md 6.2); returns how many items were taken. */
    @Override
    public int injectItem(ItemStack stack, boolean doAdd, Direction from) {
        ResourceHandler<ItemResource> handler = getItemHandler(from);
        if (handler == null || stack.isEmpty())
            return 0;
        try (Transaction transaction = Transaction.openRoot()) {
            int moved = handler.insert(ItemResource.of(stack), stack.getCount(), transaction);
            if (doAdd)
                transaction.commit();
            return moved;
        }
    }

    /** Item pipes expose {@link PipeItemHandler} on every side; it refuses sides facing an item pipe or a plug. */
    @Override
    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable Direction side) {
        if (kind != PipeType.ITEM || side == null)
            return null;
        int index = side.get3DDataValue();
        if (itemHandlers[index] == null)
            itemHandlers[index] = new PipeItemHandler(this, side);
        return itemHandlers[index];
    }

    @Override
    public boolean isSolidOnSide(Direction side) {
        // SEAM(#61): facades.
        return false;
    }

    /** The gate and wires drop with the pipe (4.2.2 {@code Pipe.onBlockRemoval}). */
    @Override
    public void destroy() {
        super.destroy();
        gateHolder.onBlockRemoval();
    }

    // Save and sync

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putByte("plugs", pluggedMask);
        output.storeNullable("module", PipeModuleType.CODEC, moduleType);
        gateHolder.writeToNBT(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        pluggedMask = input.getByteOr("plugs", (byte) 0);
        moduleType = input.read("module", PipeModuleType.CODEC).orElse(null);
        gateHolder.readFromNBT(input);
        scheduleConnectionUpdate();
    }

    @Override
    public void writePacketPayload(RegistryFriendlyByteBuf data) {
        super.writePacketPayload(data);
        data.writeByte(connectionMask);
        gateHolder.writeRenderState(data);
    }

    @Override
    public void readPacketPayload(RegistryFriendlyByteBuf data) {
        super.readPacketPayload(data);
        connectionMask = (byte) (data.readByte() & 63);
        gateHolder.readRenderState(data);
    }

    /** Client: the new mask picks another cached model and shape, so refresh the model data and re-mesh. */
    @Override
    public void postPacketHandling() {
        super.postPacketHandling();
        if (level != null && level.isClientSide()) {
            requestModelDataUpdate();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
        }
    }

    @Override
    public ModelData getModelData() {
        return ModelData.of(CONNECTIONS, connectionMask);
    }
}
