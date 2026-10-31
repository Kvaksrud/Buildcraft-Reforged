/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: the facing is turned by the block state; keyed by Block (#76).
 */

package buildcraftreforged.api.bptblocks;

import buildcraftreforged.api.blueprints.BptBlock;
import buildcraftreforged.api.blueprints.BptSlotInfo;
import buildcraftreforged.api.blueprints.IBptContext;
import java.util.LinkedList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * Stairs. 4.2.2 mapped metadata 0, 1, 2, 3 (east, west, south, north) to 2, 3, 1, 0, a clockwise turn; the default
 * rotation turns the facing the same way and keeps the half. Any state of the same stairs counts as built, since the
 * shape follows the neighbours.
 */
public class BptBlockStairs extends BptBlock {

    public BptBlockStairs(Block block) {
        super(block);
    }

    @Override
    public void addRequirements(BptSlotInfo slot, IBptContext context, LinkedList<ItemStack> requirements) {
        BptBlockIgnoreMeta.addBlockItem(slot, requirements);
    }

    @Override
    public boolean isValid(BptSlotInfo slot, IBptContext context) {
        return slot.state.getBlock() == context.world().getBlockState(slot.pos()).getBlock();
    }


}
