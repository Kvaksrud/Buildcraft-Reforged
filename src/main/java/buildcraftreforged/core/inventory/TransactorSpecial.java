/*
 * Ported from buildcraft/core/inventory/TransactorSpecial.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: ForgeDirection becomes Direction, null stacks become ItemStack.EMPTY.
 */
package buildcraftreforged.core.inventory;

import buildcraftreforged.api.inventory.ISpecialInventory;
import buildcraftreforged.core.inventory.filters.IStackFilter;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public class TransactorSpecial extends Transactor {

    protected ISpecialInventory inventory;

    public TransactorSpecial(ISpecialInventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public int inject(ItemStack stack, @Nullable Direction orientation, boolean doAdd) {
        return inventory.addItem(stack, doAdd, orientation);
    }

    @Override
    public ItemStack remove(IStackFilter filter, @Nullable Direction orientation, boolean doRemove) {
        ItemStack[] extracted = inventory.extractItem(false, orientation, 1);
        if (extracted != null && extracted.length > 0 && extracted[0] != null && !extracted[0].isEmpty() && filter.matches(extracted[0])) {
            if (doRemove) {
                inventory.extractItem(true, orientation, 1);
            }
            return extracted[0];
        }
        return ItemStack.EMPTY;
    }
}
