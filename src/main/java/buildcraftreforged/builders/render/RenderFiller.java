/*
 * Buildcraft Reforged, 2026. Not a 4.2.2 class: in 4.2.2 the filler's stripes box was made of EntityBlock entities,
 * drawn by RenderEntityBlock. Here TileFiller keeps them in its Box as render data and this renderer draws them, as
 * RenderQuarry does for the quarry. The filler block itself is the JSON model filler_block.
 */
package buildcraftreforged.builders.render;

import buildcraftreforged.builders.TileFiller;
import buildcraftreforged.core.Box;
import buildcraftreforged.core.EntityBlock;
import buildcraftreforged.core.render.RenderEntityBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class RenderFiller implements BlockEntityRenderer<TileFiller, RenderFiller.State> {

    public static class State extends BlockEntityRenderState {
        public final List<EntityBlock> blocks = new ArrayList<>();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(TileFiller tile, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(tile, state, partialTicks, cameraPosition, breakProgress);
        state.blocks.clear();
        EntityBlock[] lasers = tile.getBox().getLasers();
        if (lasers == null)
            return;
        for (EntityBlock block : lasers) {
            if (block == null || block.isDead || block.texture == null || block.iSize <= 0 || block.jSize <= 0
                    || block.kSize <= 0)
                continue;
            EntityBlock copy = new EntityBlock(null, block.posX, block.posY, block.posZ, block.iSize, block.jSize, block.kSize);
            copy.texture = block.texture;
            copy.setBrightness(block.getBrightnessForRender());
            state.blocks.add(copy);
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
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return Minecraft.getInstance().options.getEffectiveRenderDistance() * 16;
    }

    /** The filler and its whole box, where the lasers are. */
    @Override
    public AABB getRenderBoundingBox(TileFiller tile) {
        BlockPos pos = tile.getBlockPos();
        AABB filler = new AABB(pos);
        Box box = tile.getBox();
        if (!box.isInitialized())
            return filler;
        return filler.minmax(new AABB(box.xMin, box.yMin, box.zMin, box.xMax + 1, box.yMax + 1, box.zMax + 1));
    }
}
