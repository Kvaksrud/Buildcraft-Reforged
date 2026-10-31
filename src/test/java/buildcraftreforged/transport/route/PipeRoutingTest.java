package buildcraftreforged.transport.route;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import buildcraftreforged.api.transport.IPipeTile.PipeType;
import buildcraftreforged.transport.PipeConnections;
import buildcraftreforged.transport.PipeModuleType;
import buildcraftreforged.transport.graph.PipeNetwork;
import buildcraftreforged.transport.graph.PipeNetworkManager;
import buildcraftreforged.transport.graph.PipeNode;
import buildcraftreforged.transport.graph.PipeWorld;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * docs/TRANSPORT.md section 14, #50: the walk, budgets and transfers on a stub level, a grid of pipes and "chests" in
 * the plane y = 0 given as text rows ({@code P} pipe, {@code V} pipe with a Void module, {@code C} chest, {@code .}
 * nothing). Row r is z = r, column c is x = c. Every pipe has the same refill.
 */
class PipeRoutingTest {

    record Thing(String name) implements Resource {
        @Override
        public boolean isEmpty() {
            return false;
        }
    }

    private static final Thing STONE = new Thing("stone");

    /** One slot of unlimited kinds; {@code capacity} items in all. */
    static final class Chest extends SnapshotJournal<Integer> implements ResourceHandler<Thing> {
        int stored;
        int capacity;

        Chest(int capacity) {
            this.capacity = capacity;
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public Thing getResource(int index) {
            return STONE;
        }

        @Override
        public long getAmountAsLong(int index) {
            return stored;
        }

        @Override
        public long getCapacityAsLong(int index, Thing resource) {
            return capacity;
        }

        @Override
        public boolean isValid(int index, Thing resource) {
            return true;
        }

        @Override
        public int insert(int index, Thing resource, int amount, TransactionContext transaction) {
            int moved = Math.min(amount, capacity - stored);
            if (moved > 0) {
                updateSnapshots(transaction);
                stored += moved;
            }
            return moved;
        }

        @Override
        public int extract(int index, Thing resource, int amount, TransactionContext transaction) {
            return 0;
        }

        @Override
        protected Integer createSnapshot() {
            return stored;
        }

        @Override
        protected void revertToSnapshot(Integer snapshot) {
            stored = snapshot;
        }
    }

    private static final class StubPipe implements PipeWorld.Pipe {
        final byte mask;
        final @Nullable PipeModuleType module;
        /** A Director's facing: the only side the walk may leave by. */
        @Nullable Direction output;

        StubPipe(byte mask, @Nullable PipeModuleType module) {
            this.mask = mask;
            this.module = module;
        }

        @Override
        public boolean allowsOutput(Direction side) {
            return module != PipeModuleType.DIRECTOR || side == output;
        }

        @Override
        public PipeType getPipeType() {
            return PipeType.ITEM;
        }

        @Override
        public int getTier() {
            return 1;
        }

        @Override
        public byte getConnectionMask() {
            return mask;
        }

        @Override
        public @Nullable PipeModuleType getModuleType() {
            return module;
        }
    }

    private static final class Grid implements PipeWorld {
        final Long2ObjectOpenHashMap<StubPipe> pipes = new Long2ObjectOpenHashMap<>();
        final Map<Long, Chest> chests = new HashMap<>();
        final Map<PipeNode, PipeBudget> budgets = new HashMap<>();
        long refill = 1_000_000;
        final ResourceRouter<Thing> router = new ResourceRouter<>(ItemRouting.ITEM_COST, new ResourceRouter.Pipes() {
            @Override
            public PipeBudget budget(PipeNode node) {
                return budgets.computeIfAbsent(node, n -> {
                    PipeBudget budget = new PipeBudget();
                    budget.start(0); // every pipe was placed at tick 0
                    return budget;
                });
            }

            @Override
            public long refill(PipeNode node) {
                return refill;
            }
        }, endpoint -> chests.get(endpoint.pos()));
        final PipeNetwork network;

