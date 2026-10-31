/**
 * Copyright (c) SpaceToad, 2011-2012
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: the comma-separated block id mask becomes presence bits in NBT (plan section
 * 13.4); a filled slot holds stone, the block 4.2.2's mask id 1 stood for.
 */

package buildcraftreforged.core.blueprints;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A template: which positions of a box are filled, without the blocks. Ported from 4.2.2
 * {@code buildcraft.core.blueprints.BptTemplate}.
 *
 * <p>4.2.2 wrote a {@code mask:} line of block ids, 1 (stone) for filled and 0 (air) for empty, and the builder only
 * asked whether a slot's id was 0. Now the file holds one bit per position ({@code mask}, a byte array, bit
 * {@code i % 8} of byte {@code i / 8} for flat index {@code i}), and every position has a slot whose state is
 * {@link #filledState()} or air.
 */
public class BptTemplate extends BptBase {

    public BptTemplate() {
    }

    public BptTemplate(int sizeX, int sizeY, int sizeZ) {
        super(sizeX, sizeY, sizeZ);
    }

    /** The state of a filled slot: stone, 4.2.2's id 1. */
    public static BlockState filledState() {
        return Blocks.STONE.defaultBlockState();
    }

    /**
     * 4.2.2 {@code setBlockId(x, y, z, mask)} as the architect called it: a non-zero mask marks the position filled.
     */
    public void setMask(int x, int y, int z, int mask) {
        setBlockState(x, y, z, mask != 0 ? filledState() : Blocks.AIR.defaultBlockState());
    }

    /** True when the position is filled. */
    public boolean isFilled(int x, int y, int z) {
        BptSlot slot = contents[x][y][z];

        return slot != null && !slot.state.isAir();
    }

    @Override
    public void saveAttributes(CompoundTag nbt, HolderLookup.Provider registries) {
        byte[] mask = new byte[(sizeX * sizeY * sizeZ + 7) / 8];

        for (int x = 0; x < sizeX; ++x) {
            for (int y = 0; y < sizeY; ++y) {
                for (int z = 0; z < sizeZ; ++z) {
                    if (isFilled(x, y, z)) {
                        int i = index(x, y, z);
                        mask[i / 8] |= (byte) (1 << (i % 8));
                    }
                }
            }
        }

        nbt.putByteArray("mask", mask);
    }

    @Override
    public void loadAttributes(CompoundTag nbt, HolderLookup.Provider registries) throws BptError {
        byte[] mask = nbt.getByteArray("mask").orElse(new byte[0]);

        if (mask.length != (sizeX * sizeY * sizeZ + 7) / 8)
            throw new BptError("Template " + getName() + " has a mask of " + mask.length + " bytes for size " + sizeX + " x " + sizeY + " x " + sizeZ);

        for (int x = 0; x < sizeX; ++x) {
            for (int y = 0; y < sizeY; ++y) {
                for (int z = 0; z < sizeZ; ++z) {
                    int i = index(x, y, z);

                    setMask(x, y, z, (mask[i / 8] >> (i % 8)) & 1);
                }
            }
        }

    }

}
