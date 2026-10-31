/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: keyed by Block; dirt paths count as dirt (#76).
 */

package buildcraftreforged.api.bptblocks;

import buildcraftreforged.api.blueprints.BptBlock;
import buildcraftreforged.api.blueprints.BptSlotInfo;
import buildcraftreforged.api.blueprints.IBptContext;
import java.util.LinkedList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Dirt, grass and farmland: built as dirt from dirt, and any of them counts as already built. Dirt paths (1.9) are
 * made from grass with a shovel, as farmland is with a hoe, so they count too.
 */
public class BptBlockDirt extends BptBlock {

    public BptBlockDirt(Block block) {
        super(block);
    }

    @Override
    public void addRequirements(BptSlotInfo slot, IBptContext context, LinkedList<ItemStack> requirements) {
        requirements.add(new ItemStack(Blocks.DIRT));
    }

    /** 4.2.2 kept the slot's metadata on the dirt; plain dirt has no properties now. */
    @Override
    public void buildBlock(BptSlotInfo slot, IBptContext context) {
        context.world().setBlock(slot.pos(), Blocks.DIRT.defaultBlockState(), 3);
    }

    @Override
    public boolean isValid(BptSlotInfo slot, IBptContext context) {
        BlockState state = context.world().getBlockState(slot.pos());

        return state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.FARMLAND) || state.is(Blocks.DIRT_PATH);
    }
}
