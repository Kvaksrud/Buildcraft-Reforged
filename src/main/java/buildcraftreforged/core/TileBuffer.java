/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: World becomes Level, TileEntity becomes BlockEntity, ForgeDirection becomes
 * Direction; the block id becomes a BlockState (blockID becomes blockState, 0 becomes air, getBlockID becomes
 * getBlockState); blockExists becomes isLoaded and isInvalid becomes isRemoved.
 */
package buildcraftreforged.core;

import buildcraftreforged.api.core.SafeTimeTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public final class TileBuffer {

    private BlockState blockState = Blocks.AIR.defaultBlockState();
    private @Nullable BlockEntity tile;
    private final SafeTimeTracker tracker = new SafeTimeTracker();
    private final Level world;
    final int x, y, z;
    private final BlockPos pos;
    private final boolean loadUnloaded;

    public TileBuffer(Level world, int x, int y, int z, boolean loadUnloaded) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.pos = new BlockPos(x, y, z);
        this.loadUnloaded = loadUnloaded;

        refresh();
    }

    public final void refresh() {
        tile = null;
        blockState = Blocks.AIR.defaultBlockState();
        if (!loadUnloaded && !world.isLoaded(pos)) {
            return;
        }
        blockState = world.getBlockState(pos);

        if (blockState.hasBlockEntity()) {
            tile = world.getBlockEntity(pos);
        }
    }

    public void set(BlockState blockState, @Nullable BlockEntity tile) {
        this.blockState = blockState;
        this.tile = tile;
        tracker.markTime(world);
    }

    public BlockState getBlockState() {
        if (tile != null && !tile.isRemoved())
            return blockState;

        if (tracker.markTimeIfDelay(world, 20)) {
            refresh();

            if (tile != null && !tile.isRemoved())
                return blockState;
        }

        return Blocks.AIR.defaultBlockState();
    }

    public @Nullable BlockEntity getTile() {
        if (tile != null && !tile.isRemoved())
            return tile;

        if (tracker.markTimeIfDelay(world, 20)) {
            refresh();

            if (tile != null && !tile.isRemoved())
                return tile;
        }

        return null;
    }

    public boolean exists() {
        if (tile != null && !tile.isRemoved())
            return true;
        return world.isLoaded(pos);
    }

    public static TileBuffer[] makeBuffer(Level world, int x, int y, int z, boolean loadUnloaded) {
        TileBuffer[] buffer = new TileBuffer[6];
        for (int i = 0; i < 6; i++) {
            // Direction's 3D data values are ForgeDirection's ordinals: down, up, north, south, west, east.
            Direction d = Direction.from3DDataValue(i);
            buffer[i] = new TileBuffer(world, x + d.getStepX(), y + d.getStepY(), z + d.getStepZ(), loadUnloaded);
        }
        return buffer;
    }
}
