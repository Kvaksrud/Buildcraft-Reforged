/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: World becomes Level; the id mapping methods are gone with IBptContext's
 * (plan section 13.3).
 */

package buildcraftreforged.core.blueprints;

import buildcraftreforged.api.blueprints.IBptContext;
import buildcraftreforged.api.core.IBox;
import buildcraftreforged.api.core.Position;
import buildcraftreforged.core.Box;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The world, blueprint and box a slot is captured from or built into. Ported from 4.2.2
 * {@code buildcraft.core.blueprints.BptContext}. {@code world} may be null where nothing reads the world, as when a
 * stored blueprint is only rotated or mirrored.
 */
public class BptContext implements IBptContext {

    @SuppressWarnings("unused")
    private @Nullable BptBlueprint bpt;
    private Box box;
    private @Nullable Level world;

    public BptContext(@Nullable Level world, @Nullable BptBlueprint bpt, Box box) {
        this.bpt = bpt;
        this.box = box;
        this.world = world;
    }

    @Override
    public Position rotatePositionLeft(Position pos) {
        return new Position((box.sizeZ() - 1) - pos.z, pos.y, pos.x);
    }

    @Override
    public IBox surroundingBox() {
        return box;
    }

    @Override
    public Level world() {
        return world;
    }

    public void rotateLeft() {
        box = box.rotateLeft();
    }
}
