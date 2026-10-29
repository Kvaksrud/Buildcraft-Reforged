package buildcraftreforged.core;

import buildcraftreforged.api.tools.IToolWrench;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.neoforged.neoforge.common.Tags;

/**
 * Wrench checks for our blocks. Where 4.2.2 tested {@code item instanceof IToolWrench}, ports call these, so any item
 * in the {@code c:tools/wrench} tag (other mods' wrenches) works on our blocks as well as {@link IToolWrench} items.
 */
public final class WrenchHelper {
    private WrenchHelper() {
    }

    public static boolean isWrench(ItemStack stack) {
        return stack.getItem() instanceof IToolWrench || stack.is(Tags.Items.TOOLS_WRENCH);
    }

    /** {@link IToolWrench#canWrench} for an {@link IToolWrench}; true for any other item in the tag. */
    public static boolean canWrench(Player player, InteractionHand hand, BlockPos pos) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() instanceof IToolWrench wrench) {
            return wrench.canWrench(player, hand, pos);
        }
        return stack.is(Tags.Items.TOOLS_WRENCH);
    }

    /** {@link IToolWrench#wrenchUsed} for an {@link IToolWrench}; a swing for any other item in the tag. */
    public static void wrenchUsed(Player player, InteractionHand hand, BlockPos pos) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() instanceof IToolWrench wrench) {
            wrench.wrenchUsed(player, hand, pos);
        } else {
            player.swing(hand, SwingAnimation.DEFAULT, true);
        }
    }
}
