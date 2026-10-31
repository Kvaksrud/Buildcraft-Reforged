package buildcraftreforged.transport;

import buildcraftreforged.api.transport.IPipeConnection;
import buildcraftreforged.api.transport.IPipeTile.PipeType;
import buildcraftreforged.api.transport.PipeManager;
import buildcraftreforged.core.TileBuildCraft;
import buildcraftreforged.transport.flow.FlowAccumulator;
import buildcraftreforged.transport.flow.FlowRecord;
import buildcraftreforged.transport.graph.PipeNetwork;
import buildcraftreforged.transport.graph.PipeNetworks;
import buildcraftreforged.transport.route.ItemRouting;
import buildcraftreforged.transport.route.TransferResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * The Basic Extractor (docs/TRANSPORT.md 10, #58): a block between an inventory and an item pipe that pulls items when
 * an engine pulses it, as an engine on a 4.2.2 wooden pipe did ({@code PipeItemsWood}, {@code PipeLogicWood}).
 *
 * <p>Its {@link BlockBasicExtractor#FACING} is the <em>inventory face</em>; the opposite face is the <em>pipe face</em>,
 * where only an item pipe connects ({@link #overridePipeConnection}); it is that pipe's endpoint but never takes items
 * from it. The four other faces each have an energy handler that stores nothing: an insert is one pulse
 * ({@link #pulse}), which moves {@code floor(min(FE, 641) / 10)} items from the first routable slot through the pipe's
 * network, capped by the path's budgets, and takes {@code 10 × moved} FE; the rest stays in the engine (Q14).
 *
 * <p>It keeps a flow record like a pipe (9.1): measured every 10 ticks and sent in its payload when it changes, so the
 * client knows what to draw sliding through the nozzle on the engine's pulse.
 */
public class TileBasicExtractor extends TileBuildCraft implements IPipeConnection {

    /** FE per item: 4.2.2 used 1 MJ per item ({@code useEnergy(1, slot.stackSize)}). */
    public static final int ENERGY_PER_ITEM = 10;
    /** The most one pulse can use: 4.2.2's wooden pipe receiver took 64.1 MJ ({@code configure(1, 64.1f, ...)}). */
    public static final int MAX_PULSE = 641;

    private final EnergyHandler[] energyHandlers = new EnergyHandler[6];
    private final FlowAccumulator flow = new FlowAccumulator(PipeType.ITEM, this::markFlowing);
    private FlowRecord sent = FlowRecord.none(PipeType.ITEM);
    private boolean flowing;
    /** Client: the last record from the server. */
    private FlowRecord clientRecord = FlowRecord.none(PipeType.ITEM);

    public TileBasicExtractor(BlockPos pos, BlockState state) {
        super(TransportRegistry.basicExtractorTile.get(), pos, state);
    }

    /** The face against the inventory. */
    public Direction getInventoryFace() {
        return getBlockState().getValue(BlockBasicExtractor.FACING);
    }

    /** The face the pipe connects to. */
    public Direction getPipeFace() {
        return getInventoryFace().getOpposite();
    }

    /** Pulse arithmetic of docs/TRANSPORT.md 10: how many items a pulse of {@code energy} FE may move. */
    public static int itemsForPulse(long energy) {
        return (int) (Math.min(Math.max(energy, 0), MAX_PULSE) / ENERGY_PER_ITEM);
    }

    /**
     * One pulse of {@code energy} FE inside {@code transaction}: pulls up to {@link #itemsForPulse} items from the
     * inventory into the pipe and returns the FE used, {@code 10 × moved}. Nothing happens without an inventory, a
     * connected item pipe, or a destination and budget for the items.
     */
    public int pulse(long energy, TransactionContext transaction) {
        int want = itemsForPulse(energy);
        if (want <= 0 || !(level instanceof ServerLevel serverLevel) || isRemoved())
            return 0;
        Direction inventoryFace = getInventoryFace();
        BlockPos inventoryPos = worldPosition.relative(inventoryFace);
        BlockPos pipePos = worldPosition.relative(inventoryFace.getOpposite());
        if (!level.isLoaded(inventoryPos) || !level.isLoaded(pipePos)
                || !(level.getBlockEntity(pipePos) instanceof TilePipe pipe) || pipe.kind != PipeType.ITEM
                || !pipe.isPipeConnected(inventoryFace) || !PipeManager.canExtractItems(this, level, inventoryPos))
            return 0;
        ResourceHandler<ItemResource> source = level.getCapability(Capabilities.Item.BLOCK, inventoryPos,
                inventoryFace.getOpposite());
        if (source == null || source.size() == 0)
            return 0;
        PipeNetwork network = PipeNetworks.get(serverLevel, pipePos);
        if (network == null)
            return 0;
        int node = network.indexOf(pipePos.asLong());
        if (node < 0)
            return 0;
        ItemResource[] before = new ItemResource[source.size()];
        int[] amounts = new int[before.length];
        for (int slot = 0; slot < before.length; slot++) {
            before[slot] = source.getResource(slot);
            amounts[slot] = source.getAmountAsInt(slot);
        }
        // The pipe's side towards the extractor is the inventory face's direction.
        TransferResult result = ItemRouting.extract(serverLevel, network, node, inventoryFace, source, want, transaction);
        if (!result.movedAny())
            return 0;
        flow.add(result.moved(), FlowRecord.contentOf(moved(source, before, amounts)), FlowRecord.NO_COLOUR,
                inventoryFace, inventoryFace.getOpposite(), serverLevel.getGameTime(), transaction);
        return result.moved() * ENERGY_PER_ITEM;
    }

    /** What the pulse took: the resource of the first slot that has less than before. */
    private static ItemResource moved(ResourceHandler<ItemResource> source, ItemResource[] before, int[] amounts) {
        for (int slot = 0; slot < before.length && slot < source.size(); slot++) {
            if (source.getAmountAsInt(slot) < amounts[slot])
                return before[slot];
        }
        return ItemResource.EMPTY;
    }

    private void markFlowing() {
        flowing = true;
    }

    /** The energy handler of a side face, or null on the inventory and pipe faces. */
    public @Nullable EnergyHandler getEnergyHandler(@Nullable Direction side) {
        if (side == null || side.getAxis() == getInventoryFace().getAxis())
            return null;
        int index = side.get3DDataValue();
        if (energyHandlers[index] == null)
            energyHandlers[index] = new PulseHandler();
        return energyHandlers[index];
    }

    /** Only an item pipe on the pipe face connects (docs/TRANSPORT.md 3.2, 10). */
    @Override
    public ConnectOverride overridePipeConnection(PipeType type, Direction with) {
        return type == PipeType.ITEM && with == getPipeFace() ? ConnectOverride.CONNECT : ConnectOverride.DISCONNECT;
    }

    /** Server: the flow record measured every 10 ticks (docs/TRANSPORT.md 9.2), sent when it changed enough (9.3). */
    public FlowRecord getSentRecord() {
        return sent;
    }

    /** Client: the record the renderer reads. */
    public FlowRecord getClientRecord() {
        return clientRecord;
    }

    @Override
    public void updateEntity() {
        super.updateEntity();
        if (!(level instanceof ServerLevel serverLevel) || !flowing)
            return;
        long now = serverLevel.getGameTime();
        if (now % FlowAccumulator.BUCKET_TICKS != 0)
            return;
        FlowRecord record = flow.record(now, false);
        if (record.differsFrom(sent)) {
            sent = record;
            sendNetworkUpdate();
        }
        if (!record.isFlowing() && !sent.isFlowing())
            flowing = false;
    }

    @Override
    public void writePacketPayload(RegistryFriendlyByteBuf data) {
        super.writePacketPayload(data);
        FlowRecord.STREAM_CODEC.encode(data, sent);
    }

    @Override
    public void readPacketPayload(RegistryFriendlyByteBuf data) {
        super.readPacketPayload(data);
        FlowRecord record = FlowRecord.STREAM_CODEC.decode(data);
        clientRecord = record.kind() == PipeType.ITEM ? record : FlowRecord.none(PipeType.ITEM);
    }

    /** A side face's energy handler: stores nothing, an insert is a pulse, nothing comes out. */
    private final class PulseHandler implements EnergyHandler {

        @Override
        public long getAmountAsLong() {
            return 0;
        }

        /** Non-zero, so callers that look at it see a handler that takes energy. */
        @Override
        public long getCapacityAsLong() {
            return MAX_PULSE;
        }

        @Override
        public int insert(int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonNegative(amount);
            return pulse(amount, transaction);
        }

        @Override
        public int extract(int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonNegative(amount);
            return 0;
        }
    }
}
