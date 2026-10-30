/*
 * Ported from buildcraft/core/inventory/TransactorSimple.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: IInventory becomes Container, ForgeDirection becomes Direction, null stacks
 * become ItemStack.EMPTY, onInventoryChanged becomes setChanged.
 */
package buildcraftreforged.core.inventory;

import buildcraftreforged.core.inventory.InventoryIterator.IInvSlot;
import buildcraftreforged.core.inventory.filters.IStackFilter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public class TransactorSimple extends Transactor {

    protected Container inventory;

    public TransactorSimple(Container inventory) {
        this.inventory = inventory;
    }

    @Override
    public int inject(ItemStack stack, @Nullable Direction orientation, boolean doAdd) {
        List<IInvSlot> filledSlots = new ArrayList<IInvSlot>(inventory.getContainerSize());
        List<IInvSlot> emptySlots = new ArrayList<IInvSlot>(inventory.getContainerSize());
        for (IInvSlot slot : InventoryIterator.getIterable(inventory, orientation)) {
            if (slot.canPutStackInSlot(stack)) {
                if (slot.getStackInSlot().isEmpty()) {
                    emptySlots.add(slot);
                } else {
                    filledSlots.add(slot);
                }
            }
        }

        int injected = 0;
        injected = tryPut(stack, filledSlots, injected, doAdd);
        injected = tryPut(stack, emptySlots, injected, doAdd);

        inventory.setChanged();
        return injected;
    }

    private int tryPut(ItemStack stack, List<IInvSlot> slots, int injected, boolean doAdd) {
        if (injected >= stack.getCount()) {
            return injected;
        }
        for (IInvSlot slot : slots) {
            ItemStack stackInSlot = slot.getStackInSlot();
            if (stackInSlot.isEmpty() || StackHelper.instance().canStacksMerge(stackInSlot, stack)) {
                int used = addToSlot(slot, stack, injected, doAdd);
                if (used > 0) {
                    injected += used;
                    if (injected >= stack.getCount()) {
                        return injected;
                    }
                }
            }
        }
        return injected;
    }

    /**
     *
     * @param slot
     * @param stack
     * @param injected Amount not to move?
     * @param doAdd
     * @return Return the number of items moved.
     */
    protected int addToSlot(IInvSlot slot, ItemStack stack, int injected, boolean doAdd) {
        int available = stack.getCount() - injected;
        int max = Math.min(stack.getMaxStackSize(), inventory.getMaxStackSize());

        ItemStack stackInSlot = slot.getStackInSlot();
        if (stackInSlot.isEmpty()) {
            int wanted = Math.min(available, max);
            if (doAdd) {
                stackInSlot = stack.copyWithCount(wanted);
                slot.setStackInSlot(stackInSlot);
            }
            return wanted;
        }

        if (!StackHelper.instance().canStacksMerge(stack, stackInSlot)) {
            return 0;
        }

        int wanted = max - stackInSlot.getCount();
        if (wanted <= 0) {
            return 0;
        }

        if (wanted > available) {
            wanted = available;
        }

        if (doAdd) {
            stackInSlot.grow(wanted);
            slot.setStackInSlot(stackInSlot);
        }
        return wanted;
    }

    @Override
    public ItemStack remove(IStackFilter filter, @Nullable Direction orientation, boolean doRemove) {
        for (IInvSlot slot : InventoryIterator.getIterable(inventory, orientation)) {
            ItemStack stack = slot.getStackInSlot();
            if (!stack.isEmpty() && slot.canTakeStackFromSlot(stack) && filter.matches(stack)) {
                if (doRemove) {
                    return slot.decreaseStackInSlot();
                } else {
                    return stack.copyWithCount(1);
                }
            }
        }
        return ItemStack.EMPTY;
    }
}
