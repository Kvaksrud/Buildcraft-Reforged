package buildcraftreforged.transport.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import buildcraftreforged.api.transport.IPipeTile.PipeType;
import buildcraftreforged.transport.PipeModuleType;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

/** docs/TRANSPORT.md section 14, #55: the level-free parts of the Extractor, Filtered Extractor and Director. */
class ModuleLogicTest {

    // Facing search (8.2, 8.4): facing+1 … facing+6 in side order DOWN, UP, NORTH, SOUTH, WEST, EAST.

    @Test
    void facingSwitchTriesTheFollowingSidesInOrder() {
        List<Direction> tried = new ArrayList<>();
        assertNull(ModuleFacing.next(Direction.NORTH, side -> {
            tried.add(side);
            return false;
        }));
        assertEquals(List.of(Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.DOWN, Direction.UP,
                Direction.NORTH), tried);
    }

    @Test
    void facingSwitchKeepsTheFirstValidSide() {
        Set<Direction> valid = EnumSet.of(Direction.DOWN, Direction.WEST);
        assertEquals(Direction.WEST, ModuleFacing.next(Direction.NORTH, valid::contains));
        assertEquals(Direction.DOWN, ModuleFacing.next(Direction.WEST, valid::contains));
        assertEquals(Direction.WEST, ModuleFacing.next(Direction.DOWN, valid::contains));
        // The only valid side is the current one: the search ends on it.
        assertEquals(Direction.EAST, ModuleFacing.next(Direction.EAST, side -> side == Direction.EAST));
    }

    @Test
    void noFacingStartsAtUpLikeMetadataSix() {
        List<Direction> tried = new ArrayList<>();
        ModuleFacing.next(null, side -> {
            tried.add(side);
            return false;
        });
        assertEquals(List.of(Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST,
                Direction.DOWN), tried);
    }

    @Test
    void facingIndexRoundTrips() {
        assertEquals(-1, ModuleFacing.toIndex(null));
        assertNull(ModuleFacing.fromIndex(-1));
        assertNull(ModuleFacing.fromIndex(6));
        for (Direction side : Direction.values()) {
            assertEquals(side, ModuleFacing.fromIndex(ModuleFacing.toIndex(side)));
        }
    }

    // Filtered Extractor (8.3)

    /** Filters in the given slots; {@code found} lists the slots whose item the source has. */
    private static List<Integer> run(FilterCursor cursor, Set<Integer> filters, Set<Integer> found, boolean blocking,
            int attempts) {
        List<Integer> moved = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            cursor.attempt(filters::contains, blocking, slot -> {
                if (!found.contains(slot))
                    return FilterCursor.Outcome.NOT_FOUND;
                moved.add(slot);
                return FilterCursor.Outcome.MOVED;
            });
        }
        return moved;
    }

    @Test
    void roundRobinOverTheFilters() {
        FilterCursor cursor = new FilterCursor();
        assertEquals(List.of(0, 2, 5, 0, 2, 5), run(cursor, Set.of(0, 2, 5), Set.of(0, 2, 5), true, 6));
    }

    @Test
    void blockingWaitsOnAMissingItem() {
        FilterCursor cursor = new FilterCursor();
        // Slot 2's item is missing: blocking moves slot 0 once, then waits on slot 2.
        assertEquals(List.of(0), run(cursor, Set.of(0, 2, 5), Set.of(0, 5), true, 5));
        assertEquals(2, cursor.get());
    }

    @Test
    void nonBlockingSkipsAMissingItem() {
        FilterCursor cursor = new FilterCursor();
        assertEquals(List.of(0, 5, 0, 5), run(cursor, Set.of(0, 2, 5), Set.of(0, 5), false, 4));
    }

    @Test
    void nonBlockingWithNothingFoundMovesNothing() {
        FilterCursor cursor = new FilterCursor();
        assertEquals(List.of(), run(cursor, Set.of(1, 4), Set.of(), false, 3));
    }

    @Test
    void allFiltersEmptyMovesNothingAndTriesNothing() {
        FilterCursor cursor = new FilterCursor();
        boolean[] tried = {false};
        assertFalse(cursor.attempt(slot -> false, false, slot -> {
            tried[0] = true;
            return FilterCursor.Outcome.MOVED;
        }));
        assertFalse(tried[0]);
        assertEquals(-1, cursor.current(slot -> false));
    }

    @Test
    void stopKeepsTheCursorAndTriesNoFurtherFilter() {
        FilterCursor cursor = new FilterCursor();
        List<Integer> tried = new ArrayList<>();
        assertFalse(cursor.attempt(Set.of(3, 7)::contains, false, slot -> {
            tried.add(slot);
            return FilterCursor.Outcome.STOP;
        }));
        assertEquals(List.of(3), tried);
        assertEquals(3, cursor.get());
    }

    @Test
    void cursorIsKeptInRange() {
        FilterCursor cursor = new FilterCursor();
        cursor.set(22);
        assertEquals(4, cursor.get());
        cursor.set(-1);
        assertEquals(8, cursor.get());
        cursor.increment(slot -> true);
        assertEquals(0, cursor.get());
    }

    // Which kinds take which module (8.1, Q8)

    @Test
    void moduleMatrix() {
        assertTrue(PipeModuleType.EXTRACTOR.accepts(PipeType.ITEM));
        assertTrue(PipeModuleType.EXTRACTOR.accepts(PipeType.FLUID));
        assertFalse(PipeModuleType.EXTRACTOR.accepts(PipeType.POWER));
        assertTrue(PipeModuleType.FILTERED_EXTRACTOR.accepts(PipeType.ITEM));
        assertFalse(PipeModuleType.FILTERED_EXTRACTOR.accepts(PipeType.FLUID));
        assertFalse(PipeModuleType.FILTERED_EXTRACTOR.accepts(PipeType.POWER));
        assertTrue(PipeModuleType.DIRECTOR.accepts(PipeType.ITEM));
        assertTrue(PipeModuleType.DIRECTOR.accepts(PipeType.FLUID));
        assertTrue(PipeModuleType.DIRECTOR.accepts(PipeType.POWER));
        for (PipeModuleType type : PipeModuleType.values()) {
            assertFalse(type.accepts(PipeType.STRUCTURE), type.toString());
        }
    }
}