        Grid(String... rows) {
            for (int z = 0; z < rows.length; z++) {
                for (int x = 0; x < rows[z].length(); x++) {
                    long pos = BlockPos.asLong(x, 0, z);
                    switch (rows[z].charAt(x)) {
                    case 'C' -> chests.put(pos, new Chest(Integer.MAX_VALUE));
                    case 'P', 'V', 'D' -> pipes.put(pos, null);
                    default -> {
                    }
                    }
                }
            }
            for (int z = 0; z < rows.length; z++) {
                for (int x = 0; x < rows[z].length(); x++) {
                    char c = rows[z].charAt(x);
                    if (c != 'P' && c != 'V' && c != 'D')
                        continue;
                    long pos = BlockPos.asLong(x, 0, z);
                    byte mask = 0;
                    for (Direction side : Direction.values()) {
                        long other = BlockPos.offset(pos, side);
                        mask = PipeConnections.with(mask, side, pipes.containsKey(other) || chests.containsKey(other));
                    }
                    pipes.put(pos, new StubPipe(mask, switch (c) {
                    case 'V' -> PipeModuleType.VOID;
                    case 'D' -> PipeModuleType.DIRECTOR;
                    default -> null;
                    }));
                }
            }
            network = new PipeNetworkManager(this).get(pipes.keySet().longIterator().nextLong());
        }

        @Override
        public @Nullable Pipe getPipe(long pos) {
            return pipes.get(pos);
        }

        @Override
        public boolean canBeEndpoint(long pos) {
            return !pipes.containsKey(pos);
        }

        @Override
        public @Nullable BlockCapabilityCache<?, Direction> createEndpointCache(PipeType kind, long pos, Direction face,
                BooleanSupplier isValid, Runnable onInvalidate) {
            return null;
        }

        int node(int x, int z) {
            return network.indexOf(BlockPos.asLong(x, 0, z));
        }

        Chest chest(int x, int z) {
            return chests.get(BlockPos.asLong(x, 0, z));
        }

        PipeWalk.@Nullable Route walk(int x, int z, @Nullable Direction in, RandomSource random) {
            return walk(x, z, in, random, 4096);
        }

        PipeWalk.@Nullable Route walk(int x, int z, @Nullable Direction in, RandomSource random, int maxVisits) {
            long source = in == null ? PipeWalk.NO_SOURCE : BlockPos.offset(BlockPos.asLong(x, 0, z), in);
            return PipeWalk.walk(network, node(x, z), in, source,
                    endpoint -> chests.get(endpoint.pos()).stored < chests.get(endpoint.pos()).capacity, random, maxVisits);
        }

        long destination(PipeWalk.Route route) {
            return route.isSink() ? network.nodes[route.sink()].pos : network.endpoints[route.endpoint()].pos();
        }

        TransferResult push(int x, int z, Direction in, int amount, long now, boolean commit) {
            try (Transaction transaction = Transaction.openRoot()) {
                TransferResult result = router.push(network, node(x, z), in, BlockPos.offset(BlockPos.asLong(x, 0, z), in),
                        STONE, amount, now, RandomSource.create(1), ResourceRouter.Settings.DEFAULTS, transaction);
                if (commit)
                    transaction.commit();
                return result;
            }
        }
    }

    private static long pos(int x, int z) {
        return BlockPos.asLong(x, 0, z);
    }

    // The walk (docs/TRANSPORT.md 4.2)

    @Test
    void straightLineReachesTheChest() {
        Grid grid = new Grid("CPPPC");
        PipeWalk.Route route = grid.walk(1, 0, Direction.WEST, RandomSource.create(1));
        assertNotNull(route);
        assertEquals(pos(4, 0), grid.destination(route));
        int[] line = {grid.node(1, 0), grid.node(2, 0), grid.node(3, 0)};
        assertArrayEquals(line, route.path());
    }

    @Test
    void junctionWithTwoChestsSplitsEvenly() {
        Grid grid = new Grid(
                ".C.",
                "CP.",
                ".C.");
        assertEvenSplit(grid, 1, 1, Direction.WEST, pos(1, 0), pos(1, 2));
    }

    @Test
    void junctionHasNoLookAhead() {
        // One chest beside the junction, one at the end of three more pipes: still 50 % each (4.2.2 rule).
        Grid grid = new Grid(
                ".C...",
                "CPPPC");
        assertEvenSplit(grid, 1, 1, Direction.WEST, pos(1, 0), pos(4, 1));
    }

    private static void assertEvenSplit(Grid grid, int x, int z, Direction in, long a, long b) {
        RandomSource random = RandomSource.create(50);
        int toA = 0;
        int walks = 10_000;
        for (int i = 0; i < walks; i++) {
            PipeWalk.Route route = grid.walk(x, z, in, random);
            assertNotNull(route);
            long destination = grid.destination(route);
            assertTrue(destination == a || destination == b);
            if (destination == a)
                toA++;
        }
        assertEquals(0.5, toA / (double) walks, 0.02);
    }

