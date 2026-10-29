/*
 * Copyright (c) SpaceToad, 2011-2012
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: ForgeDirection becomes Direction; emitting sides refuse Forge Energy.
 */
package buildcraftreforged.api.power;

import net.minecraft.core.Direction;

/**
 * Essentially only used for Wooden Power Pipe connection rules.
 *
 * This Tile Entity interface allows you to indicate that a block can emit power
 * from a specific side.
 *
 * {@link PowerEnergyHandler} refuses Forge Energy on a side this returns true for (an engine's output side).
 *
 * @author CovertJaguar <http://www.railcraft.info/>
 */
public interface IPowerEmitter {

    public boolean canEmitPowerFrom(Direction side);
}
