/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: a BlockEntityRenderer that copies the pipe's synced wire and gate state and
 * its flow record into a render state and submits the 4.2.2 boxes as custom geometry, one texture per box; the GL
 * state calls become the render type, the GL matrix calls the pose stack. Facades and plugs (#61), which 4.2.2 drew
 * in PipeRendererWorld, are drawn here too (FacadeRenderHelper). Pipes hold nothing any more, so renderSolids, renderFluids and renderPower draw the flow
 * record (docs/TRANSPORT.md 9.4) instead of travelling items, per-side fluid amounts and per-side power stages: item
 * icons spaced by the record's rate (with the ItemBox colour marker), a fluid column whose size follows the rate, and
 * the kinesis glow whose width follows the rate, red when overloaded. Client only.
 */
package buildcraftreforged.transport.render;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.api.transport.IPipe;
import buildcraftreforged.api.transport.IPipe.WireColor;
import buildcraftreforged.api.transport.IPipeTile.PipeType;
import buildcraftreforged.core.CoreConstants;
import buildcraftreforged.core.render.FluidRenderer;
import buildcraftreforged.core.render.RenderEntityBlock;
import buildcraftreforged.core.render.RenderEntityBlock.RenderInfo;
import buildcraftreforged.transport.Gate;
import buildcraftreforged.transport.GateVanilla;
import buildcraftreforged.transport.PipeGateHolder;
import buildcraftreforged.transport.PipeTierStats;
import buildcraftreforged.transport.TilePipe;
import buildcraftreforged.transport.flow.FlowRecord;
import buildcraftreforged.transport.flow.FlowTracker;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Locale;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jspecify.annotations.Nullable;

/* Client only. */
public class PipeRendererTESR implements BlockEntityRenderer<TilePipe, PipeRendererTESR.State> {

    public static class State extends BlockEntityRenderState {
        public final boolean[] wire = new boolean[4];
        public final boolean[] lit = new boolean[4];
        public final boolean[][] wireConnected = new boolean[4][6];
        public Gate.GateKind gateKind = Gate.GateKind.None;
        public boolean gateAutarchic;
        public boolean gateLit;
        public final boolean[] pipeConnected = new boolean[6];
        /** The flow record (docs/TRANSPORT.md 9.4); nothing is drawn for it unless {@code flowing}. */
        public boolean flowing;
        public PipeType kind = PipeType.ITEM;
        public int entrySide = FlowRecord.CENTRE;
        public int exitSide = FlowRecord.CENTRE;
        /** Ticks since the shown flow started, with the partial tick. */
        public float flowTime;
        /** The record's rate over the tier's cap, 0 to 1 (fluids and power). */
        public float ratio;
        /** Items: ticks between two icons. */
        public float itemInterval;
        public final ItemStackRenderState item = new ItemStackRenderState();
        /** Items: the colour marker's RGB, or -1 for none. */
        public int itemColour = -1;
        public @Nullable TextureAtlasSprite fluidStill;
        public @Nullable TextureAtlasSprite fluidFlowing;
        public int fluidColour = 0xFFFFFF;
        public boolean overload;
        /** Facades and plugs (#61), by side. */
        public final FacadeRenderHelper.@Nullable FacadeLook[] facades = new FacadeRenderHelper.FacadeLook[6];
        public final boolean[] plugs = new boolean[6];
    }

    /** At most this many item icons per pipe (docs/TRANSPORT.md 9.4). */
    public static final int MAX_ITEM_ICONS = 8;
    /** Ticks an item icon takes to cross a pipe. */
    public static final float ITEM_CROSSING_TICKS = 10;
    /** The shortest gap between two item icons, in ticks. */
    public static final float MIN_ITEM_INTERVAL = 2;
    private static final int FULL_BRIGHT = 0xF000F0;
    /** 4.2.2 PipeIconProvider Power_Normal and Power_Overload, and ItemBox. */
    private static final Identifier POWER_NORMAL = Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID,
            "textures/pipe/texture_cyan.png");
    private static final Identifier POWER_OVERLOAD = Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID,
            "textures/pipe/texture_red_lit.png");
    private static final Identifier ITEM_BOX = Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID,
            "textures/pipe/item_box.png");

    private final @Nullable ItemModelResolver itemModelResolver;

    public PipeRendererTESR(BlockEntityRendererProvider.Context context) {
        itemModelResolver = context.itemModelResolver();
    }

    private static final Identifier[] WIRE_DARK = new Identifier[4];
    private static final Identifier[] WIRE_LIT = new Identifier[4];

    static {
        // 4.2.2 WireIconProvider: texture_<colour>_dark and texture_<colour>_lit.
        for (WireColor color : WireColor.values()) {
            String name = "textures/pipe/texture_" + color.name().toLowerCase(Locale.ENGLISH);
            WIRE_DARK[color.ordinal()] = Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, name + "_dark.png");
            WIRE_LIT[color.ordinal()] = Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, name + "_lit.png");
        }
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(TilePipe pipe, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(pipe, state, partialTicks, cameraPosition, breakProgress);
        PipeGateHolder holder = pipe.gateHolder;
        for (WireColor color : WireColor.values()) {
            int c = color.ordinal();
            state.wire[c] = holder.hasWire(color);
            state.lit[c] = holder.isWireLit(color);
            for (Direction side : Direction.values()) {
                state.wireConnected[c][side.get3DDataValue()] = holder.isWireConnected(color, side);
            }
        }
        state.gateKind = holder.getRenderedGateKind();
        state.gateAutarchic = holder.isRenderedGateAutarchic();
        state.gateLit = holder.isRenderedGateLit();
        for (Direction side : Direction.values()) {
            state.pipeConnected[side.get3DDataValue()] = pipe.isPipeConnected(side);
            BlockState facade = pipe.getFacade(side);
            state.facades[side.get3DDataValue()] = facade == null ? null : FacadeRenderHelper.look(facade);
            state.plugs[side.get3DDataValue()] = pipe.hasPlug(side);
        }
        extractFlow(pipe, state, partialTicks);
    }

    /** Copies the client's flow record (docs/TRANSPORT.md 9.4); a pipe with {@code rate == 0} draws no flow. */
    private void extractFlow(TilePipe pipe, State state, float partialTicks) {
        FlowTracker tracker = pipe.getFlowTracker();
        FlowRecord record = tracker.client();
        state.flowing = record.isFlowing() && record.kind() == pipe.kind && pipe.getLevel() != null;
        state.item.clear();
        if (!state.flowing)
            return;
        state.kind = record.kind();
        state.entrySide = record.entrySide();
        state.exitSide = record.exitSide();
        state.flowTime = pipe.getLevel().getGameTime() - tracker.clientSince() + partialTicks;
        PipeTierStats stats = pipe.getTierStats();
        switch (record.kind()) {
            case ITEM -> {
                state.itemInterval = itemInterval(record.rate());
                if (itemModelResolver != null && record.content() != null && record.content().value() instanceof Item item)
                    itemModelResolver.updateForTopItem(state.item, new ItemStack(item), ItemDisplayContext.GROUND,
                            pipe.getLevel(), null, (int) pipe.getBlockPos().asLong());
                state.itemColour = record.colour() < FlowRecord.NO_COLOUR
                        ? DyeColor.byId(record.colour()).getTextureDiffuseColor() & 0xFFFFFF : -1;
                state.flowing = !state.item.isEmpty();
            }
            case FLUID -> {
                state.ratio = Mth.clamp(record.rate() / Math.max(1, stats.fluidPerTick()), 0, 1);
                FluidStack fluid = record.content() != null && record.content().value() instanceof Fluid f
                        ? new FluidStack(f, 1) : FluidStack.EMPTY;
                state.fluidStill = FluidRenderer.getFluidTexture(fluid, false);
                state.fluidFlowing = FluidRenderer.getFluidTexture(fluid, true);
                state.fluidColour = FluidRenderer.getColorForFluidStack(fluid);
                state.flowing = state.fluidStill != null;
            }
            case POWER -> {
                state.ratio = Mth.clamp(record.rate() / Math.max(1, stats.powerPerTick()), 0, 1);
                state.overload = record.overload();
            }
            default -> state.flowing = false;
        }
    }

    /** Ticks between two item icons at {@code rate} items per second: {@code max(1 / rate s, 2 ticks)}. */
    public static float itemInterval(float rate) {
        return rate <= 0 ? Float.POSITIVE_INFINITY : Math.max(20 / rate, MIN_ITEM_INTERVAL);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.flowing) {
            switch (state.kind) {
                case ITEM -> renderSolids(state, poseStack, collector);
                case FLUID -> renderFluids(state, poseStack, collector);
                case POWER -> renderPower(state, poseStack, collector);
                default -> {
                }
            }
        }
        renderGatesWires(state, poseStack, collector);
        FacadeRenderHelper.pipeFacadeRenderer(poseStack, collector, state.facades, state.pipeConnected, state.lightCoords);
        FacadeRenderHelper.pipePlugRenderer(poseStack, collector, state.plugs, state.lightCoords);
    }

    /** The point where side {@code side} (0-5) meets the pipe's face, or the centre for {@link FlowRecord#CENTRE}. */
    private static Vec3 sidePoint(int side) {
        if (side >= 6)
            return new Vec3(0.5, 0.5, 0.5);
        Direction direction = Direction.from3DDataValue(side);
        return new Vec3(0.5 + 0.5 * direction.getStepX(), 0.5 + 0.5 * direction.getStepY(),
                0.5 + 0.5 * direction.getStepZ());
    }

    /** Where an icon is {@code progress} (0 to 1) of the way through: entry face, centre, exit face. */
    private static Vec3 itemPosition(State state, float progress) {
        Vec3 centre = new Vec3(0.5, 0.5, 0.5);
        if (progress < 0.5F)
            return sidePoint(state.entrySide).lerp(centre, progress * 2);
        return centre.lerp(sidePoint(state.exitSide), (progress - 0.5F) * 2);
    }

    /**
     * Item icons, the newest first: icon {@code j} entered at {@code j × interval} ticks after the flow started and
     * takes {@link #ITEM_CROSSING_TICKS} to cross, so the spacing follows the rate (4.2.2 doRenderItem, scale 0.7, with
     * the ItemBox marker in the walk colour).
     */
    private void renderSolids(State state, PoseStack poseStack, SubmitNodeCollector collector) {
        int newest = (int) Math.floor(state.flowTime / state.itemInterval);
        for (int j = newest, drawn = 0; j >= 0 && drawn < MAX_ITEM_ICONS; j--, drawn++) {
            float age = state.flowTime - j * state.itemInterval;
            if (age >= ITEM_CROSSING_TICKS)
                break;
            Vec3 at = itemPosition(state, age / ITEM_CROSSING_TICKS);
            poseStack.pushPose();
            poseStack.translate(at.x, at.y, at.z);
            if (state.itemColour >= 0) {
                float half = 4 / 16F;
                submitColouredBox(poseStack, collector, RenderTypes.entityTranslucent(ITEM_BOX), 0, 1, 0, 1,
                        state.itemColour, state.lightCoords, -half, -half, -half, half, half, half);
            }
            // The ground model stands on its origin; lower it so the icon sits on the pipe's axis.
            poseStack.translate(0, -0.15F, 0);
            float scale = 0.7F;
            poseStack.scale(scale, scale, scale);
            state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
    }

    /**
     * A column of the fluid from the entry face through the centre to the exit face (4.2.2 renderFluids lists, sized by
     * the rate instead of the amount): {@code 2 + 6 × ratio} pixels of the 8-pixel inner pipe. Horizontal arms fill
     * from the bottom as 4.2.2 sideHorizontal; vertical arms are a centred square as sideVertical. The arms use the
     * flowing texture, the centre the still one.
     */
    private void renderFluids(State state, PoseStack poseStack, SubmitNodeCollector collector) {
        float min = CoreConstants.PIPE_MIN_POS + 0.01F;
        float max = CoreConstants.PIPE_MAX_POS - 0.01F;
        float fill = (2 + 6 * state.ratio) / 8F;
        float height = (max - min) * fill;
        float half = (max - min) / 2 * fill;
        boolean vertical = false;
        boolean horizontal = false;
        TextureAtlasSprite flowing = state.fluidFlowing != null ? state.fluidFlowing : state.fluidStill;
        for (int side : new int[]{state.entrySide, state.exitSide}) {
            if (side >= 6)
                continue;
            Direction direction = Direction.from3DDataValue(side);
            float[] box;
            if (direction.getAxis() == Direction.Axis.Y) {
                vertical = true;
                box = new float[]{0.5F - half, 0, 0.5F - half, 0.5F + half, CoreConstants.PIPE_MIN_POS, 0.5F + half};
            } else {
                horizontal = true;
                box = new float[]{min, min, min, max, min + height, max};
            }
            armBounds(box, direction);
            submitFluidBox(poseStack, collector, flowing, state, box);
        }
        if (vertical)
            submitFluidBox(poseStack, collector, state.fluidStill, state,
                    new float[]{0.5F - half, min, 0.5F - half, 0.5F + half, max, 0.5F + half});
        if (horizontal || !vertical)
            submitFluidBox(poseStack, collector, state.fluidStill, state, new float[]{min, min, min, max, min + height, max});
    }

    /**
     * Turns {@code box}, a cross-section in the arm's two other axes, into the arm from the face of {@code side} to the
     * pipe's inner wall, along that side's axis.
     */
    private static void armBounds(float[] box, Direction side) {
        int axis = side.getAxis().ordinal(); // x 0, y 1, z 2
        boolean positive = side.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        box[axis] = positive ? CoreConstants.PIPE_MAX_POS : 0;
        box[axis + 3] = positive ? 1 : CoreConstants.PIPE_MIN_POS;
    }

    private static void submitFluidBox(PoseStack poseStack, SubmitNodeCollector collector,
            @Nullable TextureAtlasSprite sprite, State state, float[] box) {
        if (sprite == null)
            return;
        submitColouredBox(poseStack, collector, RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS),
                sprite.getU0(), sprite.getU1(), sprite.getV0(), sprite.getV1(), state.fluidColour, state.lightCoords,
                box[0], box[1], box[2], box[3], box[4], box[5]);
    }

    /**
     * The kinesis glow (4.2.2 renderPower, full bright): from the entry face through the centre to the exit face,
     * {@code 1 + 7 × ratio} pixels wide, in Power_Normal, or flickering to Power_Overload when the pipe is overloaded.
     */
    private void renderPower(State state, PoseStack poseStack, SubmitNodeCollector collector) {
        float half = (1 + 7 * state.ratio) / 16F / 2;
        boolean red = state.overload && ((int) state.flowTime / 4 & 1) == 0;
        Identifier texture = red ? POWER_OVERLOAD : POWER_NORMAL;
        RenderInfo box = new RenderInfo();
        box.brightness = FULL_BRIGHT;
        box.setBounds(0.5F - half, 0.5F - half, 0.5F - half, 0.5F + half, 0.5F + half, 0.5F + half);
        submitBox(poseStack, collector, box, texture);
        for (int side : new int[]{state.entrySide, state.exitSide}) {
            if (side >= 6)
                continue;
            Direction direction = Direction.from3DDataValue(side);
            float[] bounds = {0.5F - half, 0.5F - half, 0.5F - half, 0.5F + half, 0.5F + half, 0.5F + half};
            int axis = direction.getAxis().ordinal();
            if (direction.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
                bounds[axis] = 0.5F + half;
                bounds[axis + 3] = 1;
            } else {
                bounds[axis] = 0;
                bounds[axis + 3] = 0.5F - half;
            }
            box.setBounds(bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5]);
            submitBox(poseStack, collector, box, texture);
        }
    }

    /**
     * A box in one colour (RGB), its UVs taken from its bounds as RenderEntityBlock does and mapped into
     * {@code [u0, u1] × [v0, v1]} (a whole texture, or one atlas sprite).
     */
    private static void submitColouredBox(PoseStack poseStack, SubmitNodeCollector collector,
            RenderType renderType, float u0, float u1, float v0, float v1,
            int rgb, int light, float x0, float y0, float z0, float x1, float y1, float z1) {
        int argb = 0xFF000000 | rgb;
        collector.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> {
            UvMap uv = new UvMap(u0, u1, v0, v1);
            float fx0 = frac(x0), fx1 = frac(x1), fy0 = frac(y0), fy1 = frac(y1), fz0 = frac(z0), fz1 = frac(z1);
            // Down, up, north, south, west, east, as RenderEntityBlock.renderBlock.
            quad(pose, buffer, uv, argb, light, 0, -1, 0, x0, y0, z1, fx0, fz1, x0, y0, z0, fx0, fz0, x1, y0, z0, fx1, fz0,
                    x1, y0, z1, fx1, fz1);
            quad(pose, buffer, uv, argb, light, 0, 1, 0, x1, y1, z1, fx1, fz1, x1, y1, z0, fx1, fz0, x0, y1, z0, fx0, fz0,
                    x0, y1, z1, fx0, fz1);
            quad(pose, buffer, uv, argb, light, 0, 0, -1, x0, y1, z0, 1 - fx0, 1 - fy1, x1, y1, z0, 1 - fx1, 1 - fy1,
                    x1, y0, z0, 1 - fx1, 1 - fy0, x0, y0, z0, 1 - fx0, 1 - fy0);
            quad(pose, buffer, uv, argb, light, 0, 0, 1, x0, y1, z1, fx0, 1 - fy1, x0, y0, z1, fx0, 1 - fy0, x1, y0, z1,
                    fx1, 1 - fy0, x1, y1, z1, fx1, 1 - fy1);
            quad(pose, buffer, uv, argb, light, -1, 0, 0, x0, y1, z1, fz1, 1 - fy1, x0, y1, z0, fz0, 1 - fy1, x0, y0, z0,
                    fz0, 1 - fy0, x0, y0, z1, fz1, 1 - fy0);
            quad(pose, buffer, uv, argb, light, 1, 0, 0, x1, y0, z1, 1 - fz1, 1 - fy0, x1, y0, z0, 1 - fz0, 1 - fy0,
                    x1, y1, z0, 1 - fz0, 1 - fy1, x1, y1, z1, 1 - fz1, 1 - fy1);
        });
    }

    /** A coordinate's place in its block, for UVs: boxes around an item icon reach below 0. */
    private static float frac(float value) {
        return value - (float) Math.floor(value);
    }

    private record UvMap(float u0, float u1, float v0, float v1) {
        float u(float f) {
            return u0 + (u1 - u0) * Mth.clamp(f, 0, 1);
        }

        float v(float f) {
            return v0 + (v1 - v0) * Mth.clamp(f, 0, 1);
        }
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, UvMap uv, int argb, int light, float nx,
            float ny, float nz, float ax, float ay, float az, float au, float av, float bx, float by, float bz, float bu,
            float bv, float cx, float cy, float cz, float cu, float cv, float dx, float dy, float dz, float du, float dv) {
        vertex(pose, buffer, uv, argb, light, nx, ny, nz, ax, ay, az, au, av);
        vertex(pose, buffer, uv, argb, light, nx, ny, nz, bx, by, bz, bu, bv);
        vertex(pose, buffer, uv, argb, light, nx, ny, nz, cx, cy, cz, cu, cv);
        vertex(pose, buffer, uv, argb, light, nx, ny, nz, dx, dy, dz, du, dv);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, UvMap uv, int argb, int light, float nx,
            float ny, float nz, float x, float y, float z, float u, float v) {
        buffer.addVertex(pose, x, y, z).setColor(argb).setUv(uv.u(u), uv.v(v)).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, nx, ny, nz);
    }

    private void renderGatesWires(State state, PoseStack poseStack, SubmitNodeCollector collector) {
        if (state.wire[WireColor.Red.ordinal()]) {
            pipeWireRender(state, poseStack, collector, CoreConstants.PIPE_MIN_POS, CoreConstants.PIPE_MAX_POS, CoreConstants.PIPE_MIN_POS, IPipe.WireColor.Red);
        }

        if (state.wire[WireColor.Blue.ordinal()]) {
            pipeWireRender(state, poseStack, collector, CoreConstants.PIPE_MAX_POS, CoreConstants.PIPE_MAX_POS, CoreConstants.PIPE_MAX_POS, IPipe.WireColor.Blue);
        }

        if (state.wire[WireColor.Green.ordinal()]) {
            pipeWireRender(state, poseStack, collector, CoreConstants.PIPE_MAX_POS, CoreConstants.PIPE_MIN_POS, CoreConstants.PIPE_MIN_POS, IPipe.WireColor.Green);
        }

        if (state.wire[WireColor.Yellow.ordinal()]) {
            pipeWireRender(state, poseStack, collector, CoreConstants.PIPE_MIN_POS, CoreConstants.PIPE_MIN_POS, CoreConstants.PIPE_MAX_POS, IPipe.WireColor.Yellow);
        }

        if (state.gateKind != Gate.GateKind.None) {
            pipeGateRender(state, poseStack, collector);
        }
    }

    private static boolean isWireConnected(State state, WireColor color, Direction side) {
        return state.wireConnected[color.ordinal()][side.get3DDataValue()];
    }

    private void pipeWireRender(State state, PoseStack poseStack, SubmitNodeCollector collector, float cx, float cy,
            float cz, IPipe.WireColor color) {

        float minX = CoreConstants.PIPE_MIN_POS;
        float minY = CoreConstants.PIPE_MIN_POS;
        float minZ = CoreConstants.PIPE_MIN_POS;

        float maxX = CoreConstants.PIPE_MAX_POS;
        float maxY = CoreConstants.PIPE_MAX_POS;
        float maxZ = CoreConstants.PIPE_MAX_POS;

        boolean foundX = false, foundY = false, foundZ = false;

        if (isWireConnected(state, color, Direction.WEST)) {
            minX = 0;
            foundX = true;
        }

        if (isWireConnected(state, color, Direction.EAST)) {
            maxX = 1;
            foundX = true;
        }

        if (isWireConnected(state, color, Direction.DOWN)) {
            minY = 0;
            foundY = true;
        }

        if (isWireConnected(state, color, Direction.UP)) {
            maxY = 1;
            foundY = true;
        }

        if (isWireConnected(state, color, Direction.NORTH)) {
            minZ = 0;
            foundZ = true;
        }

        if (isWireConnected(state, color, Direction.SOUTH)) {
            maxZ = 1;
            foundZ = true;
        }

        boolean center = false;

        if (minX == 0 && maxX != 1 && (foundY || foundZ))
            if (cx == CoreConstants.PIPE_MIN_POS) {
                maxX = CoreConstants.PIPE_MIN_POS;
            } else {
                center = true;
            }

        if (minX != 0 && maxX == 1 && (foundY || foundZ))
            if (cx == CoreConstants.PIPE_MAX_POS) {
                minX = CoreConstants.PIPE_MAX_POS;
            } else {
                center = true;
            }

        if (minY == 0 && maxY != 1 && (foundX || foundZ))
            if (cy == CoreConstants.PIPE_MIN_POS) {
                maxY = CoreConstants.PIPE_MIN_POS;
            } else {
                center = true;
            }

        if (minY != 0 && maxY == 1 && (foundX || foundZ))
            if (cy == CoreConstants.PIPE_MAX_POS) {
                minY = CoreConstants.PIPE_MAX_POS;
            } else {
                center = true;
            }

        if (minZ == 0 && maxZ != 1 && (foundX || foundY))
            if (cz == CoreConstants.PIPE_MIN_POS) {
                maxZ = CoreConstants.PIPE_MIN_POS;
            } else {
                center = true;
            }

        if (minZ != 0 && maxZ == 1 && (foundX || foundY))
            if (cz == CoreConstants.PIPE_MAX_POS) {
                minZ = CoreConstants.PIPE_MAX_POS;
            } else {
                center = true;
            }

        boolean found = foundX || foundY || foundZ;

        poseStack.pushPose();

        float scale = 1.001f;
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.scale(scale, scale, scale);
        poseStack.translate(-0.5F, -0.5F, -0.5F);

        RenderInfo box = new RenderInfo();
        box.brightness = state.lightCoords;
        Identifier texture = state.lit[color.ordinal()] ? WIRE_LIT[color.ordinal()] : WIRE_DARK[color.ordinal()];

        // Z render

        if (minZ != CoreConstants.PIPE_MIN_POS || maxZ != CoreConstants.PIPE_MAX_POS || !found) {
            box.setBounds(cx == CoreConstants.PIPE_MIN_POS ? cx - 0.05F : cx, cy == CoreConstants.PIPE_MIN_POS ? cy - 0.05F : cy, minZ, cx == CoreConstants.PIPE_MIN_POS ? cx
                    : cx + 0.05F, cy == CoreConstants.PIPE_MIN_POS ? cy : cy + 0.05F, maxZ);
            submitBox(poseStack, collector, box, texture);
        }

        // X render

        if (minX != CoreConstants.PIPE_MIN_POS || maxX != CoreConstants.PIPE_MAX_POS || !found) {
            box.setBounds(minX, cy == CoreConstants.PIPE_MIN_POS ? cy - 0.05F : cy, cz == CoreConstants.PIPE_MIN_POS ? cz - 0.05F : cz, maxX, cy == CoreConstants.PIPE_MIN_POS ? cy
                    : cy + 0.05F, cz == CoreConstants.PIPE_MIN_POS ? cz : cz + 0.05F);
            submitBox(poseStack, collector, box, texture);
        }

        // Y render

        if (minY != CoreConstants.PIPE_MIN_POS || maxY != CoreConstants.PIPE_MAX_POS || !found) {
            box.setBounds(cx == CoreConstants.PIPE_MIN_POS ? cx - 0.05F : cx, minY, cz == CoreConstants.PIPE_MIN_POS ? cz - 0.05F : cz, cx == CoreConstants.PIPE_MIN_POS ? cx
                    : cx + 0.05F, maxY, cz == CoreConstants.PIPE_MIN_POS ? cz : cz + 0.05F);
            submitBox(poseStack, collector, box, texture);
        }

        if (center || !found) {
            box.setBounds(cx == CoreConstants.PIPE_MIN_POS ? cx - 0.05F : cx, cy == CoreConstants.PIPE_MIN_POS ? cy - 0.05F : cy, cz == CoreConstants.PIPE_MIN_POS ? cz - 0.05F : cz,
                    cx == CoreConstants.PIPE_MIN_POS ? cx : cx + 0.05F, cy == CoreConstants.PIPE_MIN_POS ? cy : cy + 0.05F, cz == CoreConstants.PIPE_MIN_POS ? cz : cz + 0.05F);
            submitBox(poseStack, collector, box, texture);
        }

        poseStack.popPose();
    }

    private void pipeGateRender(State state, PoseStack poseStack, SubmitNodeCollector collector) {
        float min = CoreConstants.PIPE_MIN_POS + 0.05F;
        float max = CoreConstants.PIPE_MAX_POS - 0.05F;

        RenderInfo box = new RenderInfo();
        box.brightness = state.lightCoords;
        Identifier texture = GateVanilla.texture(state.gateKind, state.gateAutarchic, state.gateLit);

        if (shouldRenderNormalPipeSide(state, Direction.WEST)) {
            box.setBounds(CoreConstants.PIPE_MIN_POS - 0.10F, min, min, CoreConstants.PIPE_MIN_POS + 0.001F, max, max);
            submitBox(poseStack, collector, box, texture);
        }

        if (shouldRenderNormalPipeSide(state, Direction.EAST)) {
            box.setBounds(CoreConstants.PIPE_MAX_POS + 0.001F, min, min, CoreConstants.PIPE_MAX_POS + 0.10F, max, max);
            submitBox(poseStack, collector, box, texture);
        }

        if (shouldRenderNormalPipeSide(state, Direction.DOWN)) {
            box.setBounds(min, CoreConstants.PIPE_MIN_POS - 0.10F, min, max, CoreConstants.PIPE_MIN_POS + 0.001F, max);
            submitBox(poseStack, collector, box, texture);
        }

        if (shouldRenderNormalPipeSide(state, Direction.UP)) {
            box.setBounds(min, CoreConstants.PIPE_MAX_POS + 0.001F, min, max, CoreConstants.PIPE_MAX_POS + 0.10F, max);
            submitBox(poseStack, collector, box, texture);
        }

        if (shouldRenderNormalPipeSide(state, Direction.NORTH)) {
            box.setBounds(min, min, CoreConstants.PIPE_MIN_POS - 0.10F, max, max, CoreConstants.PIPE_MIN_POS + 0.001F);
            submitBox(poseStack, collector, box, texture);
        }

        if (shouldRenderNormalPipeSide(state, Direction.SOUTH)) {
            box.setBounds(min, min, CoreConstants.PIPE_MAX_POS + 0.001F, max, max, CoreConstants.PIPE_MAX_POS + 0.10F);
            submitBox(poseStack, collector, box, texture);
        }
    }

    /** One box with one texture; the box's bounds are copied, since the callback runs later. */
    private static void submitBox(PoseStack poseStack, SubmitNodeCollector collector, RenderInfo box,
            Identifier texture) {
        RenderInfo copy = new RenderInfo();
        copy.setBounds(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
        copy.brightness = box.brightness;
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(texture),
                (pose, buffer) -> RenderEntityBlock.INSTANCE.renderBlock(copy, pose, buffer));
    }

    private boolean shouldRenderNormalPipeSide(State state, Direction direction) {
        return !state.pipeConnected[direction.get3DDataValue()] && state.facades[direction.get3DDataValue()] == null
                && !state.plugs[direction.get3DDataValue()] && !isOpenOrientation(state, direction);
    }

    public boolean isOpenOrientation(State state, Direction direction) {
        int connections = 0;

        Direction targetOrientation = null;

        for (Direction o : Direction.values()) {
            if (state.pipeConnected[o.get3DDataValue()]) {

                connections++;

                if (connections == 1)
                    targetOrientation = o;
            }
        }

        if (connections > 1 || connections == 0)
            return false;

        return targetOrientation.getOpposite() == direction;
    }
}
