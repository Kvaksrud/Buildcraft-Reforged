package buildcraftreforged.transport.client;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.transport.TilePipe;
import buildcraftreforged.transport.TransportRegistry;
import buildcraftreforged.transport.flow.FlowRecordsPayload;
import buildcraftreforged.transport.gui.GuiGateInterface;
import buildcraftreforged.transport.render.PipeRendererTESR;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterBlockStateModels;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client registrations of the transport module, the client half of 4.2.2's TransportProxyClient. */
@EventBusSubscriber(modid = BuildcraftReforged.MOD_ID, value = Dist.CLIENT)
public final class TransportClient {

    private TransportClient() {
    }

    @SubscribeEvent
    static void registerBlockStateModels(RegisterBlockStateModels event) {
        event.registerModel(PipeBlockStateModel.TYPE, PipeBlockStateModel.Unbaked.MAP_CODEC);
    }

    /** Gates and wires (#60) and the flow records (#59): the pipe type's only renderer. */
    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(TransportRegistry.pipeTile.get(), PipeRendererTESR::new);
    }

    /** Flow records from the server (docs/TRANSPORT.md 9.3): each pipe of the payload takes its record. */
    @SubscribeEvent
    static void registerPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(FlowRecordsPayload.TYPE, TransportClient::onFlowRecords);
    }

    private static void onFlowRecords(FlowRecordsPayload payload, IPayloadContext context) {
        Level level = context.player().level();
        for (int i = 0; i < payload.records().size(); i++) {
            BlockPos pos = payload.pos(i);
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof TilePipe pipe
                    && payload.records().get(i).kind() == pipe.kind)
                pipe.getFlowTracker().receive(payload.records().get(i), level.getGameTime());
        }
    }

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(TransportRegistry.gateMenu.get(), GuiGateInterface::new);
        event.register(TransportRegistry.filteredExtractorMenu.get(), GuiFilteredExtractor::new);
        event.register(TransportRegistry.sorterMenu.get(), GuiSorter::new);
        event.register(TransportRegistry.filteredColourTagMenu.get(), GuiFilteredColourTag::new);
    }
}
