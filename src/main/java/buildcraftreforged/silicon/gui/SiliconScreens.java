/*
 * Buildcraft Reforged, 2026: the client half of 4.2.2's silicon GuiHandler (getClientGuiElement), and the client's
 * copy of the assembly recipes, which 4.2.2 clients built themselves at start-up.
 */
package buildcraftreforged.silicon.gui;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.silicon.AssemblyRecipeManager;
import buildcraftreforged.silicon.SiliconRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/* Client only. */
@EventBusSubscriber(modid = BuildcraftReforged.MOD_ID, value = Dist.CLIENT)
final class SiliconScreens {
    private SiliconScreens() {
    }

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(SiliconRegistry.assemblyTableMenu.get(), GuiAssemblyTable::new);
    }

    @SubscribeEvent
    static void recipesReceived(RecipesReceivedEvent event) {
        if (event.getRecipeTypes().contains(SiliconRegistry.assemblyRecipeType.get())) {
            AssemblyRecipeManager.setClientRecipes(event.getRecipeMap());
        }
    }

    @SubscribeEvent
    static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        AssemblyRecipeManager.clearClientRecipes();
    }
}
