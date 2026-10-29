/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: IInventory is the vanilla Container; isItemValid is mayPlace and
 * canTakeStack is mayPickup.
 */
package buildcraftreforged.core.gui.slots;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 *
 * @author CovertJaguar <http://www.railcraft.info>
 */
public class SlotUntouchable extends SlotBase implements IPhantomSlot {

    public SlotUntouchable(Container contents, int id, int x, int y) {
        super(contents, id, x, y);
    }

    @Override
    public boolean mayPlace(ItemStack itemstack) {
        return false;
    }

    @Override
    public boolean mayPickup(Player par1EntityPlayer) {
        return false;
    }

    @Override
    public boolean canAdjust() {
        return false;
    }

    @Override
    public boolean canShift() {
        return false;
    }
}
