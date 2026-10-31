/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: the 16-step standing rotation and the wall facing are turned by the block
 * state; the text is the sign's block entity data; the signature's wall/floor field is gone with signatures; keyed by
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
 * A sign, standing or on a wall (and hanging signs, which 1.6.4 did not have). 4.2.2 added 4 of 16 steps to a standing
 * sign and left wall signs unturned (its wall case was commented out), so a turned blueprint put wall signs on the
 * wrong side; the default rotation turns both. The text travels as the block entity's data.
 */
public class BptBlockSign extends BptBlock {

    boolean isWall;

    public BptBlockSign(Block block, boolean isWall) {
        super(block);

        this.isWall = isWall;
    }

    /** 4.2.2 asked for {@code Item.sign}; each wood has its own sign item now, which the wall block shares. */
    @Override
    public void addRequirements(BptSlotInfo slot, IBptContext context, LinkedList<ItemStack> requirements) {
        BptBlockIgnoreMeta.addBlockItem(slot, requirements);
    }


}
