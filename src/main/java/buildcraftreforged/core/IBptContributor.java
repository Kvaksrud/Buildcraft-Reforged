/*
 * Modified by Buildcraft Reforged, 2026: ported from buildcraft/core/IBptContributor.java (no file header at 4.2.2);
 * TileEntity becomes BlockEntity.
 */

package buildcraftreforged.core;

import buildcraftreforged.api.blueprints.BptSlotInfo;
import buildcraftreforged.core.blueprints.BptBase;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * A block entity that adds its own data to a blueprint slot when the architect captures it, and reads it back when the
 * builder places it. Ported from 4.2.2 {@code buildcraft.core.IBptContributor}. {@code builder} is the architect or
 * builder doing the work, or null when there is none (tests, the library).
 */
public interface IBptContributor {

    public void saveToBluePrint(BlockEntity builder, BptBase bluePrint, BptSlotInfo slot);

    public void loadFromBluePrint(BlockEntity builder, BptBase bluePrint, BptSlotInfo slot);

}
