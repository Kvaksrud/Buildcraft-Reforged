/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: TileEntitySpecialRenderer becomes a BlockEntityRenderer with a render state;
 * ModelRenderer boxes become baked ModelParts with the same texture offsets and boxes; GL matrix calls become the
 * PoseStack and GL state becomes render types (entityCutout for the model, entityTranslucent over the block atlas for
 * the fluids); the metadata angle reads FACING; display lists become FluidRenderer.renderFluid. The inventory render
 * (IInventoryRenderer) is the item model refinery_block_inventory.
 */
package buildcraftreforged.factory.render;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.core.fluids.Tank;
import buildcraftreforged.core.render.FluidRenderer;
import buildcraftreforged.factory.BlockRefinery;
import buildcraftreforged.factory.TileRefinery;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jspecify.annotations.Nullable;

/* Client only. */
public class RenderRefinery implements BlockEntityRenderer<TileRefinery, RenderRefinery.State> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "textures/block/refinery.png");
    private static final float pixel = (float) (1.0 / 16.0);
    private final ModelPart tank;
    private final ModelPart magnet[] = new ModelPart[4];

    /** What render() read from the tile; 4.2.2 read the tile while drawing. */
    public static class State extends BlockEntityRenderState {
        public FluidStack liquid1 = FluidStack.EMPTY, liquid2 = FluidStack.EMPTY, liquidResult = FluidStack.EMPTY;
        public int color1 = 0xFFFFFF, color2 = 0xFFFFFF, colorResult = 0xFFFFFF;
        public int stage1, stage2, stageResult;
        public float anim = 0;
        public int angle = 0;
        public int magnet = 0;
    }

    public RenderRefinery() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // constructor:
        root.addOrReplaceChild("tank", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, -8F, -4F, 8, 16, 8),
                PartPose.offset(8, 8, 8));

        // constructor:

        for (int i = 0; i < 4; ++i) {
            root.addOrReplaceChild("magnet" + i, CubeListBuilder.create().texOffs(32, i * 8).addBox(0, -8F, -8F, 8, 4, 4),
                    PartPose.offset(8, 8, 8));
        }

        ModelPart model = LayerDefinition.create(mesh, 64, 32).bakeRoot();
        tank = model.getChild("tank");
        for (int i = 0; i < 4; ++i) {
            magnet[i] = model.getChild("magnet" + i);
        }
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(TileRefinery tile, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(tile, state, partialTicks, cameraPosition, breakProgress);

        state.liquid1 = tile.tank1.getFluid().copy();
        state.color1 = tile.tank1.colorRenderCache;
        state.stage1 = getDisplayListIndex(tile.tank1);

        state.liquid2 = tile.tank2.getFluid().copy();
        state.color2 = tile.tank2.colorRenderCache;
        state.stage2 = getDisplayListIndex(tile.tank2);

        state.liquidResult = tile.result.getFluid().copy();
        state.colorResult = tile.result.colorRenderCache;
        state.stageResult = getDisplayListIndex(tile.result);

        state.anim = tile.getAnimationStage();

        state.angle = 0;
        Direction facing = tile.getBlockState().hasProperty(BlockRefinery.FACING)
                ? tile.getBlockState().getValue(BlockRefinery.FACING) : Direction.EAST;
        switch (facing) {
            case NORTH:
                state.angle = 90;
                break;
            case SOUTH:
                state.angle = 270;
                break;
            case WEST:
                state.angle = 180;
                break;
            case EAST:
            default:
                state.angle = 0;
                break;
        }

        if (tile.animationSpeed <= 1) {
            state.magnet = 0;
        } else if (tile.animationSpeed <= 2.5) {
            state.magnet = 1;
        } else if (tile.animationSpeed <= 4.5) {
            state.magnet = 2;
        } else {
            state.magnet = 3;
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        float anim = state.anim;
        ModelPart theMagnet = magnet[state.magnet];
        RenderType model = RenderTypes.entityCutout(TEXTURE);
        int light = state.lightCoords;

        poseStack.pushPose();

        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.scale(0.99F, 0.99F, 0.99F);

        poseStack.rotateDegrees(Axis.YP, state.angle);

        poseStack.pushPose();
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        poseStack.translate(-4F * pixel, 0, -4F * pixel);
        collector.submitModelPart(tank, poseStack, model, light, OverlayTexture.NO_OVERLAY, null);
        poseStack.translate(4F * pixel, 0, 4F * pixel);

        poseStack.translate(-4F * pixel, 0, 4F * pixel);
        collector.submitModelPart(tank, poseStack, model, light, OverlayTexture.NO_OVERLAY, null);
        poseStack.translate(4F * pixel, 0, -4F * pixel);

        poseStack.translate(4F * pixel, 0, 0);
        collector.submitModelPart(tank, poseStack, model, light, OverlayTexture.NO_OVERLAY, null);
        poseStack.translate(-4F * pixel, 0, 0);
        poseStack.popPose();

        float trans1, trans2;

        if (anim <= 100) {
            trans1 = 12F * pixel * anim / 100F;
            trans2 = 0;
        } else if (anim <= 200) {
            trans1 = 12F * pixel - (12F * pixel * (anim - 100F) / 100F);
            trans2 = 12F * pixel * (anim - 100F) / 100F;
        } else {
            trans1 = 12F * pixel * (anim - 200F) / 100F;
            trans2 = 12F * pixel - (12F * pixel * (anim - 200F) / 100F);
        }

        poseStack.pushPose();
        poseStack.scale(0.99F, 0.99F, 0.99F);
        poseStack.translate(-0.51F, trans1 - 0.5F, -0.5F);
        collector.submitModelPart(theMagnet, poseStack, model, light, OverlayTexture.NO_OVERLAY, null);
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.scale(0.99F, 0.99F, 0.99F);
        poseStack.translate(-0.51F, trans2 - 0.5F, 12F * pixel - 0.5F);
        collector.submitModelPart(theMagnet, poseStack, model, light, OverlayTexture.NO_OVERLAY, null);
        poseStack.popPose();

        poseStack.translate(-0.5F, -0.5F, -0.5F);
        poseStack.scale(0.5F, 1, 0.5F);

        renderFluid(state.liquid1, state.stage1, state.color1, 0, 0, poseStack, collector, light);
        renderFluid(state.liquid2, state.stage2, state.color2, 0, 1, poseStack, collector, light);
        renderFluid(state.liquidResult, state.stageResult, state.colorResult, 1, 0.5F, poseStack, collector, light);

        poseStack.popPose();
    }

    /** One of 4.2.2's three glCallList blocks: the fluid's cuboid, moved by (x, 0, z) in the half-width frame. */
    private static void renderFluid(FluidStack liquid, int stage, int color, float x, float z, PoseStack poseStack,
            SubmitNodeCollector collector, int light) {
        if (liquid.isEmpty() || liquid.getAmount() <= 0)
            return;

        poseStack.pushPose();
        poseStack.translate(x, 0, z);
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(FluidRenderer.getFluidSheet(liquid)),
                (pose, buffer) -> FluidRenderer.renderFluid(liquid, stage, false, pose, buffer, color, light,
                        OverlayTexture.NO_OVERLAY));
        poseStack.popPose();
    }

    private int getDisplayListIndex(Tank tank) {
        return Math.min((int) ((float) tank.getFluidAmount() / (float) tank.getCapacity() * (FluidRenderer.DISPLAY_STAGES - 1)), FluidRenderer.DISPLAY_STAGES - 1);
    }
}
