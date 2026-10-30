/*
 * Ported from buildcraft/core/inventory/filters/CraftingFilter.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: a null filter stack becomes ItemStack.EMPTY; the Ore Dictionary becomes item tags (StackHelper).
 */
package buildcraftreforged.core.inventory.filters;

import buildcraftreforged.core.inventory.StackHelper;
import net.minecraft.world.item.ItemStack;

/**
 * Returns true if the stack matches any one one of the filter stacks.
 * Checks the OreDictionary and wildcards.
 *
 * @author CovertJaguar <http://www.railcraft.info>
 */
public class CraftingFilter implements IStackFilter {

    private final ItemStack[] stacks;

    public CraftingFilter(ItemStack... stacks) {
        this.stacks = stacks;
    }

    @Override
    public boolean matches(ItemStack stack) {
        if (stacks.length == 0 || !hasFilter()) {
            return true;
        }
        for (ItemStack s : stacks) {
            if (StackHelper.instance().isCraftingEquivalent(s, stack, true)) {
                return true;
            }
        }
        return false;
    }

    public ItemStack[] getStacks() {
        return stacks;
    }

    public boolean hasFilter() {
        for (ItemStack filter : stacks) {
            if (filter != null && !filter.isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
