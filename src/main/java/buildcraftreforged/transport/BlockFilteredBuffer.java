/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: Material.iron, the hardness and the creative tab move to registration;
 * registerIcons/getIcon become the block model (filtered_buffer_all on every face); onBlockActivated becomes
 * useItemOn (a held pipe passes, so it is placed) and useWithoutItem (openMenu with the tile as the MenuProvider);
 * addCreativeItems is the creative tab entry.
 */
package buildcraftreforged.transport;

import buildcraftreforged.core.BlockBuildCraft;
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

/**
 *
 * @author SandGrainOne
 */
public class BlockFilteredBuffer extends BlockBuildCraft {

    public BlockFilteredBuffer(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileFilteredBuffer(pos, state);
    }

    /** 4.2.2: a held IItemPipe falls through, so the pipe is placed against the buffer. */
    @Override
    protected InteractionResult useItemOn(ItemStack current, BlockState state, Level world, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hitResult) {
        if (current.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof BlockPipe)
            return InteractionResult.PASS;
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player entityplayer,
            BlockHitResult hitResult) {

        if (entityplayer.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }

        if (!world.isClientSide() && world.getBlockEntity(pos) instanceof TileFilteredBuffer tile) {
            entityplayer.openMenu(tile, pos);
        }

        return InteractionResult.SUCCESS;
    }
}
