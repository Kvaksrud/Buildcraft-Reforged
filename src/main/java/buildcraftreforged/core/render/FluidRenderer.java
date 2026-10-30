/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: Icons become block-atlas sprites from the fluid's FluidModel; the GL colour
 * becomes a returned ARGB colour from the model's FluidTintSource; GL display lists become renderFluid, which writes
 * the same cuboid (DISPLAY_STAGES heights) into a VertexConsumer for a BlockEntityRenderer.
 */
package buildcraftreforged.core.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.client.fluid.FluidTintSource;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jspecify.annotations.Nullable;

/**
 *
 * @author CovertJaguar <railcraft.wikispaces.com>
 */
public class FluidRenderer {

    private static final Identifier BLOCK_TEXTURE = TextureAtlas.LOCATION_BLOCKS;
    public static final int DISPLAY_STAGES = 100;

    public static @Nullable TextureAtlasSprite getFluidTexture(FluidStack fluidStack, boolean flowing) {
        if (fluidStack.isEmpty()) {
            return null;
        }
        return getFluidTexture(fluidStack.getFluid(), flowing);
    }

    /** The missing-texture sprite stands in for a fluid without a model, as "missingno" did. */
    public static @Nullable TextureAtlasSprite getFluidTexture(@Nullable Fluid fluid, boolean flowing) {
        if (fluid == null) {
            return null;
        }
        FluidModel model = getFluidModel(fluid);
        return flowing ? model.flowingMaterial().sprite() : model.stillMaterial().sprite();
    }

    public static Identifier getFluidSheet(FluidStack liquid) {
        return BLOCK_TEXTURE;
    }

    public static Identifier getFluidSheet(Fluid liquid) {
        return BLOCK_TEXTURE;
    }

    /**
     * 4.2.2 setColorForFluidStack set the GL colour from Fluid.getColor; the colour is returned now, as RGB like
     * Tank.colorRenderCache. White when the client has no fluid models yet.
     */
    public static int getColorForFluidStack(FluidStack fluidstack) {
        if (fluidstack.isEmpty() || Minecraft.getInstance() == null)
            return 0xFFFFFF;

        FluidModel model;
        try {
            model = getFluidModel(fluidstack.getFluid());
        } catch (NullPointerException e) {
            return 0xFFFFFF;
        }
        FluidTintSource tint = model.fluidTintSource();
        return tint != null ? tint.colorAsStack(fluidstack) & 0xFFFFFF : 0xFFFFFF;
    }

    private static FluidModel getFluidModel(Fluid fluid) {
        return Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.defaultFluidState());
    }

    /**
     * 4.2.2 getFluidDisplayLists compiled DISPLAY_STAGES display lists per fluid, list s being a cuboid from 0.01 to
     * 0.99 across and 0 to s / DISPLAY_STAGES high. This writes list {@code stage} directly, in the given colour
     * (RGB, as colorRenderCache).
     */
    public static void renderFluid(FluidStack fluidStack, int stage, boolean flowing, PoseStack.Pose pose, VertexConsumer buffer, int color, int light,
            int overlay) {
        TextureAtlasSprite sprite = getFluidTexture(fluidStack, flowing);
        if (sprite == null || stage <= 0) {
            return;
        }
        int argb = 0xFF000000 | (color & 0xFFFFFF);

        float minX = 0.01f;
        float minY = 0;
        float minZ = 0.01f;

        float maxX = 0.99f;
        float maxY = (float) Math.min(stage, DISPLAY_STAGES) / (float) DISPLAY_STAGES;
        float maxZ = 0.99f;

        // Faces in RenderEntityBlock order: down, up, north, south, west, east.
        quad(pose, buffer, sprite, argb, light, overlay, 0, -1, 0,
                minX, minY, maxZ, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, maxX, minZ, maxZ);
        quad(pose, buffer, sprite, argb, light, overlay, 0, 1, 0,
                minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, minX, maxX, minZ, maxZ);
        quad(pose, buffer, sprite, argb, light, overlay, 0, 0, -1,
                maxX, maxY, minZ, maxX, minY, minZ, minX, minY, minZ, minX, maxY, minZ, minX, maxX, 1 - maxY, 1 - minY);
        quad(pose, buffer, sprite, argb, light, overlay, 0, 0, 1,
                minX, maxY, maxZ, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxX, 1 - maxY, 1 - minY);
        quad(pose, buffer, sprite, argb, light, overlay, -1, 0, 0,
                minX, maxY, minZ, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minZ, maxZ, 1 - maxY, 1 - minY);
        quad(pose, buffer, sprite, argb, light, overlay, 1, 0, 0,
                maxX, maxY, maxZ, maxX, minY, maxZ, maxX, minY, minZ, maxX, maxY, minZ, minZ, maxZ, 1 - maxY, 1 - minY);
    }

    /** One face; the corners go u0/v0, u0/v1, u1/v1, u1/v0, with u and v as fractions of the sprite. */
    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, int argb, int light, int overlay,
            float nx, float ny, float nz,
            float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4,
            float u0, float u1, float v0, float v1) {
        float su0 = sprite.getU(u0);
        float su1 = sprite.getU(u1);
        float sv0 = sprite.getV(v0);
        float sv1 = sprite.getV(v1);
        vertex(pose, buffer, argb, light, overlay, nx, ny, nz, x1, y1, z1, su0, sv0);
        vertex(pose, buffer, argb, light, overlay, nx, ny, nz, x2, y2, z2, su0, sv1);
        vertex(pose, buffer, argb, light, overlay, nx, ny, nz, x3, y3, z3, su1, sv1);
        vertex(pose, buffer, argb, light, overlay, nx, ny, nz, x4, y4, z4, su1, sv0);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, int argb, int light, int overlay, float nx, float ny, float nz,
            float x, float y, float z, float u, float v) {
        buffer.addVertex(pose, x, y, z).setColor(argb).setUv(u, v).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
    }
}
