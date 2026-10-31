/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: TileEntity becomes BlockEntity, and fill, empty and flatten take the tile
 * where they took its World.
 */
package buildcraftreforged.builders.filler.pattern;

import buildcraftreforged.api.core.IBox;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

public class PatternClear extends FillerPattern {

    public PatternClear() {
        super("clear");
    }

    @Override
    public boolean iteratePattern(BlockEntity tile, IBox box, @Nullable ItemStack stackToPlace) {
        int xMin = (int) box.pMin().x;
        int yMin = (int) box.pMin().y;
        int zMin = (int) box.pMin().z;

        int xMax = (int) box.pMax().x;
        int yMax = (int) box.pMax().y;
        int zMax = (int) box.pMax().z;

        return !empty(xMin, yMin, zMin, xMax, yMax, zMax, tile);
    }
}
