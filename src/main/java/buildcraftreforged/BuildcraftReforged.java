package buildcraftreforged;

import buildcraftreforged.builders.BuildersRegistry;
import buildcraftreforged.core.BuildCraftConfiguration;
import buildcraftreforged.core.CoreRegistry;
import buildcraftreforged.core.network.PacketHandler;
import buildcraftreforged.core.utils.BCLog;
import buildcraftreforged.energy.EnergyRegistry;
import buildcraftreforged.factory.FactoryRegistry;
import buildcraftreforged.silicon.SiliconRegistry;
import buildcraftreforged.transport.TransportRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(BuildcraftReforged.MOD_ID)
public final class BuildcraftReforged {
    public static final String MOD_ID = "buildcraftreforged";
    public static final Logger LOGGER = BCLog.logger;

    public BuildcraftReforged(IEventBus modBus, ModContainer container) {
        BCLog.initLog(container.getModInfo().getVersion().toString());

        // One registry class per module; each adds its own entries in its own file.
        CoreRegistry.register(modBus);
        EnergyRegistry.register(modBus);
        FactoryRegistry.register(modBus);
        SiliconRegistry.register(modBus);
        TransportRegistry.register(modBus);
        BuildersRegistry.register(modBus);

        BuildCraftConfiguration.register(container);

        // One payload channel for every module (core/network/PacketHandler).
        PacketHandler.register(modBus);
    }
}
