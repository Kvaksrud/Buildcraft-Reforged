package buildcraftreforged.transport.route;

import buildcraftreforged.transport.PipeModuleType;
import buildcraftreforged.transport.graph.PipeNetwork;
import buildcraftreforged.transport.graph.PipeNode;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.transfer.resource.Resource;
import org.jspecify.annotations.Nullable;

/**
 * The routing walk of docs/TRANSPORT.md 4.2: from the start pipe, each junction orders its candidate sides by a random
 * permutation and tries them in turn; an accepting endpoint ends the walk, a pipe is entered, and a pipe with no way on
 * is backed out of (Q1) so the junction before it tries its next candidate. This is the 4.2.2 junction rule
 * ({@code PipeTransportItems.getPossibleMovements} + {@code Collections.shuffle}): every valid side, pipe or inventory,
 * is equally likely, with no look-ahead past a pipe.
 *
 * <p>The same walk serves items, fluids and power; {@link Rules} holds what differs. Iterative, so a long line does not
 * grow the Java stack.
 */
public final class PipeWalk {

    /** {@code sourcePos} for a walk that has no source block (no endpoint is excluded). */
    public static final long NO_SOURCE = Long.MIN_VALUE;

    /** The walk colour of an item that has none (docs/TRANSPORT.md 4.2; else an {@code EnumColor} ordinal). */
    public static final int NO_COLOUR = -1;

    private static final Direction[] SIDES = Direction.values();

    private PipeWalk() {
    }

    /** What a walk asks about pipes and endpoints. */
    public interface Rules {
        /** Whether the walk may enter this pipe. #51 refuses pipes locked to another fluid here (7.3). */
        default boolean canEnter(PipeNode node) {
            return true;
        }

        /** Whether the walk ends at this pipe, which destroys what reaches it: the Void module (4.2 step 3, 8.7). */
        default boolean isSink(PipeNode node) {
            return node.module == PipeModuleType.VOID;
        }

        /** Whether the pipe's module lets the walk leave by {@code side}: the Director allows only its facing (8.4). */
        default boolean allowsOutput(PipeNode node, Direction side) {
            return node.pipe.allowsOutput(side);
        }

        /** A simulated insert into this endpoint accepts at least one unit (4.2 step 4). */
        boolean accepts(PipeNetwork.Endpoint endpoint);

        /** What is being routed, for the modules that filter by it (the Sorter, 8.5); null when unknown. */
        default @Nullable Resource resource() {
            return null;
        }

        /**
         * Steps 5 and 6: the pipe's module filter and order (Sorter 8.5, Colour Sorter 8.9). Reorders
         * {@code sides[0 .. count)} (side indices) into the order to try them in and returns how many to try, or -1 to
         * leave them to the uniform permutation. {@code colour} is the walk colour the pipe was entered with.
         */
        default int order(PipeNode node, int[] sides, int count, int colour, RandomSource random) {
            return node.pipe.orderCandidates(sides, count, resource(), colour, random);
        }

        /** The walk colour once the pipe has been entered (the Colour Tag sets it, 8.8), from the one it entered with. */
        default int colourAfter(PipeNode node, int colour) {
            return node.pipe.walkColour(colour);
        }
    }

    /**
     * Where a walk ended. {@code path} lists node indices from the start pipe to the pipe at the destination. Exactly one
     * of {@code endpoint} (an index into {@link PipeNetwork#endpoints}) and {@code sink} (a node index) is not -1.
     */
    public record Route(int[] path, int endpoint, int sink) {
        public boolean isSink() {
            return sink >= 0;
        }

        /** The node the destination is at: the sink itself, or the pipe next to the endpoint. */
        public int last() {
            return path[path.length - 1];
        }
    }

    private static final class Frame {
        final int node;
        final int[] sides;
        final int count;
        /** The walk colour for this pipe's children: set once the pipe is entered, so backing out restores it. */
        final int colour;
        int cursor;

