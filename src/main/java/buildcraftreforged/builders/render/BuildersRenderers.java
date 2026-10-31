/*
 * Buildcraft Reforged, 2026: the builders half of 4.2.2's CoreProxyClient.registerRenderers, for the markers and the
 * filler's stripes box.
 */
package buildcraftreforged.builders.render;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.builders.BuildersRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = BuildcraftReforged.MOD_ID, value = Dist.CLIENT)
public final class BuildersRenderers {

    private BuildersRenderers() {
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BuildersRegistry.markerTile.get(), context -> new RenderMarker<>());
        event.registerBlockEntityRenderer(BuildersRegistry.pathMarkerTile.get(), context -> new RenderMarker<>());
        event.registerBlockEntityRenderer(BuildersRegistry.fillerTile.get(), context -> new RenderFiller());
        event.registerBlockEntityRenderer(BuildersRegistry.architectTile.get(), context -> new RenderArchitect());
        event.registerBlockEntityRenderer(BuildersRegistry.builderTile.get(), context -> new RenderBuilder());
    }
}
