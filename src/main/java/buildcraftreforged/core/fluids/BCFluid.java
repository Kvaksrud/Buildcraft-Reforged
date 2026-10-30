/*
 * Copyright (c) SpaceToad, 2011-2012
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: Forge's Fluid carried both the fluid and its name; the name now lives on
 * NeoForge's FluidType, so BCFluid is the FluidType and keeps the 4.2.2 translation key "fluid." + name.
 */
package buildcraftreforged.core.fluids;

import net.neoforged.neoforge.fluids.FluidType;

/**
 *
 * @author CovertJaguar <http://www.railcraft.info/>
 */
public class BCFluid extends FluidType {

    public BCFluid(String name, FluidType.Properties properties) {
        super(properties.descriptionId("fluid." + name));
    }
}
