package buildcraftreforged.transport.route;

import buildcraftreforged.transport.graph.PipeNetwork;
import buildcraftreforged.transport.graph.PipeNode;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * One transfer of a resource through a network (docs/TRANSPORT.md 4, 5.3, 6.4): walk to a destination, take the
 * smallest of what was offered, what the destination accepts and what every pipe on the path has budget for, insert
 * that, and spend it on every pipe of the path. Everything happens in the caller's transaction, so a simulation is a
 * transaction the caller aborts, and a real transfer is atomic: the destination insert, the budgets and (for callers
 * that extract) the source extract commit together or not at all.
 *
 * <p>Items and fluids share this class (#50, #51); they differ in the cost of one unit and in how a pipe's refill and
 * an endpoint's handler are found.
 */
public final class ResourceRouter<R extends Resource> {

    /** Per-pipe state the router reads: the budget and its refill per tick, in budget units. */
    public interface Pipes {
        PipeBudget budget(PipeNode node);

        long refill(PipeNode node);
    }

    /** The live handler of an endpoint, or null when the block no longer has one. */
    public interface Handlers<R extends Resource> {
        @Nullable ResourceHandler<R> handler(PipeNetwork.Endpoint endpoint);
    }

    /** {@code transport.burstSeconds} and {@code transport.maxRouteVisits}, read once per transfer. */
    public record Settings(double burstSeconds, int maxRouteVisits) {
        public static final Settings DEFAULTS = new Settings(1.0, 4096);
    }

    /** Budget units one unit of transfer costs: 20 per item (an item-tick is 1/20 item), 1 per mB. */
    public final int costPerUnit;
    private final Pipes pipes;
    private final Handlers<R> handlers;

    public ResourceRouter(int costPerUnit, Pipes pipes, Handlers<R> handlers) {
        this.costPerUnit = costPerUnit;
        this.pipes = pipes;
        this.handlers = handlers;
    }

    /** The budget of a pipe at {@code now}, refilled (docs/TRANSPORT.md 5.2). */
    public long available(PipeNode node, long now, Settings settings) {
        long refill = pipes.refill(node);
        return pipes.budget(node).available(now, refill, PipeBudget.cap(refill, settings.burstSeconds(), costPerUnit));
    }

    /** How many units the pipes of {@code path} can all carry at {@code now}: the bottleneck of 5.3. */
    public long capacity(PipeNetwork network, int[] path, long now, Settings settings) {
        long units = Long.MAX_VALUE;
        for (int node : path) {
            units = Math.min(units, available(network.nodes[node], now, settings) / costPerUnit);
        }
        return units;
    }

    /**
     * Routes up to {@code amount} of {@code resource} into the network at node {@code start}, entering on side
     * {@code in} (null: from inside the pipe) from the block at {@code sourcePos} ({@link PipeWalk#NO_SOURCE} for none),
     * and inserts it at the destination in {@code transaction}. Returns what was moved; the caller removes that much
     * from wherever it came from, in the same transaction.
     */
    public TransferResult push(PipeNetwork network, int start, @Nullable Direction in, long sourcePos, R resource,
            int amount, long now, RandomSource random, Settings settings, TransactionContext transaction) {
        if (amount <= 0 || resource.isEmpty())
            return TransferResult.SOURCE_EMPTY;

        PipeWalk.Route route = PipeWalk.walk(network, start, in, sourcePos,
                endpoint -> simulateInsert(handlers.handler(endpoint), resource, 1, transaction) > 0, random,
                settings.maxRouteVisits());
        if (route == null)
            return TransferResult.NO_DESTINATION;

        ResourceHandler<R> destination = route.isSink() ? null : handlers.handler(network.endpoints[route.endpoint()]);
        long units = route.isSink() ? amount : simulateInsert(destination, resource, amount, transaction);
        units = Math.min(units, capacity(network, route.path(), now, settings));
        if (units <= 0)
            return new TransferResult(0, TransferResult.Reason.NO_CAPACITY, route);

        int moved = route.isSink() ? (int) units
                : ResourceHandlerUtil.insertStacking(destination, resource, (int) units, transaction);
        if (moved <= 0)
            return new TransferResult(0, TransferResult.Reason.NO_CAPACITY, route);
        for (int node : route.path()) {
            pipes.budget(network.nodes[node]).spend((long) moved * costPerUnit, transaction);
        }
        // SEAM(#59): record the flow on every pipe of route.path(), as part of this transaction.
        return new TransferResult(moved, moved == amount ? TransferResult.Reason.MOVED : TransferResult.Reason.PARTIAL,
                route);
    }

    /** What {@code handler} would take, in a nested transaction that is rolled back. */
    static <R extends Resource> int simulateInsert(@Nullable ResourceHandler<R> handler, R resource, int amount,
            TransactionContext transaction) {
        if (handler == null)
            return 0;
        try (Transaction simulation = Transaction.open(transaction)) {
            return ResourceHandlerUtil.insertStacking(handler, resource, amount, simulation);
        }
    }
}
