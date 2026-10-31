package buildcraftreforged.transport.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import buildcraftreforged.api.transport.IPipeTile.PipeType;
import buildcraftreforged.transport.PipeConnections;
import buildcraftreforged.transport.PipeModuleType;
import buildcraftreforged.transport.graph.PipeNetwork;
import buildcraftreforged.transport.graph.PipeNetworkManager;
import buildcraftreforged.transport.graph.PipeNode;
import buildcraftreforged.transport.graph.PipeWorld;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * docs/TRANSPORT.md section 14, #52: the power phases of 7.4 on a stub level, a grid of power pipes and energy blocks
 * in the plane y = 0 given as text rows ({@code P} pipe, {@code R} receiver, {@code S} the pushing block, {@code .}
 * nothing). Row r is z = r, column c is x = c. Receivers take at most {@code maxReceive} FE per insert.
 */
class PowerRoutingTest {

    /** An energy store that takes at most {@code maxReceive} per insert, journaled like a real handler. */
    static final class Receiver extends SnapshotJournal<Long> implements EnergyHandler {
        long stored;
        long capacity = Long.MAX_VALUE;
        int maxReceive;

        Receiver(int maxReceive) {
            this.maxReceive = maxReceive;
        }

        @Override
        public long getAmountAsLong() {
            return stored;
        }

        @Override
        public long getCapacityAsLong() {
            return capacity;
        }

        @Override
        public int insert(int amount, TransactionContext transaction) {
            int moved = (int) Math.min(Math.min(amount, maxReceive), capacity - stored);
            if (moved > 0) {
                updateSnapshots(transaction);
                stored += moved;
            }
            return moved;
        }

        @Override
        public int extract(int amount, TransactionContext transaction) {
            return 0;
        }

        @Override
        protected Long createSnapshot() {
            return stored;
        }

        @Override
        protected void revertToSnapshot(Long snapshot) {
            stored = snapshot;
        }
    }

    private record StubPipe(byte mask) implements PipeWorld.Pipe {
        @Override
        public PipeType getPipeType() {
            return PipeType.POWER;
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
            return null;
        }
    }

    private static final class Grid implements PipeWorld {
        final Long2ObjectOpenHashMap<StubPipe> pipes = new Long2ObjectOpenHashMap<>();
        final Map<Long, Receiver> receivers = new HashMap<>();
        final Map<PipeNode, PipeBudget> budgets = new HashMap<>();
        final Map<PipeNode, PowerPipeState> states = new HashMap<>();
        final Map<Long, Long> refills = new HashMap<>();
        long refill = 1_000_000;
        long source = PipeWalk.NO_SOURCE;
        final PowerRouter router = new PowerRouter(new PowerRouter.Pipes() {
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
                return refills.getOrDefault(node.pos, refill);
            }

            @Override
            public PowerPipeState state(PipeNode node) {
                return states.computeIfAbsent(node, n -> new PowerPipeState());
            }
        }, endpoint -> receivers.get(endpoint.pos()));
        final PipeNetwork network;

        Grid(int maxReceive, String... rows) {
            for (int z = 0; z < rows.length; z++) {
                for (int x = 0; x < rows[z].length(); x++) {
                    long pos = pos(x, z);
                    switch (rows[z].charAt(x)) {
                    case 'R', 'S' -> receivers.put(pos, new Receiver(maxReceive));
                    case 'P' -> pipes.put(pos, null);
                    default -> {
                    }
                    }
                    if (rows[z].charAt(x) == 'S')
                        source = pos;
                }
            }
            for (long pos : pipes.keySet().toLongArray()) {
                byte mask = 0;
                for (Direction side : Direction.values()) {
                    long other = BlockPos.offset(pos, side);
                    mask = PipeConnections.with(mask, side, pipes.containsKey(other) || receivers.containsKey(other));
                }
                pipes.put(pos, new StubPipe(mask));
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
            return network.indexOf(pos(x, z));
        }

        Receiver receiver(int x, int z) {
            return receivers.get(pos(x, z));
        }

        PipeBudget budget(int x, int z) {
            return budgets.get(network.nodes[node(x, z)]);
        }

        PowerPipeState state(int x, int z) {
            return states.computeIfAbsent(network.nodes[node(x, z)], n -> new PowerPipeState());
        }

        /** A push from the {@code S} block into the pipe at (x, z). */
        PowerRouter.Result push(int x, int z, long amount, long now, boolean commit) {
            try (Transaction transaction = Transaction.openRoot()) {
                PowerRouter.Result result = router.push(network, node(x, z), source, amount, now, 1.0, transaction);
                if (commit)
                    transaction.commit();
                return result;
            }
        }
    }

