package buildcraftreforged.transport.route;

import buildcraftreforged.api.transport.IPipeTile.PipeType;
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
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * The item capability of an item pipe on one side (docs/TRANSPORT.md 6.2): one virtual slot that is always empty.
 * Inserting routes the stack through the network at once, entering from this side, and takes only what reaches a
 * destination (4.2.2 {@code TileGenericPipe.injectItem} took any stack whole). Extracting gives nothing. Refuses on a
 * plugged side and on a side facing another item pipe, which the network carries to instead.
 */
public final class PipeItemHandler implements ResourceHandler<ItemResource> {

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
                || pipe.kind != PipeType.ITEM || PipeConnections.hasSide(pipe.getPluggedMask(), side))
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

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return 0;
    }
}
