package buildcraftreforged.transport;

import com.mojang.math.OctahedralGroup;
import com.mojang.math.Quadrant;
import net.minecraft.core.Direction;

/**
 * Which of a pipe model's twelve parts make up the model for a connection mask: per side, the arm (part
 * {@code 6 + side}) when connected, else the centre face on that side (part {@code side}). Sides in side order.
 * Kept out of the client model so it can be tested without a client.
 */
public final class PipeModelLayout {

    public static final int PART_COUNT = 12;

    private PipeModelLayout() {
    }

    public static int centreFace(Direction side) {
        return side.get3DDataValue();
    }

    public static int arm(Direction side) {
        return 6 + side.get3DDataValue();
    }

    /** The block-model rotation (x and y quarter turns) that turns a north-facing template part towards {@code side}. */
    public static OctahedralGroup rotationTowards(Direction side) {
        for (Quadrant x : Quadrant.values()) {
            for (Quadrant y : Quadrant.values()) {
                OctahedralGroup rotation = Quadrant.fromXYAngles(x, y);
                if (rotation.rotate(Direction.NORTH) == side)
                    return rotation;
            }
        }
        throw new IllegalStateException("No rotation from north to " + side);
    }

    public static int[] parts(int connectionMask) {
        int[] parts = new int[6];
        for (Direction side : Direction.values()) {
            parts[side.get3DDataValue()] = PipeConnections.hasSide(connectionMask, side) ? arm(side) : centreFace(side);
        }
        return parts;
    }
}
