package buildcraftreforged.transport;

import buildcraftreforged.api.transport.IPipe;
import buildcraftreforged.api.transport.IPipeConnection;
import buildcraftreforged.api.transport.IPipeConnection.ConnectOverride;
import buildcraftreforged.api.transport.IPipeTile;
import buildcraftreforged.core.TileBuildCraft;
import buildcraftreforged.transport.graph.PipeNetworks;
import buildcraftreforged.transport.graph.PipeWorld;
import buildcraftreforged.transport.module.ModuleDirector;
import buildcraftreforged.transport.module.PipeModule;
import buildcraftreforged.transport.route.PipeBudget;
import buildcraftreforged.transport.route.PipeEnergyHandler;
import buildcraftreforged.transport.route.PipeItemHandler;
import buildcraftreforged.transport.route.PowerPipeFlow;
import buildcraftreforged.transport.route.PowerPipeState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.model.data.ModelData;
import net.neoforged.neoforge.model.data.ModelProperty;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
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
 * <p>The module ({@link PipeModule}, docs/TRANSPORT.md 8) is saved with its settings; only its type reaches the client.
 * It hears about connection updates, decides which sides the routing walk may leave by, and is dropped as its item
 * when the pipe is broken.
 *
 * <p>Item pipes take pushes through the item capability and route them at once ({@link PipeItemHandler},
 * docs/TRANSPORT.md 6.2), power pipes through the energy capability ({@link PipeEnergyHandler}, 6.2 and 7.4); every
 * pipe keeps its throughput {@link PipeBudget} (5), and power pipes their {@link PowerPipeState}. Fluid locks and flow records of 3.1
 * belong to #51 and #59. The overflow stack of 3.1 is not needed: a transfer is one transaction (see
 * {@link buildcraftreforged.transport.route.ItemRouting}).
 */
public class TilePipe extends TileBuildCraft implements IPipeTile, PipeWorld.Pipe {

    /** The connection mask, for the client model ({@code PipeBlockStateModel}). */
    public static final ModelProperty<Byte> CONNECTIONS = new ModelProperty<>();

    public final PipeType kind;
    private byte connectionMask;
    private byte pluggedMask;
    private @Nullable PipeModule module;
    private boolean connectionsDirty = true;
    private final BlockCapabilityCache<?, ?>[] endpointCaches = new BlockCapabilityCache<?, ?>[6];
    /** The gate and pipe wire (#60). */
    public final PipeGateHolder gateHolder = new PipeGateHolder(this);
    private final PipeBudget budget = new PipeBudget();
    private final PipeItemHandler[] itemHandlers = new PipeItemHandler[6];
    private final PipeEnergyHandler[] energyHandlers = new PipeEnergyHandler[6];
    private final PowerPipeState powerState = new PowerPipeState();
    private int powerLimit;

    public TilePipe(BlockPos pos, BlockState state) {
        super(TransportRegistry.pipeTile.get(), pos, state);
        kind = state.getBlock() instanceof BlockPipe pipe ? pipe.kind : PipeType.ITEM;
        if (kind == PipeType.POWER)
            gateHolder.flow = new PowerPipeFlow(this);
    }

    public byte getConnectionMask() {
        return connectionMask;
    }

    public byte getPluggedMask() {
        return pluggedMask;
    }

    public @Nullable PipeModuleType getModuleType() {
        return module == null ? null : module.type;
    }

