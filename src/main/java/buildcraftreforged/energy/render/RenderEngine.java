/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: TileEntitySpecialRenderer becomes a BlockEntityRenderer that copies the stage,
 * progress, orientation and base texture into a render state; the ModelRenderer boxes become baked ModelParts submitted
 * in an entity-solid render type (4.2.2: lighting and face culling on, blending off), with the rotation about the block
 * centre on the pose stack instead of on the shared parts, because submissions are drawn later; the GL matrix calls
 * become the pose stack; the breaking overlay is added; IInventoryRenderer becomes the ItemRenderer special model
 * renderer ("buildcraftreforged:engine", the base texture from the item model). The NoDynamic render mode check goes:
 * 4.2.2 set it only on dedicated servers, which never render. Client only.
 */
package buildcraftreforged.energy.render;

import buildcraftreforged.core.DefaultProps;
import buildcraftreforged.energy.TileEngine;
import buildcraftreforged.energy.TileEngine.EnergyStage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.function.Consumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Quaternionf;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class RenderEngine implements BlockEntityRenderer<TileEngine, RenderEngine.State> {

    public static final Identifier CHAMBER_TEXTURE = texture("chamber");
    public static final Identifier TRUNK_BLUE_TEXTURE = texture("trunk_blue");
    public static final Identifier TRUNK_GREEN_TEXTURE = texture("trunk_green");
    public static final Identifier TRUNK_YELLOW_TEXTURE = texture("trunk_yellow");
    public static final Identifier TRUNK_RED_TEXTURE = texture("trunk_red");
    private final ModelPart box;
    private final ModelPart trunk;
    private final ModelPart movingBox;
    private final ModelPart chamber;
    private Identifier baseTexture = TileEngine.WOOD_TEXTURE;
    private static final float[] angleMap = new float[6];

    static {
        angleMap[Direction.EAST.ordinal()] = (float) -Math.PI / 2;
        angleMap[Direction.WEST.ordinal()] = (float) Math.PI / 2;
        angleMap[Direction.UP.ordinal()] = 0;
        angleMap[Direction.DOWN.ordinal()] = (float) Math.PI;
        angleMap[Direction.SOUTH.ordinal()] = (float) Math.PI / 2;
        angleMap[Direction.NORTH.ordinal()] = (float) -Math.PI / 2;
    }

    /** What the renderer reads from the engine: the synced stage, the client-side progress and the facing. */
    public static class State extends BlockEntityRenderState {
        public EnergyStage energyStage = EnergyStage.BLUE;
        public float progress;
        public Direction orientation = Direction.UP;
        public Identifier baseTexture = TileEngine.WOOD_TEXTURE;
    }

    public RenderEngine() {

        // constructor: ModelBase's default texture size, 64 x 32; the rotation point (8, 8, 8) is applied in render.
        box = box(0, 1, -8F, -8F, -8F, 16, 4, 16);

        trunk = box(1, 1, -4F, -4F, -4F, 8, 12, 8);

        movingBox = box(0, 1, -8F, -4, -8F, 16, 4, 16);

        chamber = box(1, 1, -5F, -4, -5F, 10, 2, 10);
    }

    public RenderEngine(Identifier baseTexture) {
        this();
        this.baseTexture = baseTexture;
    }

    private static ModelPart box(int textureOffsetX, int textureOffsetY, float x, float y, float z, int width, int height, int depth) {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("box", CubeListBuilder.create().texOffs(textureOffsetX, textureOffsetY)
                .addBox(x, y, z, width, height, depth), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32).bakeRoot().getChild("box");
    }

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath(DefaultProps.MOD, "textures/block/" + name + ".png");
    }

    public void inventoryRender(PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay) {
        render(EnergyStage.BLUE, 0.25F, Direction.UP, baseTexture, poseStack, collector, light, overlay, null);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(TileEngine engine, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(engine, state, partialTicks, cameraPosition, breakProgress);
        state.energyStage = engine.getEnergyStage();
        state.progress = engine.progress;
        state.orientation = engine.getOrientation();
        state.baseTexture = engine.getTextureFile();
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        render(state.energyStage, state.progress, state.orientation, state.baseTexture, poseStack, collector, state.lightCoords,
                OverlayTexture.NO_OVERLAY, state.breakProgress);
    }

    private void render(EnergyStage energy, float progress, Direction orientation, Identifier baseTexture, PoseStack poseStack,
            SubmitNodeCollector collector, int light, int overlay, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {

        poseStack.pushPose();

        float step;

        if (progress > 0.5) {
            step = 7.99F - (progress - 0.5F) * 2F * 7.99F;
        } else {
            step = progress * 2F * 7.99F;
        }

        float translatefact = step / 16;

        float[] angle = { 0, 0, 0 };
        float[] translate = { orientation.getStepX(), orientation.getStepY(), orientation.getStepZ() };

        switch (orientation) {
            case EAST:
            case WEST:
            case DOWN:
                angle[2] = angleMap[orientation.ordinal()];
                break;
            case SOUTH:
            case NORTH:
            default:
                angle[0] = angleMap[orientation.ordinal()];
                break;
        }

        RenderType base = RenderTypes.entitySolid(baseTexture);

        renderPart(box, angle, base, poseStack, collector, light, overlay, breakProgress);

        poseStack.translate(translate[0] * translatefact, translate[1] * translatefact, translate[2] * translatefact);
        renderPart(movingBox, angle, base, poseStack, collector, light, overlay, breakProgress);
        poseStack.translate(-translate[0] * translatefact, -translate[1] * translatefact, -translate[2] * translatefact);

        RenderType chamberType = RenderTypes.entitySolid(CHAMBER_TEXTURE);

        float chamberf = 2F / 16F;

        poseStack.pushPose();
        for (int i = 0; i <= step + 2; i += 2) {
            renderPart(chamber, angle, chamberType, poseStack, collector, light, overlay, breakProgress);
            poseStack.translate(translate[0] * chamberf, translate[1] * chamberf, translate[2] * chamberf);
        }
        // 4.2.2 translated back once per chamber drawn.
        poseStack.popPose();

        Identifier texture;

        switch (energy) {
            case BLUE:
                texture = TRUNK_BLUE_TEXTURE;
                break;
            case GREEN:
                texture = TRUNK_GREEN_TEXTURE;
                break;
            case YELLOW:
                texture = TRUNK_YELLOW_TEXTURE;
                break;
            default:
                texture = TRUNK_RED_TEXTURE;
                break;
        }

        renderPart(trunk, angle, RenderTypes.entitySolid(texture), poseStack, collector, light, overlay, breakProgress);

        poseStack.popPose();
    }

    /**
     * 4.2.2 ModelRenderer.render(1/16) with rotationPoint (8, 8, 8) and rotateAngleX/Y/Z: translate to the block centre,
     * rotate Z, then Y, then X, draw the box.
     */
    private static void renderPart(ModelPart part, float[] angle, RenderType renderType, PoseStack poseStack,
            SubmitNodeCollector collector, int light, int overlay, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.rotate(new Quaternionf().rotationZYX(angle[2], angle[1], angle[0]));
        collector.submitModelPart(part, poseStack, renderType, light, overlay, null);
        if (breakProgress != null) {
            collector.order(1).submitCrumblingOverlay(part, poseStack, renderType, light, overlay, -1, breakProgress);
        }
        poseStack.popPose();
    }

    /**
     * The item render (4.2.2 IInventoryRenderer, registered per engine in EnergyProxyClient): a cold engine facing up with
     * the piston a quarter of the way out. The item model names the base texture:
     * {"type": "buildcraftreforged:engine", "texture": "buildcraftreforged:textures/block/base_wood.png"}.
     */
    public static class ItemRenderer implements NoDataSpecialModelRenderer {

        private final RenderEngine render;

        public ItemRenderer(Identifier baseTexture) {
            this.render = new RenderEngine(baseTexture);
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, int overlayCoords, boolean hasFoil,
                int outlineColor) {
            render.inventoryRender(poseStack, collector, lightCoords, overlayCoords);
        }

        /** The engine fills the block: the base, the trunk and the piston stay inside it. */
        @Override
        public void getExtents(Consumer<Vector3fc> output) {
            PoseStack poseStack = new PoseStack();
            poseStack.translate(0.5F, 0.5F, 0.5F);
            render.box.getExtentsForGui(poseStack, output);
            render.trunk.getExtentsForGui(poseStack, output);
        }

        public record Unbaked(Identifier texture) implements NoDataSpecialModelRenderer.Unbaked {
            public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(
                    i -> i.group(Identifier.CODEC.fieldOf("texture").forGetter(Unbaked::texture)).apply(i, Unbaked::new));

            @Override
            public MapCodec<Unbaked> type() {
                return MAP_CODEC;
            }

            @Override
            public ItemRenderer bake(SpecialModelRenderer.BakingContext context) {
                return new ItemRenderer(texture);
            }
        }
    }
}
