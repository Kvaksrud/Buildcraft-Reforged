package buildcraftreforged.builders;

import buildcraftreforged.core.ModuleRegisters;
import net.neoforged.bus.api.IEventBus;

/**
 * Registry entries of the builders module, the registration half of 4.2.2's BuildCraftBuilders. Add entries here as the module
 * is ported, following the naming rules in {@link buildcraftreforged.core.CoreRegistry}.
 */
public final class BuildersRegistry {
    public static final ModuleRegisters REGISTERS = new ModuleRegisters();

    private BuildersRegistry() {
    }

    public static void register(IEventBus modBus) {
        REGISTERS.register(modBus);
    }
}
