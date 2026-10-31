/*
 * Buildcraft Reforged, 2026. Not a 4.2.2 class: in 4.2.2 the laser's beam was an EntityEnergyLaser drawn by
 * core/render/RenderEnergyLaser. Here TileLaser keeps the beam as render data and this renderer hands it to
 * RenderEnergyLaser. The laser block itself (4.2.2 SiliconRenderBlock) is the JSON model block/laser_block.
 */
package buildcraftreforged.silicon.render;

import buildcraftreforged.core.EntityEnergyLaser;
import buildcraftreforged.core.render.RenderEnergyLaser;
import buildcraftreforged.silicon.TileLaser;
import com.mojang.blaze3d.vertex.PoseStack;
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

public class RenderLaserTile implements BlockEntityRenderer<TileLaser, RenderLaserTile.State> {

    /** TileLaser.findTable searches 5 blocks each way; the beam ends inside the target block. */
    private static final int LASER_REACH = 6;

    public static class State extends BlockEntityRenderState {
        public @Nullable EntityEnergyLaser laser;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(TileLaser tile, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(tile, state, partialTicks, cameraPosition, breakProgress);
        EntityEnergyLaser laser = tile.getLaser();
        state.laser = laser != null && !laser.isDead && laser.isVisible() ? laser : null;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        EntityEnergyLaser laser = state.laser;
        if (laser == null)
            return;

        BlockPos pos = state.blockPos;
        double x = laser.getHead().x - pos.getX();
        double y = laser.getHead().y - pos.getY();
        double z = laser.getHead().z - pos.getZ();
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(laser.getTexture()),
                (pose, buffer) -> RenderEnergyLaser.INSTANCE.doRender(laser, x, y, z, pose, buffer));
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(TileLaser tile) {
        return new AABB(tile.getBlockPos()).inflate(LASER_REACH);
    }
}
