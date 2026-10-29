/*
 * Modified by Buildcraft Reforged, 2026: ported from buildcraft/api/recipes/AssemblyRecipe.java (no file header at
 * 4.2.2); Ore Dictionary names replaced by item tags, energy in FE.
 */

package buildcraftreforged.api.recipes;

import java.util.LinkedList;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * An Assembly Table recipe. Ported from 4.2.2 {@code buildcraft.api.recipes.AssemblyRecipe}.
 *
 * <p>{@link #assemblyRecipes} stays the list the Assembly Table searches. The silicon phase fills it from the JSON
 * recipe type of plan section 6 instead of Java registration.
 */
public class AssemblyRecipe {

    public static LinkedList<AssemblyRecipe> assemblyRecipes = new LinkedList<AssemblyRecipe>();
    public final Object[] input;
    public final ItemStack output;
    /** Energy cost in FE (4.2.2: MJ; 1 MJ = 10 FE, plan section 7). */
    public final float energy;

    public AssemblyRecipe(ItemStack[] input, int energy, ItemStack output) {
        this.input = input;
        this.output = output;
        this.energy = energy;
    }

    /**
     * This version of AssemblyRecipe supports item tags (4.2.2: the Ore Dictionary).
     *
     * @param input Object... containing either an ItemStack, or a paired item tag and integer (ex: {@code c:dyes/blue},
     * 1). 4.2.2 took an Ore Dictionary name in place of the tag.
     * @param energy FE cost to produce
     * @param output resulting ItemStack
     */
    public AssemblyRecipe(int energy, ItemStack output, Object... input) {
        this.output = output;
        this.energy = energy;
        this.input = input;
    }

    @SuppressWarnings("unchecked")
    public boolean canBeDone(ItemStack... items) {
        for (int i = 0; i < input.length; i++) {
            if (input[i] == null)
                continue;

            if (input[i] instanceof ItemStack) {
                ItemStack requirement = (ItemStack) input[i];
                int found = 0; // Amount of ingredient found in inventory
                int expected = requirement.getCount();
                for (ItemStack item : items) {
                    if (item == null || item.isEmpty())
                        continue;

                    if (ItemStack.isSameItem(item, requirement))
                        found += item.getCount(); // Adds quantity of stack to amount found

                }

                // Return false if the amount of ingredient found
                // is not enough
                if (found < expected)
                    return false;
            } else if (input[i] instanceof TagKey) {
                TagKey<Item> oreTag = (TagKey<Item>) input[i];
                int found = 0; // Amount of ingredient found in inventory
                int expected = (Integer) input[i++ + 1];

                for (ItemStack item : items) {
                    if (item == null || item.isEmpty())
                        continue;
                    if (item.is(oreTag)) {
                        found += item.getCount();
                    }
                }

                // Return false if the amount of ingredient found
                // is not enough
                if (found < expected)
                    return false;
            }
        }

        return true;
    }
}
