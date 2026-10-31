/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: the extended bit is the EXTENDED property; keyed by Block (#76).
 */

package buildcraftreforged.api.bptblocks;

import buildcraftreforged.api.blueprints.BptSlotInfo;
import buildcraftreforged.api.blueprints.IBptContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.piston.PistonBaseBlock;

/** A piston, never built extended (4.2.2 masked off metadata bit 8); its head is ignored. */
public class BptBlockPiston extends BptBlockRotateMeta {

    public BptBlockPiston(Block block) {
        super(block);
    }

    @Override
    public void buildBlock(BptSlotInfo slot, IBptContext context) {
        context.world().setBlock(slot.pos(), slot.state.setValue(PistonBaseBlock.EXTENDED, false), 3);
    }

}
