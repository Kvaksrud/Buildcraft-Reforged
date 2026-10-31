package buildcraftreforged.transport.route;

import buildcraftreforged.transport.TilePipe;
import buildcraftreforged.transport.graph.PipeNetwork;
import buildcraftreforged.transport.graph.PipeNetworks;
import buildcraftreforged.transport.graph.PipeNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * Power transfers through power pipe networks (docs/TRANSPORT.md 7.4): pushes from engines and other energy handlers
 * into a pipe (6.2), routed at once by {@link PowerRouter}. Budgets are in FE, refilled by the pipe's
 * {@link TilePipe#getPowerRefill()} each tick (5.1): the tier's FE/t, or less under a Power Limiter (Q7).
 */
public final class PowerRouting {

    /** How long a request phase answers the Requests Energy trigger before {@link #requestsEnergy} asks again. */
    public static final int REQUEST_TICKS = 20;

    public static final PowerRouter ROUTER = new PowerRouter(new PowerRouter.Pipes() {
        @Override
        public PipeBudget budget(PipeNode node) {
            return ((TilePipe) node.pipe).getBudget();
        }

        @Override
        public long refill(PipeNode node) {
            return ((TilePipe) node.pipe).getPowerRefill();
        }

        @Override
        public PowerPipeState state(PipeNode node) {
            return ((TilePipe) node.pipe).getPowerState();
        }
    }, PowerRouting::handler);

    private PowerRouting() {
    }

    static @Nullable EnergyHandler handler(PipeNetwork.Endpoint endpoint) {
        return endpoint.cache() == null ? null : (EnergyHandler) endpoint.cache().getCapability();
    }

    /**
     * A push of up to {@code amount} FE into the pipe of node {@code start} on side {@code in} from the block beside it:
     * delivers to the receivers and spends budgets in {@code transaction}. Returns what was delivered.
     */
    public static PowerRouter.Result push(ServerLevel level, PipeNetwork network, int start, Direction in, long amount,
            TransactionContext transaction) {
        return ROUTER.push(network, start, BlockPos.offset(network.nodes[start].pos, in), amount, level.getGameTime(),
                ItemRouting.settings().burstSeconds(), transaction);
    }

    /**
     * The Requests Energy trigger (docs/TRANSPORT.md 12, Q21): whether the last request phase through this pipe found
     * more demand than it delivered. When no push has passed the pipe for {@link #REQUEST_TICKS} ticks there was no
     * request phase to ask, so this asks the receivers behind the pipe directly: any demand at all counts.
     */
    public static boolean requestsEnergy(ServerLevel level, TilePipe pipe) {
        PowerPipeState state = pipe.getPowerState();
        if (level.getGameTime() - state.lastRequestTick() < REQUEST_TICKS)
            return state.lastRequestUnmet();
        PipeNetwork network = PipeNetworks.get(level, pipe.getBlockPos());
        if (network == null)
            return false;
        try (Transaction transaction = Transaction.openRoot()) {
            return ROUTER.demand(network, network.indexOf(pipe.getBlockPos().asLong()), transaction) > 0;
        }
    }

    /** The TooMuchEnergy trigger and the overload flicker (docs/TRANSPORT.md 7.4): 60 saturated ticks. */
    public static boolean isOverloaded(ServerLevel level, TilePipe pipe) {
        return pipe.getPowerState().isOverloaded(level.getGameTime(), pipe.getPowerRefill());
    }
}
