/*
 * Buildcraft Reforged, 2026: client registrations of the energy module, the client half of 4.2.2's EnergyProxyClient
 * and BuildCraftEnergy.textureHook. Oil and fuel render through vanilla's fluid renderer with their 4.2.2 still and
 * flow textures (4.2.2: RenderingOil called RenderBlocks.renderBlockFluids); the drip particle gets its sprites. The
 * engines get RenderEngine on every engine block entity type (4.2.2 bound it to TileEngine.class) and the
 * "buildcraftreforged:engine" item model type, which replaces 4.2.2's blockByEntityRenders entry per engine metadata.
 */
package buildcraftreforged.energy;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.energy.render.EntityDropParticleFX;
import buildcraftreforged.energy.render.RenderEngine;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.client.fluid.FluidTintSource;

@EventBusSubscriber(modid = BuildcraftReforged.MOD_ID, value = Dist.CLIENT)
public final class EnergyProxyClient {

    private EnergyProxyClient() {
    }

    @SubscribeEvent
    static void registerFluidModels(RegisterFluidModelsEvent event) {
        event.register(fluidModel("oil"), EnergyRegistry.fluidOil, EnergyRegistry.fluidOilFlowing);
        event.register(fluidModel("fuel"), EnergyRegistry.fluidFuel, EnergyRegistry.fluidFuelFlowing);
    }

    /** 4.2.2 BlockBuildcraftFluid.registerIcons: "<fluid>_still" and "<fluid>_flow", no overlay, no tint. */
    private static FluidModel.Unbaked fluidModel(String fluidName) {
        return new FluidModel.Unbaked(new Material(texture(fluidName + "_still")), new Material(texture(fluidName + "_flow")), null,
                (FluidTintSource) null);
    }

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "block/" + name);
    }

    @SubscribeEvent
    static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(EnergyRegistry.dropParticle.get(), EntityDropParticleFX.Provider::new);
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        for (DeferredHolder<BlockEntityType<?>, ? extends BlockEntityType<? extends TileEngine>> tile : EnergyRegistry.engineTiles()) {
            event.registerBlockEntityRenderer(tile.get(), context -> new RenderEngine());
        }
    }

    /** The engine items: {"type": "buildcraftreforged:engine", "texture": <the engine's base texture>}. */
    @SubscribeEvent
    static void registerSpecialModelRenderers(RegisterSpecialModelRendererEvent event) {
        event.register(Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "engine"), RenderEngine.ItemRenderer.Unbaked.MAP_CODEC);
    }
}
