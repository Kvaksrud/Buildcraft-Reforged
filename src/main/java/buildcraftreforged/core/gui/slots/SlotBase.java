/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: IInventory is the vanilla Container until #23 ports the inventory layer;
 * xDisplayPosition/yDisplayPosition are x/y and getStack() == null is getItem().isEmpty().
 */
package buildcraftreforged.core.gui.slots;

import buildcraftreforged.core.gui.tooltips.IToolTipProvider;
import buildcraftreforged.core.gui.tooltips.ToolTip;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;

/**
 *
 * @author CovertJaguar <http://www.railcraft.info>
 */
public class SlotBase extends Slot implements IToolTipProvider {

    private ToolTip toolTips;

    public SlotBase(Container iinventory, int slotIndex, int posX, int posY) {
        super(iinventory, slotIndex, posX, posY);
    }

    public boolean canShift() {
        return true;
    }

    /**
     * @return the toolTips
     */
    @Override
    public ToolTip getToolTip() {
        return toolTips;
    }

    /**
     * @param toolTips the tooltips to set
     */
    public void setToolTips(ToolTip toolTips) {
        this.toolTips = toolTips;
    }

    @Override
    public boolean isToolTipVisible() {
        return getItem().isEmpty();
    }

    @Override
    public boolean isMouseOver(int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + 16 && mouseY >= y && mouseY <= y + 16;
    }
}
