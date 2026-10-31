/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: fluids named by registry id, solid coolants keyed by Item, the numeric id
 * overload removed; lookups read the combustion_coolants and solid_coolants data maps first.
 */
package buildcraftreforged.api.fuels;

import buildcraftreforged.api.fuels.FuelDataMaps.CoolantData;
import buildcraftreforged.api.fuels.FuelDataMaps.SolidCoolantData;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Combustion engine coolants. Ported from 4.2.2 {@code buildcraft.api.fuels.IronEngineCoolant}.
 *
 * <p>4.2.2 keyed solid coolants by {@code StackWrapper} (item id and damage). Items carry no damage-as-variant after
 * the flattening, so the key is the {@link Item}. Fluid names are registry ids, as in {@link IronEngineFuel}.
 *
 * <p>Lookups read the {@link FuelDataMaps#COMBUSTION_COOLANTS} and {@link FuelDataMaps#SOLID_COOLANTS} data maps
 * first and fall back to {@link #liquidCoolants} and {@link #solidCoolants}, which keep the 4.2.2 code registration.
 */
public final class IronEngineCoolant {

    public static Map<String, Coolant> liquidCoolants = new HashMap<String, Coolant>();
    public static Map<Item, FluidStack> solidCoolants = new HashMap<Item, FluidStack>();

    public static FluidStack getFluidCoolant(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        SolidCoolantData data = BuiltInRegistries.ITEM.wrapAsHolder(stack.getItem()).getData(FuelDataMaps.SOLID_COOLANTS);
        return data != null ? data.toFluidStack() : solidCoolants.get(stack.getItem());
    }

    public static Coolant getCoolant(ItemStack stack) {
        return getCoolant(getFluidCoolant(stack));
    }

    public static Coolant getCoolant(FluidStack fluidStack) {
        return fluidStack != null && !fluidStack.isEmpty() ? getCoolant(fluidStack.getFluid()) : null;
    }

    private static Coolant getCoolant(Fluid fluid) {
        final CoolantData data = BuiltInRegistries.FLUID.wrapAsHolder(fluid).getData(FuelDataMaps.COMBUSTION_COOLANTS);
        if (data != null) {
            return new Coolant() {
                @Override
                public float getDegreesCoolingPerMB(float currentHeat) {
                    return data.degreesCoolingPerMB();
                }
            };
        }
        return liquidCoolants.get(getName(fluid));
    }

    private IronEngineCoolant() {
    }

    public static interface Coolant {

        float getDegreesCoolingPerMB(float currentHeat);
    }

    public static void addCoolant(final Fluid fluid, final float degreesCoolingPerMB) {
        if (fluid != null) {
            liquidCoolants.put(getName(fluid), new Coolant() {
                @Override
                public float getDegreesCoolingPerMB(float currentHeat) {
                    return degreesCoolingPerMB;
                }
            });
        }
    }

    /**
     * Adds a solid coolant like Ice Blocks. The FluidStack must contain a registered
     * Coolant Fluid or nothing will happen. You do not need to call this for
     * Fluid Containers.
     *
     * <p>4.2.2 also had {@code addCoolant(int itemId, int metadata, FluidStack)}; numeric ids are gone, use this one.
     *
     * @param stack
     * @param coolant
     */
    public static void addCoolant(final ItemStack stack, final FluidStack coolant) {
        if (stack != null && !stack.isEmpty() && coolant != null) {
            solidCoolants.put(stack.getItem(), coolant);
        }
    }

    public static boolean isCoolant(Fluid fluid) {
        return getCoolant(fluid) != null;
    }

    private static String getName(Fluid fluid) {
        return BuiltInRegistries.FLUID.getKey(fluid).toString();
    }
}
