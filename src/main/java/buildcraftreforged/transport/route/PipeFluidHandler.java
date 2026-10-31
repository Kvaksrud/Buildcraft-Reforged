package buildcraftreforged.transport.route;

import buildcraftreforged.api.transport.IPipeTile.PipeType;
import buildcraftreforged.transport.PipeConnections;
import buildcraftreforged.transport.TilePipe;
import buildcraftreforged.transport.graph.PipeNetwork;
import buildcraftreforged.transport.graph.PipeNetworks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * The fluid capability of a fluid pipe on one side (docs/TRANSPORT.md 6.2): one tank that is always empty, of capacity
 * the tier's {@code fluidPerTick}. Inserting routes the fluid through the network at once ({@link FluidRouting}),
 * entering from this side, and takes only what reached a destination. Draining gives nothing, as 4.2.2
 * {@code PipeTransportFluids.drain} returned null. Refuses on a plugged side and on a side facing another fluid pipe,
 * decided at call time, as {@link PipeItemHandler}.
 */
public final class PipeFluidHandler implements ResourceHandler<FluidResource> {

    private final TilePipe pipe;
    private final Direction side;

    public PipeFluidHandler(TilePipe pipe, Direction side) {
        this.pipe = pipe;
        this.side = side;
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public FluidResource getResource(int index) {
        return FluidResource.EMPTY;
    }

    @Override
    public long getAmountAsLong(int index) {
        return 0;
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        return pipe.getTierStats().fluidPerTick();
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return true;
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (amount == 0 || !(pipe.getLevel() instanceof ServerLevel level) || pipe.isRemoved()
                || pipe.kind != PipeType.FLUID || PipeConnections.hasSide(pipe.getPluggedMask(), side))
            return 0;
        BlockPos neighbour = pipe.getBlockPos().relative(side);
        if (!level.isLoaded(neighbour)
                || level.getBlockEntity(neighbour) instanceof TilePipe other && other.kind == pipe.kind)
            return 0;
        PipeNetwork network = PipeNetworks.get(level, pipe.getBlockPos());
        if (network == null)
            return 0;
        int start = network.indexOf(pipe.getBlockPos().asLong());
        return FluidRouting.push(level, network, start, side, resource, amount, transaction).moved();
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        return 0;
    }
}
