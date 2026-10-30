/*
 * Ported from buildcraft/core/gui/widgets/IIndicatorController.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: package name only.
 */
package buildcraftreforged.core.gui.widgets;

import buildcraftreforged.core.gui.tooltips.ToolTip;

/**
 *
 * @author CovertJaguar <http://www.railcraft.info>
 */
public interface IIndicatorController {

    ToolTip getToolTip();

    int getScaledLevel(int size);
}
