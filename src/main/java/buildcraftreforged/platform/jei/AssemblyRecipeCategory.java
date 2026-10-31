package buildcraftreforged.platform.jei;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.api.recipes.AssemblyRecipe;
import buildcraftreforged.silicon.SiliconRegistry;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Assembly Table recipes (#72's recipe type plus the api list), shown as the table's 12 slots in three rows of four,
 * an arrow, the output and the energy in FE. Gates, pipe wire, chipsets and modules are made here, so their recipes
 * show in this category.
 */
final class AssemblyRecipeCategory extends AbstractRecipeCategory<AssemblyRecipe> {
    static final IRecipeType<AssemblyRecipe> TYPE =
            IRecipeType.create(BuildcraftReforged.MOD_ID, "assembly", AssemblyRecipe.class);

    private static final int COLUMNS = 4;

    AssemblyRecipeCategory(IGuiHelper guiHelper) {
        super(TYPE, Component.translatable("tile.assemblyTableBlock"),
                guiHelper.createDrawableItemLike(SiliconRegistry.assemblyTableBlockItem.get()), 130, 64);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, AssemblyRecipe recipe, IFocusGroup focuses) {
        List<List<ItemStack>> inputs = inputs(recipe);
        for (int i = 0; i < inputs.size(); i++) {
            builder.addInputSlot(1 + (i % COLUMNS) * 18, 1 + (i / COLUMNS) * 18)
                    .setStandardSlotBackground()
                    .addItemStacks(inputs.get(i));
        }
        builder.addOutputSlot(109, 19).setOutputSlotBackground().add(recipe.output.copy());
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, AssemblyRecipe recipe, IFocusGroup focuses) {
        builder.addRecipeArrowWidget().setPosition(78, 19);
        builder.addText(Component.translatable("jei.buildcraftreforged.energy", (long) recipe.energy), 130, 9)
                .setPosition(0, 55)
                .setColor(0xFF808080);
    }

    /** One list of stacks per input: an item stack, or every item in a tag with the tag's count. */
    @SuppressWarnings("unchecked")
    static List<List<ItemStack>> inputs(AssemblyRecipe recipe) {
        List<List<ItemStack>> inputs = new ArrayList<>();
        for (int i = 0; i < recipe.input.length; i++) {
            Object input = recipe.input[i];
            if (input instanceof ItemStack stack) {
                inputs.add(List.of(stack.copy()));
            } else if (input instanceof TagKey<?> tag) {
                int count = (Integer) recipe.input[++i];
                List<ItemStack> stacks = new ArrayList<>();
                for (Holder<Item> item : BuiltInRegistries.ITEM.getTagOrEmpty((TagKey<Item>) tag)) {
                    stacks.add(new ItemStack(item, count));
                }
                inputs.add(stacks);
            }
        }
        return inputs;
    }
}