    private static long pos(int x, int z) {
        return BlockPos.asLong(x, 0, z);
    }

    // Send phase (7.4 step 3)

    @Test
    void splitIsProportionalToRequests() {
        // Receivers north, west and east of one pipe, in that side order; the source is south.
        Grid grid = new Grid(1000,
                ".R.",
                "RPR",
                ".S.");
        grid.receiver(1, 0).maxReceive = 30;
        grid.receiver(0, 1).maxReceive = 30;
        grid.receiver(2, 1).maxReceive = 40;
        PowerRouter.Result result = grid.push(1, 1, 100, 1, true);
        assertEquals(100, result.moved());
        assertEquals(TransferResult.Reason.MOVED, result.reason());
        assertEquals(30, grid.receiver(1, 0).stored);
        assertEquals(30, grid.receiver(0, 1).stored);
        assertEquals(40, grid.receiver(2, 1).stored);
    }

    @Test
    void remainderGoesInSideOrder() {
        Grid grid = new Grid(10,
                ".R.",
                "RPR",
                ".S.");
        assertEquals(10, grid.push(1, 1, 10, 1, true).moved());
        assertEquals(4, grid.receiver(1, 0).stored, "north is first in side order");
        assertEquals(3, grid.receiver(0, 1).stored);
        assertEquals(3, grid.receiver(2, 1).stored);
    }

    @Test
    void splitFollowsRequestsAcrossBranches() {
        // One receiver on the left branch, two on the right: each receiver gets a third, not each branch a half.
        Grid grid = new Grid(1000,
                "R...R",
                "PPPPP",
                "..S.R");
        PowerRouter.Result result = grid.push(2, 1, 300, 1, true);
        assertEquals(300, result.moved());
        assertEquals(100, grid.receiver(0, 0).stored);
        assertEquals(100, grid.receiver(4, 0).stored);
        assertEquals(100, grid.receiver(4, 2).stored);
    }

    // Request phase (7.4 step 2)

    @Test
    void requestIsCappedByEachPipesBudget() {
        // Source → pipe A → pipe B → receiver. B can pass 50 this tick, so A asks for 50 although the receiver wants 1000.
        Grid grid = new Grid(1000, "SPPR");
        grid.refills.put(pos(2, 0), 50L);
        PowerRouter.Result result = grid.push(1, 0, 10_000, 1, true);
        assertEquals(50, result.moved());
        assertEquals(TransferResult.Reason.PARTIAL, result.reason());
        assertEquals(50, grid.receiver(3, 0).stored);
        assertEquals(0, grid.budget(2, 0).available(1, 50, 1000));
        // A spent what passed it, not what it could have.
        assertEquals(1_000_000 - 50, grid.budget(1, 0).available(1, 1_000_000, 20_000_000));
    }

    @Test
    void cappedBranchLeavesTheRestToTheOther() {
        // The west branch can pass 10, the east one 1 000: requests 10 and 1 000. 110 FE give floor shares 1 and 108,
        // and the remainder goes to west, first in side order: 2 and 108.
        Grid grid = new Grid(1000,
                "..S..",
                "RPPPR");
        grid.refills.put(pos(1, 1), 10L);
        assertEquals(110, grid.push(2, 1, 110, 1, true).moved());
        assertEquals(2, grid.receiver(0, 1).stored);
        assertEquals(108, grid.receiver(4, 1).stored);
    }

