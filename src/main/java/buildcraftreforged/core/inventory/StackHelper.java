/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: null stacks become ItemStack.EMPTY; item id, damage and NBT become item and
 * data components (damage is a component, and no item has subtypes any more, so matching ignores damage as 4.2.2 did
 * for items without subtypes); the Ore Dictionary becomes item tags in the c namespace.
 */
package buildcraftreforged.core.inventory;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public class StackHelper {

    /** The Ore Dictionary's WILDCARD_VALUE. */
    public static final int WILDCARD_VALUE = Short.MAX_VALUE;

    private static StackHelper instance;

    public static StackHelper instance() {
        if (instance == null) {
            instance = new StackHelper();
        }
        return instance;
    }

    public static void setInstance(StackHelper inst) {
        instance = inst;
    }

    protected StackHelper() {
    }

    /* STACK MERGING */
    /**
     * Checks if two ItemStacks are identical enough to be merged
     *
     * @param stack1 - The first stack
     * @param stack2 - The second stack
     * @return true if stacks can be merged, false otherwise
     */
    public boolean canStacksMerge(ItemStack stack1, ItemStack stack2) {
        if (stack1.isEmpty() || stack2.isEmpty())
            return false;
        if (!ItemStack.isSameItemSameComponents(stack1, stack2))
            return false;
        return true;

    }

    /**
     * Merges mergeSource into mergeTarget
     *
     * @param mergeSource - The stack to merge into mergeTarget, this stack is
     * not modified
     * @param mergeTarget - The target merge, this stack is modified if doMerge
     * is set
     * @param doMerge - To actually do the merge
     * @return The number of items that was successfully merged.
     */
    public int mergeStacks(ItemStack mergeSource, ItemStack mergeTarget, boolean doMerge) {
        if (!canStacksMerge(mergeSource, mergeTarget))
            return 0;
        int mergeCount = Math.min(mergeTarget.getMaxStackSize() - mergeTarget.getCount(), mergeSource.getCount());
        if (mergeCount < 1)
            return 0;
        if (doMerge) {
            mergeTarget.grow(mergeCount);
        }
        return mergeCount;
    }

    /* ITEM COMPARISONS */
    /**
     * Determines whether the given ItemStack should be considered equivalent
     * for crafting purposes.
     *
     * 4.2.2 looked up base's Ore Dictionary name. Its counterpart now is a tag of base in the c namespace with a
     * slash in its path (c:ingots/iron, not the broad c:ingots).
     *
     * @param base The stack to compare to.
     * @param comparison The stack to compare.
     * @param oreDictionary true to take the item tags into account.
     * @return true if comparison should be considered a crafting equivalent for
     * base.
     */
    public boolean isCraftingEquivalent(ItemStack base, ItemStack comparison, boolean oreDictionary) {
        if (isMatchingItem(base, comparison, true, false))
            return true;

        if (oreDictionary && !base.isEmpty() && !comparison.isEmpty()) {
            return base.typeHolder().tags().anyMatch(tag -> isOreTag(tag) && comparison.is(tag));
        }

        return false;
    }

    /** 4.2.2 took an Ore Dictionary id, -1 for none; the tag is null for none. */
    public boolean isCraftingEquivalent(@Nullable TagKey<Item> oreID, ItemStack comparison) {
        if (oreID != null) {
            return comparison.is(oreID);
        }

        return false;
    }

    /** The counterpart of an Ore Dictionary name: a c tag naming one material, such as c:ingots/iron. */
    public static boolean isOreTag(TagKey<Item> tag) {
        return tag.location().getNamespace().equals("c") && tag.location().getPath().contains("/");
    }

    /**
     * Compares item id, damage and NBT. Accepts wildcard damage. Ignores damage
     * entirely if the item doesn't have subtypes.
     *
     * @param base The stack to compare to.
     * @param comparison The stack to compare.
     * @return true if id, damage and NBT match.
     */
    public boolean isMatchingItem(ItemStack base, ItemStack comparison) {
        return isMatchingItem(base, comparison, true, true);
    }

    /**
     * Compares item id, and optionally damage and NBT. Accepts wildcard damage.
     * Ignores damage entirely if the item doesn't have subtypes.
     *
     * No item has subtypes now (1.6.4 subtypes are separate items), so matchDamage has nothing left to compare. NBT is
     * the data components a stack changes from its item's defaults, minus damage.
     *
     * @param a ItemStack
     * @param b ItemStack
     * @param matchDamage
     * @param matchNBT
     * @return true if matches
     */
    public boolean isMatchingItem(final ItemStack a, final ItemStack b, final boolean matchDamage, final boolean matchNBT) {
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        if (a.getItem() != b.getItem()) {
            return false;
        }
        if (matchNBT) {
            DataComponentPatch tagA = withoutDamage(a);
            if (!tagA.isEmpty() && !tagA.equals(withoutDamage(b))) {
                return false;
            }
        }
        return true;
    }

    private static DataComponentPatch withoutDamage(ItemStack stack) {
        return stack.getComponentsPatch().forget(type -> type == DataComponents.DAMAGE);
    }

    public boolean isWildcard(ItemStack stack) {
        return isWildcard(stack.getDamageValue());
    }

    public boolean isWildcard(int damage) {
        return damage == -1 || damage == WILDCARD_VALUE;
    }
}
