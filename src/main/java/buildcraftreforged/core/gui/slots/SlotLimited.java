/*
 * Ported from buildcraft/core/gui/slots/SlotLimited.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: IInventory is the vanilla Container; getSlotStackLimit is getMaxStackSize.
 */
package buildcraftreforged.core.gui.slots;

import net.minecraft.world.Container;

/**
 *
 * @author CovertJaguar <http://www.railcraft.info/>
 */
public class SlotLimited extends SlotBase {

    private final int limit;

    public SlotLimited(Container iinventory, int slotIndex, int posX, int posY, int limit) {
        super(iinventory, slotIndex, posX, posY);
        this.limit = limit;
    }

    @Override
    public int getMaxStackSize() {
        return limit;
    }
}
