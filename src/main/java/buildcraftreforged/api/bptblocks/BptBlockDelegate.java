/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: keyed by Block; the delegate slot takes the delegate block's state with the
 * properties both blocks share; the original slot is no longer overwritten (docs/BUGFIXES.md) (#76).
 */

package buildcraftreforged.api.bptblocks;

import buildcraftreforged.api.blueprints.BlueprintManager;
import buildcraftreforged.api.blueprints.BptBlock;
import buildcraftreforged.api.blueprints.BptSlotInfo;
import buildcraftreforged.api.blueprints.IBptContext;
import java.util.LinkedList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Costs, checks and turns a block as another block would (4.2.2: a burning furnace as an idle one). The block itself is
 * still the one built.
 *
 * <p>4.2.2 cloned the slot and then set {@code slot.blockId = delegateTo} on the original instead of the clone, so the
 * first requirement check turned the slot into the delegate block for good, and the clone was asked with the old id.
 * The clone is changed now, as intended; rotation is copied back onto the slot (4.2.2 turned the clone and dropped
 * it).
 */
public class BptBlockDelegate extends BptBlock {

    final Block delegateTo;

    public BptBlockDelegate(Block block, Block delegateTo) {
        super(block);

        this.delegateTo = delegateTo;
    }

    private BptSlotInfo delegateSlot(BptSlotInfo slot) {
        BptSlotInfo newSlot = slot.clone();
        newSlot.state = copyProperties(slot.state, delegateTo.defaultBlockState());
        newSlot.storedRequirements = slot.storedRequirements;
        return newSlot;
    }

    @Override
    public void addRequirements(BptSlotInfo slot, IBptContext context, LinkedList<ItemStack> requirements) {
        BlueprintManager.getBptBlock(delegateTo).addRequirements(delegateSlot(slot), context, requirements);
    }

    @Override
    public boolean isValid(BptSlotInfo slot, IBptContext context) {
        return BlueprintManager.getBptBlock(delegateTo).isValid(delegateSlot(slot), context);
    }

    @Override
    public void rotateLeft(BptSlotInfo slot, IBptContext context) {
        BptSlotInfo newSlot = delegateSlot(slot);

        BlueprintManager.getBptBlock(delegateTo).rotateLeft(newSlot, context);

        slot.state = copyProperties(newSlot.state, slot.state);
    }

    /** {@code to} with every property {@code from} also has set to {@code from}'s value. */
    static BlockState copyProperties(BlockState from, BlockState to) {
        for (Property<?> property : from.getProperties()) {
            if (to.hasProperty(property)) {
                to = copy(from, to, property);
            }
        }

        return to;
    }

    private static <T extends Comparable<T>> BlockState copy(BlockState from, BlockState to, Property<T> property) {
        return to.setValue(property, from.getValue(property));
    }

}
