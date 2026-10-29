/*
 * Ported from buildcraft/core/gui/tooltips/ToolTipLine.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: package name only. The colour is still a formatting code (0 to 15, -1 for
 * the default grey), as 4.2.2 wrote it after a section sign.
 */
package buildcraftreforged.core.gui.tooltips;

/**
 *
 * @author CovertJaguar <http://www.railcraft.info>
 */
public class ToolTipLine {

    public String text;
    public final int color;
    public int spacing;

    public ToolTipLine(String text, int color) {
        this.text = text;
        this.color = color;
    }

    public ToolTipLine(String text) {
        this(text, -1);
    }

    public ToolTipLine() {
        this("", -1);
    }

    public void setSpacing(int spacing) {
        this.spacing = spacing;
    }

    public int getSpacing() {
        return spacing;
    }
}
