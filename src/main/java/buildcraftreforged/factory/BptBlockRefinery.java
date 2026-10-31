/**
 * Copyright (c) 2011-2014, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: the orientation is the FACING block state, turned by BlockRefinery.rotate;
 * the commented-out filter code is left out; keyed by Block (#76).
 */

package buildcraftreforged.factory;

import buildcraftreforged.api.blueprints.BptBlock;
import buildcraftreforged.api.blueprints.BptSlotInfo;
import buildcraftreforged.api.blueprints.IBptContext;
import net.minecraft.world.level.block.Block;

/**
 * A refinery, built empty. 4.2.2's {@code initializeFromWorld} stored nothing (its filter lines were commented out),
 * so the tanks were never copied, which is the residual liquid fix accepted for refineries (plan section 3,
 * BuildCraft#1791). Its {@code buildBlock} read filters that were never stored and set them through commented-out
 * calls; only the placement remains.
 */
public class BptBlockRefinery extends BptBlock {

    public BptBlockRefinery(Block block) {
        super(block);
    }

    @Override
    public void initializeFromWorld(BptSlotInfo slot, IBptContext context, int x, int y, int z) {
    }

    @Override
    public void buildBlock(BptSlotInfo slot, IBptContext context) {
        context.world().setBlock(slot.pos(), slot.state, 3);
    }

}
