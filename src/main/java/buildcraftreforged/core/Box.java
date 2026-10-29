/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: World becomes Level; NBT becomes ValueInput/ValueOutput; the data streams
 * become a FriendlyByteBuf; the lasers are EntityBlock render data instead of spawned entities, and getLasers hands
 * them to the owner's BlockEntityRenderer.
 */
package buildcraftreforged.core;

import buildcraftreforged.api.core.IAreaProvider;
import buildcraftreforged.api.core.IBox;
import buildcraftreforged.api.core.LaserKind;
import buildcraftreforged.api.core.Position;
import buildcraftreforged.core.utils.Utils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class Box implements IBox {

    public int xMin, yMin, zMin, xMax, yMax, zMax;
    public boolean initialized;

    private EntityBlock @Nullable [] lasers;

    public Box() {
        reset();
    }

    public void reset() {
        initialized = false;
        xMin = Integer.MAX_VALUE;
        yMin = Integer.MAX_VALUE;
        zMin = Integer.MAX_VALUE;
        xMax = Integer.MAX_VALUE;
        yMax = Integer.MAX_VALUE;
        zMax = Integer.MAX_VALUE;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void initialize(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax) {
        this.xMin = xMin;
        this.yMin = yMin;
        this.zMin = zMin;
        this.xMax = xMax;
        this.yMax = yMax;
        this.zMax = zMax;

        initialized = !(xMin == Integer.MAX_VALUE || yMin == Integer.MAX_VALUE || zMin == Integer.MAX_VALUE || xMax == Integer.MAX_VALUE
                || yMax == Integer.MAX_VALUE || zMax == Integer.MAX_VALUE);
    }

    public void initialize(Box box) {
        initialize(box.xMin, box.yMin, box.zMin, box.xMax, box.yMax, box.zMax);
    }

    public void initialize(IAreaProvider a) {
        initialize(a.xMin(), a.yMin(), a.zMin(), a.xMax(), a.yMax(), a.zMax());
    }

    public void initialize(ValueInput nbttagcompound) {
        initialize(nbttagcompound.getIntOr("xMin", 0), nbttagcompound.getIntOr("yMin", 0), nbttagcompound.getIntOr("zMin", 0),
                nbttagcompound.getIntOr("xMax", 0), nbttagcompound.getIntOr("yMax", 0), nbttagcompound.getIntOr("zMax", 0));
    }

    public void initialize(int centerX, int centerY, int centerZ, int size) {
        initialize(centerX - size, centerY - size, centerZ - size, centerX + size, centerY + size, centerZ + size);
    }

    public List<BlockIndex> getBlocksInArea() {
        List<BlockIndex> blocks = new ArrayList<BlockIndex>();

        for (int x = xMin; x <= xMax; x++) {
            for (int y = yMin; y <= yMax; y++) {
                for (int z = zMin; z <= zMax; z++) {
                    blocks.add(new BlockIndex(x, y, z));
                }
            }
        }

        return blocks;
    }

    @Override
    public void expand(int amount) {
        xMin += amount;
        yMin += amount;
        zMin += amount;
        xMax += amount;
        yMax += amount;
        zMax += amount;
    }

    @Override
    public void contract(int amount) {
        expand(-amount);
    }

    @Override
    public boolean contains(int x, int y, int z) {
        if (x >= xMin && x <= xMax && y >= yMin && y <= yMax && z >= zMin && z <= zMax)
            return true;

        return false;
    }

    public boolean contains(Position p) {
        return contains((int) p.x, (int) p.y, (int) p.z);
    }

    public boolean contains(BlockIndex i) {
        return contains(i.x, i.y, i.z);
    }

    @Override
    public Position pMin() {
        return new Position(xMin, yMin, zMin);
    }

    @Override
    public Position pMax() {
        return new Position(xMax, yMax, zMax);
    }

    public int sizeX() {
        return xMax - xMin + 1;
    }

    public int sizeY() {
        return yMax - yMin + 1;
    }

    public int sizeZ() {
        return zMax - zMin + 1;
    }

    public double centerX() {
        return xMin + sizeX() / 2.0;
    }

    public double centerY() {
        return yMin + sizeY() / 2.0;
    }

    public double centerZ() {
        return zMin + sizeZ() / 2.0;
    }

    public Box rotateLeft() {
        Box nBox = new Box();
        nBox.xMin = (sizeZ() - 1) - zMin;
        nBox.yMin = yMin;
        nBox.zMin = xMin;

        nBox.xMax = (sizeZ() - 1) - zMax;
        nBox.yMax = yMax;
        nBox.zMax = xMax;

        nBox.reorder();

        return nBox;
    }

    public void reorder() {
        int tmp;

        if (xMin > xMax) {
            tmp = xMin;
            xMin = xMax;
            xMax = tmp;
        }

        if (yMin > yMax) {
            tmp = yMin;
            yMin = yMax;
            yMax = tmp;
        }

        if (zMin > zMax) {
            tmp = zMin;
            zMin = zMax;
            zMax = tmp;
        }
    }

    @Override
    public void createLasers(Level world, LaserKind kind) {
        if (lasers == null) {
            lasers = Utils.createLaserBox(world, xMin, yMin, zMin, xMax, yMax, zMax, kind);
        }
    }

    @Override
    public void deleteLasers() {
        if (lasers != null) {
            for (EntityBlock b : lasers) {
                if (b != null) {
                    b.setDead();
                }
            }

            lasers = null;
        }
    }

    /** The lasers made by createLasers, for the owner's BlockEntityRenderer; null when there are none. */
    public EntityBlock @Nullable [] getLasers() {
        return lasers;
    }

    public void writeToStream(FriendlyByteBuf stream) {
        stream.writeBoolean(initialized);
        stream.writeInt(xMin);
        stream.writeInt(yMin);
        stream.writeInt(zMin);
        stream.writeInt(xMax);
        stream.writeInt(yMax);
        stream.writeInt(zMax);
    }

    public void readFromStream(FriendlyByteBuf stream) {
        initialized = stream.readBoolean();
        xMin = stream.readInt();
        yMin = stream.readInt();
        zMin = stream.readInt();
        xMax = stream.readInt();
        yMax = stream.readInt();
        zMax = stream.readInt();
    }

    public void writeToNBT(ValueOutput nbttagcompound) {
        nbttagcompound.putInt("xMin", xMin);
        nbttagcompound.putInt("yMin", yMin);
        nbttagcompound.putInt("zMin", zMin);

        nbttagcompound.putInt("xMax", xMax);
        nbttagcompound.putInt("yMax", yMax);
        nbttagcompound.putInt("zMax", zMax);
    }

    @Override
    public String toString() {
        return "{" + xMin + ", " + xMax + "}, {" + yMin + ", " + yMax + "}, {" + zMin + ", " + zMax + "}";
    }

}
