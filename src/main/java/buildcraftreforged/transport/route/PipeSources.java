package buildcraftreforged.transport.route;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.transport.TilePipe;
import buildcraftreforged.transport.graph.PipeNetwork;
import buildcraftreforged.transport.graph.PipeNetworks;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * The source tick of docs/TRANSPORT.md 6.1: once per server level tick, after block entities have ticked, every
 * network's source pipes make one transfer attempt each, starting at {@code sourceCursor} and wrapping, and the cursor
 * moves on by one so no source is always first. Source pipes ask for their network on their own tick
 * ({@code TilePipe.updateEntity}), so every source's network exists here.
 */
@EventBusSubscriber(modid = BuildcraftReforged.MOD_ID)
public final class PipeSources {

    private PipeSources() {
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level)
            tick(level);
    }

    public static void tick(ServerLevel level) {
        for (PipeNetwork network : PipeNetworks.get(level).networks().toArray(PipeNetwork[]::new)) {
            int count = network.sourceNodes.length;
            if (count == 0 || !network.isValid())
                continue;
            int first = Math.floorMod(network.sourceCursor, count);
            for (int i = 0; i < count && network.isValid(); i++) {
                tickSource(level, network, network.sourceNodes[(first + i) % count]);
            }
            network.sourceCursor = (first + 1) % count;
        }
    }

    /** One attempt of the source module on the node's pipe ({@code PipeModule.tickSource}). */
    private static void tickSource(ServerLevel level, PipeNetwork network, int node) {
        if (network.nodes[node].pipe instanceof TilePipe pipe && pipe.getModule() != null && !pipe.isRemoved())
            pipe.getModule().tickSource(level, network, node, pipe);
    }
}