    @Test
    void deadEndIsBackedOutOf() {
        Grid grid = new Grid(
                ".PPP",
                "CP..",
                ".P..",
                ".PPC");
        RandomSource random = RandomSource.create(7);
        for (int i = 0; i < 200; i++) {
            PipeWalk.Route route = grid.walk(1, 1, Direction.WEST, random);
            assertNotNull(route);
            assertEquals(pos(3, 3), grid.destination(route));
            assertEquals(grid.node(2, 3), route.last());
        }
    }

    @Test
    void loopTerminatesAndReachesTheChest() {
        Grid grid = new Grid(
                "PPP.",
                "P.PC",
                "PPP.");
        RandomSource random = RandomSource.create(3);
        for (int i = 0; i < 200; i++) {
            PipeWalk.Route route = grid.walk(0, 0, null, random);
            assertNotNull(route);
            assertEquals(pos(3, 1), grid.destination(route));
            assertEquals(route.path().length, Arrays.stream(route.path()).distinct().count(), "a pipe visited twice");
        }
    }

    @Test
    void fullChestIsNoCandidate() {
        Grid grid = new Grid("CPPC");
        grid.chest(3, 0).capacity = 0;
        assertNull(grid.walk(1, 0, Direction.WEST, RandomSource.create(1)));
        assertEquals(TransferResult.Reason.NO_DESTINATION, grid.push(1, 0, Direction.WEST, 1, 0, true).reason());
    }

    @Test
    void sourceIsNeverTheDestination() {
        // The source chest also touches the far end of the U; it is still never a destination, on any face.
        Grid grid = new Grid(
                "PC",
                "PP");
        assertNull(grid.walk(1, 1, Direction.NORTH, RandomSource.create(1)));
    }

    @Test
    void tooManyVisitsFails() {
        Grid grid = new Grid("CPPPPPPC");
        assertNotNull(grid.walk(1, 0, Direction.WEST, RandomSource.create(1), 6));
        assertNull(grid.walk(1, 0, Direction.WEST, RandomSource.create(1), 5));
    }

    @Test
    void voidPipeIsASink() {
        Grid grid = new Grid("CPPV");
        PipeWalk.Route route = grid.walk(1, 0, Direction.WEST, RandomSource.create(1));
        assertNotNull(route);
        assertTrue(route.isSink());
        assertEquals(grid.node(3, 0), route.sink());
        TransferResult result = grid.push(1, 0, Direction.WEST, 5, 100, true);
        assertEquals(5, result.moved());
    }

    // Director (docs/TRANSPORT.md 8.4, section 14 #55)

    @Test
    void directorSendsEverythingOutOfItsFacing() {
        Grid grid = new Grid(
                ".C.",
                "CDC",
                ".C.");
        grid.pipes.get(pos(1, 1)).output = Direction.SOUTH;
        RandomSource random = RandomSource.create(11);
        for (int i = 0; i < 200; i++) {
            PipeWalk.Route route = grid.walk(1, 1, Direction.WEST, random);
            assertNotNull(route);
            assertEquals(pos(1, 2), grid.destination(route));
        }
        // The facing is read live: turning it needs no network rebuild.
        grid.pipes.get(pos(1, 1)).output = Direction.EAST;
        PipeWalk.Route route = grid.walk(1, 1, Direction.WEST, random);
        assertNotNull(route);
        assertEquals(pos(2, 1), grid.destination(route));
    }

    @Test
    void directorWithRefusingFacingBacksUpToThePreviousJunction() {
        // The junction (1,1) can go north to a chest or east into the Director, which faces a full chest (2,0): every
        // walk that enters the Director backs out and ends in the junction's other chest, never east of the Director.
        Grid grid = new Grid(
                ".CC.",
                "CPDC");
        grid.chest(2, 0).capacity = 0;
        grid.pipes.get(pos(2, 1)).output = Direction.NORTH;
        RandomSource random = RandomSource.create(5);
        for (int i = 0; i < 200; i++) {
            PipeWalk.Route route = grid.walk(1, 1, Direction.WEST, random);
            assertNotNull(route);
            assertEquals(pos(1, 0), grid.destination(route));
        }
        // Facing a chest that has room, the Director takes about half.
        grid.pipes.get(pos(2, 1)).output = Direction.EAST;
        int east = 0;
        for (int i = 0; i < 2000; i++) {
            PipeWalk.Route route = grid.walk(1, 1, Direction.WEST, random);
            assertNotNull(route);
            long destination = grid.destination(route);
            assertTrue(destination == pos(1, 0) || destination == pos(3, 1));
            if (destination == pos(3, 1))
                east++;
        }
        assertEquals(0.5, east / 2000.0, 0.05);
    }

