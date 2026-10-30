package buildcraftreforged.transport.client;

import buildcraftreforged.BuildcraftReforged;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterBlockStateModels;

/** Client registrations of the transport module, the client half of 4.2.2's TransportProxyClient. */
@EventBusSubscriber(modid = BuildcraftReforged.MOD_ID, value = Dist.CLIENT)
public final class TransportClient {

    private TransportClient() {
    }

    @SubscribeEvent
    static void registerBlockStateModels(RegisterBlockStateModels event) {
        event.registerModel(PipeBlockStateModel.TYPE, PipeBlockStateModel.Unbaked.MAP_CODEC);
    }
}
