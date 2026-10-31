package buildcraftreforged.transport.graph;

import buildcraftreforged.transport.PipeModuleType;
import java.util.Arrays;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

/**
 * One pipe of a {@link PipeNetwork} (docs/TRANSPORT.md 3.4). {@link #next} says where each side leads: a node index,
 * {@link #NONE}, or {@code -2 - endpointIndex} (use {@link #isEndpoint} and {@link #endpointIndex}).
 */
public final class PipeNode {
    /** No connection on that side, or a connection to a pipe that is unloaded or does not connect back. */
    public static final int NONE = -1;

    public final long pos;
    public final int tier;
    /** The module type when the network was built; a module change rebuilds the network. #55 makes this the module. */
    public final @Nullable PipeModuleType module;
    public final byte connectionMask;
    /** The pipe this node was built from, for its budgets and records (#50–#59). Valid while the network is. */
    public final PipeWorld.Pipe pipe;
    final int[] next = new int[6];

    PipeNode(long pos, PipeWorld.Pipe pipe) {
        this.pos = pos;
        this.pipe = pipe;
        tier = pipe.getTier();
        module = pipe.getModuleType();
        connectionMask = pipe.getConnectionMask();
        Arrays.fill(next, NONE);
    }

    /** Where {@code side} leads: a node index, {@link #NONE}, or an encoded endpoint. */
    public int next(Direction side) {
        return next[side.get3DDataValue()];
    }

    public static boolean isNode(int next) {
        return next >= 0;
    }

    public static boolean isEndpoint(int next) {
        return next <= -2;
    }

    public static int endpointIndex(int next) {
        return -2 - next;
    }

    static int encodeEndpoint(int endpointIndex) {
        return -2 - endpointIndex;
    }
}
