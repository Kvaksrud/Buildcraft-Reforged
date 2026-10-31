package buildcraftreforged.energy;

import buildcraftreforged.api.fuels.FuelDataMaps;
import buildcraftreforged.api.fuels.IronEngineFuel;
import buildcraftreforged.core.BuildCraftConfiguration;
import buildcraftreforged.core.ModuleRegisters;
import net.neoforged.bus.api.IEventBus;

/**
 * Registry entries of the energy module, the registration half of 4.2.2's BuildCraftEnergy. Add entries here as the module
 * is ported, following the naming rules in {@link buildcraftreforged.core.CoreRegistry}.
 */
public final class EnergyRegistry {
    public static final ModuleRegisters REGISTERS = new ModuleRegisters();

    private EnergyRegistry() {
    }

    public static void register(IEventBus modBus) {
        REGISTERS.register(modBus);

        // Iron Engine Fuels and Coolants (#43): the values are data (FuelDataMaps); the 4.2.2 config multipliers
        // scale the oil and fuel burning times when the engine looks them up.
        modBus.addListener(FuelDataMaps::register);
        IronEngineFuel.burningTimeMultipliers.put("buildcraftreforged:oil", BuildCraftConfiguration.fuelOilMultiplier::get);
        IronEngineFuel.burningTimeMultipliers.put("buildcraftreforged:fuel", BuildCraftConfiguration.fuelFuelMultiplier::get);
    }
}
