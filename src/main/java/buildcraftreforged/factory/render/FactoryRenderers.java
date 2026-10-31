/*
 * Buildcraft Reforged, 2026: the factory half of 4.2.2's FactoryProxyClient.initializeTileEntities, for the tank.
 */
package buildcraftreforged.factory.render;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.factory.FactoryRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = BuildcraftReforged.MOD_ID, value = Dist.CLIENT)
public final class FactoryRenderers {

    private FactoryRenderers() {
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(FactoryRegistry.tankTile.get(), context -> new RenderTank());
        event.registerBlockEntityRenderer(FactoryRegistry.refineryTile.get(), context -> new RenderRefinery());
        event.registerBlockEntityRenderer(FactoryRegistry.pumpTile.get(), context -> new RenderPump());
        event.registerBlockEntityRenderer(FactoryRegistry.quarryTile.get(), context -> new RenderQuarry());
    }
}
