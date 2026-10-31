/**
 * Copyright (c) SpaceToad, 2011-2012
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: World becomes Level and AxisAlignedBB AABB; missingMaterials (status panel)
 * is new.
 */

package buildcraftreforged.core.blueprints;

import buildcraftreforged.api.core.IAreaProvider;
import buildcraftreforged.core.Box;
import buildcraftreforged.core.IBuilderInventory;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

public abstract class BptBuilderBase implements IAreaProvider {

    public BptBase bluePrint;
    int x, y, z;
    public boolean done;
    protected BptContext context;
    /**
     * Not in 4.2.2: the last getNextBlock found slots to build but not their items in the inventory (the builder's
     * Missing materials status).
     */
    public boolean missingMaterials;

    public BptBuilderBase(BptBase bluePrint, Level world, int x, int y, int z) {
        this.bluePrint = bluePrint;
        this.x = x;
        this.y = y;
        this.z = z;
        done = false;

        Box box = new Box();
        box.initialize(this);

        if (bluePrint instanceof BptBlueprint) {
            context = new BptContext(world, (BptBlueprint) bluePrint, box);
        } else {
            context = new BptContext(world, null, box);
        }
    }

    public abstract @Nullable BptSlot getNextBlock(Level world, IBuilderInventory inv);

    @Override
    public int xMin() {
        return x - bluePrint.anchorX;
    }

    @Override
    public int yMin() {
        return y - bluePrint.anchorY;
    }

    @Override
    public int zMin() {
        return z - bluePrint.anchorZ;
    }

    @Override
    public int xMax() {
        return x + bluePrint.sizeX - bluePrint.anchorX - 1;
    }

    @Override
    public int yMax() {
        return y + bluePrint.sizeY - bluePrint.anchorY - 1;
    }

    @Override
    public int zMax() {
        return z + bluePrint.sizeZ - bluePrint.anchorZ - 1;
    }

    @Override
    public void removeFromWorld() {

    }

    public AABB getBoundingBox() {
        return new AABB(xMin(), yMin(), zMin(), xMax(), yMax(), zMax());
    }

    public void postProcessing(Level world) {

    }

    public BptContext getContext() {
        return context;
    }
}