    @Test
    void directorWithoutFacingIsADeadEnd() {
        Grid grid = new Grid("CDC");
        assertNull(grid.walk(1, 0, Direction.WEST, RandomSource.create(1)));
    }

    // Budgets (docs/TRANSPORT.md 5.4), one Extractor-like pull per tick over the path pipes.

    /** Items moved per tick 1..ticks by sources pulling through {@code paths} (budget refills, start at tick 0). */
    private static int[][] run(long[] refills, int[][] paths, int ticks) {
        PipeBudget[] budgets = new PipeBudget[refills.length];
        for (int i = 0; i < budgets.length; i++) {
            budgets[i] = new PipeBudget();
            budgets[i].start(0);
        }
        int[][] moved = new int[paths.length][ticks + 1];
        for (int t = 1; t <= ticks; t++) {
            for (int s = 0; s < paths.length; s++) {
                int source = (s + t - 1) % paths.length; // the source cursor rotates by one each tick (6.1)
                long n = Long.MAX_VALUE;
                for (int pipe : paths[source]) {
                    n = Math.min(n, budgets[pipe].available(t, refills[pipe], PipeBudget.cap(refills[pipe], 1.0, 20)) / 20);
                }
                try (Transaction transaction = Transaction.openRoot()) {
                    for (int pipe : paths[source]) {
                        budgets[pipe].spend(n * 20, transaction);
                    }
                    transaction.commit();
                }
                moved[source][t] = (int) n;
            }
        }
        return moved;
    }

    private static int[] onePipe(long refill, int ticks) {
        return run(new long[] {refill}, new int[][] {{0}}, ticks)[0];
    }

    private static int sum(int[] moved, int from, int to) {
        return Arrays.stream(moved, from, to + 1).sum();
    }

    @Test
    void woodMovesOneItemEveryTwentyTicks() {
        int[] moved = onePipe(1, 40);
        for (int t = 1; t <= 40; t++) {
            assertEquals(t == 20 || t == 40 ? 1 : 0, moved[t], "tick " + t);
        }
    }

    @Test
    void ironMovesFourASecond() {
        int[] moved = onePipe(4, 20);
        for (int t = 1; t <= 20; t++) {
            assertEquals(t % 5 == 0 ? 1 : 0, moved[t], "tick " + t);
        }
    }

    @Test
    void goldAndQuartzFollowTheWorkedExamples() {
        assertArrayEquals(new int[] {0, 1, 1, 1, 1}, Arrays.copyOfRange(onePipe(16, 5), 1, 6));
        assertArrayEquals(new int[] {1, 2, 1, 2, 2}, Arrays.copyOfRange(onePipe(32, 5), 1, 6));
    }

    @Test
    void enderMovesFiveHundredTwelveASecond() {
        int[] moved = onePipe(512, 20);
        assertEquals(25, moved[1]);
        assertEquals(26, moved[2]);
        assertEquals(128, sum(moved, 1, 5));
        assertEquals(512, sum(moved, 1, 20));
    }

    @Test
    void slowestPipeOnThePathSetsTheRate() {
        // gold -> wood -> iron
        int[][] moved = run(new long[] {16, 1, 4}, new int[][] {{0, 1, 2}}, 60);
        assertEquals(3, sum(moved[0], 1, 60));
    }

    @Test
    void sharedTrunkIsSplitBetweenSources() {
        // Two iron source pipes (0, 1) into one iron trunk (2).
        int[][] moved = run(new long[] {4, 4, 4}, new int[][] {{0, 2}, {1, 2}}, 20);
        assertEquals(2, sum(moved[0], 1, 20));
        assertEquals(2, sum(moved[1], 1, 20));
    }

    @Test
    void idleBudgetIsCappedAtOneSecond() {
        PipeBudget budget = new PipeBudget();
        budget.start(0);
        long cap = PipeBudget.cap(2, 1.0, 20);
        assertEquals(40, cap);
        assertEquals(40, budget.available(60, 2, cap), "stone idle for 3 s holds 2 items, not 6");
    }

    @Test
    void configChangeAppliesOnTheNextRefillAndCapShrinksAtOnce() {
        PipeBudget budget = new PipeBudget();
        budget.start(0);
        assertEquals(10, budget.available(10, 1, 20));
        assertEquals(14, budget.available(11, 4, 80), "the new refill applies from the next tick");
        assertEquals(80, budget.available(100, 4, 80));
        assertEquals(20, budget.available(100, 1, 20), "a smaller cap applies at once");
        assertEquals(1, PipeBudget.cap(0, 0.05, 1), "the cap is at least one unit of transfer");
    }

