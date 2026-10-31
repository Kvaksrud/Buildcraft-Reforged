/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: the side is the block's FACING (or a separate wall block), turned by the
 * block state; keyed by Block (#76).
 */

package buildcraftreforged.api.bptblocks;

import buildcraftreforged.api.blueprints.BptBlock;
import buildcraftreforged.api.blueprints.BptSlotInfo;
import buildcraftreforged.api.blueprints.IBptContext;
import java.util.LinkedList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * A block stuck to a side of its neighbour (torches, markers). 4.2.2 turned metadata 1 to 4 (x+, x-, z+, z- sides)
 * clockwise; the default rotation turns the facing. A wall torch costs a torch: the wall block shares the item.
 */
public class BptBlockWallSide extends BptBlock {

    public BptBlockWallSide(Block block) {
        super(block);
    }

    @Override
    public void addRequirements(BptSlotInfo slot, IBptContext context, LinkedList<ItemStack> requirements) {
        BptBlockIgnoreMeta.addBlockItem(slot, requirements);
    }

}
