/*
 * Ported from buildcraft/transport/ItemPlug.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: fitting and taking off (4.2.2 BlockGenericPipe.addOrStripPlug) is the
 * item's useOn; the commented-out onItemUseFirst is dropped; the unlocalised name moves to registration; icons and
 * PlugItemRenderer become a JSON item model.
 */
package buildcraftreforged.transport;

import buildcraftreforged.core.ItemBuildCraft;
import buildcraftreforged.transport.PipeParts.Part;
import buildcraftreforged.transport.PipeParts.RaytraceResult;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class ItemPlug extends ItemBuildCraft {

    public ItemPlug(Properties properties) {
        super(properties);
        setPassSneakClick(true);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().getBlockEntity(context.getClickedPos()) instanceof TilePipe pipe
                && addOrStripPlug(pipe, context.getPlayer(), context.getItemInHand(), context.getClickLocation(),
                        context.getClickedFace()))
            return InteractionResult.SUCCESS;
        return super.useOn(context);
    }

    /**
     * Sneaking on a plug takes it off; a click on the pipe or its gate plugs the side clicked (the arm's side, or for
     * the centre the face clicked).
     */
    public static boolean addOrStripPlug(TilePipe pipe, @Nullable Player player, ItemStack stack, Vec3 hit,
            Direction side) {
        RaytraceResult rayTraceResult = PipeParts.doRayTrace(pipe, hit, side);
        if (player != null && player.isShiftKeyDown()) {
            if (rayTraceResult != null && rayTraceResult.hitPart() == Part.Plug) {
                if (pipe.removeAndDropPlug(rayTraceResult.sideHit()))
                    return true;
            }
        }
        if (rayTraceResult != null && (rayTraceResult.hitPart() == Part.Pipe || rayTraceResult.hitPart() == Part.Gate)) {
            if (addPlug(player, stack, pipe, rayTraceResult.sideHit() != null ? rayTraceResult.sideHit() : side))
                return true;
        }
        return false;
    }

    private static boolean addPlug(@Nullable Player player, ItemStack stack, TilePipe pipe, Direction side) {
        if (pipe.addPlug(side)) {
            if (!pipe.getLevel().isClientSide() && (player == null || !player.getAbilities().instabuild)) {
                stack.shrink(1);
            }
            return true;
        }
        return false;
    }
}
