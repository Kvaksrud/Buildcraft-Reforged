package buildcraftreforged.core.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import buildcraftreforged.api.core.Position;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class UtilsTest {

    private static final Position ORIGIN = new Position(0, 0, 0);

    @Test
    void get2dOrientationPointsFromTheFirstPositionToTheSecond() {
        assertEquals(Direction.EAST, Utils.get2dOrientation(ORIGIN, new Position(5, 0, 0)));
        assertEquals(Direction.WEST, Utils.get2dOrientation(ORIGIN, new Position(-5, 0, 0)));
        assertEquals(Direction.SOUTH, Utils.get2dOrientation(ORIGIN, new Position(0, 0, 5)));
        assertEquals(Direction.NORTH, Utils.get2dOrientation(ORIGIN, new Position(0, 0, -5)));
        assertEquals(Direction.EAST, Utils.get2dOrientation(ORIGIN, new Position(5, 0, 1)));
    }

    @Test
    void get3dOrientationAddsUpAndDown() {
        assertEquals(Direction.UP, Utils.get3dOrientation(ORIGIN, new Position(0, 5, 0)));
        assertEquals(Direction.DOWN, Utils.get3dOrientation(ORIGIN, new Position(0, -5, 0)));
        assertEquals(Direction.EAST, Utils.get3dOrientation(ORIGIN, new Position(5, 1, 0)));
    }

    @Test
    void concatJoinsArraysInOrder() {
        assertArrayEquals(new int[] { 1, 2, 3 }, Utils.concat(new int[] { 1 }, new int[] { 2, 3 }));
        assertArrayEquals(new float[] { 1F, 2F }, Utils.concat(new float[] { 1F }, new float[] { 2F }));
        assertArrayEquals(new String[] { "a", "b", "c" }, Utils.concat(new String[] { "a", "b" }, new String[] { "c" }));
    }

    @Test
    void createSlotArrayCountsUpFromFirst() {
        assertArrayEquals(new int[] { 4, 5, 6 }, Utils.createSlotArray(4, 3));
        assertArrayEquals(new int[0], Utils.createSlotArray(9, 0));
    }
}
