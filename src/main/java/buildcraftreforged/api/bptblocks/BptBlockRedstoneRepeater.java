/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: the facing is turned by the block state, which keeps the delay; one block
 * for the lit and unlit repeater; keyed by Block (#76).
 */

package buildcraftreforged.api.bptblocks;

import buildcraftreforged.api.blueprints.BptBlock;
import buildcraftreforged.api.blueprints.BptSlotInfo;
import buildcraftreforged.api.blueprints.IBptContext;
import java.util.LinkedList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * A redstone repeater. 4.2.2 turned the low two metadata bits and kept the delay bits above them; the facing and the
 * delay are separate properties now and the default rotation turns only the facing.
 */
public class BptBlockRedstoneRepeater extends BptBlock {

    public BptBlockRedstoneRepeater(Block block) {
        super(block);
    }

    /** 4.2.2 asked for {@code Item.redstoneRepeater}, the repeater block's item. */
    @Override
    public void addRequirements(BptSlotInfo slot, IBptContext context, LinkedList<ItemStack> requirements) {
        BptBlockIgnoreMeta.addBlockItem(slot, requirements);
    }

}
