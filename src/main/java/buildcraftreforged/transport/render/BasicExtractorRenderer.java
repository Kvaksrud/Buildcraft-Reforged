package buildcraftreforged.transport.render;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.energy.TileEngine;
import buildcraftreforged.transport.BlockBasicExtractor;
import buildcraftreforged.transport.TileBasicExtractor;
import buildcraftreforged.transport.flow.FlowRecord;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Basic Extractor's suction (docs/TRANSPORT.md 10). Client only. The diagonal stripes on its three steps are drawn
 * here so they can move; idle they stand still. While an engine facing it is in the second half of its stroke (progress
 * past 0.5, where 4.2.2 {@code TileEngine} sent its power) and the extractor's flow record has a rate, the stripes
 * scroll one period from the inventory face towards the pipe face and the record's item slides from the inventory face
 * through the nozzle into the pipe, both over that half-stroke. The record arrives in the block entity's payload.
 */
public class BasicExtractorRenderer implements BlockEntityRenderer<TileBasicExtractor, BasicExtractorRenderer.State> {

    public static class State extends BlockEntityRenderState {
        public Direction facing = Direction.NORTH;
        /** How far the suction is, 0 to 1, or -1 when idle. */
        public float phase = -1;
        public final ItemStackRenderState item = new ItemStackRenderState();
    }

    private static final Identifier STRIPES = Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID,
            "textures/pipe/basic_extractor_stripes.png");
    /** The stripe texture is 32 × 16 pixels and repeats every 4 pixels along u. */
    private static final float TEXTURE_WIDTH = 32;
    private static final float STRIPE_PERIOD = 4;
    /** How far outside the step faces the stripes are drawn, in blocks. */
    private static final float LIFT = 0.002F;

    private final @Nullable ItemModelResolver itemModelResolver;

    public BasicExtractorRenderer(BlockEntityRendererProvider.Context context) {
        itemModelResolver = context.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(TileBasicExtractor tile, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(tile, state, partialTicks, cameraPosition, breakProgress);
        state.facing = tile.getBlockState().getValue(BlockBasicExtractor.FACING);
        state.phase = -1;
        state.item.clear();
        Level level = tile.getLevel();
        FlowRecord record = tile.getClientRecord();
        if (level == null || !record.isFlowing())
            return;
        state.phase = suctionPhase(tile, state.facing, partialTicks);
        if (state.phase >= 0 && itemModelResolver != null && record.content() != null
                && record.content().value() instanceof Item item)
            itemModelResolver.updateForTopItem(state.item, new ItemStack(item), ItemDisplayContext.GROUND, level, null,
                    (int) tile.getBlockPos().asLong());
    }

    /**
     * The furthest suction of the engines on the four side faces that face the extractor: 0 to 1 over the second half
     * of the stroke, -1 when none is there.
     */
    public static float suctionPhase(TileBasicExtractor tile, Direction facing, float partialTicks) {
        float phase = -1;
        for (Direction side : Direction.values()) {
            if (side.getAxis() == facing.getAxis())
                continue;
            if (tile.getLevel().getBlockEntity(tile.getBlockPos().relative(side)) instanceof TileEngine engine
                    && engine.getOrientation() == side.getOpposite()) {
                float progress = engine.progress + partialTicks * engine.getPistonSpeed();
                if (progress > 0.5F && progress <= 1)
                    phase = Math.max(phase, Mth.clamp((progress - 0.5F) * 2, 0, 1));
            }
        }
        return phase;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        float offset = state.phase < 0 ? 0 : state.phase * STRIPE_PERIOD;
        Direction facing = state.facing;
        int light = state.lightCoords;
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(STRIPES), (pose, buffer) -> {
            for (int[] step : BlockBasicExtractor.STEPS) {
                float half = step[0] / 16F + LIFT;
                float d0 = step[1] / 16F;
                float d1 = step[2] / 16F;
                // The four faces around the depth axis: across coordinate a or b held at ±half.
                for (int face = 0; face < 4; face++) {
                    float fixed = face % 2 == 0 ? -half : half;
                    boolean aFixed = face < 2;
                    // u runs along the depth, shifted by the suction; v runs across the face.
                    float u0 = (STRIPE_PERIOD - offset + step[1]) / TEXTURE_WIDTH;
                    float u1 = (STRIPE_PERIOD - offset + step[2]) / TEXTURE_WIDTH;
                    float v0 = 0;
                    float v1 = step[0] * 2 / 16F;
                    float[][] corners = new float[4][];
                    int i = 0;
                    for (float[] c : new float[][] {{d0, -1}, {d1, -1}, {d1, 1}, {d0, 1}}) {
                        float other = c[1] * half;
                        float a = aFixed ? fixed : other;
                        float b = aFixed ? other : fixed;
                        corners[i++] = point(facing, a, b, c[0]);
                    }
                    float[][] uvs = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
                    Vec3 normal = normal(facing, aFixed, fixed);
                    quad(pose, buffer, corners, uvs, light, normal, false);
                    quad(pose, buffer, corners, uvs, light, normal.scale(-1), true);
                }
            }
        });

        if (state.phase >= 0 && !state.item.isEmpty()) {
            // From just inside the inventory face to the pipe face, along the centre line.
            float[] at = point(facing, 0, 0, -0.1F + 1.1F * state.phase);
            poseStack.pushPose();
            poseStack.translate(at[0], at[1] - 0.15F, at[2]);
            poseStack.scale(0.6F, 0.6F, 0.6F);
            state.item.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
    }

    /**
     * A point of the block in block coordinates: {@code depth} blocks in from the inventory face {@code facing},
     * {@code a} and {@code b} blocks from the centre along the other two axes (in x, y, z order).
     */
    static float[] point(Direction facing, float a, float b, float depth) {
        float[] p = new float[3];
        int axis = facing.getAxis().ordinal();
        boolean positive = facing.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        p[axis] = positive ? 1 - depth : depth;
        int first = axis == 0 ? 1 : 0;
        int second = axis == 2 ? 1 : 2;
        p[first] = 0.5F + a;
        p[second] = 0.5F + b;
        return p;
    }

    private static Vec3 normal(Direction facing, boolean aFixed, float fixed) {
        int axis = facing.getAxis().ordinal();
        int first = axis == 0 ? 1 : 0;
        int second = axis == 2 ? 1 : 2;
        double[] n = new double[3];
        n[aFixed ? first : second] = Math.signum(fixed);
        return new Vec3(n[0], n[1], n[2]);
    }

    /** One quad, wound one way or the other, so both sides draw whatever the face's orientation. */
    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, float[][] corners, float[][] uvs, int light,
            Vec3 normal, boolean reversed) {
        for (int k = 0; k < 4; k++) {
            int i = reversed ? 3 - k : k;
            buffer.addVertex(pose, corners[i][0], corners[i][1], corners[i][2]).setColor(0xFFFFFFFF)
                    .setUv(uvs[i][0], uvs[i][1]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                    .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
        }
    }
}
