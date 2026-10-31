/*
 * Copyright (c) SpaceToad, 2011-2012
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: drawn from the pipe's BlockEntityRenderer instead of the chunk renderer, as
 * custom geometry on the block atlas. renderBlock.getIcon(side, meta) becomes the sprite of the facade state's model
 * quads on that face, and the item colour its block tint; RenderBlocks bounds become boxes with a per-face mask
 * (4.2.2 setRenderSide). The uvRotate calls for logs are gone: the axis state's model already turns the end and bark
 * faces, though the bark is not rotated within a face. The plug boxes of 4.2.2 PipeRendererWorld.pipePlugRenderer live
 * here too, and the facade item's boxes of FacadeItemRenderer. Client only.
 */
package buildcraftreforged.transport.render;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.core.CoreConstants;
import buildcraftreforged.core.render.RenderEntityBlock;
import buildcraftreforged.core.render.RenderEntityBlock.RenderInfo;
import buildcraftreforged.core.utils.MatrixTranformations;
import buildcraftreforged.transport.PipeParts;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * @author CovertJaguar <http://www.railcraft.info/>
 */
public class FacadeRenderHelper {

    /** 4.2.2 PipeIconProvider PipeStructureCobblestone, the texture of facade supports and plugs. */
    public static final Identifier STRUCTURE_PIPE = Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID,
            "textures/block/pipe_structure_cobblestone.png");

    private static final float zFightOffset = 1F / 4096F;
    private static final float[][] zeroStateFacade = new float[3][2];
    private static final float[][] zeroStateSupport = new float[3][2];
    private static final float[] xOffsets = new float[6];
    private static final float[] yOffsets = new float[6];
    private static final float[] zOffsets = new float[6];

    static {

        // X START - END
        zeroStateFacade[0][0] = 0.0F;
        zeroStateFacade[0][1] = 1.0F;
        // Y START - END
        zeroStateFacade[1][0] = 0.0F;
        zeroStateFacade[1][1] = PipeParts.FACADE_THICKNESS;
        // Z START - END
        zeroStateFacade[2][0] = 0.0F;
        zeroStateFacade[2][1] = 1.0F;

        // X START - END
        zeroStateSupport[0][0] = CoreConstants.PIPE_MIN_POS;
        zeroStateSupport[0][1] = CoreConstants.PIPE_MAX_POS;
        // Y START - END
        zeroStateSupport[1][0] = PipeParts.FACADE_THICKNESS;
        zeroStateSupport[1][1] = CoreConstants.PIPE_MIN_POS;
        // Z START - END
        zeroStateSupport[2][0] = CoreConstants.PIPE_MIN_POS;
        zeroStateSupport[2][1] = CoreConstants.PIPE_MAX_POS;

        xOffsets[0] = zFightOffset;
        xOffsets[1] = zFightOffset;
        xOffsets[2] = 0;
        xOffsets[3] = 0;
        xOffsets[4] = 0;
        xOffsets[5] = 0;

        yOffsets[0] = 0;
        yOffsets[1] = 0;
        yOffsets[2] = zFightOffset;
        yOffsets[3] = zFightOffset;
        yOffsets[4] = 0;
        yOffsets[5] = 0;

        zOffsets[0] = zFightOffset;
        zOffsets[1] = zFightOffset;
        zOffsets[2] = 0;
        zOffsets[3] = 0;
        zOffsets[4] = 0;
        zOffsets[5] = 0;
    }

    /** What a facade looks like: the sprite and tint (RGB) of each face, by {@link Direction#get3DDataValue()}. */
    public record FacadeLook(TextureAtlasSprite[] sprites, int[] colours) {
    }

    /**
     * The face sprites of {@code state}'s block model: the first quad culled to each face, else the first unculled
     * quad facing that way, else the particle sprite (4.2.2 Block.getIcon(side, meta) and the item colour).
     */
    @SuppressWarnings("deprecation")
    public static FacadeLook look(BlockState state) {
        Minecraft minecraft = Minecraft.getInstance();
        BlockStateModel model = minecraft.getModelManager().getBlockStateModelSet().get(state);
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(RandomSource.create(42L), parts);
        TextureAtlasSprite particle = model.particleMaterial().sprite();
        TextureAtlasSprite[] sprites = new TextureAtlasSprite[6];
        int[] colours = new int[6];
        for (Direction side : Direction.values()) {
            BakedQuad quad = faceQuad(parts, side);
            int index = side.get3DDataValue();
            sprites[index] = quad == null ? particle : quad.materialInfo().sprite();
            colours[index] = 0xFFFFFF;
            if (quad != null && quad.materialInfo().isTinted()) {
                BlockTintSource tint = minecraft.getBlockColors().getTintSource(state, quad.materialInfo().tintIndex());
                if (tint != null)
                    colours[index] = tint.color(state) & 0xFFFFFF;
            }
        }
        return new FacadeLook(sprites, colours);
    }

    private static @Nullable BakedQuad faceQuad(List<BlockStateModelPart> parts, Direction side) {
        for (BlockStateModelPart part : parts) {
            List<BakedQuad> quads = part.getQuads(side);
            if (!quads.isEmpty())
                return quads.get(0);
        }
        for (BlockStateModelPart part : parts) {
            for (BakedQuad quad : part.getQuads(null)) {
                if (quad.direction() == side)
                    return quad;
            }
        }
        return null;
    }

    private static float[] renderBounds(float[][] rotated, Direction side) {
        int i = side.ordinal();
        return new float[] {
                rotated[0][0] + xOffsets[i],
                rotated[1][0] + yOffsets[i],
                rotated[2][0] + zOffsets[i],
                rotated[0][1] - xOffsets[i],
                rotated[1][1] - yOffsets[i],
                rotated[2][1] - zOffsets[i]};
    }

    /**
     * 4.2.2 {@code pipeFacadeRenderer}: a solid facade on each side with one, or a hollow one around the arm when the
     * pipe is connected there, then a structure-pipe support from each solid facade to the pipe centre.
     *
     * @param facades the look of the facade per side, or null for none
     */
    public static void pipeFacadeRenderer(PoseStack poseStack, SubmitNodeCollector collector,
            @Nullable FacadeLook[] facades, boolean[] connected, int light) {
        for (Direction direction : Direction.values()) {
            FacadeLook look = facades[direction.get3DDataValue()];
            if (look == null)
                continue;
            int renderMask = 0;
            for (Direction side : Direction.values()) {
                if (side == direction || side == direction.getOpposite() || facades[side.get3DDataValue()] == null)
                    renderMask |= 1 << side.get3DDataValue();
            }

            // Hollow facade
            if (connected[direction.get3DDataValue()]) {
                float[][] rotated = MatrixTranformations.deepClone(zeroStateFacade);
                rotated[0][0] = CoreConstants.PIPE_MIN_POS - zFightOffset * 4;
                rotated[0][1] = CoreConstants.PIPE_MAX_POS + zFightOffset * 4;
                rotated[2][0] = 0.0F;
                rotated[2][1] = CoreConstants.PIPE_MIN_POS - zFightOffset * 2;
                MatrixTranformations.transform(rotated, direction);
                submitFacadeBox(poseStack, collector, look, light, renderBounds(rotated, direction), renderMask);

                rotated = MatrixTranformations.deepClone(zeroStateFacade);
                rotated[0][0] = CoreConstants.PIPE_MIN_POS - zFightOffset * 4;
                rotated[0][1] = CoreConstants.PIPE_MAX_POS + zFightOffset * 4;
                rotated[2][0] = CoreConstants.PIPE_MAX_POS + zFightOffset * 2;
                MatrixTranformations.transform(rotated, direction);
                submitFacadeBox(poseStack, collector, look, light, renderBounds(rotated, direction), renderMask);

                rotated = MatrixTranformations.deepClone(zeroStateFacade);
                rotated[0][0] = 0.0F;
                rotated[0][1] = CoreConstants.PIPE_MIN_POS - zFightOffset * 2;
                MatrixTranformations.transform(rotated, direction);
                submitFacadeBox(poseStack, collector, look, light, renderBounds(rotated, direction), renderMask);

                rotated = MatrixTranformations.deepClone(zeroStateFacade);
                rotated[0][0] = CoreConstants.PIPE_MAX_POS + zFightOffset * 2;
                rotated[0][1] = 1F;
                MatrixTranformations.transform(rotated, direction);
                submitFacadeBox(poseStack, collector, look, light, renderBounds(rotated, direction), renderMask);
            } else { // Solid facade
                float[][] rotated = MatrixTranformations.deepClone(zeroStateFacade);
                MatrixTranformations.transform(rotated, direction);
                submitFacadeBox(poseStack, collector, look, light, renderBounds(rotated, direction), renderMask);
            }
        }

        // Structure Pipe
        for (Direction direction : Direction.values()) {
            if (facades[direction.get3DDataValue()] != null && !connected[direction.get3DDataValue()]) {
                float[][] rotated = MatrixTranformations.deepClone(zeroStateSupport);
                MatrixTranformations.transform(rotated, direction);
                submitStructureBox(poseStack, collector, light, rotated[0][0], rotated[1][0], rotated[2][0],
                        rotated[0][1], rotated[1][1], rotated[2][1]);
            }
        }
    }

    /** 4.2.2 {@code PipeRendererWorld.pipePlugRenderer}: the cap and the knob of each plug, in the structure texture. */
    public static void pipePlugRenderer(PoseStack poseStack, SubmitNodeCollector collector, boolean[] plugs, int light) {
        float[][] zeroState = new float[3][2];
        // X START - END
        zeroState[0][0] = 0.25F + zFightOffset;
        zeroState[0][1] = 0.75F - zFightOffset;
        // Y START - END
        zeroState[1][0] = 0.125F;
        zeroState[1][1] = 0.251F;
        // Z START - END
        zeroState[2][0] = 0.25F + zFightOffset;
        zeroState[2][1] = 0.75F - zFightOffset;

        for (Direction direction : Direction.values()) {
            if (plugs[direction.get3DDataValue()]) {
                float[][] rotated = MatrixTranformations.deepClone(zeroState);
                MatrixTranformations.transform(rotated, direction);
                submitStructureBox(poseStack, collector, light, rotated[0][0], rotated[1][0], rotated[2][0],
                        rotated[0][1], rotated[1][1], rotated[2][1]);
            }
        }

        // X START - END
        zeroState[0][0] = 0.25F + 0.125F / 2 + zFightOffset;
        zeroState[0][1] = 0.75F - 0.125F / 2 + zFightOffset;
        // Y START - END
        zeroState[1][0] = 0.25F;
        zeroState[1][1] = 0.25F + 0.125F;
        // Z START - END
        zeroState[2][0] = 0.25F + 0.125F / 2;
        zeroState[2][1] = 0.75F - 0.125F / 2;

        for (Direction direction : Direction.values()) {
            if (plugs[direction.get3DDataValue()]) {
                float[][] rotated = MatrixTranformations.deepClone(zeroState);
                MatrixTranformations.transform(rotated, direction);
                submitStructureBox(poseStack, collector, light, rotated[0][0], rotated[1][0], rotated[2][0],
                        rotated[0][1], rotated[1][1], rotated[2][1]);
            }
        }
    }

    /**
     * 4.2.2 {@code FacadeItemRenderer.renderFacadeItem}: a one-pixel facade on the south face and a structure pipe stub
     * behind it, in block coordinates.
     */
    public static void renderFacadeItem(PoseStack poseStack, SubmitNodeCollector collector, FacadeLook look, int light) {
        submitFacadeBox(poseStack, collector, look, light, new float[] {0F, 0F, 1F - 1F / 16F, 1F, 1F, 1F}, 63);
        submitStructureBox(poseStack, collector, light, CoreConstants.PIPE_MIN_POS, CoreConstants.PIPE_MIN_POS,
                CoreConstants.PIPE_MIN_POS + 0.25F, CoreConstants.PIPE_MAX_POS, CoreConstants.PIPE_MAX_POS,
                1F - 1F / 16F);
    }

    private static void submitStructureBox(PoseStack poseStack, SubmitNodeCollector collector, int light, float x0,
            float y0, float z0, float x1, float y1, float z1) {
        RenderInfo box = new RenderInfo();
        box.setBounds(x0, y0, z0, x1, y1, z1);
        box.brightness = light;
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(STRUCTURE_PIPE),
                (pose, buffer) -> RenderEntityBlock.INSTANCE.renderBlock(box, pose, buffer));
    }

    /**
     * One facade box, each face with its own sprite and tint, UVs from the box's place in the block as the block model
     * maps them; only the faces in {@code renderMask} (bit {@code d} = {@link Direction#get3DDataValue()}).
     */
    private static void submitFacadeBox(PoseStack poseStack, SubmitNodeCollector collector, FacadeLook look, int light,
            float[] b, int renderMask) {
        float x0 = b[0], y0 = b[1], z0 = b[2], x1 = b[3], y1 = b[4], z1 = b[5];
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS), (pose, buffer) -> {
            for (Direction side : Direction.values()) {
                int index = side.get3DDataValue();
                if ((renderMask & 1 << index) == 0)
                    continue;
                TextureAtlasSprite sprite = look.sprites()[index];
                int argb = 0xFF000000 | look.colours()[index];
                switch (side) {
                    case DOWN -> quad(pose, buffer, sprite, argb, light, side,
                            x0, y0, z1, x0, 1 - z1, x0, y0, z0, x0, 1 - z0, x1, y0, z0, x1, 1 - z0, x1, y0, z1, x1, 1 - z1);
                    case UP -> quad(pose, buffer, sprite, argb, light, side,
                            x0, y1, z0, x0, z0, x0, y1, z1, x0, z1, x1, y1, z1, x1, z1, x1, y1, z0, x1, z0);
                    case NORTH -> quad(pose, buffer, sprite, argb, light, side,
                            x1, y1, z0, 1 - x1, 1 - y1, x1, y0, z0, 1 - x1, 1 - y0, x0, y0, z0, 1 - x0, 1 - y0,
                            x0, y1, z0, 1 - x0, 1 - y1);
                    case SOUTH -> quad(pose, buffer, sprite, argb, light, side,
                            x0, y1, z1, x0, 1 - y1, x0, y0, z1, x0, 1 - y0, x1, y0, z1, x1, 1 - y0, x1, y1, z1, x1, 1 - y1);
                    case WEST -> quad(pose, buffer, sprite, argb, light, side,
                            x0, y1, z0, z0, 1 - y1, x0, y0, z0, z0, 1 - y0, x0, y0, z1, z1, 1 - y0, x0, y1, z1, z1, 1 - y1);
                    case EAST -> quad(pose, buffer, sprite, argb, light, side,
                            x1, y1, z1, 1 - z1, 1 - y1, x1, y0, z1, 1 - z1, 1 - y0, x1, y0, z0, 1 - z0, 1 - y0,
                            x1, y1, z0, 1 - z0, 1 - y1);
                }
            }
        });
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, int argb, int light,
            Direction side, float ax, float ay, float az, float au, float av, float bx, float by, float bz, float bu,
            float bv, float cx, float cy, float cz, float cu, float cv, float dx, float dy, float dz, float du, float dv) {
        vertex(pose, buffer, sprite, argb, light, side, ax, ay, az, au, av);
        vertex(pose, buffer, sprite, argb, light, side, bx, by, bz, bu, bv);
        vertex(pose, buffer, sprite, argb, light, side, cx, cy, cz, cu, cv);
        vertex(pose, buffer, sprite, argb, light, side, dx, dy, dz, du, dv);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, int argb,
            int light, Direction side, float x, float y, float z, float u, float v) {
        buffer.addVertex(pose, x, y, z).setColor(argb).setUv(sprite.getU(clamp(u)), sprite.getV(clamp(v)))
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, side.getStepX(), side.getStepY(), side.getStepZ());
    }

    private static float clamp(float f) {
        return Math.max(0, Math.min(1, f));
    }
}
