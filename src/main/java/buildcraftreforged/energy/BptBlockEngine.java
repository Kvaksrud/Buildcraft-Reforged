/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: the orientation is the FACING block state, turned by BlockEngine.rotate
 * (BuildCraft#1771, docs/BUGFIXES.md); the signature override is gone with signatures; keyed by Block (#76).
 */

package buildcraftreforged.energy;

import buildcraftreforged.api.blueprints.BptBlock;
import buildcraftreforged.api.blueprints.BptSlotInfo;
import buildcraftreforged.api.blueprints.IBptContext;
import net.minecraft.world.level.block.Block;

/**
 * An engine, built empty: 4.2.2 stored only the orientation, so a built engine had no energy, heat, fuel or coolant.
 * The orientation is the block state now, so nothing is stored, and the requirement is the engine's item (no drops are
 * recorded, as in 4.2.2).
 *
 * <p>4.2.2 turned the stored orientation around the DOWN axis, the wrong way (BuildCraft#1771); the default
 * {@link BptBlock#rotateLeft} turns the facing with the structure.
 */
public class BptBlockEngine extends BptBlock {

    public BptBlockEngine(Block block) {
        super(block);
    }

    @Override
    public void initializeFromWorld(BptSlotInfo bptSlot, IBptContext context, int x, int y, int z) {
    }

    @Override
    public void buildBlock(BptSlotInfo slot, IBptContext context) {
        context.world().setBlock(slot.pos(), slot.state, 3);
    }
}
