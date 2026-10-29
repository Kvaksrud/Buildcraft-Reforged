package buildcraftreforged;

import buildcraftreforged.builders.BuildersRegistry;
import buildcraftreforged.core.BuildCraftConfiguration;
import buildcraftreforged.core.CoreRegistry;
import buildcraftreforged.core.network.PacketHandler;
import buildcraftreforged.energy.EnergyRegistry;
import buildcraftreforged.factory.FactoryRegistry;
import buildcraftreforged.silicon.SiliconRegistry;
import buildcraftreforged.transport.TransportRegistry;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(BuildcraftReforged.MOD_ID)
public final class BuildcraftReforged {
    public static final String MOD_ID = "buildcraftreforged";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BuildcraftReforged(IEventBus modBus, ModContainer container) {
        LOGGER.info("Buildcraft Reforged {} loading", container.getModInfo().getVersion());

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
