/*
 * Buildcraft Reforged, 2026: where the Assembly Table finds its recipes. 4.2.2 kept them all in the static
 * AssemblyRecipe.assemblyRecipes list, filled in Java at start-up. The 4.2.2 recipes are JSON now (AssemblyTableRecipe),
 * loaded by the server's recipe manager and sent whole to clients, whose GUI lists them; the static list stays for
 * recipes other mods add in code through the api.
 */
package buildcraftreforged.silicon;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.api.recipes.AssemblyRecipe;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import org.jspecify.annotations.Nullable;

/**
 * The recipes an Assembly Table offers: the {@code buildcraftreforged:assembly} recipes of the level's side, ordered by
 * recipe id, followed by {@link AssemblyRecipe#assemblyRecipes}. Server and client each keep their own objects (in a
 * single-player game both run in one JVM), so a table compares recipes only with those of its own side.
 */
@EventBusSubscriber(modid = BuildcraftReforged.MOD_ID)
public final class AssemblyRecipeManager {

    private static @Nullable RecipeMap serverMap;
    private static List<AssemblyRecipe> serverRecipes = List.of();
    private static @Nullable RecipeMap clientMap;
    private static @Nullable RecipeMap clientRecipesMap;
    private static List<AssemblyRecipe> clientRecipes = List.of();
    /** Bumped whenever either side's recipe objects are replaced, so tables can look theirs up again. */
    private static int generation = 0;

    private AssemblyRecipeManager() {
    }

    /** Every recipe the table on this level's side knows, data-driven first. */
    public static List<AssemblyRecipe> getRecipes(Level level) {
        List<AssemblyRecipe> data;
        if (level instanceof ServerLevel serverLevel) {
            data = serverRecipes(serverLevel.getServer().getRecipeManager().recipeMap());
        } else {
            data = clientRecipes();
        }

        if (AssemblyRecipe.assemblyRecipes.isEmpty())
            return data;

        List<AssemblyRecipe> all = new ArrayList<>(data.size() + AssemblyRecipe.assemblyRecipes.size());
        all.addAll(data);
        all.addAll(AssemblyRecipe.assemblyRecipes);
        return all;
    }

    /**
     * The first recipe whose output is stack, as 4.2.2 found a recipe from the output it saved or received (item id
     * and damage then; item and components now).
     */
    public static @Nullable AssemblyRecipe findRecipe(Level level, ItemStack stack) {
        if (stack.isEmpty())
            return null;
        for (AssemblyRecipe recipe : getRecipes(level)) {
            if (ItemStack.isSameItemSameComponents(recipe.output, stack))
                return recipe;
        }
        return null;
    }

    public static int getGeneration() {
        return generation;
    }

    /** Built on first use after each recipe load, when item components are bound. */
    private static synchronized List<AssemblyRecipe> serverRecipes(RecipeMap map) {
        if (map != serverMap) {
            serverMap = map;
            serverRecipes = convert(map);
            generation++;
        }
        return serverRecipes;
    }

    private static synchronized List<AssemblyRecipe> clientRecipes() {
        if (clientMap != clientRecipesMap) {
            clientRecipesMap = clientMap;
            clientRecipes = clientMap == null ? List.of() : convert(clientMap);
            generation++;
        }
        return clientRecipes;
    }

    /** Client side: the assembly recipes the server sent (RecipesReceivedEvent, see silicon.gui.SiliconScreens). */
    public static synchronized void setClientRecipes(RecipeMap map) {
        clientMap = map;
    }

    /** Client side: leaving a server forgets its recipes. */
    public static synchronized void clearClientRecipes() {
        clientMap = null;
    }

    private static List<AssemblyRecipe> convert(RecipeMap map) {
        Collection<RecipeHolder<AssemblyTableRecipe>> holders = map.byType(SiliconRegistry.assemblyRecipeType.get());
        return holders.stream()
                .sorted(Comparator.comparing(holder -> holder.id().identifier()))
                .flatMap(holder -> holder.value().toAssemblyRecipes().stream())
                .toList();
    }

    /** The GUI lists the recipes on the client, which only gets the recipe types a mod asks for. */
    @SubscribeEvent
    static void sendRecipes(OnDatapackSyncEvent event) {
        event.sendRecipes(SiliconRegistry.assemblyRecipeType.get());
    }
}
