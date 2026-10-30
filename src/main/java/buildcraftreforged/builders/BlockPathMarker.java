/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: getBlockTexture's active icon becomes the ACTIVE property, which
 * TilePathMarker keeps equal to tryingToConnect, and the blockstate file picks the model; breakBlock's
 * Utils.preDestroyBlock runs from TileBuildCraft.preRemoveSideEffects.
 */

package buildcraftreforged.builders;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class BlockPathMarker extends BlockMarker {

    /** The marker is searching for another path marker: 4.2.2 drew it with blockPathMarkerActive. */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public BlockPathMarker(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ACTIVE);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TilePathMarker(pos, state);
    }
}
