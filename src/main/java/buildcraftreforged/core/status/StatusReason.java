/*
 * Buildcraft Reforged, 2026: new file, no 4.2.2 original. The status panel's Reason line (plan section 10).
 */
package buildcraftreforged.core.status;

/**
 * The Reason line of the status panel: what is blocked (plan section 10, docs/TRANSPORT.md 6.3). {@link #NONE} hides the
 * line. The order is the wire order; append new reasons at the end.
 */
public enum StatusReason {
    NONE,
    /** "Output blocked": the push found a destination that took nothing (TRANSPORT.md 6.3, NO_CAPACITY). */
    OUTPUT_FULL,
    /** "No destination": nothing next to the machine takes its output (TRANSPORT.md 6.3, NO_DESTINATION). */
    NO_DESTINATION,
    /** The owner may not break or place the block (claims, plan section 6 fake player). */
    PROTECTED_BLOCK,
    /** A builder or table is waiting for materials. */
    MISSING_MATERIALS;

    public String getTranslationKey() {
        return "gui.status.reason." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
