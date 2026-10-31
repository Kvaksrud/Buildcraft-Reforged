/*
 * Buildcraft Reforged, 2026: new file, no 4.2.2 original.
 */
package buildcraftreforged.core.status;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.core.CoreRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Pairs the status menu with its screen. */
@EventBusSubscriber(modid = BuildcraftReforged.MOD_ID, value = Dist.CLIENT)
final class StatusPanelClient {
    private StatusPanelClient() {
    }

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(CoreRegistry.statusMenu.get(), GuiStatus::new);
    }
}
