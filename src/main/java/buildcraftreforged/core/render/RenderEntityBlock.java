/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: no longer an entity Render. A BlockEntityRenderer calls doRenderBlock from a
 * submitCustomGeometry callback with the EntityBlock's texture bound by the render type; GL11 matrix calls become the
 * PoseStack, the Tessellator and RenderBlocks faces become VertexConsumer quads with RenderBlocks' bound-based UVs;
 * RenderInfo's Block template and Icons become one texture Identifier for the whole box.
 */
package buildcraftreforged.core.render;

import buildcraftreforged.core.EntityBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.Arrays;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public class RenderEntityBlock {

    public static RenderEntityBlock INSTANCE = new RenderEntityBlock();

    public static class RenderInfo {

        public double minX;
        public double minY;
        public double minZ;
        public double maxX;
        public double maxY;
        public double maxZ;
        public @Nullable Identifier texture = null;
        public boolean[] renderSide = new boolean[6];
        public float light = -1f;
        public int brightness = -1;

        public RenderInfo() {
            setRenderAllSides();
        }

        public RenderInfo(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
            this();
            setBounds(minX, minY, minZ, maxX, maxY, maxZ);
        }

        public final void setBounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        public final void setRenderSingleSide(int side) {
            Arrays.fill(renderSide, false);
            renderSide[side] = true;
        }

        public final void setRenderAllSides() {
            Arrays.fill(renderSide, true);
        }

        public void rotate() {
            double temp = minX;
            minX = minZ;
            minZ = temp;

            temp = maxX;
            maxX = maxZ;
            maxZ = temp;
        }

        public void reverseX() {
            double temp = minX;
            minX = 1 - maxX;
            maxX = 1 - temp;
        }

        public void reverseZ() {
            double temp = minZ;
            minZ = 1 - maxZ;
            maxZ = 1 - temp;
        }
    }

    private RenderEntityBlock() {
    }

    /**
     * Draws the box cell by cell, as 4.2.2 did. (i, j, k) is the box's position relative to the pose; light is the
     * packed light to use when the EntityBlock sets no brightness of its own.
     */
    public void doRenderBlock(EntityBlock entity, double i, double j, double k, PoseStack.Pose pose, VertexConsumer buffer,
            int light) {
        if (entity.isDead)
            return;

        PoseStack poseStack = new PoseStack();
        poseStack.last().set(pose);

        RenderInfo util = new RenderInfo();
        util.texture = entity.texture;
        util.brightness = entity.getBrightnessForRender() > 0 ? entity.getBrightnessForRender() : light;

        for (int iBase = 0; iBase < entity.iSize; ++iBase) {
            for (int jBase = 0; jBase < entity.jSize; ++jBase) {
                for (int kBase = 0; kBase < entity.kSize; ++kBase) {

                    util.minX = 0;
                    util.minY = 0;
                    util.minZ = 0;

                    double remainX = entity.iSize - iBase;
                    double remainY = entity.jSize - jBase;
                    double remainZ = entity.kSize - kBase;

                    util.maxX = (remainX > 1.0 ? 1.0 : remainX);
                    util.maxY = (remainY > 1.0 ? 1.0 : remainY);
                    util.maxZ = (remainZ > 1.0 ? 1.0 : remainZ);

                    poseStack.pushPose();
                    poseStack.translate(i, j, k);
                    poseStack.rotateDegrees(Axis.XP, entity.rotationX);
                    poseStack.rotateDegrees(Axis.YP, entity.rotationY);
                    poseStack.rotateDegrees(Axis.ZP, entity.rotationZ);
                    poseStack.translate(iBase, jBase, kBase);

                    renderBlock(util, poseStack.last(), buffer);
                    poseStack.popPose();
                }
            }
        }
    }

    /** RenderBlocks.renderFace*: UVs come from the render bounds, in 16ths of the texture. */
    public void renderBlock(RenderInfo info, PoseStack.Pose pose, VertexConsumer buffer) {
        int light = info.brightness >= 0 ? info.brightness : 0xF000F0;
        double x0 = info.minX, y0 = info.minY, z0 = info.minZ, x1 = info.maxX, y1 = info.maxY, z1 = info.maxZ;

        if (info.renderSide[0]) // YNeg
            quad(pose, buffer, light, 0, -1, 0,
                    x0, y0, z1, x0, z1, x0, y0, z0, x0, z0, x1, y0, z0, x1, z0, x1, y0, z1, x1, z1);
        if (info.renderSide[1]) // YPos
            quad(pose, buffer, light, 0, 1, 0,
                    x1, y1, z1, x1, z1, x1, y1, z0, x1, z0, x0, y1, z0, x0, z0, x0, y1, z1, x0, z1);
        if (info.renderSide[2]) // ZNeg
            quad(pose, buffer, light, 0, 0, -1,
                    x0, y1, z0, 1 - x0, 1 - y1, x1, y1, z0, 1 - x1, 1 - y1, x1, y0, z0, 1 - x1, 1 - y0, x0, y0, z0, 1 - x0, 1 - y0);
        if (info.renderSide[3]) // ZPos
            quad(pose, buffer, light, 0, 0, 1,
                    x0, y1, z1, x0, 1 - y1, x0, y0, z1, x0, 1 - y0, x1, y0, z1, x1, 1 - y0, x1, y1, z1, x1, 1 - y1);
        if (info.renderSide[4]) // XNeg
            quad(pose, buffer, light, -1, 0, 0,
                    x0, y1, z1, z1, 1 - y1, x0, y1, z0, z0, 1 - y1, x0, y0, z0, z0, 1 - y0, x0, y0, z1, z1, 1 - y0);
        if (info.renderSide[5]) // XPos
            quad(pose, buffer, light, 1, 0, 0,
                    x1, y0, z1, 1 - z1, 1 - y0, x1, y0, z0, 1 - z0, 1 - y0, x1, y1, z0, 1 - z0, 1 - y1, x1, y1, z1, 1 - z1, 1 - y1);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, int light, float nx, float ny, float nz,
            double ax, double ay, double az, double au, double av, double bx, double by, double bz, double bu, double bv,
            double cx, double cy, double cz, double cu, double cv, double dx, double dy, double dz, double du, double dv) {
        vertex(pose, buffer, light, nx, ny, nz, ax, ay, az, au, av);
        vertex(pose, buffer, light, nx, ny, nz, bx, by, bz, bu, bv);
        vertex(pose, buffer, light, nx, ny, nz, cx, cy, cz, cu, cv);
        vertex(pose, buffer, light, nx, ny, nz, dx, dy, dz, du, dv);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, int light, float nx, float ny, float nz,
            double x, double y, double z, double u, double v) {
        buffer.addVertex(pose, (float) x, (float) y, (float) z).setColor(0xFFFFFFFF).setUv((float) u, (float) v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
    }
}
