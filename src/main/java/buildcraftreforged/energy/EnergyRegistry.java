package buildcraftreforged.energy;

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
    }
}
