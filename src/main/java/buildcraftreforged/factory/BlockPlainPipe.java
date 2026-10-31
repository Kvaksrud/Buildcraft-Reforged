/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: Material.glass moves to registration; the block bounds become the shape and
 * the block model; idDropped 0 becomes noLootTable; isOpaqueCube and renderAsNormalBlock become noOcclusion.
 */
package buildcraftreforged.factory;

import buildcraftreforged.core.CoreConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The Mining Pipe: the column the mining well leaves below itself. It drops nothing. */
public class BlockPlainPipe extends Block {
    // SEAM(#67): 4.2.2 implemented IFramePipeConnection (isPipeConnected always false); the quarry frames port it.

    private static final VoxelShape SHAPE = Shapes.box(CoreConstants.PIPE_MIN_POS, 0.0, CoreConstants.PIPE_MIN_POS,
            CoreConstants.PIPE_MAX_POS, 1.0, CoreConstants.PIPE_MAX_POS);

    public BlockPlainPipe(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    public float getHeightInPipe() {
        return 0.5F;
    }
}
