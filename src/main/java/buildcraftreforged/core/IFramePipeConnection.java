/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: IBlockAccess becomes BlockGetter.
 */

package buildcraftreforged.core;

import net.minecraft.world.level.BlockGetter;

public interface IFramePipeConnection {

    public boolean isPipeConnected(BlockGetter blockAccess, int x1, int y1, int z1, int x2, int y2, int z2);
}
