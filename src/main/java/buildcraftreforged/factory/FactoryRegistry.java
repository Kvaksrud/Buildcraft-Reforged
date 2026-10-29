package buildcraftreforged.factory;

import buildcraftreforged.core.ModuleRegisters;
import net.neoforged.bus.api.IEventBus;

/**
 * Registry entries of the factory module, the registration half of 4.2.2's BuildCraftFactory. Add entries here as the module
 * is ported, following the naming rules in {@link buildcraftreforged.core.CoreRegistry}.
 */
public final class FactoryRegistry {
    public static final ModuleRegisters REGISTERS = new ModuleRegisters();

    private FactoryRegistry() {
    }

    public static void register(IEventBus modBus) {
        REGISTERS.register(modBus);
    }
}
