/*
 * Buildcraft Reforged, 2026. Not a 4.2.2 class: in 4.2.2 a marker's lasers were EntityBlock and EntityPowerLaser
 * entities, drawn by RenderEntityBlock and core/render/RenderLaser. Here the marker block entity keeps them and this
 * renderer draws them: the red box and blue signal lasers of TileMarker through RenderEntityBlock, and the laser
 * between two linked path markers with RenderLaser's 16x1x1 model box. The marker block itself
 * (4.2.2 RenderingMarkers) is the JSON model block/template_marker.
 */
package buildcraftreforged.builders.render;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.builders.TileMarker;
import buildcraftreforged.builders.TilePathMarker;
import buildcraftreforged.core.EntityBlock;
import buildcraftreforged.core.render.RenderEntityBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class RenderMarker<T extends TileMarker> implements BlockEntityRenderer<T, RenderMarker.State> {

    /** 4.2.2 EntityPowerLaser texture 0, which TilePathMarker set. */
    public static final Identifier PATH_LASER_TEXTURE =
            Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "textures/entity/laser_1.png");
    /** Utils.createLaser's setBrightness(210), as a packed light value. */
    private static final int LASER_LIGHT = 210;
    /** How far a marker's lasers reach: TileMarker's maxSize and TilePathMarker's searchSize. */
    private static final int LASER_REACH = 64;

    public static class State extends BlockEntityRenderState {
        public final List<EntityBlock> boxes = new ArrayList<>();
        public final List<Vec3> pathLasers = new ArrayList<>();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(T tile, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(tile, state, partialTicks, cameraPosition, breakProgress);
        state.boxes.clear();
        state.pathLasers.clear();
        addBoxes(state, tile.getLasers());
        addBoxes(state, tile.getSignals());

        if (tile instanceof TilePathMarker pathMarker) {
            for (BlockPos link : pathMarker.linkPositions) {
                // Each laser is drawn once, by the marker at the lower position.
                if (link != null && tile.getBlockPos().compareTo(link) < 0) {
                    state.pathLasers.add(Vec3.atLowerCornerOf(link.subtract(tile.getBlockPos())));
                }
            }
        }
    }

    private static void addBoxes(State state, EntityBlock @Nullable [] boxes) {
        if (boxes == null)
            return;
        for (EntityBlock box : boxes) {
            if (box != null && !box.isDead && box.texture != null)
                state.boxes.add(box);
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        BlockPos pos = state.blockPos;
        for (EntityBlock box : state.boxes) {
            collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(box.texture),
                    (pose, buffer) -> RenderEntityBlock.INSTANCE.doRenderBlock(box, box.posX - pos.getX(),
                            box.posY - pos.getY(), box.posZ - pos.getZ(), pose, buffer, state.lightCoords));
        }

        for (Vec3 tail : state.pathLasers) {
            collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(PATH_LASER_TEXTURE),
                    (pose, buffer) -> renderLaser(pose, buffer, new Vec3(0.5, 0.5, 0.5), tail.add(0.5, 0.5, 0.5)));
        }
    }

    /**
     * 4.2.2 RenderLaser.doRender with EntityLaser.updateGraphicData's angles: one 16x1x1 model box per block of
     * length, the last one scaled to the remainder.
     */
    private static void renderLaser(PoseStack.Pose pose, VertexConsumer buffer, Vec3 head, Vec3 tail) {
        double dx = head.x - tail.x;
        double dy = head.y - tail.y;
        double dz = head.z - tail.z;
        double renderSize = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double angleZ = 360 - (Math.atan2(dz, dx) * 180.0 / Math.PI + 180.0);
        dx = Math.sqrt(renderSize * renderSize - dy * dy);
        double angleY = -Math.atan2(dy, dx) * 180 / Math.PI;

        PoseStack poseStack = new PoseStack();
        poseStack.last().set(pose);
        poseStack.translate(head.x, head.y, head.z);
        poseStack.rotateDegrees(Axis.YP, (float) angleZ);
        poseStack.rotateDegrees(Axis.ZP, (float) angleY);

        float lasti = 0;
        for (float i = 0; i <= renderSize - 1; ++i) {
            laserBox(poseStack.last(), buffer);
            poseStack.translate(1, 0, 0);
            lasti = i;
        }
        lasti++;
        poseStack.scale((float) renderSize - lasti, 1, 1);
        laserBox(poseStack.last(), buffer);
    }

    /** RenderLaser's ModelRenderer box: addBox(0, -0.5, -0.5, 16, 1, 1) at scale 1/16, texture 64 x 32. */
    private static void laserBox(PoseStack.Pose pose, VertexConsumer buffer) {
        float x0 = 0, x1 = 1, y0 = -1 / 32F, y1 = 1 / 32F, z0 = -1 / 32F, z1 = 1 / 32F;
        // ModelBox texture layout for 16 x 1 x 1 at (0, 0), in pixels of the 64 x 32 texture.
        face(pose, buffer, 1, 0, 0, 17, 1, 18, 2, x1, y1, z0, x1, y1, z1, x1, y0, z1, x1, y0, z0);
        face(pose, buffer, -1, 0, 0, 0, 1, 1, 2, x0, y1, z1, x0, y1, z0, x0, y0, z0, x0, y0, z1);
        face(pose, buffer, 0, 1, 0, 1, 0, 17, 1, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
        face(pose, buffer, 0, -1, 0, 17, 0, 33, 1, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        face(pose, buffer, 0, 0, -1, 1, 1, 17, 2, x0, y1, z0, x1, y1, z0, x1, y0, z0, x0, y0, z0);
        face(pose, buffer, 0, 0, 1, 18, 1, 34, 2, x1, y1, z1, x0, y1, z1, x0, y0, z1, x1, y0, z1);
    }

    private static void face(PoseStack.Pose pose, VertexConsumer buffer, float nx, float ny, float nz, float u0, float v0,
            float u1, float v1, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
            float dx, float dy, float dz) {
        vertex(pose, buffer, nx, ny, nz, ax, ay, az, u0, v0);
        vertex(pose, buffer, nx, ny, nz, bx, by, bz, u1, v0);
        vertex(pose, buffer, nx, ny, nz, cx, cy, cz, u1, v1);
        vertex(pose, buffer, nx, ny, nz, dx, dy, dz, u0, v1);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float nx, float ny, float nz, float x, float y,
            float z, float u, float v) {
        buffer.addVertex(pose, x, y, z).setColor(0xFFFFFFFF).setUv(u / 64F, v / 32F).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LASER_LIGHT).setNormal(pose, nx, ny, nz);
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return Minecraft.getInstance().options.getEffectiveRenderDistance() * 16;
    }

    @Override
    public AABB getRenderBoundingBox(T tile) {
        return new AABB(tile.getBlockPos()).inflate(LASER_REACH);
    }
}