    @Test
    void diamondExampleSplitsTheRefillEvenly() {
        // docs/TRANSPORT.md 5.5: two consumers asking 3 000 FE/t behind one diamond pipe (5 120 FE/t), empty budget.
        Grid grid = new Grid(3000,
                ".R.",
                "SPR");
        grid.refill = 5120;
        for (long tick = 1; tick <= 5; tick++) {
            long northBefore = grid.receiver(1, 0).stored;
            long eastBefore = grid.receiver(2, 1).stored;
            assertEquals(5120, grid.push(1, 1, 100_000, tick, true).moved(), "tick " + tick);
            assertEquals(2560, grid.receiver(1, 0).stored - northBefore);
            assertEquals(2560, grid.receiver(2, 1).stored - eastBefore);
        }
    }

    // Exclusions and loops

    @Test
    void pushingBlockReceivesNothing() {
        Grid grid = new Grid(1000, "SPPR");
        PowerRouter.Result result = grid.push(1, 0, 500, 1, true);
        assertEquals(500, result.moved());
        assertEquals(0, grid.receiver(0, 0).stored, "#1827: no power back where it came from");
        assertEquals(500, grid.receiver(3, 0).stored);
    }

    @Test
    void pushingBlockOnAnotherFaceReceivesNothing() {
        // The source block touches the line twice; neither face is a receiver.
        Grid grid = new Grid(1000,
                "PSP",
                "PPP",
                "..R");
        grid.push(0, 0, 500, 1, true);
        assertEquals(0, grid.receivers.get(grid.source).stored);
        assertEquals(500, grid.receiver(2, 2).stored);
    }

    @Test
    void blockOnTwoPipesIsOneReceiver() {
        // (3,1) touches two pipes. Counted twice it would ask 200 and get 100 of 150; once, the 150 split 75 / 75.
        Grid grid = new Grid(100,
                "SPPR",
                "..PR",
                "..PP");
        assertEquals(150, grid.push(1, 0, 150, 1, true).moved());
        assertEquals(75, grid.receiver(3, 0).stored);
        assertEquals(75, grid.receiver(3, 1).stored);
    }

    @Test
    void loopDeliversOnce() {
        Grid grid = new Grid(1000,
                "SPPP",
                ".P.P",
                ".PPPR");
        assertEquals(700, grid.push(1, 0, 700, 1, true).moved());
        assertEquals(700, grid.receiver(4, 2).stored);
    }

    @Test
    void noReceiverIsNoDestination() {
        Grid grid = new Grid(1000, "SPP");
        PowerRouter.Result result = grid.push(1, 0, 100, 1, true);
        assertEquals(0, result.moved());
        assertEquals(TransferResult.Reason.NO_DESTINATION, result.reason());
    }

    @Test
    void fullReceiverIsNoDestination() {
        Grid grid = new Grid(1000, "SPR");
        grid.receiver(2, 0).capacity = 0;
        assertEquals(TransferResult.Reason.NO_DESTINATION, grid.push(1, 0, 100, 1, true).reason());
    }

    @Test
    void emptyBudgetIsNoCapacity() {
        Grid grid = new Grid(1000, "SPR");
        grid.refill = 80;
        // Tick 0: the pipe was placed this tick and has nothing yet.
        PowerRouter.Result result = grid.push(1, 0, 100, 0, true);
        assertEquals(0, result.moved());
        assertEquals(TransferResult.Reason.NO_CAPACITY, result.reason());
        assertEquals(0, grid.receiver(2, 0).stored);
    }

    // Simulation and budgets

