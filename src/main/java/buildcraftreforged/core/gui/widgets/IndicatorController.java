/*
 * Ported from buildcraft/core/gui/widgets/IndicatorController.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: package name only.
 */
package buildcraftreforged.core.gui.widgets;

import buildcraftreforged.core.gui.tooltips.ToolTip;
import buildcraftreforged.core.gui.tooltips.ToolTipLine;

/**
 *
 * @author CovertJaguar <http://www.railcraft.info/>
 */
public abstract class IndicatorController implements IIndicatorController {

    private final ToolTip tips = new ToolTip() {
        @Override
        public void refresh() {
            refreshToolTip();
        }
    };
    protected ToolTipLine tip = new ToolTipLine();

    public IndicatorController() {
        tips.add(tip);
    }

    protected void refreshToolTip() {
    }

    @Override
    public final ToolTip getToolTip() {
        return tips;
    }
}
