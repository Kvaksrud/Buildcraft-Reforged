/*
 * Ported from buildcraft/core/inventory/InventoryIteratorSimple.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: IInventory becomes Container.
 */
package buildcraftreforged.core.inventory;

import buildcraftreforged.core.inventory.InventoryIterator.IInvSlot;
import java.util.Iterator;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * @author CovertJaguar <http://www.railcraft.info>
 */
class InventoryIteratorSimple implements Iterable<IInvSlot> {

    private final Container inv;

    InventoryIteratorSimple(Container inv) {
        this.inv = inv;
    }

    @Override
    public Iterator<IInvSlot> iterator() {
        return new Iterator<IInvSlot>() {
            int slot = 0;

            @Override
            public boolean hasNext() {
                return slot < inv.getContainerSize();
            }

            @Override
            public IInvSlot next() {
                return new InvSlot(slot++);
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException("Remove not supported.");
            }
        };
    }

    private class InvSlot implements IInvSlot {

        private int slot;

        public InvSlot(int slot) {
            this.slot = slot;
        }

        @Override
        public ItemStack getStackInSlot() {
            return inv.getItem(slot);
        }

        @Override
        public void setStackInSlot(ItemStack stack) {
            inv.setItem(slot, stack);
        }

        @Override
        public boolean canPutStackInSlot(ItemStack stack) {
            return inv.canPlaceItem(slot, stack);
        }

        @Override
        public boolean canTakeStackFromSlot(ItemStack stack) {
            return true;
        }

        @Override
        public ItemStack decreaseStackInSlot() {
            return inv.removeItem(slot, 1);
        }

        @Override
        public int getIndex() {
            return slot;
        }
    }
}
