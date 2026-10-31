/*
 * Buildcraft Reforged, 2026. Not a 4.2.2 class: in 4.2.2 the builder's stripes box and path lasers were EntityBlock and
 * EntityPowerLaser entities, and its robot an EntityRobot drawn by core/render/RenderRobot (an 8 x 8 x 8 cube with
 * robot.png) with an EntityEnergyLaser to the block it builds. Here TileBuilder keeps them all as render data and this
 * renderer draws them: the box through RenderEntityBlock, the lasers through RenderLaser and RenderEnergyLaser, the
 * robot as RenderRobot's cube. The builder block itself is the JSON model builder_block.
 */
package buildcraftreforged.builders.render;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.builders.TileBuilder;
import buildcraftreforged.core.Box;
import buildcraftreforged.core.EntityBlock;
import buildcraftreforged.core.EntityEnergyLaser;
import buildcraftreforged.core.EntityLaser;
import buildcraftreforged.core.EntityRobot;
import buildcraftreforged.core.render.RenderEnergyLaser;
import buildcraftreforged.core.render.RenderEntityBlock;
import buildcraftreforged.core.render.RenderLaser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
import org.jspecify.annotations.Nullable;

public class RenderBuilder implements BlockEntityRenderer<TileBuilder, RenderBuilder.State> {

