/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: only renderGatesWires so far (the contents renderers are #59): a
 * BlockEntityRenderer that copies the pipe's synced wire and gate state into a render state and submits the 4.2.2
 * boxes as custom geometry, one texture per box; the GL state calls become the render type, the GL matrix calls the
 * pose stack; facades and plugs (#61) are not yet in shouldRenderNormalPipeSide. Client only.
 */
package buildcraftreforged.transport.render;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.api.transport.IPipe;
import buildcraftreforged.api.transport.IPipe.WireColor;
import buildcraftreforged.core.CoreConstants;
import buildcraftreforged.core.render.RenderEntityBlock;
import buildcraftreforged.core.render.RenderEntityBlock.RenderInfo;
import buildcraftreforged.transport.Gate;
import buildcraftreforged.transport.GateVanilla;
import buildcraftreforged.transport.PipeGateHolder;
import buildcraftreforged.transport.TilePipe;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Locale;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
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
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        renderGatesWires(state, poseStack, collector);
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
        // SEAM(#61): no facade and no plug on the side.
        return !state.pipeConnected[direction.get3DDataValue()] && !isOpenOrientation(state, direction);
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
