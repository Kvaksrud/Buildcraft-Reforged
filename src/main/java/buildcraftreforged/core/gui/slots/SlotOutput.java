/*
 * Ported from buildcraft/core/gui/slots/SlotOutput.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: IInventory is the vanilla Container; isItemValid is mayPlace.
 */
package buildcraftreforged.core.gui.slots;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

public class SlotOutput extends SlotBase {

    public SlotOutput(Container iinventory, int slotIndex, int posX, int posY) {
        super(iinventory, slotIndex, posX, posY);
    }

    @Override
    public boolean mayPlace(ItemStack itemstack) {
        return false;
    }
}
