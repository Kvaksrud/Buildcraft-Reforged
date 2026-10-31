package buildcraftreforged.gametest;

import buildcraftreforged.api.fuels.IronEngineCoolant;
import buildcraftreforged.api.fuels.IronEngineFuel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * #43: the combustion engine fuel and coolant lookups read the loaded data maps. The GameTest data pack adds lava as
 * a fuel (the 4.2.2 commented-out value) behind a {@code neoforge:registered} condition, plus an entry for a fluid
 * that does not exist, which the condition skips.
 */
final class FuelTests {
    private FuelTests() {
    }

    static void fuels(GameTestHelper helper) {
        // Water and ice, from the mod's own data.
        helper.assertTrue(IronEngineCoolant.isCoolant(Fluids.WATER), "water is not a coolant");
        IronEngineCoolant.Coolant water = IronEngineCoolant.getCoolant(new FluidStack(Fluids.WATER, 1));
        helper.assertValueEqual(0.0023F, water.getDegreesCoolingPerMB(0), "water cooling per mB");
        FluidStack ice = IronEngineCoolant.getFluidCoolant(new ItemStack(Items.ICE));
        helper.assertTrue(FluidStack.matches(new FluidStack(Fluids.WATER, 2000), ice), "ice is not 2000 mB of water: " + ice);
        helper.assertTrue(IronEngineCoolant.getCoolant(new ItemStack(Items.ICE)) != null, "ice does not cool");
        helper.assertFalse(IronEngineCoolant.isCoolant(Fluids.LAVA), "lava is a coolant");
        helper.assertTrue(IronEngineCoolant.getFluidCoolant(new ItemStack(Items.PACKED_ICE)) == null, "packed ice is a coolant");

        // Lava, from the GameTest data pack: a conditional entry that loaded.
        IronEngineFuel.Fuel lava = IronEngineFuel.getFuelForFluid(Fluids.LAVA);
        helper.assertTrue(lava != null, "the GameTest data pack's lava fuel did not load");
        helper.assertValueEqual(Fluids.LAVA, lava.liquid, "lava fuel fluid");
        helper.assertValueEqual(10F, lava.powerPerCycle, "lava FE per cycle");
        helper.assertValueEqual(20000, lava.totalBurningTime, "lava burning time");
        helper.assertTrue(IronEngineFuel.getFuelForFluid(Fluids.WATER) == null, "water is a fuel");

        // Data wins over a code registration of the same fluid; the multiplier scales the burning time.
        IronEngineFuel.addFuel(Fluids.LAVA, 99, 1);
        IronEngineFuel.burningTimeMultipliers.put("minecraft:lava", () -> 0.5);
        try {
            lava = IronEngineFuel.getFuelForFluid(Fluids.LAVA);
            helper.assertValueEqual(10F, lava.powerPerCycle, "lava FE per cycle with a code registration");
            helper.assertValueEqual(10000, lava.totalBurningTime, "lava burning time at multiplier 0.5");
        } finally {
            IronEngineFuel.fuels.remove("minecraft:lava");
            IronEngineFuel.burningTimeMultipliers.remove("minecraft:lava");
        }

        // Oil and fuel are #42's fluids; once they exist, the mod's data gives them the 4.2.2 values × 10, scaled by
        // fuel.oil.combustion and fuel.fuel.combustion (default 1).
        assertFuel(helper, "buildcraftreforged:oil", 30F, 5000);
        assertFuel(helper, "buildcraftreforged:fuel", 60F, 25000);
        helper.succeed();
    }

    private static void assertFuel(GameTestHelper helper, String name, float powerPerCycle, int totalBurningTime) {
        Fluid fluid = BuiltInRegistries.FLUID.getOptional(Identifier.parse(name)).orElse(null);
        if (fluid == null) {
            return;
        }
        IronEngineFuel.Fuel fuel = IronEngineFuel.getFuelForFluid(fluid);
        helper.assertTrue(fuel != null, name + " is not a fuel");
        helper.assertValueEqual(powerPerCycle, fuel.powerPerCycle, name + " FE per cycle");
        helper.assertValueEqual(totalBurningTime, fuel.totalBurningTime, name + " burning time");
    }
}
