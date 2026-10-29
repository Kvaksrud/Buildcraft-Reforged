/*
 * Ported from buildcraft/core/utils/CraftingHelper.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: InventoryCrafting becomes CraftingInput and the CraftingManager scan becomes
 * the server RecipeManager lookup. The copied repair branch is gone: vanilla's repair recipe (RepairItemRecipe) is an
 * ordinary recipe in the manager now, so the lookup finds it like any other.
 */
package buildcraftreforged.core.utils;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public class CraftingHelper {

    /** Returns null where no recipe matches, and on the client, which has no recipe manager. */
    public static @Nullable RecipeHolder<CraftingRecipe> findMatchingRecipe(CraftingInput par1InventoryCrafting, Level par2World) {
        if (!(par2World instanceof ServerLevel serverLevel))
            return null;

        return serverLevel.recipeAccess().getRecipeFor(RecipeType.CRAFTING, par1InventoryCrafting, serverLevel).orElse(null);
    }
}
