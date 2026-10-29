/*
 * Ported from buildcraft/core/gui/tooltips/IToolTipProvider.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: package name only.
 */
package buildcraftreforged.core.gui.tooltips;

/**
 *
 * @author CovertJaguar <http://www.railcraft.info/>
 */
public interface IToolTipProvider {

    ToolTip getToolTip();

    boolean isToolTipVisible();

    boolean isMouseOver(int mouseX, int mouseY);
}
