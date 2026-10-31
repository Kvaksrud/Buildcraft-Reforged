package buildcraftreforged.transport.route;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.api.transport.IPipeTile.PipeType;
import buildcraftreforged.transport.PipeModuleType;
import buildcraftreforged.transport.graph.PipeNetwork;
import buildcraftreforged.transport.graph.PipeNetworks;
import buildcraftreforged.transport.graph.PipeNode;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * The source tick of docs/TRANSPORT.md 6.1: once per server level tick, after block entities have ticked, every
 * network's source pipes make one transfer attempt each, starting at {@code sourceCursor} and wrapping, and the cursor
 * moves on by one so no source is always first. Source pipes ask for their network on their own tick
 * ({@code TilePipe.updateEntity}), so every source's network exists here.
 */
@EventBusSubscriber(modid = BuildcraftReforged.MOD_ID)
public final class PipeSources {

    private static final Direction[] SIDES = Direction.values();

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

    private static void tickSource(ServerLevel level, PipeNetwork network, int node) {
        PipeNode pipe = network.nodes[node];
        if (network.kind == PipeType.ITEM && pipe.module == PipeModuleType.EXTRACTOR) {
            // Until #55 saves a facing in the module, an Extractor pulls from the first side, in side order, that leads
            // to an endpoint with an item handler (4.2.2 PipeLogicWood.switchSource keeps the first valid one).
            for (Direction side : SIDES) {
                int next = pipe.next(side);
                ResourceHandler<ItemResource> source = PipeNode.isEndpoint(next)
                        ? ItemRouting.handler(network.endpoints[PipeNode.endpointIndex(next)]) : null;
                if (source != null && source.size() > 0) {
                    ItemRouting.extract(level, network, node, side, source);
                    break;
                }
            }
        }
        // SEAM(#55): Filtered Extractor and the Extractor's saved facing; SEAM(#56): Pickup; SEAM(#51): fluid Extractor.
    }
}
