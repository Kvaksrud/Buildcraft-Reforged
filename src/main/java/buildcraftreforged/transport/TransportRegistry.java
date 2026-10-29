package buildcraftreforged.transport;

import buildcraftreforged.core.ModuleRegisters;
import net.neoforged.bus.api.IEventBus;

/**
 * Registry entries of the transport module, the registration half of 4.2.2's BuildCraftTransport. Add entries here as the module
 * is ported, following the naming rules in {@link buildcraftreforged.core.CoreRegistry}.
 */
public final class TransportRegistry {
    public static final ModuleRegisters REGISTERS = new ModuleRegisters();

    private TransportRegistry() {
    }

    public static void register(IEventBus modBus) {
        REGISTERS.register(modBus);
    }
}