        Frame(int node, int[] sides, int count, int colour) {
            this.node = node;
            this.sides = sides;
            this.count = count;
            this.colour = colour;
        }
    }

    /** The uniform random permutation of step 6 over {@code sides[0 .. count)}. */
    public static void shuffle(int[] sides, int count, RandomSource random) {
        for (int i = count - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int swap = sides[i];
            sides[i] = sides[j];
            sides[j] = swap;
        }
    }

    /**
     * Walks from node {@code start}, entered on side {@code in} (null when the transfer starts inside the pipe). No
     * endpoint at {@code sourcePos} is ever a destination: the block a transfer comes from never receives it back, on
     * any face. Returns null when no destination was found or more than {@code maxVisits} pipes were visited (Q20).
     */
    public static @Nullable Route walk(PipeNetwork network, int start, @Nullable Direction in, long sourcePos, Rules rules,
            RandomSource random, int maxVisits) {
        return walk(network, start, in, sourcePos, rules, random, maxVisits, NO_COLOUR);
    }

    /**
     * {@link #walk(PipeNetwork, int, Direction, long, Rules, RandomSource, int)} for an item that starts with walk colour
     * {@code colour} ({@link #NO_COLOUR} for none): the Filtered Colour Tag tags what it extracts (8.10).
     */
    public static @Nullable Route walk(PipeNetwork network, int start, @Nullable Direction in, long sourcePos, Rules rules,
            RandomSource random, int maxVisits, int colour) {
        PipeNode[] nodes = network.nodes;
        if (!rules.canEnter(nodes[start]))
            return null;
        boolean[] visited = new boolean[nodes.length];
        IntArrayList path = new IntArrayList();
        List<Frame> stack = new ArrayList<>();
        int visits = 0;

        int node = start;
        Direction from = in;
        while (true) {
            if (node >= 0) {
                // Enter a pipe (steps 1-6).
                if (++visits > maxVisits)
                    return null;
                visited[node] = true;
                path.add(node);
                PipeNode pipe = nodes[node];
                if (rules.isSink(pipe))
                    return new Route(path.toIntArray(), -1, node);
                int[] sides = new int[6];
                int count = 0;
                for (Direction side : SIDES) {
                    if (side == from)
                        continue;
                    int next = pipe.next(side);
                    if (next == PipeNode.NONE || !rules.allowsOutput(pipe, side))
                        continue;
                    if (PipeNode.isNode(next)) {
                        if (visited[next] || !rules.canEnter(nodes[next]))
                            continue;
                    } else {
                        PipeNetwork.Endpoint endpoint = network.endpoints[PipeNode.endpointIndex(next)];
                        if (endpoint.pos() == sourcePos || !rules.accepts(endpoint))
                            continue;
                    }
                    sides[count++] = side.get3DDataValue();
                }
                // Steps 5-6 with the colour the pipe was entered with; then the pipe's own colour applies onward.
                int entered = stack.isEmpty() ? colour : stack.getLast().colour;
                int ordered = rules.order(pipe, sides, count, entered, random);
                if (ordered < 0)
                    shuffle(sides, count, random);
                else
                    count = Math.min(ordered, count);
                stack.add(new Frame(node, sides, count, rules.colourAfter(pipe, entered)));
            }

            // Step 7: try the next candidate of the innermost junction; step 8: back out of a dead end.
            Frame frame = stack.getLast();
            if (frame.cursor == frame.count) {
                stack.removeLast();
                path.removeInt(path.size() - 1);
                if (stack.isEmpty())
                    return null;
                node = -1;
                continue;
            }
            Direction side = SIDES[frame.sides[frame.cursor++]];
            int next = nodes[frame.node].next(side);
            if (PipeNode.isEndpoint(next))
                return new Route(path.toIntArray(), PipeNode.endpointIndex(next), -1);
            if (visited[next]) {
                // Reached through another branch since this junction listed it.
                node = -1;
                continue;
            }
            node = next;
            from = side.getOpposite();
        }
    }
}
