/*
 * Ported from buildcraft/core/gui/tooltips/ToolTip.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: package name only.
 */
package buildcraftreforged.core.gui.tooltips;

import com.google.common.collect.ForwardingList;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author CovertJaguar <http://www.railcraft.info/>
 */
public class ToolTip extends ForwardingList<ToolTipLine> {

    private final List<ToolTipLine> delegate = new ArrayList<>();
    private final long delay;
    private long mouseOverStart;

    public ToolTip() {
        this.delay = 0;
    }

    public ToolTip(int delay) {
        this.delay = delay;
    }

    @Override
    protected final List<ToolTipLine> delegate() {
        return delegate;
    }

    public void onTick(boolean mouseOver) {
        if (delay == 0) {
            return;
        }
        if (mouseOver) {
            if (mouseOverStart == 0) {
                mouseOverStart = System.currentTimeMillis();
            }
        } else {
            mouseOverStart = 0;
        }
    }

    public boolean isReady() {
        if (delay == 0) {
            return true;
        }
        if (mouseOverStart == 0) {
            return false;
        }
        return System.currentTimeMillis() - mouseOverStart >= delay;
    }

    public void refresh() {
    }
}
