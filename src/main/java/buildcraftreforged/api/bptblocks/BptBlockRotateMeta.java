/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: the rotation table on metadata is gone, the block state turns itself; keyed by
 * Block (#76).
 */

package buildcraftreforged.api.bptblocks;

import buildcraftreforged.api.blueprints.BptBlock;
import buildcraftreforged.api.blueprints.BptSlotInfo;
import buildcraftreforged.api.blueprints.IBptContext;
import java.util.LinkedList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * A block that faces a side: costs one of its item, and any state of the same block counts as built.
 *
 * <p>4.2.2 took the four metadata values of north, east, south and west in turning order ({@code rot}), a flag for
 * turning forwards or backwards, and the mask of the bits that hold the facing. Every table it was given turned
 * clockwise seen from above, which is what the block state's own rotation does (the default {@link BptBlock#rotateLeft});
 * the constructor takes only the block now.
 */
public class BptBlockRotateMeta extends BptBlock {

    public BptBlockRotateMeta(Block block) {
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
