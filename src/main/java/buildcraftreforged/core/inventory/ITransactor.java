/*
 * Ported from buildcraft/core/inventory/ITransactor.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: ForgeDirection becomes Direction (null for UNKNOWN); remove returns
 * ItemStack.EMPTY where it returned null.
 */
package buildcraftreforged.core.inventory;

import buildcraftreforged.core.inventory.filters.IStackFilter;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public interface ITransactor {

    /**
     * Adds an Item to the inventory.
     *
     * @param stack
     * @param orientation
     * @param doAdd
     * @return The ItemStack, with stackSize equal to amount moved.
     */
    ItemStack add(ItemStack stack, @Nullable Direction orientation, boolean doAdd);

    /**
     * Removes and returns a single item from the inventory matching the filter.
     *
     * @param filter
     * @param orientation
     * @param doRemove
     * @return the item, or ItemStack.EMPTY
     */
    ItemStack remove(IStackFilter filter, @Nullable Direction orientation, boolean doRemove);
}
