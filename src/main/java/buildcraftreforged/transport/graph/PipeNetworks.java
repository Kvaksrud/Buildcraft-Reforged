package buildcraftreforged.transport.graph;

import buildcraftreforged.BuildcraftReforged;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.jspecify.annotations.Nullable;

/**
 * One transient {@link PipeNetworkManager} per server level (docs/TRANSPORT.md 3.4), created on first use and dropped
 * when the level unloads. Server thread only.
 */
@EventBusSubscriber(modid = BuildcraftReforged.MOD_ID)
public final class PipeNetworks {
    private static final Map<ServerLevel, PipeNetworkManager> MANAGERS = new WeakHashMap<>();

    private PipeNetworks() {
    }

    public static PipeNetworkManager get(ServerLevel level) {
        return MANAGERS.computeIfAbsent(level, l -> new PipeNetworkManager(new LevelPipeWorld(l)));
    }

    /** The network containing the pipe at {@code pos}, or null (see {@link PipeNetworkManager#get(long)}). */
    public static @Nullable PipeNetwork get(ServerLevel level, BlockPos pos) {
        return get(level).get(pos);
    }

    /**
     * Invalidates the networks at and around {@code pos}. A no-op on the client and where no manager exists yet,
     * since nothing is cached there; so it never creates a manager for a level that is shutting down.
     */
    public static void invalidate(@Nullable Level level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel) {
            PipeNetworkManager manager = MANAGERS.get(serverLevel);
            if (manager != null)
                manager.invalidate(pos);
        }
    }

    @SubscribeEvent
    static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            PipeNetworkManager manager = MANAGERS.remove(level);
            if (manager != null)
                manager.clear();
        }
    }
}
