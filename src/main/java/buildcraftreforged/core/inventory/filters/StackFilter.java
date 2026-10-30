/*
 * Ported from buildcraft/core/inventory/filters/StackFilter.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: TileEntityFurnace.getItemBurnTime > 0 becomes the
 * COOKING_FUEL data component, the furnace's own fuel test.
 */
package buildcraftreforged.core.inventory.filters;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

/**
 * This interface is used with several of the functions in IItemTransfer to
 * provide a convenient means of dealing with entire classes of items without
 * having to specify each item individually.
 *
 * @author CovertJaguar <http://www.railcraft.info>
 */
public enum StackFilter implements IStackFilter {

    ALL {
        @Override
        public boolean matches(ItemStack stack) {
            return true;
        }
    },
    FUEL {
        @Override
        public boolean matches(ItemStack stack) {
            return stack.has(DataComponents.COOKING_FUEL);
        }
    };

    @Override
    public abstract boolean matches(ItemStack stack);
}
