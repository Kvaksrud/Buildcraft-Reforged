/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: id mapping methods removed (plan section 13.3); Level in place of World.
 */

package buildcraftreforged.api.blueprints;

import buildcraftreforged.api.core.IBox;
import buildcraftreforged.api.core.Position;
import net.minecraft.world.level.Level;

/**
 * This interface provide contextual information when building or initializing blueprint slots.
 *
 * <p>Ported from 4.2.2 {@code buildcraft.api.blueprints.IBptContext}. 4.2.2 also had {@code mapItemStack},
 * {@code mapWorldId} and {@code storeId}, which translated numeric ids between the blueprint's world and this one.
 * Block states and item stacks are saved by name now, so there is nothing to translate (plan section 13.3).
 */
public interface IBptContext {

    public Position rotatePositionLeft(Position pos);

    public IBox surroundingBox();

    public Level world();
}
