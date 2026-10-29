/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: fluids named by registry id, power in FE.
 */
package buildcraftreforged.api.fuels;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;

/**
 * Combustion engine fuels. Ported from 4.2.2 {@code buildcraft.api.fuels.IronEngineFuel}.
 *
 * <p>Fluid names are registry ids ({@code buildcraftreforged:oil}) where 4.2.2 used the FluidRegistry name
 * ({@code oil}).
 */
public class IronEngineFuel {

    public static Map<String, Fuel> fuels = new HashMap<String, Fuel>();

    public static Fuel getFuelForFluid(Fluid liquid) {
        return liquid == null ? null : fuels.get(BuiltInRegistries.FLUID.getKey(liquid).toString());
    }

    private IronEngineFuel() {
    }

    public static class Fuel {

        public final Fluid liquid;
        /** FE per engine cycle (4.2.2: MJ; 1 MJ = 10 FE, plan section 7). */
        public final float powerPerCycle;
        public final int totalBurningTime;

        /** Looks the fluid up when called, as 4.2.2 did; register the fluid first. */
        private Fuel(String fluidName, float powerPerCycle, int totalBurningTime) {
            this(BuiltInRegistries.FLUID.getValue(Identifier.parse(fluidName)), powerPerCycle, totalBurningTime);
        }

        private Fuel(Fluid liquid, float powerPerCycle, int totalBurningTime) {
            this.liquid = liquid;
            this.powerPerCycle = powerPerCycle;
            this.totalBurningTime = totalBurningTime;
        }
    }

    /** @param powerPerCycle FE per engine cycle (4.2.2: MJ). */
    public static void addFuel(Fluid fluid, float powerPerCycle, int totalBurningTime) {
        fuels.put(BuiltInRegistries.FLUID.getKey(fluid).toString(), new Fuel(fluid, powerPerCycle, totalBurningTime));
    }

    /**
     * @param fluidName fluid registry id, such as {@code buildcraftreforged:fuel}
     * @param powerPerCycle FE per engine cycle (4.2.2: MJ).
     */
    public static void addFuel(String fluidName, float powerPerCycle, int totalBurningTime) {
        fuels.put(fluidName, new Fuel(fluidName, powerPerCycle, totalBurningTime));
    }
}
