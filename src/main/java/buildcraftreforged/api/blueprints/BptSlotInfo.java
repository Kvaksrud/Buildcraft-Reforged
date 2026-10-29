/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: a BlockState in place of block id and metadata (plan section 13.4);
 * CompoundTag in place of NBTTagCompound.
 */

package buildcraftreforged.api.blueprints;

import java.util.LinkedList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * This class records a slot, either from a blueprint or from a block placed in the world.
 *
 * <p>Ported from 4.2.2 {@code buildcraft.api.blueprints.BptSlotInfo}. 4.2.2 stored {@code blockId} and {@code meta};
 * both became {@link #state} (plan section 13.4), air where 4.2.2 had id 0.
 */
public class BptSlotInfo {

    public BlockState state = Blocks.AIR.defaultBlockState();
    public int x;
    public int y;
    public int z;

    /**
     * This field contains requirements for a given block when stored in the blueprint. Modders can either rely on this list or compute their own int BptBlock.
     */
    public LinkedList<ItemStack> storedRequirements = new LinkedList<ItemStack>();

    /**
     * This tree contains additional data to be stored in the blueprint. By default, it will be initialized from BptBlock.initializeFromWorld with the
     * block entity's saved data (if any) and will be loaded from BptBlock.buildBlock into the placed block entity.
     */
    public CompoundTag cpt = new CompoundTag();

    /** The slot's position; 4.2.2 code used {@code x, y, z} directly. */
    public BlockPos pos() {
        return new BlockPos(x, y, z);
    }

    @Override
    public BptSlotInfo clone() {
        BptSlotInfo obj = new BptSlotInfo();

        obj.x = x;
        obj.y = y;
        obj.z = z;
        obj.state = state;
        obj.cpt = cpt.copy();

        return obj;
    }

}
