/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: fluids named by registry id, power in FE; lookups read the
 * combustion_fuels data map first, with per-fluid burning time multipliers.
 */
package buildcraftreforged.api.fuels;

import buildcraftreforged.api.fuels.FuelDataMaps.FuelData;
import java.util.HashMap;
import java.util.Map;
import java.util.function.DoubleSupplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;

/**
 * Combustion engine fuels. Ported from 4.2.2 {@code buildcraft.api.fuels.IronEngineFuel}.
 *
 * <p>Fluid names are registry ids ({@code buildcraftreforged:oil}) where 4.2.2 used the FluidRegistry name
 * ({@code oil}).
 *
 * <p>{@link #getFuelForFluid} reads the {@link FuelDataMaps#COMBUSTION_FUELS} data map first and falls back to
 * {@link #fuels}, which keeps the 4.2.2 code registration for other mods. A data-map fuel's burning time is scaled by
 * the fluid's entry in {@link #burningTimeMultipliers}, as 4.2.2 scaled oil and fuel by {@code fuel.oil.combustion} and
 * {@code fuel.fuel.combustion} when it registered them.
 */
public class IronEngineFuel {

    public static Map<String, Fuel> fuels = new HashMap<String, Fuel>();
    /** Burning time multipliers for data-map fuels, by fluid registry id. New in this port. */
    public static Map<String, DoubleSupplier> burningTimeMultipliers = new HashMap<String, DoubleSupplier>();

    public static Fuel getFuelForFluid(Fluid liquid) {
        if (liquid == null) {
            return null;
        }
        String name = BuiltInRegistries.FLUID.getKey(liquid).toString();
        FuelData data = BuiltInRegistries.FLUID.wrapAsHolder(liquid).getData(FuelDataMaps.COMBUSTION_FUELS);
        if (data != null) {
            DoubleSupplier multiplier = burningTimeMultipliers.get(name);
            int totalBurningTime = multiplier == null ? data.totalBurningTime()
                    : (int) (data.totalBurningTime() * multiplier.getAsDouble());
            return new Fuel(liquid, data.powerPerCycle(), totalBurningTime);
        }
        return fuels.get(name);
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
