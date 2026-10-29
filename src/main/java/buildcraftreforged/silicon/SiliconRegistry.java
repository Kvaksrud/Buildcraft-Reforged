package buildcraftreforged.silicon;

import buildcraftreforged.core.ModuleRegisters;
import net.neoforged.bus.api.IEventBus;

/**
 * Registry entries of the silicon module, the registration half of 4.2.2's BuildCraftSilicon. Add entries here as the module
 * is ported, following the naming rules in {@link buildcraftreforged.core.CoreRegistry}.
 */
public final class SiliconRegistry {
    public static final ModuleRegisters REGISTERS = new ModuleRegisters();

    private SiliconRegistry() {
    }

    public static void register(IEventBus modBus) {
        REGISTERS.register(modBus);
    }
}
