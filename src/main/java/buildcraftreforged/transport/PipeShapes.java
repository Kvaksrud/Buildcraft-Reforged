package buildcraftreforged.transport;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Outline and collision shape for each of the 64 connection masks: the 4.2.2 pipe centre (0.25–0.75, as
 * {@code BlockGenericPipe.getBoundingBox}) plus one arm out to the block edge per connected side. Built once.
 */
public final class PipeShapes {

    public static final double MIN = 4;
    public static final double MAX = 12;

    private static final VoxelShape CENTRE = Block.box(MIN, MIN, MIN, MAX, MAX, MAX);
    private static final VoxelShape[] ARMS = new VoxelShape[6];
    private static final VoxelShape[] SHAPES = new VoxelShape[64];

    static {
        for (Direction side : Direction.values()) {
            ARMS[side.get3DDataValue()] = arm(side);
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
