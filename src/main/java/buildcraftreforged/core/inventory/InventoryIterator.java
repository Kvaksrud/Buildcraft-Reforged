/*
 * Ported from buildcraft/core/inventory/InventoryIterator.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: IInventory becomes Container, ISidedInventory becomes WorldlyContainer,
 * ForgeDirection becomes Direction. A null side (4.2.2's UNKNOWN) reaches every slot, as NeoForge's unsided item
 * handler does; 4.2.2 passed UNKNOWN's ordinal 6 to getAccessibleSlotsFromSide.
 */
package buildcraftreforged.core.inventory;

import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * @author CovertJaguar <http://www.railcraft.info>
 */
public class InventoryIterator {

    /**
     * Returns an Iterable object for the specified side of the inventory.
     *
     * @param inv
     * @param side
     * @return Iterable
     */
    public static Iterable<IInvSlot> getIterable(Container inv, @Nullable Direction side) {
        if (inv instanceof WorldlyContainer && side != null) {
            return new InventoryIteratorSided((WorldlyContainer) inv, side);
        }

        return new InventoryIteratorSimple(inv);
    }

    public interface IInvSlot {

        /**
         * Returns the slot number of the underlying Inventory.
         *
         * @return the slot number
         */
        int getIndex();

        boolean canPutStackInSlot(ItemStack stack);

        boolean canTakeStackFromSlot(ItemStack stack);

        ItemStack decreaseStackInSlot();

        ItemStack getStackInSlot();

        void setStackInSlot(ItemStack stack);
    }
}
