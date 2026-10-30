/*
 * Ported from buildcraft/core/inventory/filters/InvertedStackFilter.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: a null stack becomes ItemStack.EMPTY.
 */
package buildcraftreforged.core.inventory.filters;

import net.minecraft.world.item.ItemStack;

/**
 *
 * @author CovertJaguar <http://www.railcraft.info>
 */
public class InvertedStackFilter implements IStackFilter {

    private final IStackFilter filter;

    public InvertedStackFilter(IStackFilter filter) {
        this.filter = filter;
    }

    @Override
    public boolean matches(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return !filter.matches(stack);
    }
}
