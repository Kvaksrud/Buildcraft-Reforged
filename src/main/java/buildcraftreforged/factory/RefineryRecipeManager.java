/*
 * Buildcraft Reforged, 2026: where the refinery finds its recipes. 4.2.2 kept them all in the static sorted set of
 * RefineryRecipes, filled in Java at start-up. The 4.2.2 recipe is JSON now (RefineryRecipe), loaded by the server's
 * recipe manager and sent to clients, whose GUI shows the result of the chosen filters; the static set stays for
 * recipes other mods add in code through the api.
 */
package buildcraftreforged.factory;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.api.recipes.RefineryRecipes;
import buildcraftreforged.api.recipes.RefineryRecipes.Recipe;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.SortedSet;
import java.util.TreeSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jspecify.annotations.Nullable;

/**
 * The recipes a refinery runs: the {@code buildcraftreforged:refinery} recipes of the level's side, in 4.2.2's
 * {@link Recipe#compareTo} order (two-ingredient recipes before the one-ingredient recipe on the same fluid), then
 * {@link RefineryRecipes#getRecipes()}. As in 4.2.2's {@code TreeSet}, a recipe with the same ingredients as an
 * earlier one is dropped; JSON recipes are taken in recipe id order. Server and client each keep their own set (in a
 * single-player game both run in one JVM).
 */
@EventBusSubscriber(modid = BuildcraftReforged.MOD_ID)
public final class RefineryRecipeManager {

    private static @Nullable RecipeMap serverMap;
    private static SortedSet<Recipe> serverRecipes = Collections.emptySortedSet();
    private static SortedSet<Recipe> clientRecipes = Collections.emptySortedSet();

    private RefineryRecipeManager() {
    }

    /** The JSON recipes of this level's side; a null level counts as the client (the GUI). */
    public static SortedSet<Recipe> getDataRecipes(@Nullable Level level) {
        if (level instanceof ServerLevel serverLevel) {
            return serverRecipes(serverLevel.getServer().getRecipeManager().recipeMap());
        }
        return clientRecipes;
    }

    /** 4.2.2 {@code RefineryRecipes.findRefineryRecipe}, JSON recipes first. */
    public static @Nullable Recipe findRefineryRecipe(@Nullable Level level, @Nullable FluidStack liquid1,
            @Nullable FluidStack liquid2) {
        for (Recipe recipe : getDataRecipes(level)) {
            if (recipe.matches(liquid1, liquid2))
                return recipe;
        }
        return RefineryRecipes.findRefineryRecipe(liquid1, liquid2);
    }

    private static synchronized SortedSet<Recipe> serverRecipes(RecipeMap map) {
        if (map != serverMap) {
            serverMap = map;
            serverRecipes = sorted(map.byType(FactoryRegistry.refineryRecipeType.get()));
        }
        return serverRecipes;
    }

    /** Client side: the refinery recipes the server sent (RecipesReceivedEvent, see factory.gui.FactoryScreens). */
    public static void setClientRecipes(RecipeMap map) {
        clientRecipes = sorted(map.byType(FactoryRegistry.refineryRecipeType.get()));
    }

    /** Client side: leaving a server forgets its recipes. */
    public static void clearClientRecipes() {
        clientRecipes = Collections.emptySortedSet();
    }

    private static SortedSet<Recipe> sorted(Collection<RecipeHolder<RefineryRecipe>> holders) {
        SortedSet<Recipe> recipes = new TreeSet<>();
        holders.stream()
                .sorted(Comparator.comparing(holder -> holder.id().identifier()))
                .forEach(holder -> recipes.add(holder.value().getRecipe()));
        return Collections.unmodifiableSortedSet(recipes);
    }

    /** The GUI looks recipes up on the client, which only gets the recipe types a mod asks for. */
    @SubscribeEvent
    static void sendRecipes(OnDatapackSyncEvent event) {
        event.sendRecipes(FactoryRegistry.refineryRecipeType.get());
    }
}
