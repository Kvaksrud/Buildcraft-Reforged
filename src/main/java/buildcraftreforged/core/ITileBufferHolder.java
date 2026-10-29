/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: ForgeDirection becomes Direction, TileEntity becomes BlockEntity and the
 * block id becomes a BlockState (getBlockId becomes getBlockState).
 */

package buildcraftreforged.core;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public interface ITileBufferHolder {

    public void blockRemoved(Direction from);

    public void blockCreated(Direction from, BlockState state, @Nullable BlockEntity tile);

    public BlockState getBlockState(Direction to);

    public @Nullable BlockEntity getTile(Direction to);

}
