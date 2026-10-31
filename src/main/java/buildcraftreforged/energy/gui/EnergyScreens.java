/*
 * Buildcraft Reforged, 2026: the client half of 4.2.2's energy GuiHandler (getClientGuiElement) for the engines.
 */
package buildcraftreforged.energy.gui;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.energy.EnergyRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/* Client only. */
@EventBusSubscriber(modid = BuildcraftReforged.MOD_ID, value = Dist.CLIENT)
final class EnergyScreens {
    private EnergyScreens() {
    }

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(EnergyRegistry.engineStoneMenu.get(), GuiStoneEngine::new);
    }
}
