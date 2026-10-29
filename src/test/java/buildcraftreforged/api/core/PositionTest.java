package buildcraftreforged.api.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.junit.jupiter.api.Test;

class PositionTest {

    @Test
    void moveForwardsStepsAlongTheOrientation() {
        assertXyz(1, 2, 3, moved(Direction.UP, p -> p.moveForwards(0)));
        assertXyz(1, 3, 3, moved(Direction.UP, p -> p.moveForwards(1)));
        assertXyz(1, 1, 3, moved(Direction.DOWN, p -> p.moveForwards(1)));
        assertXyz(1, 2, 4, moved(Direction.SOUTH, p -> p.moveForwards(1)));
        assertXyz(1, 2, 2, moved(Direction.NORTH, p -> p.moveForwards(1)));
        assertXyz(2, 2, 3, moved(Direction.EAST, p -> p.moveForwards(1)));
        assertXyz(0, 2, 3, moved(Direction.WEST, p -> p.moveForwards(1)));
    }

    @Test
    void moveForwardsMatchesTheDirectionVector() {
        for (Direction direction : Direction.values()) {
            Position p = new Position(0, 0, 0, direction);
            p.moveForwards(2.5);
            assertXyz(direction.getStepX() * 2.5, direction.getStepY() * 2.5, direction.getStepZ() * 2.5, p);
        }
    }

    @Test
    void moveBackwardsIsTheOppositeOfForwards() {
        assertXyz(1, 2, 2, moved(Direction.SOUTH, p -> p.moveBackwards(1)));
        assertXyz(1, 1, 3, moved(Direction.UP, p -> p.moveBackwards(1)));
    }

    @Test
    void moveRightTurnsClockwiseSeenFromAbove() {
        assertXyz(0, 2, 3, moved(Direction.SOUTH, p -> p.moveRight(1)));
        assertXyz(2, 2, 3, moved(Direction.NORTH, p -> p.moveRight(1)));
        assertXyz(1, 2, 4, moved(Direction.EAST, p -> p.moveRight(1)));
        assertXyz(1, 2, 2, moved(Direction.WEST, p -> p.moveRight(1)));
        assertXyz(1, 2, 3, moved(Direction.UP, p -> p.moveRight(1)));
    }

    @Test
    void moveLeftIsTheOppositeOfRight() {
        assertXyz(2, 2, 3, moved(Direction.SOUTH, p -> p.moveLeft(1)));
        assertXyz(1, 2, 2, moved(Direction.EAST, p -> p.moveLeft(1)));
    }

    @Test
    void moveUpOnlyMovesHorizontalOrientations() {
        assertXyz(1, 3, 3, moved(Direction.NORTH, p -> p.moveUp(1)));
        assertXyz(1, 1, 3, moved(Direction.WEST, p -> p.moveDown(1)));
        assertXyz(1, 2, 3, moved(Direction.UP, p -> p.moveUp(1)));
        assertXyz(1, 2, 3, moved(Direction.DOWN, p -> p.moveDown(1)));
    }

    @Test
    void unknownOrientationDoesNotMove() {
        Position p = new Position(1, 2, 3);
        assertNull(p.orientation);

        p.moveForwards(1);
        p.moveRight(1);
        p.moveUp(1);
        assertXyz(1, 2, 3, p);
    }

    @Test
    void copyConstructorCopiesCoordinatesAndOrientation() {
        Position source = new Position(1.5, 2.5, 3.5, Direction.EAST);
        Position copy = new Position(source);
        assertXyz(1.5, 2.5, 3.5, copy);
        assertEquals(Direction.EAST, copy.orientation);

        copy.moveForwards(1);
        assertXyz(1.5, 2.5, 3.5, source);
    }

    @Test
    void minAndMaxArePerAxis() {
        Position a = new Position(1, 5, -2);
        Position b = new Position(3, 0, -4);

        assertXyz(1, 0, -4, a.min(b));
        assertXyz(1, 0, -4, b.min(a));
        assertXyz(3, 5, -2, a.max(b));
        assertXyz(3, 5, -2, b.max(a));
        assertNull(a.min(b).orientation);
    }

    @Test
    void savesUnderTheIjkKeysAndLoadsWithoutOrientation() {
        TagValueOutput output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        new Position(1.5, -2, 300.25, Direction.UP).writeToNBT(output);
        CompoundTag tag = output.buildResult();

        assertEquals(1.5, tag.getDoubleOr("i", 0));
        assertEquals(-2, tag.getDoubleOr("j", 0));
        assertEquals(300.25, tag.getDoubleOr("k", 0));

        Position loaded = new Position(TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, tag));
        assertXyz(1.5, -2, 300.25, loaded);
        assertNull(loaded.orientation);
    }

    @Test
    void toStringListsTheCoordinates() {
        assertEquals("{1.0, 2.5, -3.0}", new Position(1, 2.5, -3).toString());
    }

    private interface Move {
        void apply(Position p);
    }

    private static Position moved(Direction orientation, Move move) {
        Position p = new Position(1, 2, 3, orientation);
        move.apply(p);
        return p;
    }

    private static void assertXyz(double x, double y, double z, Position p) {
        assertEquals(x, p.x, 1e-9, "x of " + p);
        assertEquals(y, p.y, 1e-9, "y of " + p);
        assertEquals(z, p.z, 1e-9, "z of " + p);
    }
}
