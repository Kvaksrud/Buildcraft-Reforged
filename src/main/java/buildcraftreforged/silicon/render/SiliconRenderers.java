/*
 * Buildcraft Reforged, 2026: 4.2.2's SiliconProxyClient.registerRenderers. The laser block model (SiliconRenderBlock)
 * is JSON; the beam, an entity renderer in 4.2.2, is a block entity renderer.
 */
package buildcraftreforged.silicon.render;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.silicon.SiliconRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = BuildcraftReforged.MOD_ID, value = Dist.CLIENT)
public final class SiliconRenderers {

    private SiliconRenderers() {
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(SiliconRegistry.laserTile.get(), context -> new RenderLaserTile());
    }
}
