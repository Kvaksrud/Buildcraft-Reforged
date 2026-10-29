/*
 * Modified by Buildcraft Reforged, 2026: ported from buildcraft/api/inventory/ISelectiveInventory.java (no file header
 * at 4.2.2); Direction in place of ForgeDirection.
 */

package buildcraftreforged.api.inventory;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

/**
 * An {@link ISpecialInventory} that can extract only requested items. Ported from 4.2.2
 * {@code buildcraft.api.inventory.ISelectiveInventory}.
 *
 * <p><b>Plan section 8:</b> not expected to survive P3, for the same reason as {@link ISpecialInventory}; the Filtered
 * Extractor module filters on the item handler capability.
 */
public interface ISelectiveInventory extends ISpecialInventory {
    /**
     * Requests specified items to be extracted from the inventory
     *
     * @param desired
     *            Array which can contain ItemStacks, Items, or classes describing the type of item accepted or excluded.
     * @param exclusion
     *            If true desired items are not eligible for returning.
     * @param doRemove
     *            If false no actual extraction may occur.
     * @param from
     *            Orientation the ItemStack is requested from.
     * @param maxItemCount
     *            Maximum amount of items to extract (spread over all returned item stacks)
     * @return Array of item stacks extracted from the inventory
     */
    ItemStack[] extractItem(Object[] desired, boolean exclusion, boolean doRemove, Direction from, int maxItemCount);
}
