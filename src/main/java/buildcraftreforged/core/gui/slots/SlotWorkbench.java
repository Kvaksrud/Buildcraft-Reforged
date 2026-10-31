/*
 * Ported from buildcraft/core/gui/slots/SlotWorkbench.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: IInventory is the vanilla Container; isItemValid is mayPlace; hasContainerItem
 * is a crafting remainder on the item.
 */
package buildcraftreforged.core.gui.slots;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

public class SlotWorkbench extends SlotBase {

    public SlotWorkbench(Container iinventory, int slotIndex, int posX, int posY) {
        super(iinventory, slotIndex, posX, posY);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return !stack.isEmpty() && stack.isStackable() && stack.getItem().getCraftingRemainder(stack) == null;
    }

    @Override
    public boolean canShift() {
        return false;
    }
}
