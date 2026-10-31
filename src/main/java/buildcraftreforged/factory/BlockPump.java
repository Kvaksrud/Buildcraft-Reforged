/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: Material.iron, the hardness and the creative tab move to registration; the
 * icons become the JSON model pump_block; breakBlock's Utils.preDestroyBlock runs from
 * TileBuildCraft.preRemoveSideEffects; onBlockActivated becomes useItemOn (the wrench) and useWithoutItem (the status
 * panel, plan section 10, new); onNeighborBlockChange becomes neighborChanged; the status light (StatusPanel.BLOCKED)
 * is new.
 */
package buildcraftreforged.factory;

import buildcraftreforged.core.BlockBuildCraft;
import buildcraftreforged.core.WrenchHelper;
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
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public class BlockPump extends BlockBuildCraft {

    public BlockPump(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(StatusPanel.BLOCKED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(StatusPanel.BLOCKED);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TilePump(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack current, BlockState state, Level world, BlockPos pos, Player entityplayer,
            InteractionHand hand, BlockHitResult hitResult) {
        if (world.getBlockEntity(pos) instanceof TilePump pump) {

            // Drop through if the player is sneaking
            if (entityplayer.isShiftKeyDown())
                return InteractionResult.PASS;

            // Restart the quarry if its a wrench
            if (WrenchHelper.isWrench(current) && WrenchHelper.canWrench(entityplayer, hand, pos)) {

                pump.tank.reset();
                pump.rebuildQueue();
                WrenchHelper.wrenchUsed(entityplayer, hand, pos);
                return InteractionResult.SUCCESS;
            }
        }

        // 4.2.2 returned false here, so a held block is placed against the pump; an empty hand opens the status panel.
        return current.isEmpty() ? InteractionResult.TRY_WITH_EMPTY_HAND : InteractionResult.PASS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (!(world.getBlockEntity(pos) instanceof TilePump pump) || player.isShiftKeyDown())
            return InteractionResult.PASS;
        StatusPanel.open(player, pump);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level world, BlockPos pos, Block block, @Nullable Orientation orientation,
            boolean movedByPiston) {
        super.neighborChanged(state, world, pos, block, orientation, movedByPiston);
        if (world.getBlockEntity(pos) instanceof TilePump tile) {
            tile.onNeighborBlockChange();
        }
    }
}
