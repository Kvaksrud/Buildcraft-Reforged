/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: BlockContainer becomes BlockBuildCraft; Material.wood, the hardness and the
 * creative tab move to registration; the icons are the JSON model (library_topbottom on top and bottom, library_side
 * elsewhere); onBlockActivated becomes useWithoutItem (openMenu with the tile as the MenuProvider); onBlockPlacedBy's
 * owner is TileBuildCraft's (the placer's name and UUID, #27); addCreativeItems is dropped.
 */

package buildcraftreforged.builders;

import buildcraftreforged.core.BlockBuildCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class BlockBlueprintLibrary extends BlockBuildCraft {

    public BlockBlueprintLibrary(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player entityplayer,
            BlockHitResult hitResult) {

        // Drop through if the player is sneaking
        if (entityplayer.isShiftKeyDown())
            return InteractionResult.PASS;

        if (world.getBlockEntity(pos) instanceof TileBlueprintLibrary tile) {
            if (tile.canOpen(entityplayer))
                if (!world.isClientSide()) {
                    entityplayer.openMenu(tile, pos);
                }
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileBlueprintLibrary(pos, state);
    }
}
