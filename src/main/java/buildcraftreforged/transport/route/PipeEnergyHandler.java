package buildcraftreforged.transport.route;

import buildcraftreforged.api.transport.IPipeTile.PipeType;
import buildcraftreforged.transport.PipeConnections;
import buildcraftreforged.transport.TilePipe;
import buildcraftreforged.transport.graph.PipeNetwork;
import buildcraftreforged.transport.graph.PipeNetworks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * The energy capability of a power pipe on one side (docs/TRANSPORT.md 6.2): it stores nothing (amount 0) and
 * inserting routes the FE through the network at once ({@link PowerRouting}), entering from this side, taking only what
 * the receivers took. Every power pipe accepts pushes from any adjacent block (Q6). Extracting gives nothing. Refuses on
 * a plugged side and on a side facing another power pipe, decided at call time, as {@link PipeItemHandler}.
 *
 * <p>The capacity is the pipe's refill per tick, so callers that look at it see a non-zero handler.
 */
public final class PipeEnergyHandler implements EnergyHandler {

    private final TilePipe pipe;
    private final Direction side;

    public PipeEnergyHandler(TilePipe pipe, Direction side) {
        this.pipe = pipe;
        this.side = side;
    }

    @Override
    public long getAmountAsLong() {
        return 0;
    }

    @Override
    public long getCapacityAsLong() {
        return pipe.getPowerRefill();
    }

    @Override
    public int insert(int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonNegative(amount);
        if (amount == 0 || !(pipe.getLevel() instanceof ServerLevel level) || pipe.isRemoved()
                || pipe.kind != PipeType.POWER || PipeConnections.hasSide(pipe.getBlockedMask(), side))
            return 0;
        BlockPos neighbour = pipe.getBlockPos().relative(side);
        if (!level.isLoaded(neighbour)
                || level.getBlockEntity(neighbour) instanceof TilePipe other && other.kind == pipe.kind)
            return 0;
        PipeNetwork network = PipeNetworks.get(level, pipe.getBlockPos());
        if (network == null)
            return 0;
        int start = network.indexOf(pipe.getBlockPos().asLong());
        return (int) PowerRouting.push(level, network, start, side, amount, transaction).moved();
    }

    @Override
    public int extract(int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonNegative(amount);
        return 0;
    }
}
