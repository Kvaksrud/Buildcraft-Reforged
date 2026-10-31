/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: the upper-half bit becomes the door's HALF, the orientation its FACING
 * (turned by the block state); any door block (#76).
 */

package buildcraftreforged.api.bptblocks;

import buildcraftreforged.api.blueprints.BptBlock;
import buildcraftreforged.api.blueprints.BptSlotInfo;
import buildcraftreforged.api.blueprints.IBptContext;
import java.util.LinkedList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * A door: two blocks placed as one unit from the lower half. 4.2.2 used metadata bit 8 for the upper half and turned
 * the low two bits itself; the half is {@link DoorBlock#HALF} now and the default {@link BptBlock#rotateLeft} turns the
 * facing. The upper half keeps its own captured state (hinge, open), which 4.2.2's single upper metadata could not.
 */
public class BptBlockDoor extends BptBlock {

    final ItemStack stack;

    public BptBlockDoor(Block block, ItemStack stack) {
        super(block);

        this.stack = stack;
    }

    private static boolean isUpper(BptSlotInfo slot) {
        return slot.state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER;
    }

    @Override
    public void addRequirements(BptSlotInfo slot, IBptContext context, LinkedList<ItemStack> requirements) {
        if (!isUpper(slot)) {
            requirements.add(stack.copy());
        }
    }

    @Override
    public boolean ignoreBuilding(BptSlotInfo slot) {
        return isUpper(slot);
    }

    /** The lower half, then the upper half above it, as DoorBlock.setPlacedBy does. */
    @Override
    public void buildBlock(BptSlotInfo slot, IBptContext context) {
        context.world().setBlock(slot.pos(), slot.state, 3);
        context.world().setBlock(slot.pos().above(), slot.state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
    }
}
