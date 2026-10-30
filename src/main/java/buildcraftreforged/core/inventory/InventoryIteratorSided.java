/*
 * Ported from buildcraft/core/inventory/InventoryIteratorSided.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: ISidedInventory becomes WorldlyContainer, the side ordinal becomes a
 * Direction.
 */
package buildcraftreforged.core.inventory;

import buildcraftreforged.core.inventory.InventoryIterator.IInvSlot;
import java.util.Iterator;
import net.minecraft.core.Direction;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;

/**
 * @author CovertJaguar <http://www.railcraft.info>
 */
class InventoryIteratorSided implements Iterable<IInvSlot> {

    private final WorldlyContainer inv;
    private final Direction side;

    InventoryIteratorSided(WorldlyContainer inv, Direction side) {
        this.inv = inv;
        this.side = side;
    }

    @Override
    public Iterator<IInvSlot> iterator() {
        return new Iterator<IInvSlot>() {
            int[] slots = inv.getSlotsForFace(side);
            int index = 0;

            @Override
            public boolean hasNext() {
                return index < slots.length;
            }

            @Override
            public IInvSlot next() {
                return new InvSlot(slots[index++]);
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
            return inv.canPlaceItemThroughFace(slot, stack, side);
        }

        @Override
        public boolean canTakeStackFromSlot(ItemStack stack) {
            return inv.canTakeItemThroughFace(slot, stack, side);
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
