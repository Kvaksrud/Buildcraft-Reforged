/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: "meta" is every property but the block; keyed by Block (#76).
 */

package buildcraftreforged.api.bptblocks;

import buildcraftreforged.api.blueprints.BptBlock;
import buildcraftreforged.api.blueprints.BptSlotInfo;
import buildcraftreforged.api.blueprints.IBptContext;
import java.util.LinkedList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * A block whose state follows its neighbours (rails, glass panes): costs one of its item, and any state of the same
 * block counts as built. Rotation is the default, which turns rail shapes and pane sides with the structure.
 */
public class BptBlockIgnoreMeta extends BptBlock {

    public BptBlockIgnoreMeta(Block block) {
        super(block);
    }

    @Override
    public void addRequirements(BptSlotInfo slot, IBptContext context, LinkedList<ItemStack> requirements) {
        addBlockItem(slot, requirements);
    }

    @Override
    public boolean isValid(BptSlotInfo slot, IBptContext context) {
        return slot.state.getBlock() == context.world().getBlockState(slot.pos()).getBlock();
    }

    /**
     * 4.2.2's {@code new ItemStack(slot.blockId, 1, 0)}, shared by the schematics that cost one of the block's item. A
     * block without an item asks for nothing.
     */
    static void addBlockItem(BptSlotInfo slot, LinkedList<ItemStack> requirements) {
        ItemStack stack = new ItemStack(slot.state.getBlock().asItem());

        if (!stack.isEmpty()) {
            requirements.add(stack);
        }
    }
}
