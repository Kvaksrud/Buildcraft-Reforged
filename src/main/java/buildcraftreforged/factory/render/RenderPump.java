/*
 * Buildcraft Reforged, 2026. Not a 4.2.2 class: in 4.2.2 the pump's tube was an EntityBlock entity spawned by
 * TilePump.createTube, with FactoryProxyClient's pump_tube texture, and drawn by RenderEntityBlock. Here TilePump keeps
 * the tube and this renderer draws it through RenderEntityBlock. The pump block itself is the JSON model pump_block.
 */
package buildcraftreforged.factory.render;

import buildcraftreforged.core.EntityBlock;
import buildcraftreforged.core.render.RenderEntityBlock;
import buildcraftreforged.factory.TilePump;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class RenderPump implements BlockEntityRenderer<TilePump, RenderPump.State> {

    public static class State extends BlockEntityRenderState {
        public @Nullable EntityBlock tube;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(TilePump tile, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(tile, state, partialTicks, cameraPosition, breakProgress);
        EntityBlock tube = tile.getTube();
        if (tube == null || tube.isDead || tube.texture == null || tube.jSize <= 0) {
            state.tube = null;
            return;
        }
        // A copy: the tile moves its tube on the client thread while the frame is drawn.
        EntityBlock copy = new EntityBlock(null, tube.posX, tube.posY, tube.posZ, tube.iSize, tube.jSize, tube.kSize);
        copy.texture = tube.texture;
        state.tube = copy;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        EntityBlock tube = state.tube;
        if (tube == null || tube.texture == null)
            return;
        BlockPos pos = state.blockPos;
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(tube.texture),
                (pose, buffer) -> RenderEntityBlock.INSTANCE.doRenderBlock(tube, tube.posX - pos.getX(),
                        tube.posY - pos.getY(), tube.posZ - pos.getZ(), pose, buffer, state.lightCoords));
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return Minecraft.getInstance().options.getEffectiveRenderDistance() * 16;
    }

    /** From the pump down to where the tube is heading. */
    @Override
    public AABB getRenderBoundingBox(TilePump tile) {
        BlockPos pos = tile.getBlockPos();
        double bottom = Math.min(tile.getAimY(), Double.isNaN(tile.getTubeY()) ? pos.getY() : tile.getTubeY());
        return new AABB(pos.getX(), Math.min(bottom, pos.getY()), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
    }
}
