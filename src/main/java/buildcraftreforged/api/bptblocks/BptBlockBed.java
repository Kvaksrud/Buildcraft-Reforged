/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: the head bit of the metadata becomes the bed's PART, the orientation its
 * FACING (turned by the block state); any bed block, its own item (#76).
 */

package buildcraftreforged.api.bptblocks;

import buildcraftreforged.api.blueprints.BptBlock;
import buildcraftreforged.api.blueprints.BptSlotInfo;
import buildcraftreforged.api.blueprints.IBptContext;
import java.util.LinkedList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * A bed: two blocks placed as one unit from the foot. 4.2.2 used metadata bit 8 for the head and turned the low bits
 * itself; the head is {@link AbstractBedBlock#PART} now and the default {@link BptBlock#rotateLeft} turns the facing.
 */
public class BptBlockBed extends BptBlock {

    public BptBlockBed(Block block) {
        super(block);
    }

    private static boolean isHead(BptSlotInfo slot) {
        return slot.state.getValue(AbstractBedBlock.PART) == BedPart.HEAD;
    }

    /** 4.2.2 asked for {@code Item.bed}; there is one bed item per bed block now. */
    @Override
    public void addRequirements(BptSlotInfo slot, IBptContext context, LinkedList<ItemStack> requirements) {
        if (!isHead(slot)) {
            ItemStack stack = new ItemStack(slot.state.getBlock().asItem());

            if (!stack.isEmpty()) {
                requirements.add(stack);
            }
        }
    }

    /** The foot, then the head one block towards the facing (4.2.2's switch on the metadata). */
    @Override
    public void buildBlock(BptSlotInfo slot, IBptContext context) {
        if (isHead(slot))
            return;

        BlockState foot = slot.state.setValue(AbstractBedBlock.OCCUPIED, false);
        BlockPos head = slot.pos().relative(foot.getValue(AbstractBedBlock.FACING));

        context.world().setBlock(slot.pos(), foot, 3);
        context.world().setBlock(head, foot.setValue(AbstractBedBlock.PART, BedPart.HEAD), 3);
    }

    @Override
    public boolean ignoreBuilding(BptSlotInfo slot) {
        return isHead(slot);
    }
}