    @Test
    void simulationChangesNothing() {
        Grid grid = new Grid(1000, "SPPR");
        grid.refill = 80;
        PowerRouter.Result simulated = grid.push(1, 0, 500, 10, false);
        assertEquals(500, simulated.moved());
        assertEquals(0, grid.receiver(3, 0).stored);
        assertEquals(800, grid.budget(1, 0).available(10, 80, 1600));
        assertEquals(800, grid.budget(2, 0).available(10, 80, 1600));
        assertEquals(0, grid.state(1, 0).passed(10, 80));
        assertEquals(Long.MIN_VALUE, grid.state(1, 0).lastRequestTick());
        assertEquals(500, grid.push(1, 0, 500, 10, true).moved());
        assertEquals(500, grid.receiver(3, 0).stored);
        assertEquals(300, grid.budget(1, 0).available(10, 80, 1600));
    }

    @Test
    void tierOneLinePassesItsRefillAfterTheBurst() {
        // docs/TRANSPORT.md 5.5: wood, 80 FE/t, cap 1 600. After the first second's burst, 80 a tick.
        Grid grid = new Grid(Integer.MAX_VALUE, "SPPPPPR");
        grid.refill = 80;
        assertEquals(1600, grid.push(1, 0, 100_000, 20, true).moved());
        for (long tick = 21; tick <= 120; tick++)
            assertEquals(80, grid.push(1, 0, 100_000, tick, true).moved(), "tick " + tick);
    }

    @Test
    void redstonePulseFitsAWoodPipe() {
        Grid grid = new Grid(Integer.MAX_VALUE, "SPPPPPR");
        grid.refill = 80;
        long delivered = 0;
        for (long tick = 1; tick <= 160; tick++) {
            if (tick % 16 == 0)
                delivered += grid.push(1, 0, 10, tick, true).moved();
        }
        assertEquals(100, delivered);
        assertEquals(100, grid.receiver(6, 0).stored);
    }

    // Overload and requests (7.4, Q21)

    @Test
    void overloadRisesAfterSixtySaturatedTicksAndFalls() {
        Grid grid = new Grid(Integer.MAX_VALUE, "SPR");
        grid.refill = 80;
        grid.push(1, 0, 1600, 20, true); // spend the first second's burst
        for (long tick = 21; tick <= 80; tick++) {
            assertEquals(80, grid.push(1, 0, 100_000, tick, true).moved());
            // Ticks 20 .. tick - 1 are closed, all saturated.
            assertEquals(Math.min(PowerPipeState.OVERLOAD_TICKS, tick - 20), grid.state(1, 0).overload(tick, 80));
        }
        assertEquals(PowerPipeState.OVERLOAD_TICKS, grid.state(1, 0).overload(81, 80));
        assertTrue(grid.state(1, 0).isOverloaded(81, 80));
        // A flow below 95 % of the refill lowers it again.
        grid.push(1, 0, 70, 81, true);
        assertEquals(59, grid.state(1, 0).overload(82, 80));
        assertFalse(grid.state(1, 0).isOverloaded(82, 80));
        // Idle ticks lower it one a tick, down to 0.
        assertEquals(49, grid.state(1, 0).overload(92, 80));
        assertEquals(0, grid.state(1, 0).overload(500, 80));
    }

    @Test
    void requestPhaseRecordsUnmetDemand() {
        Grid grid = new Grid(1000, "SPR");
        grid.refill = 80;
        grid.push(1, 0, 100_000, 1, true);
        assertTrue(grid.state(1, 0).lastRequestUnmet(), "receiver asked 1000, the pipe passed 80");
        assertEquals(1, grid.state(1, 0).lastRequestTick());
        grid.receiver(2, 0).maxReceive = 10;
        grid.push(1, 0, 100_000, 2, true);
        assertFalse(grid.state(1, 0).lastRequestUnmet());
    }

    @Test
    void demandAsksEveryReceiver() {
        Grid grid = new Grid(300, "RPPR");
        try (Transaction transaction = Transaction.openRoot()) {
            assertEquals(600, grid.router.demand(grid.network, grid.node(1, 0), transaction));
        }
        assertEquals(0, grid.receiver(0, 0).stored);
    }
}
