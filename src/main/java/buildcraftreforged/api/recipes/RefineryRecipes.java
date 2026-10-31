/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: NeoForge FluidStack, fluids named by registry id, energy in FE.
 */
package buildcraftreforged.api.recipes;

import com.google.common.base.Objects;
import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Refinery recipes. Ported from 4.2.2 {@code buildcraft.api.recipes.RefineryRecipes}.
 *
 * <p>The sorted set stays the lookup the Refinery uses. The factory phase fills it from the JSON recipe type of plan
 * section 6. {@code null} still means "no second ingredient" and "no fluid", as in 4.2.2; an empty stack counts as
 * {@code null}.
 */
public final class RefineryRecipes {

    private static SortedSet<Recipe> recipes = new TreeSet<Recipe>();

    /** @param energy FE per cycle (4.2.2: MJ). */
    public static void addRecipe(FluidStack ingredient, FluidStack result, int energy, int delay) {
        addRecipe(ingredient, null, result, energy, delay);
    }

    /** @param energy FE per cycle (4.2.2: MJ). */
    public static void addRecipe(FluidStack ingredient1, FluidStack ingredient2, FluidStack result, int energy, int delay) {
        Recipe recipe = new Recipe(ingredient1, ingredient2, result, energy, delay);
        recipes.add(recipe);
    }

    /**
     * Builds a recipe without adding it to the code-registered set. Not in 4.2.2: the refinery's JSON recipes (#68)
     * keep their own set per side and fall back to this one.
     *
     * @param energy FE per cycle (4.2.2: MJ).
     */
    public static Recipe createRecipe(FluidStack ingredient1, FluidStack ingredient2, FluidStack result, int energy, int delay) {
        return new Recipe(ingredient1, ingredient2, result, energy, delay);
    }

    public static SortedSet<Recipe> getRecipes() {
        return Collections.unmodifiableSortedSet(recipes);
    }

    public static Recipe findRefineryRecipe(FluidStack liquid1, FluidStack liquid2) {
        for (Recipe recipe : recipes) {
            if (recipe.matches(liquid1, liquid2))
                return recipe;
        }

        return null;
    }

    private RefineryRecipes() {
    }

    /** 1.6.4 {@code FluidStack.containsFluid}: same fluid and components, and at least as much. */
    private static boolean containsFluid(FluidStack stack, FluidStack other) {
        if (other == null || other.isEmpty())
            return false;
        return FluidStack.isSameFluidSameComponents(stack, other) && stack.getAmount() >= other.getAmount();
    }

    /** 1.6.4 {@code Fluid.getName()}: the registry id, so {@code compareTo} sorts by name as before. */
    private static String getName(Fluid fluid) {
        return BuiltInRegistries.FLUID.getKey(fluid).toString();
    }

    public static final class Recipe implements Comparable<Recipe> {

        public final FluidStack ingredient1;
        public final FluidStack ingredient2;
        public final FluidStack result;
        /** FE per cycle (4.2.2: MJ). */
        public final int energy;
        public final int delay;

        private Recipe(FluidStack ingredient1, FluidStack ingredient2, FluidStack result, int energy, int delay) {
            if (ingredient1 == null || ingredient1.isEmpty())
                throw new IllegalArgumentException("First Ingredient cannot be null!");
            this.ingredient1 = ingredient1;
            this.ingredient2 = ingredient2 == null || ingredient2.isEmpty() ? null : ingredient2;
            this.result = result;
            this.energy = energy;
            this.delay = delay;
        }

        public boolean matches(FluidStack liquid1, FluidStack liquid2) {
            if (liquid1 != null && liquid1.isEmpty())
                liquid1 = null;
            if (liquid2 != null && liquid2.isEmpty())
                liquid2 = null;

            // No inputs, return.
            if (liquid1 == null && liquid2 == null)
                return false;

            // Return if two ingredients are required but only one was supplied.
            if ((ingredient1 != null && ingredient2 != null) && (liquid1 == null || liquid2 == null))
                return false;

            if (liquid1 != null && liquid2 != null) {
                if (containsFluid(liquid1, ingredient1) && containsFluid(liquid1, ingredient2))
                    return true;
                if (containsFluid(liquid1, ingredient2) && containsFluid(liquid1, ingredient1))
                    return true;
            }

            if (liquid1 != null)
                return containsFluid(liquid1, ingredient1) || containsFluid(liquid1, ingredient2);

            if (liquid2 != null)
                return containsFluid(liquid2, ingredient1) || containsFluid(liquid2, ingredient2);

            return false;
        }

        // Compares to only the types of source materials.
        // We consider non-null < null in order that one-ingredient recipe is checked after
        // the failure of matching two-ingredient recipes which include that liquid.
        @Override
        public int compareTo(Recipe other) {
            if (other == null)
                return -1;
            else if (ingredient1.getFluid() != other.ingredient1.getFluid())
                return getName(ingredient1.getFluid()).compareTo(getName(other.ingredient1.getFluid()));
            else if (ingredient1.getAmount() != other.ingredient1.getAmount())
                return other.ingredient1.getAmount() - ingredient1.getAmount();
            else if (ingredient2 == null)
                return other.ingredient2 == null ? 0 : 1;
            else if (other.ingredient2 == null)
                return -1;
            else if (ingredient2.getFluid() != other.ingredient2.getFluid())
                return getName(ingredient2.getFluid()).compareTo(getName(other.ingredient2.getFluid()));
            else if (ingredient2.getAmount() != other.ingredient2.getAmount())
                return other.ingredient2.getAmount() - ingredient2.getAmount();

            return 0;
        }

        // equals() should be consistent with compareTo().
        @Override
        public boolean equals(Object obj) {
            return obj instanceof Recipe
                    && Objects.equal(ingredient1, ((Recipe) obj).ingredient1)
                    && Objects.equal(ingredient2, ((Recipe) obj).ingredient2);
        }

        // hashCode() should be overridden because equals() was overridden.
        @Override
        public int hashCode() {
            return Objects.hashCode(ingredient1, ingredient2);
        }
    }
}
