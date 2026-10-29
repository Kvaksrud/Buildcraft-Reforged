package buildcraftreforged;

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
    }
}
