/*
 * Buildcraft Reforged, 2026: new file, no 4.2.2 original. The status panel's State line (plan section 10).
 */
package buildcraftreforged.core.status;

/** The State line of the status panel (plan section 10). The order is the wire order; append new states at the end. */
public enum MachineStatus {
    WORKING(0x2a7f2a),
    IDLE(0x404040),
    BLOCKED(0xb02020),
    NO_POWER(0xb06000),
    OVERHEATING(0xb02020),
    OUT_OF_FUEL(0xb06000),
    NO_WORK_AREA(0xb06000);

    /** Text colour of the value in the panel. */
    public final int colour;

    MachineStatus(int colour) {
        this.colour = colour;
    }

    public String getTranslationKey() {
        return "gui.status.state." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
