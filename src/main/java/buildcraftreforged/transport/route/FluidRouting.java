package buildcraftreforged.transport.route;

import buildcraftreforged.core.BuildCraftConfiguration;
import buildcraftreforged.transport.TilePipe;
import buildcraftreforged.transport.graph.PipeNetwork;
import buildcraftreforged.transport.graph.PipeNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * Fluid transfers through fluid pipe networks (docs/TRANSPORT.md 7.2, 7.3): pushes from blocks into a pipe (6.2) and
 * pulls by an Extractor (8.2). The walk and the budgets are the item ones ({@link ResourceRouter}) with fluid handlers
 * as endpoints and budgets in mB, refilled by the tier's {@code fluidPerTick} (5.1). A pipe stores no fluid; the 4.2.2
 * one-fluid rule is a lock ({@link FluidPipeState}): every pipe a transfer passed refuses other fluids for
 * {@code transport.fluidLockTicks}.
 */
public final class FluidRouting {

    /** Budget units per mB (docs/TRANSPORT.md 5.1). */
    public static final int FLUID_COST = 1;

    public static final ResourceRouter<FluidResource> ROUTER = new ResourceRouter<>(FLUID_COST, new ResourceRouter.Pipes() {
        @Override
        public PipeBudget budget(PipeNode node) {
            return ((TilePipe) node.pipe).getBudget();
        }

        @Override
        public long refill(PipeNode node) {
            return ((TilePipe) node.pipe).getTierStats().fluidPerTick();
        }
    }, FluidRouting::handler);

    /** {@code transport.fluidLockTicks} before the config loads (docs/TRANSPORT.md 2.2, Q5). */
    public static final int DEFAULT_LOCK_TICKS = 20;

    private FluidRouting() {
    }

    /** The live fluid handler of an endpoint, or null when the block no longer has one. */
    @SuppressWarnings("unchecked")
    public static @Nullable ResourceHandler<FluidResource> handler(PipeNetwork.Endpoint endpoint) {
        return endpoint.cache() == null ? null : (ResourceHandler<FluidResource>) endpoint.cache().getCapability();
    }

    /** The configured {@code transport.fluidLockTicks}, the default before load. */
    public static int lockTicks() {
        return BuildCraftConfiguration.COMMON_SPEC.isLoaded() ? BuildCraftConfiguration.pipeFluidLockTicks.get()
                : DEFAULT_LOCK_TICKS;
    }

    private static FluidPipeState state(PipeNode node) {
        return ((TilePipe) node.pipe).getFluidState();
    }

    /**
     * A push into the pipe of node {@code start} on side {@code in} from the block beside it: walks past pipes locked to
     * other fluids, fills the destination, spends budgets, locks and records the path, all in {@code transaction}.
     * Returns what was moved.
     */
    public static TransferResult push(ServerLevel level, PipeNetwork network, int start, Direction in,
            FluidResource resource, int amount, TransactionContext transaction) {
        return route(network, start, in, BlockPos.offset(network.nodes[start].pos, in), resource, amount,
                level.getGameTime(), level.getRandom(), ItemRouting.settings(), lockTicks(), transaction);
    }

    /**
     * One Extractor attempt at node {@code node} draining {@code source}, the handler of the block on the pipe's side
     * {@code facing} (docs/TRANSPORT.md 8.2): offers {@code min(simulated drain, budget of the source pipe)} of the
     * first slot whose fluid has a destination, one successful transfer at most. The drain and the fill are one
     * transaction.
     */
    public static TransferResult extract(ServerLevel level, PipeNetwork network, int node, Direction facing,
            ResourceHandler<FluidResource> source) {
        ResourceRouter.Settings settings = ItemRouting.settings();
        long now = level.getGameTime();
        long budget = ROUTER.available(network.nodes[node], now, settings) / FLUID_COST;
        if (budget <= 0)
            return new TransferResult(0, TransferResult.Reason.NO_CAPACITY, null);
        long sourcePos = BlockPos.offset(network.nodes[node].pos, facing);
        int lockTicks = lockTicks();

        TransferResult result = TransferResult.SOURCE_EMPTY;
        for (int slot = 0; slot < source.size(); slot++) {
            FluidResource resource = source.getResource(slot);
            if (resource.isEmpty())
                continue;
            int want = (int) Math.min(Math.min(budget, source.getAmountAsLong(slot)), Integer.MAX_VALUE);
            if (want <= 0)
                continue;
            try (Transaction transaction = Transaction.openRoot()) {
                int offered;
                try (Transaction simulation = Transaction.open(transaction)) {
                    offered = source.extract(slot, resource, want, simulation);
                }
                if (offered <= 0)
                    continue;
                TransferResult attempt = route(network, node, facing, sourcePos, resource, offered, now,
                        level.getRandom(), settings, lockTicks, transaction);
                if (!attempt.movedAny()) {
                    if (attempt.reason() == TransferResult.Reason.NO_CAPACITY)
                        return attempt;
                    result = attempt;
                    continue;
                }
                if (source.extract(slot, resource, attempt.moved(), transaction) != attempt.moved())
                    continue; // The source gave less than it promised: roll the whole transfer back.
                transaction.commit();
                return attempt;
            }
        }
        return result;
    }

    /**
     * {@link ResourceRouter#push} for fluids: pipes with a live lock on another fluid are not entered (7.3; a locked
     * start pipe gives NO_DESTINATION), and every pipe of a successful transfer's path is locked to the fluid and
     * records it (9.2), in {@code transaction}.
     */
    static TransferResult route(PipeNetwork network, int start, @Nullable Direction in, long sourcePos,
            FluidResource resource, int amount, long now, RandomSource random, ResourceRouter.Settings settings,
            int lockTicks, TransactionContext transaction) {
        TransferResult result = ROUTER.push(network, start, in, sourcePos, resource, amount, now, random, settings,
                node -> state(node).canPass(resource, now, lockTicks), transaction);
        if (result.movedAny() && result.route() != null)
            record(network, result.route(), in, resource, result.moved(), now, transaction);
        return result;
    }

    /** Locks every pipe of {@code route} to {@code resource} and records {@code moved} mB with its entry and exit sides. */
    private static void record(PipeNetwork network, PipeWalk.Route route, @Nullable Direction in, FluidResource resource,
            int moved, long now, TransactionContext transaction) {
        int[] path = route.path();
        for (int i = 0; i < path.length; i++) {
            PipeNode node = network.nodes[path[i]];
            Direction entry = i == 0 ? in : sideTowards(node, path[i - 1]);
            Direction exit;
            if (i < path.length - 1)
                exit = sideTowards(node, path[i + 1]);
            else
                exit = route.isSink() ? null : network.endpoints[route.endpoint()].side();
            state(node).recordPassed(resource, moved, entry, exit, now, transaction);
        }
        // SEAM(#59): the flow record's accumulator reads FluidPipeState (content, sides, mB passed this tick).
    }

    /** The side of {@code node} that leads to node index {@code other}, or null if none does. */
    static @Nullable Direction sideTowards(PipeNode node, int other) {
        for (Direction side : Direction.values()) {
            if (node.next(side) == other)
                return side;
        }
        return null;
    }
}
