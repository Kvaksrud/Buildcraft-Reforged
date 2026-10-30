/*
 * Ported from buildcraft/core/inventory/TransactorRoundRobin.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: IInventory becomes Container, ForgeDirection becomes Direction, null stacks
 * become ItemStack.EMPTY.
 */
package buildcraftreforged.core.inventory;

import buildcraftreforged.core.inventory.InventoryIterator.IInvSlot;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public class TransactorRoundRobin extends TransactorSimple {

    public TransactorRoundRobin(Container inventory) {
        super(inventory);
    }

    @Override
    public int inject(ItemStack stack, @Nullable Direction orientation, boolean doAdd) {

        int added = 0;

        for (int itemLoop = 0; itemLoop < stack.getCount(); ++itemLoop) { // add 1 item n times.

            int smallestStackSize = Integer.MAX_VALUE;
            IInvSlot minSlot = null;

            for (IInvSlot slot : InventoryIterator.getIterable(inventory, orientation)) {
                ItemStack stackInInventory = slot.getStackInSlot();

                if (stackInInventory.isEmpty()) {
                    continue;
                }

                if (stackInInventory.getCount() >= stackInInventory.getMaxStackSize()) {
                    continue;
                }

                if (stackInInventory.getCount() >= inventory.getMaxStackSize()) {
                    continue;
                }

                if (StackHelper.instance().canStacksMerge(stack, stackInInventory) && stackInInventory.getCount() < smallestStackSize) {
                    smallestStackSize = stackInInventory.getCount();
                    minSlot = slot;
                }
                if (smallestStackSize <= 1) {
                    break;
                }
            }

            if (minSlot != null) {
                added += addToSlot(minSlot, stack, stack.getCount() - 1, doAdd); // add 1 item n times, into the selected slot
            } else {
                break;
            }

        }

        return added;
    }
}
