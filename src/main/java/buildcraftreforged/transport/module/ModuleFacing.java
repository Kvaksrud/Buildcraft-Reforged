package buildcraftreforged.transport.module;

import java.util.function.Predicate;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

/**
 * The facing search shared by the Extractor and the Director (docs/TRANSPORT.md 8.2, 8.4): 4.2.2
 * {@code PipeLogicWood.switchSource} and {@code PipeLogicIron.switchPosition} try {@code facing+1 … facing+6} mod 6 in
 * side order and keep the first valid side. Level-free, so it is unit-tested.
 */
public final class ModuleFacing {

    private static final Direction[] SIDES = Direction.values();

    private ModuleFacing() {
    }

    /**
     * The first valid side after {@code current} in side order, wrapping, ending with {@code current} itself; null when
     * no side is valid. No facing yet counts as 4.2.2's metadata 6, so the search starts at {@code UP} and ends at
     * {@code DOWN}.
     */
    public static @Nullable Direction next(@Nullable Direction current, Predicate<Direction> valid) {
        int start = current == null ? SIDES.length : current.get3DDataValue();
        for (int i = start + 1; i <= start + SIDES.length; i++) {
            Direction side = SIDES[i % SIDES.length];
            if (valid.test(side))
                return side;
        }
        return null;
    }

    /** Saved as -1 for none, else the side's {@code get3DDataValue}. */
    static int toIndex(@Nullable Direction facing) {
        return facing == null ? -1 : facing.get3DDataValue();
    }

    static @Nullable Direction fromIndex(int index) {
        return index >= 0 && index < SIDES.length ? SIDES[index] : null;
    }
}
