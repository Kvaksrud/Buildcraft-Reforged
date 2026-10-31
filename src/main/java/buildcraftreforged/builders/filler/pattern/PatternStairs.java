/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: TileEntity becomes BlockEntity, and fill, empty and flatten take the tile
 * where they took its World; the tile's coordinates are its block position.
 */
package buildcraftreforged.builders.filler.pattern;

import buildcraftreforged.api.core.IBox;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

public class PatternStairs extends FillerPattern {

    public PatternStairs() {
        super("stairs");
    }

    @Override
    public boolean iteratePattern(BlockEntity tile, IBox box, @Nullable ItemStack stackToPlace) {
        int xMin = (int) box.pMin().x;
        int yMin = (int) box.pMin().y;
        int zMin = (int) box.pMin().z;

        int xMax = (int) box.pMax().x;
        int yMax = (int) box.pMax().y;
        int zMax = (int) box.pMax().z;

        int sizeX = xMax - xMin + 1;
        int sizeZ = zMax - zMin + 1;

        int height;
        int heightStep;
        int dimX = 0;
        int dimZ = 0;

        if (tile.getBlockPos().getY() <= yMin) {
            height = yMin;
            heightStep = 1;
        } else {
            height = yMax;
            heightStep = -1;
        }

        int kind = 0;

        int steps[] = new int[]{0, 0, 0, 0};

        int x = 0, z = 0;
        int stepDiagX = 0, stepDiagZ = 0;

        if (tile.getBlockPos().getX() == xMin - 1) {
            steps[0] = 1;
        } else if (tile.getBlockPos().getX() == xMax + 1) {
            steps[1] = 1;
        } else if (tile.getBlockPos().getZ() == zMin - 1) {
            steps[2] = 1;
        } else if (tile.getBlockPos().getZ() == zMax + 1) {
            steps[3] = 1;
        } else {
            kind = 1;

            if (tile.getBlockPos().getX() <= xMin) {
                x = xMin;
            } else if (tile.getBlockPos().getX() >= xMax) {
                x = xMax;
            }

            if (tile.getBlockPos().getZ() <= zMin) {
                z = zMin;
            } else if (tile.getBlockPos().getZ() >= zMax) {
                z = zMax;
            }

            if (heightStep == 1) {
                stepDiagX = -1;
                dimX = sizeX - 1;

                stepDiagZ = -1;
                dimZ = sizeZ - 1;
            } else {
                stepDiagX = 1;
                dimX = 0;

                stepDiagZ = 1;
                dimZ = 0;
            }
        }

        int x1 = 0, x2 = 0, z1 = 0, z2 = 0;

        x1 = xMin;
        x2 = xMax;

        z1 = zMin;
        z2 = zMax;

        if (heightStep == -1) {
            if (steps[0] == 1) {
                x1 = xMax - sizeX + 1;
                x2 = x1;
            }

            if (steps[1] == 1) {
                x2 = xMin + sizeX - 1;
                x1 = x2;
            }

            if (steps[2] == 1) {
                z1 = zMax - sizeZ + 1;
                z2 = z1;
            }

            if (steps[3] == 1) {
                z2 = zMin + sizeZ - 1;
                z1 = z2;
            }
        }

        if (kind == 0) {
            while (x2 - x1 + 1 > 0 && z2 - z1 + 1 > 0 && x2 - x1 < sizeX && z2 - z1 < sizeZ && height >= yMin && height <= yMax) {

                if (fill(x1, height, z1, x2, height, z2, stackToPlace, tile))
                    return false;

                if (heightStep == 1) {
                    x1 += steps[0];
                    x2 -= steps[1];
                    z1 += steps[2];
                    z2 -= steps[3];
                } else {
                    x2 += steps[0];
                    x1 -= steps[1];
                    z2 += steps[2];
                    z1 -= steps[3];
                }

                height += heightStep;
            }
        } else if (kind == 1) {
            while (dimX >= 0 && dimX < sizeX && dimZ >= 0 && dimZ < sizeZ && height >= yMin && height <= yMax) {

                if (heightStep == 1) {
                    if (tile.getBlockPos().getX() >= xMax) {
                        x1 = x - sizeX + 1;
                        x2 = x1 + dimX;
                    } else {
                        x2 = x + sizeX - 1;
                        x1 = x2 - dimX;
                    }

                    if (tile.getBlockPos().getZ() >= zMax) {
                        z1 = z - sizeZ + 1;
                        z2 = z1 + dimZ;
                    } else {
                        z2 = z + sizeZ - 1;
                        z1 = z2 - dimZ;
                    }
                } else if (heightStep == -1) {
                    if (tile.getBlockPos().getX() <= xMin) {
                        x1 = x;
                        x2 = x1 + dimX;
                    } else {
                        x2 = x;
                        x1 = x2 - dimX;
                    }

                    if (tile.getBlockPos().getZ() <= zMin) {
                        z1 = z;
                        z2 = z1 + dimZ;
                    } else {
                        z2 = z;
                        z1 = z2 - dimZ;
                    }

                }

                if (fill(x1, height, z1, x2, height, z2, stackToPlace, tile))
                    return false;

                dimX += stepDiagX;
                dimZ += stepDiagZ;

                height += heightStep;
            }
        }

        return true;
    }
}
