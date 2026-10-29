/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: fluids named by registry id, solid coolants keyed by Item, the numeric id
 * overload removed.
 */
package buildcraftreforged.api.fuels;

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
 */
public final class IronEngineCoolant {

    public static Map<String, Coolant> liquidCoolants = new HashMap<String, Coolant>();
    public static Map<Item, FluidStack> solidCoolants = new HashMap<Item, FluidStack>();

    public static FluidStack getFluidCoolant(ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : solidCoolants.get(stack.getItem());
    }

    public static Coolant getCoolant(ItemStack stack) {
        return getCoolant(getFluidCoolant(stack));
    }

    public static Coolant getCoolant(FluidStack fluidStack) {
        return fluidStack != null && !fluidStack.isEmpty() ? liquidCoolants.get(getName(fluidStack.getFluid())) : null;
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
        return liquidCoolants.containsKey(getName(fluid));
    }

    private static String getName(Fluid fluid) {
        return BuiltInRegistries.FLUID.getKey(fluid).toString();
    }
}
