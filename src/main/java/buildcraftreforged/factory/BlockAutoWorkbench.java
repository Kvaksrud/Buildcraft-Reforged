/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: Material.wood, the hardness and the creative tab move to registration; the
 * icons are the JSON model; onBlockActivated becomes useItemOn (a held pipe passes, so it is placed) and
 * useWithoutItem (openMenu with the tile as the MenuProvider).
 */

package buildcraftreforged.factory;

import buildcraftreforged.core.BlockBuildCraft;
import buildcraftreforged.transport.BlockPipe;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class BlockAutoWorkbench extends BlockBuildCraft {

    public BlockAutoWorkbench(Properties properties) {
        super(properties);
    }

    /** 4.2.2: a player holding a pipe (IItemPipe) places it instead of opening the GUI. */
    static boolean isPipe(ItemStack current) {
        return current.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof BlockPipe;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack current, BlockState state, Level world, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hitResult) {
        if (isPipe(current))
            return InteractionResult.PASS;
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player entityplayer,
            BlockHitResult hitResult) {

        // Drop through if the player is sneaking
        if (entityplayer.isShiftKeyDown())
            return InteractionResult.PASS;

        if (!world.isClientSide() && world.getBlockEntity(pos) instanceof TileAutoWorkbench tile) {
            entityplayer.openMenu(tile, pos);
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileAutoWorkbench(pos, state);
    }
}
