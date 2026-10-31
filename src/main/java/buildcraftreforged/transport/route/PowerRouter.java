package buildcraftreforged.transport.route;

import buildcraftreforged.transport.flow.FlowAccumulator;
import buildcraftreforged.transport.flow.FlowRecord;
import buildcraftreforged.transport.graph.PipeNetwork;
import buildcraftreforged.transport.graph.PipeNode;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * One push of Forge Energy through a power network (docs/TRANSPORT.md 7.4): the 4.2.2 request and send phases of
 * {@code PipeTransportPower}, made instant over a breadth-first tree, with nothing stored in the pipes.
 *
 * <ol>
 * <li><b>Tree.</b> Breadth-first from the pipe that was pushed into, neighbours in side order; every reached pipe has
 * one parent. Every block with an energy handler on a reached pipe is a receiver, once, through the first of its faces
 * the search meets. The pushing block is never a receiver (BuildCraft#1827), on any face.</li>
 * <li><b>Request</b>, children first: a receiver asks for what its handler would take now (a simulated insert of
 * {@link Integer#MAX_VALUE}); a pipe asks for {@code min(budget, sum of what its children and receivers ask)}.</li>
 * <li><b>Send</b>, root first: the root gets {@code min(offered, its request)}; a pipe given {@code y} gives each
 * outlet {@code floor(y × request / sum of requests)} and the remainder 1 FE at a time to outlets in side order whose
 * share is still below their request.</li>
 * <li><b>Deliver</b>: each receiver gets a real insert of its share. What the receivers took is summed up the tree;
 * every pipe spends its budget by what passed it and records it ({@link PowerPipeState}). The total is returned.</li>
 * </ol>
 *
 * Everything happens in the caller's transaction, so a simulated push is a transaction the caller aborts: it delivers,
 * spends and records nothing. A pipe's refill is its tier's FE/t, lowered by a Power Limiter (Q7) through
 * {@link Pipes#refill}.
 */
public final class PowerRouter {

    /** Per-pipe state the router reads and writes. */
    public interface Pipes {
        PipeBudget budget(PipeNode node);

        /** FE per tick: the tier's {@code powerPerTick}, or less under a Power Limiter (docs/TRANSPORT.md Q7). */
        long refill(PipeNode node);

        PowerPipeState state(PipeNode node);

        /** The flow record's accumulator (docs/TRANSPORT.md 9.2), null where none is kept. */
        default @Nullable FlowAccumulator flow(PipeNode node) {
            return null;
        }
    }

    /** The live energy handler of an endpoint, or null when the block no longer has one. */
    public interface Handlers {
        @Nullable EnergyHandler handler(PipeNetwork.Endpoint endpoint);
    }

    /**
     * The tree of one push, for flow records (#59): the pipes reached, in breadth-first order ({@code order[0]} is the
     * root), each pipe's parent node ({@code -1} for the root, {@code -2} outside the tree) and the side of the pipe
     * that faces its parent, and the
     * FE that passed each pipe (indexed by node; 0 for pipes outside the tree). {@code receivers} are the endpoint
     * indices that were asked, {@code delivered} what each took, in the same order.
     */
    public record Tree(int[] order, int[] parent, @Nullable Direction[] parentSide, long[] passed, int[] receivers,
            long[] delivered) {

        /** The side of node {@code node} through which its power arrived; null for the root. */
        public @Nullable Direction entrySide(int node) {
            return parentSide[node];
        }
    }

    /** The outcome of a push: FE delivered, the reason (docs/TRANSPORT.md 6.3) and the tree, if one was built. */
    public record Result(long moved, TransferResult.Reason reason, @Nullable Tree tree) {
        public static final Result SOURCE_EMPTY = new Result(0, TransferResult.Reason.SOURCE_EMPTY, null);

        public boolean movedAny() {
            return moved > 0;
        }
    }

    private static final Direction[] SIDES = Direction.values();

    private final Pipes pipes;
    private final Handlers handlers;
    /** Networks with a push running: a receiver that pushes back into the same network gets nothing. */
    private final Set<PipeNetwork> active = Collections.newSetFromMap(new IdentityHashMap<>());

    public PowerRouter(Pipes pipes, Handlers handlers) {
        this.pipes = pipes;
        this.handlers = handlers;
    }

    /** The budget of a pipe at {@code now}, refilled (docs/TRANSPORT.md 5.2), 1 FE per unit. */
    public long available(PipeNode node, long now, double burstSeconds) {
        long refill = pipes.refill(node);
        return pipes.budget(node).available(now, refill, PipeBudget.cap(refill, burstSeconds, 1));
    }

    /**
     * Pushes up to {@code amount} FE into the network at node {@code start} from the block at {@code sourcePos}
     * ({@link PipeWalk#NO_SOURCE} for none), in {@code transaction}. Returns what the receivers took; the pusher removes
     * that much from itself in the same transaction.
     */
    public Result push(PipeNetwork network, int start, long sourcePos, long amount, long now, double burstSeconds,
            TransactionContext transaction) {
        if (amount <= 0)
            return Result.SOURCE_EMPTY;
        if (!active.add(network))
            return new Result(0, TransferResult.Reason.NO_CAPACITY, null);
        try {
            return run(network, start, sourcePos, amount, now, burstSeconds, transaction);
        } finally {
            active.remove(network);
        }
    }

    private Result run(PipeNetwork network, int start, long sourcePos, long amount, long now, double burstSeconds,
            TransactionContext transaction) {
        int size = network.nodes.length;

        // 1. Tree.
        int[] order = new int[size];
        int[] parent = new int[size];
        Direction[] parentSide = new Direction[size];
        Arrays.fill(parent, -2); // not reached
        int[] receiverNode = new int[network.endpoints.length];
        Arrays.fill(receiverNode, -1);
        LongOpenHashSet receiverBlocks = new LongOpenHashSet();
        int count = 0;
        order[count++] = start;
        parent[start] = -1;
        for (int head = 0; head < count; head++) {
            int node = order[head];
            PipeNode pipe = network.nodes[node];
            for (Direction side : SIDES) {
                int next = pipe.next(side);
                if (PipeNode.isNode(next)) {
                    if (parent[next] == -2) {
                        parent[next] = node;
                        parentSide[next] = side.getOpposite();
                        order[count++] = next;
                    }
                } else if (PipeNode.isEndpoint(next)) {
                    int endpoint = PipeNode.endpointIndex(next);
                    long pos = network.endpoints[endpoint].pos();
                    if (pos != sourcePos && receiverBlocks.add(pos))
                        receiverNode[endpoint] = node;
                }
            }
        }

        // 2. Request, children first (reverse breadth-first order).
        long[] demand = new long[network.endpoints.length];
        long[] want = new long[size]; // what the receivers behind each pipe ask for, before its budget
        long[] request = new long[size];
        long[] budget = new long[size];
        boolean anyDemand = false;
        for (int e = 0; e < demand.length; e++) {
            if (receiverNode[e] < 0)
                continue;
            demand[e] = simulateInsert(handlers.handler(network.endpoints[e]), Integer.MAX_VALUE, transaction);
            want[receiverNode[e]] += demand[e];
            anyDemand |= demand[e] > 0;
        }
        for (int i = count - 1; i >= 0; i--) {
            int node = order[i];
            budget[node] = available(network.nodes[node], now, burstSeconds);
            request[node] = Math.min(budget[node], want[node]);
            if (parent[node] >= 0)
                want[parent[node]] += request[node];
        }
        if (!anyDemand)
            return new Result(0, TransferResult.Reason.NO_DESTINATION, null);

        // 3. Send, root first.
        long[] given = new long[size];
        long[] share = new long[network.endpoints.length];
        given[start] = Math.min(amount, request[start]);
        for (int i = 0; i < count; i++) {
            int node = order[i];
            long y = given[node];
            if (y <= 0)
                continue;
            PipeNode pipe = network.nodes[node];
            long total = 0;
            for (Direction side : SIDES)
                total += outletRequest(pipe.next(side), node, parent, receiverNode, request, demand);
            if (total <= 0)
                continue;
            long left = y;
            for (Direction side : SIDES) {
                int next = pipe.next(side);
                long r = outletRequest(next, node, parent, receiverNode, request, demand);
                if (r <= 0)
                    continue;
                long s = y * r / total;
                left -= s;
                give(next, s, given, share);
            }
            for (Direction side : SIDES) {
                if (left <= 0)
                    break;
                int next = pipe.next(side);
                long r = outletRequest(next, node, parent, receiverNode, request, demand);
                if (r > 0 && outletGiven(next, given, share) < r) {
                    give(next, 1, given, share);
                    left--;
                }
            }
        }

        // 4. Deliver, then sum what was taken up the tree.
        long[] passed = new long[size];
        int receiverCount = 0;
        for (int e = 0; e < demand.length; e++)
            if (receiverNode[e] >= 0)
                receiverCount++;
        int[] receivers = new int[receiverCount];
        long[] delivered = new long[receiverCount];
        long[] tookBy = new long[demand.length];
        int r = 0;
        for (int e = 0; e < demand.length; e++) {
            if (receiverNode[e] < 0)
                continue;
            receivers[r] = e;
            if (share[e] > 0) {
                EnergyHandler handler = handlers.handler(network.endpoints[e]);
                long took = handler == null ? 0
                        : handler.insert((int) Math.min(share[e], Integer.MAX_VALUE), transaction);
                delivered[r] = took;
                tookBy[e] = took;
                passed[receiverNode[e]] += took;
            }
            r++;
        }
        for (int i = count - 1; i > 0; i--) {
            int node = order[i];
            passed[parent[node]] += passed[node];
        }
        for (int i = 0; i < count; i++) {
            int node = order[i];
            PipeNode pipe = network.nodes[node];
            if (passed[node] > 0) {
                pipes.budget(pipe).spend(passed[node], transaction);
                pipes.state(pipe).recordPassed(passed[node], now, pipes.refill(pipe), transaction);
            }
            if (want[node] > 0)
                pipes.state(pipe).recordRequest(want[node] > passed[node], now, transaction);
        }
        recordFlow(network, order, count, parent, parentSide, passed, receiverNode, tookBy, sourcePos, now,
                transaction);

        long moved = passed[start];
        TransferResult.Reason reason = moved <= 0 ? TransferResult.Reason.NO_CAPACITY
                : moved == amount ? TransferResult.Reason.MOVED : TransferResult.Reason.PARTIAL;
        return new Result(moved, reason, new Tree(Arrays.copyOf(order, count), parent, parentSide, passed, receivers,
                delivered));
    }

    /**
     * Adds what passed each pipe of the tree to its flow record (docs/TRANSPORT.md 9.2), in {@code transaction}. A pipe
     * is entered from its parent (the root from the pushing block, or its centre when that is not beside it) and left
     * by the outlet that took the most, first in side order on a tie: one exit per pipe, as the record holds one.
     */
    private void recordFlow(PipeNetwork network, int[] order, int count, int[] parent, Direction[] parentSide,
            long[] passed, int[] receiverNode, long[] tookBy, long sourcePos, long now, TransactionContext transaction) {
        for (int i = 0; i < count; i++) {
            int node = order[i];
            if (passed[node] <= 0)
                continue;
            PipeNode pipe = network.nodes[node];
            FlowAccumulator flow = pipes.flow(pipe);
            if (flow == null)
                continue;
            Direction entry = parent[node] >= 0 ? parentSide[node] : sideTo(pipe.pos, sourcePos);
            Direction exit = null;
            long most = 0;
            for (Direction side : SIDES) {
                int next = pipe.next(side);
                long amount = 0;
                if (PipeNode.isNode(next) && parent[next] == node)
                    amount = passed[next];
                else if (PipeNode.isEndpoint(next) && receiverNode[PipeNode.endpointIndex(next)] == node)
                    amount = tookBy[PipeNode.endpointIndex(next)];
                if (amount > most) {
                    most = amount;
                    exit = side;
                }
            }
            flow.add(passed[node], null, FlowRecord.NO_COLOUR, entry, exit, now, transaction);
        }
    }

    /** The side of the block at {@code from} that faces {@code to}, or null when they are not neighbours. */
    private static @Nullable Direction sideTo(long from, long to) {
        for (Direction side : SIDES) {
            if (BlockPos.offset(from, side) == to)
                return side;
        }
        return null;
    }

    /**
     * What the receivers behind {@code start} ask for now, with no budget applied and no source excluded: the demand a
     * push into that pipe would find. Simulated in {@code transaction}.
     */
    public long demand(PipeNetwork network, int start, TransactionContext transaction) {
        if (!active.add(network))
            return 0;
        try (Transaction simulation = Transaction.open(transaction)) {
            long total = 0;
            boolean[] reached = new boolean[network.nodes.length];
            LongOpenHashSet receiverBlocks = new LongOpenHashSet();
            int[] queue = new int[network.nodes.length];
            int count = 0;
            queue[count++] = start;
            reached[start] = true;
            for (int head = 0; head < count; head++) {
                PipeNode pipe = network.nodes[queue[head]];
                for (Direction side : SIDES) {
                    int next = pipe.next(side);
                    if (PipeNode.isNode(next) && !reached[next]) {
                        reached[next] = true;
                        queue[count++] = next;
                    } else if (PipeNode.isEndpoint(next)) {
                        PipeNetwork.Endpoint endpoint = network.endpoints[PipeNode.endpointIndex(next)];
                        if (receiverBlocks.add(endpoint.pos()))
                            total += simulateInsert(handlers.handler(endpoint), Integer.MAX_VALUE, simulation);
                    }
                }
            }
            return total;
        } finally {
            active.remove(network);
        }
    }

    /** The request of the outlet {@code next} of pipe {@code node}: a tree child or a receiver of this pipe, else 0. */
    private static long outletRequest(int next, int node, int[] parent, int[] receiverNode, long[] request,
            long[] demand) {
        if (PipeNode.isNode(next))
            return parent[next] == node ? request[next] : 0;
        if (PipeNode.isEndpoint(next)) {
            int endpoint = PipeNode.endpointIndex(next);
            return receiverNode[endpoint] == node ? demand[endpoint] : 0;
        }
        return 0;
    }

    private static long outletGiven(int next, long[] given, long[] share) {
        return PipeNode.isNode(next) ? given[next] : share[PipeNode.endpointIndex(next)];
    }

    private static void give(int next, long amount, long[] given, long[] share) {
        if (PipeNode.isNode(next))
            given[next] += amount;
        else
            share[PipeNode.endpointIndex(next)] += amount;
    }

    /** What {@code handler} would take, in a nested transaction that is rolled back. */
    static long simulateInsert(@Nullable EnergyHandler handler, int amount, TransactionContext transaction) {
        if (handler == null)
            return 0;
        try (Transaction simulation = Transaction.open(transaction)) {
            return handler.insert(amount, simulation);
        }
    }
}