    // Transfers (docs/TRANSPORT.md 5.3, 6.3)

    @Test
    void partialTransferMovesWhatTheDestinationTakes() {
        Grid grid = new Grid("CPPC");
        grid.chest(3, 0).capacity = 3;
        TransferResult result = grid.push(1, 0, Direction.WEST, 10, 100, true);
        assertEquals(TransferResult.Reason.PARTIAL, result.reason());
        assertEquals(3, result.moved());
        assertEquals(3, grid.chest(3, 0).stored);
    }

    @Test
    void emptyBudgetRefusesAndSpendsNothing() {
        Grid grid = new Grid("CPPC");
        grid.refill = 1;
        // At tick 0 every budget is still 0.
        TransferResult result = grid.push(1, 0, Direction.WEST, 1, 0, true);
        assertEquals(TransferResult.Reason.NO_CAPACITY, result.reason());
        assertEquals(0, grid.chest(3, 0).stored);
        // Twenty ticks later each pipe holds one item's worth: one moves, the second is refused.
        assertEquals(1, grid.push(1, 0, Direction.WEST, 2, 20, true).moved());
        assertEquals(TransferResult.Reason.NO_CAPACITY, grid.push(1, 0, Direction.WEST, 1, 20, true).reason());
    }

    @Test
    void simulationChangesNothing() {
        Grid grid = new Grid("CPPC");
        grid.refill = 1;
        assertEquals(1, grid.push(1, 0, Direction.WEST, 1, 20, false).moved());
        assertEquals(0, grid.chest(3, 0).stored, "a simulated push inserted");
        assertEquals(1, grid.push(1, 0, Direction.WEST, 1, 20, true).moved(), "a simulated push spent budget");
        assertEquals(1, grid.chest(3, 0).stored);
    }

    @Test
    void budgetsAreSpentOnEveryPipeOfThePath() {
        Grid grid = new Grid(
                "CPPC",
                ".P..",
                ".C..");
        grid.refill = 20; // 1 item a tick, cap 20 items
        grid.chest(1, 2).capacity = 0;
        assertEquals(20, grid.push(1, 0, Direction.WEST, 64, 20, true).moved());
        Long2IntOpenHashMap left = new Long2IntOpenHashMap();
        for (PipeNode node : grid.network.nodes) {
            left.put(node.pos, (int) grid.router.available(node, 20, ResourceRouter.Settings.DEFAULTS));
        }
        assertEquals(0, left.get(pos(1, 0)));
        assertEquals(0, left.get(pos(2, 0)));
        assertEquals(400, left.get(pos(1, 1)), "a pipe off the path spent budget");
    }

    // #51: the walk refuses pipes the router's canEnter refuses (fluid locks, docs/TRANSPORT.md 7.3)

    @Test
    void refusedPipeIsWalkedAround() {
        // The junction at (1, 0) leads east to a chest through (2, 0) and south to a chest through (1, 1).
        Grid grid = new Grid(
                "CPPC",
                ".P..",
                ".C..");
        int refused = grid.node(2, 0);
        RandomSource random = RandomSource.create(5);
        for (int i = 0; i < 50; i++) {
            try (Transaction transaction = Transaction.openRoot()) {
                TransferResult result = grid.router.push(grid.network, grid.node(1, 0), Direction.WEST, pos(0, 0), STONE,
                        1, 1000, random, ResourceRouter.Settings.DEFAULTS, node -> grid.network.indexOf(node.pos) != refused,
                        transaction);
                assertEquals(1, result.moved());
                assertNotNull(result.route());
                assertEquals(pos(1, 2), grid.destination(result.route()), "went through the refused pipe");
            }
        }
    }

    @Test
    void refusedStartPipeHasNoDestination() {
        Grid grid = new Grid("CPPC");
        try (Transaction transaction = Transaction.openRoot()) {
            TransferResult result = grid.router.push(grid.network, grid.node(1, 0), Direction.WEST, pos(0, 0), STONE, 1,
                    1000, RandomSource.create(1), ResourceRouter.Settings.DEFAULTS, node -> node.pos != pos(1, 0),
                    transaction);
            assertEquals(TransferResult.Reason.NO_DESTINATION, result.reason());
            assertEquals(0, grid.chest(3, 0).stored);
        }
    }
}
