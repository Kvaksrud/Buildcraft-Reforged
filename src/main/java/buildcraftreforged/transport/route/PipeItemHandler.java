package buildcraftreforged.transport.route;

import buildcraftreforged.api.transport.IPipeTile.PipeType;
import buildcraftreforged.core.status.IOutputRefusal;
import buildcraftreforged.core.status.StatusReason;
import buildcraftreforged.transport.PipeConnections;
import buildcraftreforged.transport.TilePipe;
import buildcraftreforged.transport.graph.PipeNetwork;
import buildcraftreforged.transport.graph.PipeNetworks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * The item capability of an item pipe on one side (docs/TRANSPORT.md 6.2): one virtual slot that is always empty.
 * Inserting routes the stack through the network at once, entering from this side, and takes only what reaches a
 * destination (4.2.2 {@code TileGenericPipe.injectItem} took any stack whole). Extracting gives nothing. Refuses on a
 * plugged side and on a side facing another item pipe, which the network carries to instead. A machine whose push it
 * refused asks {@link #whyRefused} for its Reason line (#63).
 */
public final class PipeItemHandler implements ResourceHandler<ItemResource>, IOutputRefusal {

    private final TilePipe pipe;
    private final Direction side;

    public PipeItemHandler(TilePipe pipe, Direction side) {
        this.pipe = pipe;
        this.side = side;
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public ItemResource getResource(int index) {
        return ItemResource.EMPTY;
    }

    @Override
    public long getAmountAsLong(int index) {
        return 0;
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        return resource.isEmpty() ? Item.ABSOLUTE_MAX_STACK_SIZE : resource.getMaxStackSize();
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return true;
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (amount == 0 || !(pipe.getLevel() instanceof ServerLevel level) || pipe.isRemoved()
                || pipe.kind != PipeType.ITEM || PipeConnections.hasSide(pipe.getBlockedMask(), side))
            return 0;
        BlockPos neighbour = pipe.getBlockPos().relative(side);
        if (!level.isLoaded(neighbour)
                || level.getBlockEntity(neighbour) instanceof TilePipe other && other.kind == pipe.kind)
            return 0;
        PipeNetwork network = PipeNetworks.get(level, pipe.getBlockPos());
        if (network == null)
            return 0;
        int start = network.indexOf(pipe.getBlockPos().asLong());
        return ItemRouting.push(level, network, start, side, resource, amount, transaction).moved();
    }

    /**
     * #63: a simulated push that finds a destination without capacity, or a walk that finds none while the network
     * still has an item endpoint other than the pushing block (a full chest the walk backed out of), is "Output
     * blocked"; a pipe that leads to no item handler at all is "No destination".
     */
    @Override
    public StatusReason whyRefused(ItemResource resource) {
        if (resource.isEmpty() || !(pipe.getLevel() instanceof ServerLevel level) || pipe.isRemoved()
                || pipe.kind != PipeType.ITEM || PipeConnections.hasSide(pipe.getBlockedMask(), side))
            return StatusReason.NO_DESTINATION;
        PipeNetwork network = PipeNetworks.get(level, pipe.getBlockPos());
        if (network == null)
            return StatusReason.NO_DESTINATION;
        int start = network.indexOf(pipe.getBlockPos().asLong());
        TransferResult result;
        try (Transaction simulation = Transaction.openRoot()) {
            result = ItemRouting.push(level, network, start, side, resource, 1, simulation);
        }
        if (result.reason() != TransferResult.Reason.NO_DESTINATION)
            return StatusReason.OUTPUT_FULL;
        long source = pipe.getBlockPos().relative(side).asLong();
        for (PipeNetwork.Endpoint endpoint : network.endpoints) {
            if (endpoint.pos() != source && ItemRouting.handler(endpoint) != null)
                return StatusReason.OUTPUT_FULL;
        }
        return StatusReason.NO_DESTINATION;
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return 0;
    }
}
