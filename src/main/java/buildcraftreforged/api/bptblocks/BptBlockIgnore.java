/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: keyed by Block; asks for nothing (#76).
 */

package buildcraftreforged.api.bptblocks;

import buildcraftreforged.api.blueprints.BptBlock;
import buildcraftreforged.api.blueprints.BptSlotInfo;
import buildcraftreforged.api.blueprints.IBptContext;
import java.util.LinkedList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;

/**
 * A block the builder never places and never clears (snow, tall grass, ice, piston heads). 4.2.2 added a requirement
 * of stack size 0, which asked for nothing; an empty stack is not a requirement now, so none is added.
 */
public class BptBlockIgnore extends BptBlock {

    public BptBlockIgnore(Block block) {
        super(block);
    }

    @Override
    public void addRequirements(BptSlotInfo slot, IBptContext context, LinkedList<ItemStack> requirements) {
    }

    @Override
    public boolean isValid(BptSlotInfo slot, IBptContext context) {
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
        return true;
    }

}
