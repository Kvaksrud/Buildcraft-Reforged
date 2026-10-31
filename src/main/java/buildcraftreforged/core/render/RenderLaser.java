/*
 * Ported from buildcraft/core/render/RenderLaser.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: no longer an entity Render. A BlockEntityRenderer calls doRender from a
 * submitCustomGeometry callback with the laser's texture bound by the render type; GL11 matrix calls become the
 * PoseStack; the ModelRenderer box becomes ModelBox, which writes the same 16x1x1 box with ModelBox's texture layout
 * as VertexConsumer quads; lighting is the laser's brightness instead of GL_LIGHTING off.
 */
package buildcraftreforged.core.render;

import buildcraftreforged.core.EntityLaser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class RenderLaser {

    public static final RenderLaser INSTANCE = new RenderLaser();

    private ModelBox box;

    public RenderLaser() {

        box = new ModelBox(0, 0);
    }

    /**
     * Draws the beam. (x, y, z) is the laser's head relative to the pose, which 4.2.2 reached as the entity position
     * plus renderOffset().
     */
    public void doRender(EntityLaser laser, double x, double y, double z, PoseStack.Pose pose, VertexConsumer buffer) {

        if (!laser.isVisible() || laser.getTexture() == null)
            return;

        PoseStack poseStack = new PoseStack();
        poseStack.last().set(pose);

        poseStack.translate(x, y, z);

        poseStack.rotateDegrees(Axis.YP, (float) laser.angleZ);
        poseStack.rotateDegrees(Axis.ZP, (float) laser.angleY);

        int light = laser.getBrightnessForRender();

        float lasti = 0;

        for (float i = 0; i <= laser.renderSize - 1; ++i) {
            getBox(laser).render(poseStack.last(), buffer, light);
            poseStack.translate(1, 0, 0);
            lasti = i;
        }

        lasti++;

        poseStack.scale(((float) laser.renderSize - lasti), 1, 1);
        getBox(laser).render(poseStack.last(), buffer, light);

        iterate(laser);
    }

    protected void iterate(EntityLaser laser) {
    }

    protected ModelBox getBox(EntityLaser laser) {
        return box;
    }

    /**
     * 1.6.4's ModelRenderer with addBox(0, -0.5F, -0.5F, 16, 1, 1) rendered at 1/16: one block long, 1/16 thick, with
     * ModelBox's texture layout for a 16 x 1 x 1 box at (textureOffsetX, textureOffsetY) of a 64 x 32 texture.
     */
    public static class ModelBox {

        private final float textureOffsetX;
        private final float textureOffsetY;

        public ModelBox(int textureOffsetX, int textureOffsetY) {
            this.textureOffsetX = textureOffsetX;
            this.textureOffsetY = textureOffsetY;
        }

        public void render(PoseStack.Pose pose, VertexConsumer buffer, int light) {
            float x0 = 0, x1 = 1, y0 = -1 / 32F, y1 = 1 / 32F, z0 = -1 / 32F, z1 = 1 / 32F;
            face(pose, buffer, light, 1, 0, 0, 17, 1, 18, 2, x1, y1, z0, x1, y1, z1, x1, y0, z1, x1, y0, z0);
            face(pose, buffer, light, -1, 0, 0, 0, 1, 1, 2, x0, y1, z1, x0, y1, z0, x0, y0, z0, x0, y0, z1);
            face(pose, buffer, light, 0, 1, 0, 1, 0, 17, 1, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
            face(pose, buffer, light, 0, -1, 0, 17, 0, 33, 1, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
            face(pose, buffer, light, 0, 0, -1, 1, 1, 17, 2, x0, y1, z0, x1, y1, z0, x1, y0, z0, x0, y0, z0);
            face(pose, buffer, light, 0, 0, 1, 18, 1, 34, 2, x1, y1, z1, x0, y1, z1, x0, y0, z1, x1, y0, z1);
        }

        private void face(PoseStack.Pose pose, VertexConsumer buffer, int light, float nx, float ny, float nz, float u0,
                float v0, float u1, float v1, float ax, float ay, float az, float bx, float by, float bz, float cx,
                float cy, float cz, float dx, float dy, float dz) {
            u0 += textureOffsetX;
            u1 += textureOffsetX;
            v0 += textureOffsetY;
            v1 += textureOffsetY;
            vertex(pose, buffer, light, nx, ny, nz, ax, ay, az, u0, v0);
            vertex(pose, buffer, light, nx, ny, nz, bx, by, bz, u1, v0);
            vertex(pose, buffer, light, nx, ny, nz, cx, cy, cz, u1, v1);
            vertex(pose, buffer, light, nx, ny, nz, dx, dy, dz, u0, v1);
        }

        private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, int light, float nx, float ny, float nz,
                float x, float y, float z, float u, float v) {
            buffer.addVertex(pose, x, y, z).setColor(0xFFFFFFFF).setUv(u / 64F, v / 32F)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        }
    }
}
