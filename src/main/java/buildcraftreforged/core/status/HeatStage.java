/*
 * Buildcraft Reforged, 2026: new file, no 4.2.2 original. The heat stages of the status panel's Heat line.
 */
package buildcraftreforged.core.status;

/**
 * Heat stage on the status panel's Heat line. Named and ordered as 4.2.2's {@code TileEngine.EnergyStage}, so an engine
 * maps its stage with {@code HeatStage.values()[stage.ordinal()]}.
 */
public enum HeatStage {
    BLUE(0x2050c0),
    GREEN(0x2a7f2a),
    YELLOW(0xa08000),
    RED(0xb02020),
    OVERHEAT(0xb02020);

    public final int colour;

    HeatStage(int colour) {
        this.colour = colour;
    }

    public String getTranslationKey() {
        return "gui.status.heat." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
