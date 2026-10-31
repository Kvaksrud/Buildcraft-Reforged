/*
 * Ported from buildcraft/transport/render/FacadeItemRenderer.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: an IItemRenderer with GL translations per render type becomes the special
 * item model "buildcraftreforged:facade", whose argument is the facade's block state; the base item model's display
 * transforms replace the per-type translations. The boxes are FacadeRenderHelper.renderFacadeItem. Client only.
 */
package buildcraftreforged.transport.render;

import buildcraftreforged.core.CoreConstants;
import buildcraftreforged.transport.ItemFacade;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import java.util.function.Consumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

public class FacadeItemRenderer implements SpecialModelRenderer<BlockState> {

    @Override
    public void submit(@Nullable BlockState argument, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
            int lightCoords, int overlayCoords, boolean hasFoil, int outlineColor) {
        if (argument == null)
            return;
        FacadeRenderHelper.renderFacadeItem(poseStack, submitNodeCollector, FacadeRenderHelper.look(argument),
                lightCoords);
    }

    /** The facade covers the south face; the structure pipe stub stays inside the pipe centre. */
    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        output.accept(new Vector3f(0, 0, 1 - 1F / 16F));
        output.accept(new Vector3f(1, 1, 1));
        output.accept(new Vector3f(CoreConstants.PIPE_MIN_POS, CoreConstants.PIPE_MIN_POS, CoreConstants.PIPE_MIN_POS));
    }

    @Override
    public @Nullable BlockState extractArgument(ItemStack stack) {
        return ItemFacade.getBlockState(stack);
    }

    public record Unbaked() implements SpecialModelRenderer.Unbaked<BlockState> {
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public FacadeItemRenderer bake(SpecialModelRenderer.BakingContext context) {
            return new FacadeItemRenderer();
        }
    }
}
