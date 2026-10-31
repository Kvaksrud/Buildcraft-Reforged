package buildcraftreforged.transport.graph;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import buildcraftreforged.api.transport.IPipeTile.PipeType;
import buildcraftreforged.transport.PipeConnections;
import buildcraftreforged.transport.PipeModuleType;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Arrays;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import org.junit.jupiter.api.Test;
import org.jspecify.annotations.Nullable;

/**
 * docs/TRANSPORT.md section 14, #49: the network graph on a stub level that is a map of positions to pipes. The stub
 * recomputes masks and calls {@link PipeNetworkManager#invalidate} the way {@code TilePipe} does.
 */
class PipeNetworkManagerTest {

    private static final class StubPipe implements PipeWorld.Pipe {
        final PipeType kind;
        byte mask;
        @Nullable PipeModuleType module;

        StubPipe(PipeType kind) {
            this.kind = kind;
        }

        @Override
        public PipeType getPipeType() {
            return kind;
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

    /** Pipes, endpoint blocks ("chests", accepted by every kind) and unloaded positions. */
    private static final class StubWorld implements PipeWorld {
        final Long2ObjectOpenHashMap<StubPipe> pipes = new Long2ObjectOpenHashMap<>();
        final LongOpenHashSet chests = new LongOpenHashSet();
        final LongOpenHashSet unloaded = new LongOpenHashSet();
        final Long2ObjectOpenHashMap<Runnable> listeners = new Long2ObjectOpenHashMap<>();
        final PipeNetworkManager manager = new PipeNetworkManager(this);

        @Override
        public @Nullable Pipe getPipe(long pos) {
            return unloaded.contains(pos) ? null : pipes.get(pos);
        }

        @Override
        public boolean canBeEndpoint(long pos) {
            return !unloaded.contains(pos) && !pipes.containsKey(pos);
        }

        @Override
        public @Nullable BlockCapabilityCache<?, Direction> createEndpointCache(PipeType kind, long pos, Direction face,
                BooleanSupplier isValid, Runnable onInvalidate) {
            listeners.put(pos, () -> {
                if (isValid.getAsBoolean())
                    onInvalidate.run();
            });
            return null;
        }

        StubPipe place(int x, PipeType kind) {
            return place(x, 0, kind);
        }

        StubPipe place(int x, int y, PipeType kind) {
            long pos = BlockPos.asLong(x, y, 0);
            StubPipe pipe = new StubPipe(kind);
            pipes.put(pos, pipe);
            manager.invalidate(pos); // onLoad
            updateAround(pos);
            return pipe;
        }

        void remove(int x) {
            long pos = BlockPos.asLong(x, 0, 0);
            pipes.remove(pos);
            manager.invalidate(pos); // setRemoved
            updateAround(pos);
        }

        void chest(int x) {
            long pos = BlockPos.asLong(x, 0, 0);
            chests.add(pos);
            updateAround(pos);
        }

        /** Chunk unload: the pipe's block entity is removed, the neighbours' masks are left alone. */
        void unload(int x) {
            long pos = BlockPos.asLong(x, 0, 0);
            unloaded.add(pos);
            manager.invalidate(pos);
        }

        void load(int x) {
            long pos = BlockPos.asLong(x, 0, 0);
            unloaded.remove(pos);
            manager.invalidate(pos);
            updateAround(pos);
        }

        private void updateAround(long pos) {
            update(pos);
            for (Direction side : Direction.values()) {
                update(BlockPos.offset(pos, side));
            }
        }

        /** The connection rule reduced to kind and endpoints; a mask change invalidates, as in TilePipe. */
        private void update(long pos) {
            StubPipe pipe = pipes.get(pos);
            if (pipe == null || unloaded.contains(pos))
                return;
            byte mask = 0;
            for (Direction side : Direction.values()) {
                long other = BlockPos.offset(pos, side);
                StubPipe neighbour = (StubPipe) getPipe(other);
                boolean connect = neighbour != null ? neighbour.kind == pipe.kind : chests.contains(other);
                mask = PipeConnections.with(mask, side, connect);
            }
            if (mask != pipe.mask) {
                pipe.mask = mask;
                manager.invalidate(pos);
            }
        }

        @Nullable PipeNetwork get(int x) {
            return manager.get(BlockPos.asLong(x, 0, 0));
        }
    }

    private static long pos(int x) {
        return BlockPos.asLong(x, 0, 0);
    }

    private static long[] positions(PipeNetwork network) {
        return Arrays.stream(network.nodes).mapToLong(node -> node.pos).toArray();
    }

    @Test
    void lineOfFiveIsOneNetworkInBfsOrder() {
        StubWorld world = new StubWorld();
        for (int x = 0; x < 5; x++) {
            world.place(x, PipeType.ITEM);
        }
        PipeNetwork network = world.get(2);
        assertNotNull(network);
        assertEquals(5, network.size());
        // From the middle: side order visits WEST (x-1) before EAST (x+1).
        assertArrayEquals(new long[] {pos(2), pos(1), pos(3), pos(0), pos(4)}, positions(network));
        assertEquals(1, network.nodes[0].next(Direction.WEST));
        assertEquals(2, network.nodes[0].next(Direction.EAST));
        assertEquals(PipeNode.NONE, network.nodes[0].next(Direction.UP));
        for (int x = 0; x < 5; x++) {
            assertSame(network, world.get(x), "every pipe maps to the same cached network");
        }
        assertEquals(0, network.endpoints.length);
    }

    @Test
    void endpointsAreRecordedPerSide() {
        StubWorld world = new StubWorld();
        world.chest(-1);
        world.place(0, PipeType.ITEM);
        world.place(1, PipeType.ITEM);
        world.chest(2);
        PipeNetwork network = world.get(0);
        assertEquals(2, network.endpoints.length);
        PipeNetwork.Endpoint west = network.endpoints[PipeNode.endpointIndex(network.nodes[0].next(Direction.WEST))];
        assertEquals(pos(-1), west.pos());
        assertEquals(Direction.EAST, west.face());
        assertEquals(0, west.node());
        assertTrue(PipeNode.isEndpoint(network.nodes[1].next(Direction.EAST)));
    }

    @Test
    void mergeJoinsTwoLinesAndInvalidatesBoth() {
        StubWorld world = new StubWorld();
        world.place(0, PipeType.ITEM);
        world.place(1, PipeType.ITEM);
        world.place(3, PipeType.ITEM);
        world.place(4, PipeType.ITEM);
        PipeNetwork left = world.get(0);
        PipeNetwork right = world.get(4);
        assertNotSame(left, right);
        assertEquals(2, left.size());

        world.place(2, PipeType.ITEM);
        assertFalse(left.isValid());
        assertFalse(right.isValid());
        PipeNetwork merged = world.get(0);
        assertEquals(5, merged.size());
        assertSame(merged, world.get(4));
    }

    @Test
    void splitGivesTwoNetworks() {
        StubWorld world = new StubWorld();
        for (int x = 0; x < 5; x++) {
            world.place(x, PipeType.ITEM);
        }
        PipeNetwork whole = world.get(0);
        world.remove(2);
        assertFalse(whole.isValid());
        assertNull(world.get(2));
        PipeNetwork left = world.get(0);
        PipeNetwork right = world.get(4);
        assertNotSame(left, right);
        assertArrayEquals(new long[] {pos(0), pos(1)}, positions(left));
        assertArrayEquals(new long[] {pos(4), pos(3)}, positions(right));
    }

    @Test
    void endpointInvalidationRebuildsButContentsDoNot() {
        StubWorld world = new StubWorld();
        world.place(0, PipeType.ITEM);
        world.chest(1);
        PipeNetwork network = world.get(0);
        // Changing what is in the chest calls nothing: the network stays cached.
        assertSame(network, world.get(0));
        assertFalse(world.manager.isDirty(pos(0)));

        Runnable staleListener = world.listeners.get(pos(1));
        staleListener.run();
        assertFalse(network.isValid());
        assertTrue(world.manager.isDirty(pos(0)));
        PipeNetwork rebuilt = world.get(0);
        assertNotSame(network, rebuilt);
        assertEquals(1, rebuilt.endpoints.length);

        // The old network's cache is no longer valid, so its listener does nothing.
        staleListener.run();
        assertSame(rebuilt, world.get(0));
    }

    @Test
    void kindsStaySeparate() {
        StubWorld world = new StubWorld();
        world.place(0, PipeType.ITEM);
        world.place(1, PipeType.ITEM);
        world.place(0, 1, PipeType.FLUID);
        world.place(1, 1, PipeType.FLUID);
        PipeNetwork items = world.get(0);
        PipeNetwork fluids = world.manager.get(BlockPos.asLong(0, 1, 0));
        assertNotSame(items, fluids);
        assertEquals(PipeType.ITEM, items.kind);
        assertEquals(PipeType.FLUID, fluids.kind);
        assertEquals(2, items.size());
        assertEquals(2, fluids.size());
        // Nor is the other kind's pipe an endpoint.
        assertEquals(0, items.endpoints.length);
    }

    @Test
    void unloadedChunkSplitsAndReloadMerges() {
        StubWorld world = new StubWorld();
        for (int x = 0; x < 5; x++) {
            world.place(x, PipeType.ITEM);
        }
        PipeNetwork whole = world.get(0);
        world.unload(2);
        assertFalse(whole.isValid());
        // The neighbours still have their bits towards x = 2, but an unloaded side leads nowhere (not an endpoint).
        PipeNetwork left = world.get(0);
        assertEquals(2, left.size());
        assertEquals(PipeNode.NONE, left.node(pos(1)).next(Direction.EAST));
        assertEquals(0, left.endpoints.length);
        assertNotSame(left, world.get(4));

        world.load(2);
        assertFalse(left.isValid());
        assertEquals(5, world.get(0).size());
    }

    @Test
    void sameWorldBuildsSameArrays() {
        StubWorld world = new StubWorld();
        world.chest(-1);
        for (int x = 0; x < 4; x++) {
            world.place(x, PipeType.ITEM);
        }
        world.place(1, 1, PipeType.ITEM);
        world.chest(4);
        PipeNetwork first = world.get(0);
        world.manager.invalidate(pos(0));
        PipeNetwork second = world.get(0);
        assertNotSame(first, second);
        assertArrayEquals(positions(first), positions(second));
        for (int i = 0; i < first.size(); i++) {
            for (Direction side : Direction.values()) {
                assertEquals(first.nodes[i].next(side), second.nodes[i].next(side));
            }
        }
        assertEquals(first.endpoints.length, second.endpoints.length);
        for (int e = 0; e < first.endpoints.length; e++) {
            assertEquals(first.endpoints[e].pos(), second.endpoints[e].pos());
            assertEquals(first.endpoints[e].node(), second.endpoints[e].node());
        }
    }

    @Test
    void sourceNodesAreSortedByPosition() {
        StubWorld world = new StubWorld();
        for (int x = 0; x < 4; x++) {
            world.place(x, PipeType.ITEM);
        }
        world.pipes.get(pos(3)).module = PipeModuleType.EXTRACTOR;
        world.pipes.get(pos(0)).module = PipeModuleType.PICKUP;
        world.pipes.get(pos(1)).module = PipeModuleType.DIRECTOR;
        world.manager.invalidate(pos(0));
        world.manager.invalidate(pos(3));
        PipeNetwork network = world.get(2);
        long[] sources = Arrays.stream(network.sourceNodes).mapToLong(i -> network.nodes[i].pos).toArray();
        assertArrayEquals(new long[] {pos(0), pos(3)}, sources);
    }

    @Test
    void networksListsOnlyValidOnes() {
        StubWorld world = new StubWorld();
        world.place(0, PipeType.ITEM);
        world.place(5, PipeType.ITEM);
        PipeNetwork a = world.get(0);
        PipeNetwork b = world.get(5);
        assertEquals(2, world.manager.networks().size());
        world.manager.invalidate(pos(0));
        assertEquals(1, world.manager.networks().size());
        assertTrue(world.manager.networks().contains(b));
        assertFalse(a.isValid());
    }
}
