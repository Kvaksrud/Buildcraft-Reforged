/*
 * Buildcraft Reforged, 2026: the client half of 4.2.2's factory GuiHandler (getClientGuiElement) for the refinery,
 * auto workbench and hopper, and the client's copy of the refinery recipes.
 */
package buildcraftreforged.factory.gui;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.factory.FactoryRegistry;
import buildcraftreforged.factory.RefineryRecipeManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/* Client only. */
@EventBusSubscriber(modid = BuildcraftReforged.MOD_ID, value = Dist.CLIENT)
final class FactoryScreens {
    private FactoryScreens() {
    }

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(FactoryRegistry.refineryMenu.get(), GuiRefinery::new);
        event.register(FactoryRegistry.autoWorkbenchMenu.get(), GuiAutoCrafting::new);
        event.register(FactoryRegistry.hopperMenu.get(), GuiHopper::new);
    }

    @SubscribeEvent
    static void recipesReceived(RecipesReceivedEvent event) {
        if (event.getRecipeTypes().contains(FactoryRegistry.refineryRecipeType.get())) {
            RefineryRecipeManager.setClientRecipes(event.getRecipeMap());
        }
    }

    @SubscribeEvent
    static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        RefineryRecipeManager.clearClientRecipes();
    }
}
