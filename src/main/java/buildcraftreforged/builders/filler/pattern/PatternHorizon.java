/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: TileEntity becomes BlockEntity, and fill, empty and flatten take the tile
 * where they took its World; the world's top is the level's maximum Y and the bottom of flatten its minimum Y + 1
 * (1.6.4: 1).
 */
package buildcraftreforged.builders.filler.pattern;

import buildcraftreforged.api.core.IBox;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

public class PatternHorizon extends FillerPattern {

    public PatternHorizon() {
        super("horizon");
    }

    @Override
    public boolean iteratePattern(BlockEntity tile, IBox box, @Nullable ItemStack stackToPlace) {
        int xMin = (int) box.pMin().x;
        int yMin = (int) box.pMin().y;
        int zMin = (int) box.pMin().z;

        int xMax = (int) box.pMax().x;
        int zMax = (int) box.pMax().z;

        if (stackToPlace != null
                && flatten(xMin, tile.getLevel().getMinY() + 1, zMin, xMax, yMin - 1, zMax, tile, stackToPlace)) {
            return false;
        }
        return !empty(xMin, yMin, zMin, xMax, tile.getLevel().getMaxY(), zMax, tile);
    }
}
