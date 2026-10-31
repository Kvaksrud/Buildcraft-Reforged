package buildcraftreforged.platform.jei;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.api.recipes.RefineryRecipes;
import buildcraftreforged.factory.FactoryRegistry;
import buildcraftreforged.factory.RefineryRecipeManager;
import buildcraftreforged.silicon.AssemblyRecipeManager;
import buildcraftreforged.silicon.SiliconRegistry;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * JEI plugin, replacing 4.2.2's external NEI plugins (PLAN.md section 6). JEI finds it through {@link JeiPlugin};
 * nothing else refers to this class, so it is never loaded when JEI is absent.
 *
 * <p>Categories: the Assembly Table (chipsets, gates, pipe wire, modules, and later plugs and facades) and the
 * Refinery. Both read the client's copy of the recipes, which the server sends on data pack sync; JEI starts from
 * the same RecipesReceivedEvent at LOWEST priority, after silicon.gui.SiliconScreens and factory.gui.FactoryScreens
 * have kept that copy. The gate AND/OR swaps are vanilla crafting recipes and show in JEI's crafting category.
 */
@JeiPlugin
public class BuildCraftJeiPlugin implements IModPlugin {
    private static final Identifier UID = Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "jei");

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new AssemblyRecipeCategory(guiHelper), new RefineryRecipeCategory(guiHelper));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(AssemblyRecipeCategory.TYPE, AssemblyRecipeManager.getRecipes(Minecraft.getInstance().level));

        List<RefineryRecipes.Recipe> refinery = new ArrayList<>(RefineryRecipeManager.getDataRecipes(null));
        refinery.addAll(RefineryRecipes.getRecipes());
        registration.addRecipes(RefineryRecipeCategory.TYPE, refinery);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(AssemblyRecipeCategory.TYPE, SiliconRegistry.assemblyTableBlockItem.get());
        registration.addCraftingStation(RefineryRecipeCategory.TYPE, FactoryRegistry.refineryBlockItem.get());
    }
}
