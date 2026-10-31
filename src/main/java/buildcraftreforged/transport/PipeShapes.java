package buildcraftreforged.transport;

import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Outline and collision shape for each of the 64 connection masks: the 4.2.2 pipe centre (0.25–0.75, as
 * {@code BlockGenericPipe.getBoundingBox}) plus one arm out to the block edge per connected side. Built once.
 *
 * <p>Facades and plugs (#61) add their 4.2.2 boxes ({@link PipeParts}): a facade a 2-pixel slab over its whole side, a
 * plug its cap. Shapes with either are built on first use and kept.
 */
public final class PipeShapes {

    public static final double MIN = 4;
    public static final double MAX = 12;

    private static final VoxelShape CENTRE = Block.box(MIN, MIN, MIN, MAX, MAX, MAX);
    private static final VoxelShape[] ARMS = new VoxelShape[6];
    private static final VoxelShape[] SHAPES = new VoxelShape[64];
    private static final VoxelShape[] FACADES = new VoxelShape[6];
    private static final VoxelShape[] PLUGS = new VoxelShape[6];
    /** Key: connection mask | facade mask << 6 | plug mask << 12. */
    private static final ConcurrentHashMap<Integer, VoxelShape> WITH_PARTS = new ConcurrentHashMap<>();

    static {
        for (Direction side : Direction.values()) {
            ARMS[side.get3DDataValue()] = arm(side);
            FACADES[side.get3DDataValue()] = Shapes.create(PipeParts.getFacadeBoundingBox(side));
            PLUGS[side.get3DDataValue()] = Shapes.create(PipeParts.getPlugBoundingBox(side));
        }
        for (int mask = 0; mask < 64; mask++) {
            VoxelShape shape = CENTRE;
            for (Direction side : Direction.values()) {
                if (PipeConnections.hasSide(mask, side))
                    shape = Shapes.or(shape, ARMS[side.get3DDataValue()]);
            }
            SHAPES[mask] = shape.optimize();
        }
    }

    private PipeShapes() {
    }

    public static VoxelShape get(int connectionMask) {
        return SHAPES[connectionMask & 63];
    }

    /** The pipe's shape with a facade on each side of {@code facadeMask} and a plug on each side of {@code plugMask}. */
    public static VoxelShape get(int connectionMask, int facadeMask, int plugMask) {
        if ((facadeMask & 63) == 0 && (plugMask & 63) == 0)
            return get(connectionMask);
        int key = (connectionMask & 63) | (facadeMask & 63) << 6 | (plugMask & 63) << 12;
        return WITH_PARTS.computeIfAbsent(key, k -> {
            VoxelShape shape = get(k);
            for (Direction side : Direction.values()) {
                if (PipeConnections.hasSide(k >> 6, side))
                    shape = Shapes.or(shape, FACADES[side.get3DDataValue()]);
                if (PipeConnections.hasSide(k >> 12, side))
                    shape = Shapes.or(shape, PLUGS[side.get3DDataValue()]);
            }
            return shape.optimize();
        });
    }

    private static VoxelShape arm(Direction side) {
        double[] min = { MIN, MIN, MIN };
        double[] max = { MAX, MAX, MAX };
        int axis = side.getAxis().ordinal();
        if (side.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
            min[axis] = MAX;
            max[axis] = 16;
        } else {
            min[axis] = 0;
            max[axis] = MIN;
        }
        return Block.box(min[0], min[1], min[2], max[0], max[1], max[2]);
    }
}
