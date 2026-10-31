/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: TileEntitySpecialRenderer becomes a BlockEntityRenderer that copies the tank
 * into a render state; the GL display list becomes FluidRenderer.renderFluid submitted as custom geometry in a
 * translucent render type on the block atlas; glColor4f becomes the vertex colour; the GL matrix calls become the
 * pose stack. Client only.
 */
package buildcraftreforged.factory.render;

import buildcraftreforged.core.render.FluidRenderer;
import buildcraftreforged.factory.TileTank;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jspecify.annotations.Nullable;

public class RenderTank implements BlockEntityRenderer<TileTank, RenderTank.State> {

    public static class State extends BlockEntityRenderState {
        public FluidStack liquid = FluidStack.EMPTY;
        public int color = 0xFFFFFF;
        public int capacity;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(TileTank tank, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(tank, state, partialTicks, cameraPosition, breakProgress);
        state.liquid = tank.tank.getFluid().copy();
        state.color = tank.tank.colorRenderCache;
        state.capacity = tank.tank.getCapacity();
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {

        FluidStack liquid = state.liquid;
        int color = state.color;
        if (liquid.isEmpty() || liquid.getAmount() <= 0) {
            return;
        }

        int stage = (int) ((float) liquid.getAmount() / (float) (state.capacity) * (FluidRenderer.DISPLAY_STAGES - 1));

        poseStack.pushPose();

        poseStack.translate(0.125F, 0.5F, 0.125F);
        poseStack.scale(0.75F, 0.999F, 0.75F);
        poseStack.translate(0, -0.5F, 0);

        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(FluidRenderer.getFluidSheet(liquid)),
                (pose, buffer) -> FluidRenderer.renderFluid(liquid, stage, false, pose, buffer, color, state.lightCoords,
                        OverlayTexture.NO_OVERLAY));

        poseStack.popPose();
    }
}
