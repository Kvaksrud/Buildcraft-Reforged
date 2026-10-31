package buildcraftreforged.transport.graph;

import buildcraftreforged.api.transport.IPipeTile.PipeType;
import buildcraftreforged.transport.PipeConnections;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

/**
 * The network cache of one level (docs/TRANSPORT.md 3.4). Networks are built on demand by {@link #get} and dropped by
 * {@link #invalidate}; nothing is saved. Server thread only. {@link PipeNetworks} keeps one per {@code ServerLevel}.
 *
 * <p>A position is "dirty" exactly when it is not mapped to a valid network: invalidating a network unmaps all its
 * nodes, so the next {@code get} on any of them builds afresh. Merge and split need no special code: a change at a
 * position invalidates the networks there and at its six neighbours, and each piece is rebuilt when asked for.
 */
public final class PipeNetworkManager {
    private static final Direction[] SIDES = Direction.values();

    private final PipeWorld world;
    private final Long2ObjectOpenHashMap<PipeNetwork> byPos = new Long2ObjectOpenHashMap<>();
    private final ObjectLinkedOpenHashSet<PipeNetwork> networks = new ObjectLinkedOpenHashSet<>();
    private int nextId;

    public PipeNetworkManager(PipeWorld world) {
        this.world = world;
    }

    public @Nullable PipeNetwork get(BlockPos pos) {
        return get(pos.asLong());
    }

    /**
     * The network containing the pipe at {@code pos}, built now if the position is dirty. Null when there is no loaded
     * pipe there, or it is a structure pipe (which carries nothing, docs/TRANSPORT.md 11).
     */
    public @Nullable PipeNetwork get(long pos) {
        PipeNetwork cached = byPos.get(pos);
        if (cached != null && cached.valid)
            return cached;
        PipeWorld.Pipe start = world.getPipe(pos);
        if (start == null || start.getPipeType() == PipeType.STRUCTURE)
            return null;
        return build(pos, start);
    }

    /** Whether the next {@link #get} at {@code pos} builds a network instead of returning a cached one. */
    public boolean isDirty(long pos) {
        PipeNetwork cached = byPos.get(pos);
        return cached == null || !cached.valid;
    }

    /** The valid networks built so far, in build order. */
    public Collection<PipeNetwork> networks() {
        return Collections.unmodifiableCollection(networks);
    }

    /**
     * Something changed at {@code pos}: a pipe loaded or was removed, its connection mask, module or plugs changed, or
     * an endpoint capability next to it was invalidated. Invalidates the networks at {@code pos} and its six
     * neighbours. Cheap, touches no level state, so it is safe from block entity and capability callbacks.
     */
    public void invalidate(long pos) {
        invalidateAt(pos);
        for (Direction side : SIDES) {
            invalidateAt(BlockPos.offset(pos, side));
        }
    }

    public void invalidate(BlockPos pos) {
        invalidate(pos.asLong());
    }

    /** Drops every network, for level unload. */
    public void clear() {
        for (PipeNetwork network : networks) {
            network.valid = false;
        }
        networks.clear();
        byPos.clear();
    }

    private void invalidateAt(long pos) {
        PipeNetwork network = byPos.get(pos);
        if (network != null)
            discard(network);
    }

    private void discard(PipeNetwork network) {
        if (!network.valid)
            return;
        network.valid = false;
        networks.remove(network);
        for (PipeNode node : network.nodes) {
            if (byPos.get(node.pos) == network)
                byPos.remove(node.pos);
        }
    }

    /**
     * Breadth-first search from {@code start} over loaded pipes of the same kind, neighbours in side order. A side
     * counts as a connection to another pipe only when both masks have the bit; a connected side towards a loaded
     * non-pipe block is an endpoint.
     */
    private PipeNetwork build(long start, PipeWorld.Pipe startPipe) {
        PipeType kind = startPipe.getPipeType();
        Long2IntOpenHashMap indexByPos = new Long2IntOpenHashMap();
        indexByPos.defaultReturnValue(-1);
        List<PipeNode> nodes = new ArrayList<>();
        List<long[]> endpointSpecs = new ArrayList<>(); // {pos, node, side}

        indexByPos.put(start, 0);
        nodes.add(new PipeNode(start, startPipe));
        for (int i = 0; i < nodes.size(); i++) {
            PipeNode node = nodes.get(i);
            for (Direction side : SIDES) {
                if (!PipeConnections.hasSide(node.connectionMask, side))
                    continue;
                long neighbourPos = BlockPos.offset(node.pos, side);
                int known = indexByPos.get(neighbourPos);
                if (known >= 0) {
                    if (PipeConnections.hasSide(nodes.get(known).connectionMask, side.getOpposite()))
                        node.next[side.get3DDataValue()] = known;
                    continue;
                }
                PipeWorld.Pipe neighbour = world.getPipe(neighbourPos);
                if (neighbour != null) {
                    if (neighbour.getPipeType() == kind
                            && PipeConnections.hasSide(neighbour.getConnectionMask(), side.getOpposite())) {
                        int index = nodes.size();
                        indexByPos.put(neighbourPos, index);
                        nodes.add(new PipeNode(neighbourPos, neighbour));
                        node.next[side.get3DDataValue()] = index;
                    }
                } else if (world.canBeEndpoint(neighbourPos)) {
                    node.next[side.get3DDataValue()] = PipeNode.encodeEndpoint(endpointSpecs.size());
                    endpointSpecs.add(new long[] {neighbourPos, i, side.get3DDataValue()});
                }
            }
        }

        int[] sourceNodes = sourceNodes(nodes);
        PipeNetwork network = new PipeNetwork(kind, nextId++, indexByPos, nodes.toArray(PipeNode[]::new),
                new PipeNetwork.Endpoint[endpointSpecs.size()], sourceNodes);
        for (int e = 0; e < endpointSpecs.size(); e++) {
            long[] spec = endpointSpecs.get(e);
            int nodeIndex = (int) spec[1];
            long pipePos = network.nodes[nodeIndex].pos;
            Direction side = Direction.from3DDataValue((int) spec[2]);
            network.endpoints[e] = new PipeNetwork.Endpoint(spec[0], side.getOpposite(), nodeIndex, side,
                    world.createEndpointCache(kind, spec[0], side.getOpposite(), network::isValid,
                            () -> invalidate(pipePos)));
        }

        for (PipeNode node : network.nodes) {
            PipeNetwork old = byPos.put(node.pos, network);
            if (old != null && old != network)
                discard(old);
        }
        networks.add(network);
        return network;
    }

    private static int[] sourceNodes(List<PipeNode> nodes) {
        return IntStream.range(0, nodes.size())
                .filter(i -> nodes.get(i).module != null && nodes.get(i).module.isSource())
                .boxed()
                .sorted((a, b) -> Long.compare(nodes.get(a).pos, nodes.get(b).pos))
                .mapToInt(Integer::intValue)
                .toArray();
    }
}
