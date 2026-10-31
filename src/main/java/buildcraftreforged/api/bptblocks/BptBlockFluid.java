/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: metadata 0 becomes a source fluid state; keyed by Block (#76).
 */

package buildcraftreforged.api.bptblocks;

import buildcraftreforged.api.blueprints.BptBlock;
import buildcraftreforged.api.blueprints.BptSlotInfo;
import buildcraftreforged.api.blueprints.IBptContext;
import java.util.LinkedList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A fluid block. Only sources are built, each from a full bucket; flowing fluid is left to flow (plan section 13.4).
 * 4.2.2 tested {@code meta == 0}; that is {@code FluidState.isSource} now, and the source is placed as the block's
 * default state.
 */
public class BptBlockFluid extends BptBlock {

    private final ItemStack bucketStack;

    public BptBlockFluid(Block block, ItemStack bucketStack) {
        super(block);

        this.bucketStack = bucketStack;
    }

    private static boolean isSource(BlockState state) {
        return state.getFluidState().isSource();
    }

    @Override
    public void addRequirements(BptSlotInfo slot, IBptContext context, LinkedList<ItemStack> requirements) {
        if (isSource(slot.state) && !bucketStack.isEmpty()) {
            requirements.add(bucketStack.copy());
        }
    }

    @Override
    public boolean isValid(BptSlotInfo slot, IBptContext context) {
        if (isSource(slot.state)) {
            BlockState state = context.world().getBlockState(slot.pos());

            return state.getBlock() == slot.state.getBlock() && isSource(state);
        } else
            return true;
    }

    @Override
    public void rotateLeft(BptSlotInfo slot, IBptContext context) {

    }

    @Override
    public void mirror(BptSlotInfo slot, IBptContext context, Mirror mirror) {

    }

    @Override
    public boolean ignoreBuilding(BptSlotInfo slot) {
        return !isSource(slot.state);
    }

    @Override
    public void buildBlock(BptSlotInfo slot, IBptContext context) {
        if (isSource(slot.state)) {
            context.world().setBlock(slot.pos(), slot.state.getBlock().defaultBlockState(), 3);
        }
    }

}