    public @Nullable PipeModule getModule() {
        return module;
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

    /** Power pipes: overload, FE passed this tick and the last request phase (docs/TRANSPORT.md 7.4). Not saved. */
    public PowerPipeState getPowerState() {
        return powerState;
    }

    /**
     * The Power Limiter's FE/t (docs/TRANSPORT.md 7.4, Q7), or 0 for none: a fitted Director's mode, else what
     * {@link #setPowerLimit} set.
     */
    public int getPowerLimit() {
        if (kind == PipeType.POWER && module instanceof ModuleDirector director)
            return director.getPowerLimit();
        return powerLimit;
    }

    /**
     * Sets the Power Limiter's FE/t, 0 for none (docs/TRANSPORT.md Q7). The Director module on a power pipe (#55) calls
     * this whenever it is fitted, loaded, removed or switched; this pipe does not save it.
     */
    public void setPowerLimit(int fePerTick) {
        powerLimit = Math.max(0, fePerTick);
    }

    /** The power budget's refill per tick: the tier's FE/t, lowered by the Power Limiter if one is set. */
    public long getPowerRefill() {
        int tier = getTierStats().powerPerTick();
        int limit = getPowerLimit();
        return limit > 0 ? Math.min(tier, limit) : tier;
    }

    public PipeConnections.PipeEnd end() {
        return new PipeConnections.PipeEnd(kind, pluggedMask, getModuleType());
    }

    /** Plugs (#61) call this. A plugged side never connects, on either pipe. */
    public void setPlugged(Direction side, boolean plugged) {
        byte mask = PipeConnections.with(pluggedMask, side, plugged);
        if (mask != pluggedMask) {
            pluggedMask = mask;
            onConfigurationChanged();
        }
    }

    /** Fits a new module of {@code type} with default settings, or removes the module for null. */
    public void setModuleType(@Nullable PipeModuleType type) {
        if (type != getModuleType())
            setModule(type == null ? null : PipeModule.create(type));
    }

    /**
     * Fits {@code newModule} (null removes the module). The module decides Isolator and ban rules, so connections are
     * recomputed and the network is rebuilt.
     */
    public void setModule(@Nullable PipeModule newModule) {
        if (newModule == module)
            return;
        boolean server = level != null && !level.isClientSide();
        if (module != null && server)
            module.onRemoved(this);
        module = newModule;
        gateHolder.target = module == null ? null : module.gateTarget(this);
        if (module != null && server)
            module.onInstalled(this);
        onConfigurationChanged();
        if (server)
            sendNetworkUpdate();
    }

    /** Removes the module and returns its item with its settings (empty when there is none or it has no item). */
    public ItemStack removeModule() {
        if (module == null || level == null)
            return ItemStack.EMPTY;
        ItemStack stack = module.toItem(level.registryAccess());
        setModule(null);
        return stack;
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
        if (module != null && module.type.isSource() && level instanceof ServerLevel serverLevel)
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

        if (module != null)
            module.onNeighbourChange(this);
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

    /** The module decides which sides the routing walk may leave by (the Director, docs/TRANSPORT.md 8.4). */
    /** Power pipes expose {@link PipeEnergyHandler} on every side; it refuses sides facing a power pipe or a plug. */
    public @Nullable EnergyHandler getEnergyHandler(@Nullable Direction side) {
        if (kind != PipeType.POWER || side == null)
            return null;
        int index = side.get3DDataValue();
        if (energyHandlers[index] == null)
            energyHandlers[index] = new PipeEnergyHandler(this, side);
        return energyHandlers[index];
    }

    @Override
    public boolean allowsOutput(Direction side) {
        return module == null || module.allowsOutput(this, side);
    }

    /** The gate and wires drop with the pipe (4.2.2 {@code Pipe.onBlockRemoval}), and the module as its item. */
    @Override
    public void destroy() {
        super.destroy();
        gateHolder.onBlockRemoval();
        Level world = level;
        if (world != null && !world.isClientSide() && module != null) {
            ItemStack stack = module.toItem(world.registryAccess());
            module.onRemoved(this);
            module = null;
            gateHolder.target = null;
            if (!stack.isEmpty())
                Block.popResource(world, worldPosition, stack);
        }
    }

    @Override
    public boolean isSolidOnSide(Direction side) {
        // SEAM(#61): facades.
        return false;
    }

    // Save and sync

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putByte("plugs", pluggedMask);
        output.storeNullable("module", PipeModuleType.CODEC, getModuleType());
        if (module != null)
            module.save(output.child("module_settings"));
        gateHolder.writeToNBT(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        pluggedMask = input.getByteOr("plugs", (byte) 0);
        module = input.read("module", PipeModuleType.CODEC).map(PipeModule::create).orElse(null);
        if (module != null)
            module.load(input.childOrEmpty("module_settings"));
        gateHolder.target = module == null ? null : module.gateTarget(this);
        gateHolder.readFromNBT(input);
        scheduleConnectionUpdate();
    }

    @Override
    public void writePacketPayload(RegistryFriendlyByteBuf data) {
        super.writePacketPayload(data);
        data.writeByte(connectionMask);
        PipeModuleType type = getModuleType();
        data.writeByte(type == null ? -1 : type.ordinal());
        gateHolder.writeRenderState(data);
    }

    @Override
    public void readPacketPayload(RegistryFriendlyByteBuf data) {
        super.readPacketPayload(data);
        connectionMask = (byte) (data.readByte() & 63);
        // The client keeps a module of the synced type with default settings: enough to know what using the pipe does.
        int type = data.readByte();
        PipeModuleType[] types = PipeModuleType.values();
        PipeModuleType synced = type >= 0 && type < types.length ? types[type] : null;
        if (synced != getModuleType()) {
            module = synced == null ? null : PipeModule.create(synced);
            gateHolder.target = module == null ? null : module.gateTarget(this);
        }
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
