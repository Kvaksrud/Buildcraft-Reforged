package buildcraftreforged.transport.client;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.transport.TransportRegistry;
import buildcraftreforged.transport.gui.GuiGateInterface;
import buildcraftreforged.transport.render.PipeRendererTESR;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterBlockStateModels;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client registrations of the transport module, the client half of 4.2.2's TransportProxyClient. */
@EventBusSubscriber(modid = BuildcraftReforged.MOD_ID, value = Dist.CLIENT)
public final class TransportClient {

    private TransportClient() {
    }

    @SubscribeEvent
    static void registerBlockStateModels(RegisterBlockStateModels event) {
        event.registerModel(PipeBlockStateModel.TYPE, PipeBlockStateModel.Unbaked.MAP_CODEC);
    }

    /** Gates and wires (#60); the flow renderer (#59) joins this renderer, the pipe type's only one. */
    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(TransportRegistry.pipeTile.get(), context -> new PipeRendererTESR());
    }

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(TransportRegistry.gateMenu.get(), GuiGateInterface::new);
    }
}
