/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: one item per engine block, so getMetadata and setHasSubtypes go and the
 * per-damage getUnlocalizedName (tile.engineWood, tile.engineStone, tile.engineIron) becomes each block's
 * overrideDescription.
 */
package buildcraftreforged.energy;

import buildcraftreforged.core.ItemBlockBuildCraft;

public class ItemEngine extends ItemBlockBuildCraft {

    public ItemEngine(BlockEngine block, Properties properties) {
        super(block, properties);
    }
}
