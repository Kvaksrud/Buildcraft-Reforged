/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: LAST_ORIGINAL_BLOCK and LAST_ORIGINAL_ITEM removed with numeric ids;
 * softBlocks is a set of blocks instead of an array indexed by block id.
 */

package buildcraftreforged.api.core;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.block.Block;

public class BuildCraftAPI {

    public static final Set<Block> softBlocks = ConcurrentHashMap.newKeySet();
}
