/*
 * Ported from buildcraft/core/inventory/InventoryWrapperSimple.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: IInventory becomes Container, ISidedInventory's methods become
 * WorldlyContainer's, the side ordinal becomes a Direction.
 */
package buildcraftreforged.core.inventory;

import buildcraftreforged.core.utils.Utils;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public class InventoryWrapperSimple extends InventoryWrapper {

    private final int[] slots;

    public InventoryWrapperSimple(Container inventory) {
        super(inventory);
        slots = Utils.createSlotArray(0, inventory.getContainerSize());
    }

    @Override
    public int[] getSlotsForFace(Direction var1) {
        return slots;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slotIndex, ItemStack itemstack, @Nullable Direction side) {
        return canPlaceItem(slotIndex, itemstack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slotIndex, ItemStack itemstack, Direction side) {
        return true;
    }

}
