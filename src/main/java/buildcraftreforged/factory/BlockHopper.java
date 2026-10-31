/*
 * Ported from buildcraft/factory/BlockHopper.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: Material.iron, the hardness, the creative tab and isOpaqueCube move to
 * registration; RenderHopper's model (getRenderType blockByEntityModel) is a static JSON model; onBlockActivated becomes
 * useItemOn (a held pipe passes, so it is placed) and useWithoutItem (openMenu with the tile as the MenuProvider); the
 * red light shows a hopper whose items nothing below takes (plan section 10).
 */
package buildcraftreforged.factory;

import buildcraftreforged.core.BlockBuildCraft;
import buildcraftreforged.core.status.StatusPanel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

public class BlockHopper extends BlockBuildCraft {

    public BlockHopper(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(StatusPanel.BLOCKED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(StatusPanel.BLOCKED);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileHopper(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack current, BlockState state, Level world, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hitResult) {
        if (BlockAutoWorkbench.isPipe(current))
            return InteractionResult.PASS;
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player entityplayer,
            BlockHitResult hitResult) {

        // Drop through if the player is sneaking
        if (entityplayer.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }

        if (!world.isClientSide() && world.getBlockEntity(pos) instanceof TileHopper tile) {
            entityplayer.openMenu(tile, pos);
        }

        return InteractionResult.SUCCESS;
    }
}
