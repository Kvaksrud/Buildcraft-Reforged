/*
 * Buildcraft Reforged, 2026: the client half of 4.2.2's builders GuiHandler (getClientGuiElement).
 */
package buildcraftreforged.builders.gui;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.builders.BuildersRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/* Client only. */
@EventBusSubscriber(modid = BuildcraftReforged.MOD_ID, value = Dist.CLIENT)
final class BuildersScreens {
    private BuildersScreens() {
    }

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(BuildersRegistry.fillerMenu.get(), GuiFiller::new);
        event.register(BuildersRegistry.architectMenu.get(), GuiTemplate::new);
        event.register(BuildersRegistry.builderMenu.get(), GuiBuilder::new);
    }
}
