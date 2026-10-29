/*
 * Modified by Buildcraft Reforged, 2026: ported from buildcraft/api/inventory/ISpecialInventory.java (no file header
 * at 4.2.2); vanilla Container and Direction in place of IInventory and ForgeDirection.
 */

package buildcraftreforged.api.inventory;

import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * An inventory that decides itself how stacks are added and extracted. Ported from 4.2.2
 * {@code buildcraft.api.inventory.ISpecialInventory}.
 *
 * <p><b>Plan section 8:</b> not expected to survive P3. Rate-based routing finds a destination with a simulated insert
 * on the item handler capability, which already lets a block decide per side what it accepts and gives. Kept until P3
 * replaces its callers (pipes, Builder, Quarry output).
 */
public interface ISpecialInventory extends Container {

    /**
     * Offers an ItemStack for addition to the inventory.
     *
     * @param stack
     *            ItemStack offered for addition. Do not manipulate this!
     * @param doAdd
     *            If false no actual addition should take place. Implementors should simulate.
     * @param from
     *            Orientation the ItemStack is offered from.
     * @return Amount of items used from the passed stack.
     */
    int addItem(ItemStack stack, boolean doAdd, Direction from);

    /**
     * Requests items to be extracted from the inventory
     *
     * @param doRemove
     *            If false no actual extraction may occur. Implementors should simulate.
     *            Can be used to "peek" at what the result would be
     * @param from
     *            Orientation the ItemStack is requested from.
     * @param maxItemCount
     *            Maximum amount of items to extract (spread over all returned item stacks)
     * @return Array of item stacks that were/would be extracted from the inventory
     */
    ItemStack[] extractItem(boolean doRemove, Direction from, int maxItemCount);

}
