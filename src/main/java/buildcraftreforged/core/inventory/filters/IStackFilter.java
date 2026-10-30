/*
 * Ported from buildcraft/core/inventory/filters/IStackFilter.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: unchanged apart from the package.
 */
package buildcraftreforged.core.inventory.filters;

import net.minecraft.world.item.ItemStack;

/**
 * This interface provides a convenient means of dealing with entire classes of
 * items without having to specify each item individually.
 *
 * @author CovertJaguar <http://www.railcraft.info>
 */
public interface IStackFilter {

    public boolean matches(ItemStack stack);
}