    /** 4.2.2 RenderRobot.TEXTURE. */
    public static final Identifier ROBOT_TEXTURE =
            Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "textures/entity/robot.png");

    public static class State extends BlockEntityRenderState {
        public final List<EntityBlock> blocks = new ArrayList<>();
        public final List<EntityLaser> lasers = new ArrayList<>();
        public @Nullable EntityEnergyLaser robotLaser;
        public boolean robot;
        public double robotX, robotY, robotZ;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(TileBuilder tile, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(tile, state, partialTicks, cameraPosition, breakProgress);
        state.blocks.clear();
        state.lasers.clear();
        state.robotLaser = null;
        state.robot = false;

        EntityBlock[] boxLasers = tile.getBox().getLasers();
        if (boxLasers != null) {
            for (EntityBlock block : boxLasers) {
                if (block == null || block.isDead || block.texture == null || block.iSize <= 0 || block.jSize <= 0
                        || block.kSize <= 0)
                    continue;
                EntityBlock copy = new EntityBlock(null, block.posX, block.posY, block.posZ, block.iSize, block.jSize, block.kSize);
                copy.texture = block.texture;
                copy.setBrightness(block.getBrightnessForRender());
                state.blocks.add(copy);
            }
        }

        List<EntityLaser> pathLasers = tile.getPathLasers();
        if (pathLasers != null) {
            for (EntityLaser laser : pathLasers) {
                if (!laser.isDead && laser.isVisible())
                    state.lasers.add(laser);
            }
        }

        EntityRobot robot = tile.getRobot();
        if (robot != null) {
            state.robot = true;
            state.robotX = robot.posX + robot.motionX * partialTicks;
            state.robotY = robot.posY + robot.motionY * partialTicks;
            state.robotZ = robot.posZ + robot.motionZ * partialTicks;
            EntityEnergyLaser laser = robot.getLaser();
            if (laser != null && !laser.isDead && laser.isVisible())
                state.robotLaser = laser;
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        BlockPos pos = state.blockPos;
        for (EntityBlock block : state.blocks) {
            collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(block.texture),
                    (pose, buffer) -> RenderEntityBlock.INSTANCE.doRenderBlock(block, block.posX - pos.getX(),
                            block.posY - pos.getY(), block.posZ - pos.getZ(), pose, buffer, state.lightCoords));
        }

        for (EntityLaser laser : state.lasers) {
            double x = laser.getHead().x - pos.getX();
            double y = laser.getHead().y - pos.getY();
            double z = laser.getHead().z - pos.getZ();
            collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(laser.getTexture()),
                    (pose, buffer) -> RenderLaser.INSTANCE.doRender(laser, x, y, z, pose, buffer));
        }

        EntityEnergyLaser robotLaser = state.robotLaser;
        if (robotLaser != null) {
            double x = robotLaser.getHead().x - pos.getX();
            double y = robotLaser.getHead().y - pos.getY();
            double z = robotLaser.getHead().z - pos.getZ();
            collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(robotLaser.getTexture()),
                    (pose, buffer) -> RenderEnergyLaser.INSTANCE.doRender(robotLaser, x, y, z, pose, buffer));
        }

        if (state.robot) {
            float x = (float) (state.robotX - pos.getX());
            float y = (float) (state.robotY - pos.getY());
            float z = (float) (state.robotZ - pos.getZ());
            collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(ROBOT_TEXTURE),
                    (pose, buffer) -> renderRobot(pose, buffer, x, y, z));
        }
    }

    /**
     * RenderRobot: ModelRenderer.addBox(-4, -4, -4, 8, 8, 8) at 1/16, unlit (full brightness), with 1.6.4's box texture
     * layout at (0, 0) of the 64 x 32 robot.png.
     */
    private static void renderRobot(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z) {
        float r = 4 / 16F;
        float x0 = x - r, x1 = x + r, y0 = y - r, y1 = y + r, z0 = z - r, z1 = z + r;
        int light = 0xF000F0;
        // East and west: 1.6.4's +x and -x quads.
        face(pose, buffer, light, 1, 0, 0, 16, 8, 24, 16, x1, y1, z1, x1, y1, z0, x1, y0, z0, x1, y0, z1);
        face(pose, buffer, light, -1, 0, 0, 0, 8, 8, 16, x0, y1, z0, x0, y1, z1, x0, y0, z1, x0, y0, z0);
        // Up and down.
        face(pose, buffer, light, 0, 1, 0, 8, 0, 16, 8, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
        face(pose, buffer, light, 0, -1, 0, 16, 0, 24, 8, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0);
        // North and south.
        face(pose, buffer, light, 0, 0, -1, 8, 8, 16, 16, x1, y1, z0, x0, y1, z0, x0, y0, z0, x1, y0, z0);
        face(pose, buffer, light, 0, 0, 1, 24, 8, 32, 16, x0, y1, z1, x1, y1, z1, x1, y0, z1, x0, y0, z1);
    }

    private static void face(PoseStack.Pose pose, VertexConsumer buffer, int light, float nx, float ny, float nz, float u0,
            float v0, float u1, float v1, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy,
            float cz, float dx, float dy, float dz) {
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

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return Minecraft.getInstance().options.getEffectiveRenderDistance() * 16;
    }

    /** The builder, its box (with the robot's room around it) and its path. */
    @Override
    public AABB getRenderBoundingBox(TileBuilder tile) {
        BlockPos pos = tile.getBlockPos();
        AABB result = new AABB(pos);
        Box box = tile.getBox();
        if (box.isInitialized())
            result = result.minmax(new AABB(box.xMin - 1, box.yMin - 1, box.zMin - 1, box.xMax + 2, box.yMax + 2, box.zMax + 2));
        EntityRobot robot = tile.getRobot();
        if (robot != null && robot.getBox().isInitialized()) {
            Box robotBox = robot.getBox();
            result = result.minmax(new AABB(robotBox.xMin - 1, robotBox.yMin - 1, robotBox.zMin - 1, robotBox.xMax + 2,
                    robotBox.yMax + 2, robotBox.zMax + 2));
        }
        List<EntityLaser> pathLasers = tile.getPathLasers();
        if (pathLasers != null) {
            for (EntityLaser laser : pathLasers) {
                result = result.minmax(new AABB(laser.getHead().x, laser.getHead().y, laser.getHead().z,
                        laser.getTail().x, laser.getTail().y, laser.getTail().z).inflate(1));
            }
        }
        return result;
    }
}
