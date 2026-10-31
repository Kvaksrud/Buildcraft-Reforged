package buildcraftreforged.transport.graph;

import buildcraftreforged.api.transport.IPipeTile.PipeType;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import org.jspecify.annotations.Nullable;

/**
 * A connected component of pipes of one kind (docs/TRANSPORT.md 3.4), built by {@link PipeNetworkManager#get}. The
 * arrays never change; when anything in the component changes the network is marked invalid and a new one is built on
 * the next {@code get}. Holders of a network check {@link #isValid()} before using it again in a later tick.
 *
 * <p>Node 0 is the pipe the build started from; the rest follow in breadth-first order, neighbours in side order, so
 * the same world always gives the same arrays.
 */
public final class PipeNetwork {

    /**
     * A non-pipe block face that a node is connected to. {@code pos} is the block, {@code face} its face towards the
     * pipe, {@code node} the index of that pipe and {@code side} the pipe's side towards the block. The cache reads the
     * kind's capability live; its invalidation invalidates the network. Null only where no capability exists to cache.
     */
    public record Endpoint(long pos, Direction face, int node, Direction side,
            @Nullable BlockCapabilityCache<?, Direction> cache) {
    }

    public final PipeType kind;
    /** Unique per manager (per level session), for logs and tests. */
    public final int id;
    final Long2IntOpenHashMap indexByPos;
    public final PipeNode[] nodes;
    public final Endpoint[] endpoints;
    /** Nodes whose module is a source (extractors, pickup), sorted by position (docs/TRANSPORT.md 6.1). */
    public final int[] sourceNodes;
    /** Fairness rotation over {@link #sourceNodes} (docs/TRANSPORT.md 6.1), advanced by the source tick (#50). */
    public int sourceCursor;
    boolean valid = true;

    PipeNetwork(PipeType kind, int id, Long2IntOpenHashMap indexByPos, PipeNode[] nodes, Endpoint[] endpoints,
            int[] sourceNodes) {
        this.kind = kind;
        this.id = id;
        this.indexByPos = indexByPos;
        this.nodes = nodes;
        this.endpoints = endpoints;
        this.sourceNodes = sourceNodes;
    }

    public boolean isValid() {
        return valid;
    }

    /** The node index of the pipe at {@code pos}, or -1 if it is not in this network. */
    public int indexOf(long pos) {
        return indexByPos.getOrDefault(pos, -1);
    }

    public @Nullable PipeNode node(long pos) {
        int index = indexOf(pos);
        return index < 0 ? null : nodes[index];
    }

    public int size() {
        return nodes.length;
    }

    @Override
    public String toString() {
        return "PipeNetwork#" + id + "(" + kind + ", " + nodes.length + " pipes, " + endpoints.length + " endpoints"
                + (valid ? "" : ", invalid") + ")";
    }
}
