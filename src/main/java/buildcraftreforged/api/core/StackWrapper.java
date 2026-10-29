/*
 * Copyright (c) SpaceToad, 2011-2012
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: item id, damage and NBT become the item and its data components, so damage
 * now always counts (it is a component) and hashCode agrees with equals.
 */
package buildcraftreforged.api.core;

import net.minecraft.world.item.ItemStack;

/**
 *
 * @author CovertJaguar <http://www.railcraft.info/>
 */
public class StackWrapper {

    public final ItemStack stack;

    public StackWrapper(ItemStack stack) {
        this.stack = stack;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 67 * hash + ItemStack.hashItemAndComponents(stack);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final StackWrapper other = (StackWrapper) obj;
        if (!ItemStack.isSameItemSameComponents(stack, other.stack))
            return false;
        return true;
    }
}
