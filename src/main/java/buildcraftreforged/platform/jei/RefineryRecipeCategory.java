package buildcraftreforged.platform.jei;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.api.recipes.RefineryRecipes;
import buildcraftreforged.factory.FactoryRegistry;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Refinery recipes (#68's recipe type plus the api set): one or two input fluids, an arrow, the output fluid, and the
 * energy per cycle (and the delay when it is more than one tick).
 */
final class RefineryRecipeCategory extends AbstractRecipeCategory<RefineryRecipes.Recipe> {
    static final IRecipeType<RefineryRecipes.Recipe> TYPE =
            IRecipeType.create(BuildcraftReforged.MOD_ID, "refinery", RefineryRecipes.Recipe.class);

    RefineryRecipeCategory(IGuiHelper guiHelper) {
        super(TYPE, Component.translatable("tile.refineryBlock"),
                guiHelper.createDrawableItemLike(FactoryRegistry.refineryBlockItem.get()), 130, 46);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RefineryRecipes.Recipe recipe, IFocusGroup focuses) {
        addFluid(builder, RecipeIngredientRole.INPUT, recipe.ingredient1, 1);
        if (recipe.ingredient2 != null) {
            addFluid(builder, RecipeIngredientRole.INPUT, recipe.ingredient2, 19);
        }
        addFluid(builder, RecipeIngredientRole.OUTPUT, recipe.result, 73);
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, RefineryRecipes.Recipe recipe, IFocusGroup focuses) {
        builder.addRecipeArrowWidget().setPosition(42, 1);
        builder.addText(Component.translatable("jei.buildcraftreforged.energy_per_cycle", recipe.energy), 130, 9)
                .setPosition(0, 24)
                .setColor(0xFF808080);
        if (recipe.delay > 1) {
            builder.addText(Component.translatable("jei.buildcraftreforged.delay", recipe.delay), 130, 9)
                    .setPosition(0, 35)
                    .setColor(0xFF808080);
        }
    }

    private static void addFluid(IRecipeLayoutBuilder builder, RecipeIngredientRole role, FluidStack stack, int x) {
        builder.addSlot(role, x, 1)
                .setStandardSlotBackground()
                .setFluidRenderer(stack.getAmount(), false, 16, 16)
                .add(stack.getFluid(), stack.getAmount(), stack.getComponentsPatch());
    }
}
