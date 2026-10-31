package buildcraftreforged.transport.module;

import java.util.function.IntFunction;
import java.util.function.IntPredicate;

/**
 * The round-robin filter cursor of the Filtered Extractor (docs/TRANSPORT.md 8.3, 4.2.2 {@code PipeItemsEmerald}
 * {@code getCurrentFilter} and {@code incrementFilter}), level-free so it is unit-tested. The filters are given as
 * "slot {@code i} holds a filter".
 */
public final class FilterCursor {

    public static final int SIZE = 9;

    /** What one try with one filter gave. */
    public enum Outcome {
        /** Items moved: the cursor moves on. */
        MOVED,
        /** Nothing matching that filter could be moved: non-blocking mode tries the next filter. */
        NOT_FOUND,
        /** Stop for this tick without moving the cursor, e.g. no budget left. */
        STOP
    }

    private int current;

    public int get() {
        return current;
    }

    public void set(int current) {
        this.current = Math.floorMod(current, SIZE);
    }

    /** {@code getCurrentFilter}: the current slot, moving past an empty one first; -1 when every slot is empty. */
    public int current(IntPredicate hasFilter) {
        if (!hasFilter.test(current))
            increment(hasFilter);
        return hasFilter.test(current) ? current : -1;
    }

    /** {@code incrementFilter}: one step, then past empty slots, at most {@link #SIZE} more steps. */
    public void increment(IntPredicate hasFilter) {
        current = (current + 1) % SIZE;
        for (int count = 0; !hasFilter.test(current) && count < SIZE; count++) {
            current = (current + 1) % SIZE;
        }
    }

    /**
     * One extraction attempt: try the current filter; on success move on. Blocking mode waits on a filter whose item
     * is missing; non-blocking mode tries each following filter once ({@code count < 9}, as 4.2.2). Returns whether
     * anything moved.
     */
    public boolean attempt(IntPredicate hasFilter, boolean blocking, IntFunction<Outcome> tryFilter) {
        int slot = current(hasFilter);
        if (slot < 0)
            return false;
        Outcome outcome = tryFilter.apply(slot);
        for (int count = 1; outcome == Outcome.NOT_FOUND && !blocking && count < SIZE; count++) {
            increment(hasFilter);
            outcome = tryFilter.apply(current);
        }
        if (outcome == Outcome.MOVED)
            increment(hasFilter);
        return outcome == Outcome.MOVED;
    }
}
