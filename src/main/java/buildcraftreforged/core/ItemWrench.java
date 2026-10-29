/*
 * Ported from BuildCraft 4.2.2, buildcraft/core/ItemWrench.java (no header in the original).
 * Modified by Buildcraft Reforged, 2026: onItemUseFirst takes a UseOnContext and returns an InteractionResult;
 * Block.rotateBlock becomes RotationHelper.rotateBlock; shouldPassSneakingClickToBlock becomes doesSneakBypassUse;
 * setFull3D becomes the handheld item model. The item is in the c:tools/wrench tag.
 */
package buildcraftreforged.core;

import buildcraftreforged.api.tools.IToolWrench;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;

// TODO(#21): extends ItemBuildCraft once the base classes are ported, as in 4.2.2.
public class ItemWrench extends ItemBuildCraft implements IToolWrench {

    private final Set<Class<? extends Block>> shiftRotations = new HashSet<Class<? extends Block>>();

    public ItemWrench(Item.Properties properties) {
        super(properties);
        shiftRotations.add(LeverBlock.class);
        shiftRotations.add(ButtonBlock.class);
        shiftRotations.add(ChestBlock.class);
    }

    private boolean isShiftRotation(Class<? extends Block> cls) {
        for (Class<? extends Block> shift : shiftRotations) {
            if (shift.isAssignableFrom(cls))
                return true;
        }
        return false;
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level world = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        BlockState state = world.getBlockState(pos);

        if (state.isAir() || player == null)
            return InteractionResult.PASS;

        if (player.isShiftKeyDown() != isShiftRotation(state.getBlock().getClass()))
            return InteractionResult.PASS;

        // Runs on both sides: the client predicts the rotation, the server's result wins. SUCCESS swings the arm,
        // which 4.2.2 did with player.swingItem().
        if (RotationHelper.rotateBlock(world, pos, context.getClickedFace())) {
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public boolean canWrench(Player player, InteractionHand hand, BlockPos pos) {
        return true;
    }

    @Override
    public void wrenchUsed(Player player, InteractionHand hand, BlockPos pos) {
        player.swing(hand, SwingAnimation.DEFAULT, true);
    }

    @Override
    public boolean doesSneakBypassUse(ItemStack stack, LevelReader level, BlockPos pos, Player player) {
        return true;
    }
}
