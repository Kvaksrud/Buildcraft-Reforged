package buildcraftreforged.transport.route;

import org.jspecify.annotations.Nullable;

/**
 * The outcome of one transfer (docs/TRANSPORT.md 6.3). Whatever was not moved stays where it was; nothing is ever
 * spawned in the world. {@code route} is the walk's result when the walk found a destination, else null.
 */
public record TransferResult(int moved, Reason reason, PipeWalk.@Nullable Route route) {

    public enum Reason {
        /** Everything offered was moved. */
        MOVED,
        /** Some, not all, of what was offered was moved. */
        PARTIAL,
        /** The walk found no destination. */
        NO_DESTINATION,
        /** A destination exists, but the budgets along the path (or the destination) took nothing. */
        NO_CAPACITY,
        /** The source had nothing to offer. */
        SOURCE_EMPTY
    }

    public static final TransferResult SOURCE_EMPTY = new TransferResult(0, Reason.SOURCE_EMPTY, null);
    public static final TransferResult NO_DESTINATION = new TransferResult(0, Reason.NO_DESTINATION, null);

    public boolean movedAny() {
        return moved > 0;
    }
}
