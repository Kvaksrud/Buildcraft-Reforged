package buildcraftreforged.transport.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

/**
 * docs/TRANSPORT.md section 14, #56 and #57: the Sorter and Colour Sorter candidate rules, the Pickup's range and
 * sucking boxes, and the Filtered Colour Tag's cursor, without a level.
 */
class RoutingModuleLogicTest {

    private static final int DOWN = 0;
    private static final int UP = 1;
    private static final int NORTH = 2;
    private static final int SOUTH = 3;
    private static final int WEST = 4;
    private static final int EAST = 5;

    // Sorter (8.5)

    @Test
    void sorterWeighsBySlotCountAndNeverUsesFilteredNonMatchingSides() {
        // NORTH has two matching slots, SOUTH one, EAST a filter for something else, UP no filter.
        boolean[] filtered = new boolean[6];
        filtered[NORTH] = filtered[SOUTH] = filtered[EAST] = true;
        int[] matches = new int[6];
        matches[NORTH] = 2;
        matches[SOUTH] = 1;
        RandomSource random = RandomSource.create(7);
        int north = 0;
        int walks = 9000;
        for (int i = 0; i < walks; i++) {
            int[] sides = {UP, NORTH, SOUTH, EAST};
            int count = CandidateOrder.sorter(sides, 4, filtered, matches, random);
            assertEquals(2, count, "only the matching sides are tried");
            assertTrue(sides[0] != sides[1] && (sides[0] == NORTH || sides[0] == SOUTH));
            if (sides[0] == NORTH)
                north++;
        }
        assertEquals(2.0 / 3.0, north / (double) walks, 0.02);
    }

    @Test
    void sorterWithoutMatchesUsesTheUnfilteredSidesEvenly() {
        boolean[] filtered = new boolean[6];
        filtered[EAST] = true;
        RandomSource random = RandomSource.create(8);
        int up = 0;
        for (int i = 0; i < 9000; i++) {
            int[] sides = {UP, NORTH, EAST};
            int count = CandidateOrder.sorter(sides, 3, filtered, new int[6], random);
            assertEquals(2, count);
            assertTrue(Arrays.stream(sides, 0, 2).noneMatch(side -> side == EAST), "a filtered side was used");
            if (sides[0] == UP)
                up++;
        }
        assertEquals(0.5, up / 9000.0, 0.02);
        // Every side filtered for other items: nowhere to go.
        boolean[] all = new boolean[6];
        Arrays.fill(all, true);
        assertEquals(0, CandidateOrder.sorter(new int[] {UP, NORTH}, 2, all, new int[6], random));
    }

    // Colour Sorter (8.9)

    @Test
    void colourSorterSendsMatchingItemsOutOfTheOutputAndOthersElsewhere() {
        RandomSource random = RandomSource.create(1);
        int[] sides = {UP, NORTH, EAST};
        assertEquals(1, CandidateOrder.colourSorter(sides, 3, EAST, true, random));
        assertEquals(EAST, sides[0]);

        for (int i = 0; i < 100; i++) {
            int[] others = {UP, NORTH, EAST};
            assertEquals(2, CandidateOrder.colourSorter(others, 3, EAST, false, random));
            assertTrue(others[0] != EAST && others[1] != EAST, "a non-matching item may leave by the output");
        }
        // Matching, but the output is no candidate: the other sides.
        int[] noOutput = {UP, NORTH};
        assertEquals(2, CandidateOrder.colourSorter(noOutput, 2, EAST, true, random));
        // No output side at all: every side.
        assertEquals(3, CandidateOrder.colourSorter(new int[] {UP, NORTH, EAST}, 3, -1, true, random));
    }

    // Pickup (8.6, Q12)

    @Test
    void pickupRangeGrowsEveryThreeTiers() {
        int[] expected = {1, 1, 1, 2, 2, 2, 3, 3, 3, 4};
        for (int tier = 1; tier <= 10; tier++) {
            assertEquals(expected[tier - 1], ModulePickup.range(tier), "range of tier " + tier);
        }
    }

    /** 4.2.2 {@code PipeItemsObsidian.getSuckingBox}, case by case, for comparison. */
    private static AABB legacyBox(int x, int y, int z, Direction open, int d) {
        double x1 = x, y1 = y, z1 = z, x2 = x, y2 = y, z2 = z;
        switch (open) {
        case EAST -> { x1 += d; x2 += 1 + d; }
        case WEST -> { x1 -= d - 1; x2 -= d; }
        case UP, DOWN -> { x1 += d + 1; x2 -= d; z1 += d + 1; z2 -= d; }
        case SOUTH -> { z1 += d; z2 += d + 1; }
        default -> { z1 -= d - 1; z2 -= d; }
        }
        switch (open) {
        case EAST, WEST -> { y1 += d + 1; y2 -= d; z1 += d + 1; z2 -= d; }
        case UP -> { y1 += d + 1; y2 += d; }
        case DOWN -> { y1 -= d - 1; y2 -= d; }
        default -> { y1 += d + 1; y2 -= d; x1 += d + 1; x2 -= d; }
        }
        return new AABB(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2), Math.max(y1, y2),
                Math.max(z1, z2));
    }

    @Test
    void suckingBoxIsTheLegacySlabForEverySide() {
        for (Direction open : Direction.values()) {
            for (int d = 1; d <= 4; d++) {
                AABB box = ModulePickup.suckingBox(new BlockPos(10, 20, 30), open, d);
                assertEquals(legacyBox(10, 20, 30, open, d), box, open + " at " + d);
                assertEquals(1.0, Math.min(box.getXsize(), Math.min(box.getYsize(), box.getZsize())), 1e-9);
                assertEquals(2 * d + 1, Math.max(box.getXsize(), Math.max(box.getYsize(), box.getZsize())), 1e-9);
            }
        }
    }

    @Test
    void pickupNeedsExactlyOneConnection() {
        assertEquals(Direction.UP, ModulePickup.openSide((byte) 0b000001));
        assertEquals(Direction.WEST, ModulePickup.openSide((byte) 0b100000));
        assertEquals(null, ModulePickup.openSide((byte) 0));
        assertEquals(null, ModulePickup.openSide((byte) 0b000011));
    }

    // Filtered Colour Tag (8.10)

    @Test
    void filteredColourTagCursorSkipsEmptyAndInactiveFilters() {
        boolean[] usable = {true, false, false, true};
        int cursor = 0;
        int[] seen = new int[6];
        for (int i = 0; i < 6; i++) {
            cursor = ModuleFilteredColourTag.nextFilter(cursor, slot -> usable[slot]);
            seen[i] = Math.floorMod(cursor, ModuleFilteredColourTag.FILTERS);
        }
        assertEquals("[3, 0, 3, 0, 3, 0]", Arrays.toString(seen));
        // Nothing usable: four extra steps at most, landing on an unusable filter.
        int next = ModuleFilteredColourTag.nextFilter(0, slot -> false);
        assertEquals(5, next);
    }
}
