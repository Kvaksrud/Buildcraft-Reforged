package buildcraftreforged.gametest.gui;

import buildcraftreforged.BuildcraftReforged;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Pairs the sample menu with its screen, which replaces 4.2.2's {@code GuiHandler.getClientGuiElement}. */
@EventBusSubscriber(modid = BuildcraftReforged.MOD_ID, value = Dist.CLIENT)
final class SampleGuiClient {
    private SampleGuiClient() {
    }

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(SampleGui.menuType, GuiSampleGui::new);
    }
}
