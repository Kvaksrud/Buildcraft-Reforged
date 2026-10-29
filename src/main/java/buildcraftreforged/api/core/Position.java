/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: ForgeDirection becomes Direction, with null for ForgeDirection.UNKNOWN;
 * NBTTagCompound becomes ValueInput/ValueOutput; TileEntity becomes BlockEntity.
 */

package buildcraftreforged.api.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class Position {

    public double x, y, z;
    /** {@code null} where 4.2.2 used {@code ForgeDirection.UNKNOWN}. */
    public @Nullable Direction orientation;

    public Position(double ci, double cj, double ck) {
        x = ci;
        y = cj;
        z = ck;
        orientation = null;
    }

    public Position(double ci, double cj, double ck, @Nullable Direction corientation) {
        x = ci;
        y = cj;
        z = ck;
        orientation = corientation;
    }

    public Position(Position p) {
        x = p.x;
        y = p.y;
        z = p.z;
        orientation = p.orientation;
    }

    public Position(ValueInput nbttagcompound) {
        x = nbttagcompound.getDoubleOr("i", 0);
        y = nbttagcompound.getDoubleOr("j", 0);
        z = nbttagcompound.getDoubleOr("k", 0);

        orientation = null;
    }

    public Position(BlockEntity tile) {
        BlockPos pos = tile.getBlockPos();
        x = pos.getX();
        y = pos.getY();
        z = pos.getZ();
    }

    public void moveRight(double step) {
        switch (orientation) {
        case SOUTH:
            x = x - step;
            break;
        case NORTH:
            x = x + step;
            break;
        case EAST:
            z = z + step;
            break;
        case WEST:
            z = z - step;
            break;
        case null, default:
        }
    }

    public void moveLeft(double step) {
        moveRight(-step);
    }

    public void moveForwards(double step) {
        switch (orientation) {
        case UP:
            y = y + step;
            break;
        case DOWN:
            y = y - step;
            break;
        case SOUTH:
            z = z + step;
            break;
        case NORTH:
            z = z - step;
            break;
        case EAST:
            x = x + step;
            break;
        case WEST:
            x = x - step;
            break;
        case null, default:
        }
    }

    public void moveBackwards(double step) {
        moveForwards(-step);
    }

    public void moveUp(double step) {
        switch (orientation) {
        case SOUTH:
        case NORTH:
        case EAST:
        case WEST:
            y = y + step;
            break;
        case null, default:
        }

    }

    public void moveDown(double step) {
        moveUp(-step);
    }

    public void writeToNBT(ValueOutput nbttagcompound) {
        nbttagcompound.putDouble("i", x);
        nbttagcompound.putDouble("j", y);
        nbttagcompound.putDouble("k", z);
    }

    @Override
    public String toString() {
        return "{" + x + ", " + y + ", " + z + "}";
    }

    public Position min(Position p) {
        return new Position(p.x > x ? x : p.x, p.y > y ? y : p.y, p.z > z ? z : p.z);
    }

    public Position max(Position p) {
        return new Position(p.x < x ? x : p.x, p.y < y ? y : p.y, p.z < z ? z : p.z);
    }

}
