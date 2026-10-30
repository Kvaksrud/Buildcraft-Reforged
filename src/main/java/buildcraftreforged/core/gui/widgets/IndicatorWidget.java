/*
 * Ported from buildcraft/core/gui/widgets/IndicatorWidget.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: package name and the generic GuiBuildCraft only.
 */
package buildcraftreforged.core.gui.widgets;

import buildcraftreforged.core.gui.GuiBuildCraft;
import buildcraftreforged.core.gui.tooltips.ToolTip;

/**
 *
 * @author CovertJaguar <http://www.railcraft.info>
 */
public class IndicatorWidget extends Widget {

    public final IIndicatorController controller;

    public IndicatorWidget(IIndicatorController controller, int x, int y, int u, int v, int w, int h) {
        super(x, y, u, v, w, h);
        this.controller = controller;
    }

    @Override
    public void draw(GuiBuildCraft<?> gui, int guiX, int guiY, int mouseX, int mouseY) {
        int scale = controller.getScaledLevel(h);
        gui.drawTexturedModalRect(guiX + x, guiY + y + h - scale, u, v + h - scale, w, scale);
    }

    @Override
    public ToolTip getToolTip() {
        return controller.getToolTip();
    }
}
