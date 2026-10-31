package buildcraftreforged.transport.route;

import buildcraftreforged.core.BuildCraftConfiguration;
import buildcraftreforged.transport.TilePipe;
import buildcraftreforged.transport.flow.FlowAccumulator;
import buildcraftreforged.transport.graph.PipeNetwork;
import buildcraftreforged.transport.graph.PipeNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * Item transfers through item pipe networks (docs/TRANSPORT.md 4-6): pushes from blocks into a pipe (6.2) and pulls by
 * an extracting source (8.2). Budgets are in item-ticks, 1/20 item, refilled by the tier's items per second each tick
 * (5.1), so a wood pipe gains 1 a tick and spends 20 per item.
 */
public final class ItemRouting {

    /** Budget units per item (docs/TRANSPORT.md 5.1). */
    public static final int ITEM_COST = 20;

    public static final ResourceRouter<ItemResource> ROUTER = new ResourceRouter<>(ITEM_COST, new ResourceRouter.Pipes() {
        @Override
        public PipeBudget budget(PipeNode node) {
            return ((TilePipe) node.pipe).getBudget();
        }

        @Override
        public long refill(PipeNode node) {
            return ((TilePipe) node.pipe).getTierStats().itemsPerSecond();
        }

        @Override
        public FlowAccumulator flow(PipeNode node) {
            return ((TilePipe) node.pipe).getFlowTracker().accumulator;
        }
    }, ItemRouting::handler);

    private ItemRouting() {
    }

    /** The live item handler of an endpoint, or null when the block no longer has one. */
    @SuppressWarnings("unchecked")
    public static @Nullable ResourceHandler<ItemResource> handler(PipeNetwork.Endpoint endpoint) {
        return endpoint.cache() == null ? null : (ResourceHandler<ItemResource>) endpoint.cache().getCapability();
    }

    /** The configured {@code transport.burstSeconds} and {@code transport.maxRouteVisits}, defaults before load. */
    public static ResourceRouter.Settings settings() {
        if (!BuildCraftConfiguration.COMMON_SPEC.isLoaded())
            return ResourceRouter.Settings.DEFAULTS;
        return new ResourceRouter.Settings(BuildCraftConfiguration.pipeBurstSeconds.get(),
                BuildCraftConfiguration.pipeMaxRouteVisits.get());
    }

    /**
     * A push into the pipe of node {@code start} on side {@code in} from the block beside it: walks, inserts at the
     * destination and spends budgets in {@code transaction}. Returns what was moved.
     */
    public static TransferResult push(ServerLevel level, PipeNetwork network, int start, Direction in,
            ItemResource resource, int amount, TransactionContext transaction) {
        return ROUTER.push(network, start, in, BlockPos.offset(network.nodes[start].pos, in), resource, amount,
                level.getGameTime(), level.getRandom(), settings(), transaction);
    }

    /**
     * One attempt of an extracting source at node {@code node} pulling from {@code source}, the handler of the block on
     * the pipe's side {@code facing} (docs/TRANSPORT.md 8.2, Q13): up to {@code floor(budget / 20)} items from the first
     * slot whose stack has a destination, one successful transfer at most. The pull and the insert are one
     * transaction, so nothing is ever left between the two.
     */
    public static TransferResult extract(ServerLevel level, PipeNetwork network, int node, Direction facing,
            ResourceHandler<ItemResource> source) {
        return extract(level, network, node, facing, source, resource -> true);
    }

    /**
     * {@link #extract(ServerLevel, PipeNetwork, int, Direction, ResourceHandler)} from the slots whose resource passes
     * {@code filter} only (the Filtered Extractor, docs/TRANSPORT.md 8.3). SOURCE_EMPTY when no slot matches.
     */
    public static TransferResult extract(ServerLevel level, PipeNetwork network, int node, Direction facing,
            ResourceHandler<ItemResource> source, Predicate<ItemResource> filter) {
        return extract(level, network, node, facing, source, filter, PipeWalk.NO_COLOUR);
    }

    /**
     * {@link #extract(ServerLevel, PipeNetwork, int, Direction, ResourceHandler, Predicate)} with the items tagged
     * {@code colour} for the walk (the Filtered Colour Tag, docs/TRANSPORT.md 8.10).
     */
    public static TransferResult extract(ServerLevel level, PipeNetwork network, int node, Direction facing,
            ResourceHandler<ItemResource> source, Predicate<ItemResource> filter, int colour) {
        ResourceRouter.Settings settings = settings();
        long now = level.getGameTime();
        long items = ROUTER.available(network.nodes[node], now, settings) / ITEM_COST;
        if (items <= 0)
            return new TransferResult(0, TransferResult.Reason.NO_CAPACITY, null);
        long sourcePos = BlockPos.offset(network.nodes[node].pos, facing);

        TransferResult result = TransferResult.SOURCE_EMPTY;
        for (int slot = 0; slot < source.size(); slot++) {
            ItemResource resource = source.getResource(slot);
            if (resource.isEmpty() || !filter.test(resource))
                continue;
            int want = (int) Math.min(items, source.getAmountAsInt(slot));
            if (want <= 0)
                continue;
            try (Transaction transaction = Transaction.openRoot()) {
                int offered;
                try (Transaction simulation = Transaction.open(transaction)) {
                    offered = source.extract(slot, resource, want, simulation);
                }
                if (offered <= 0)
                    continue;
                TransferResult attempt = ROUTER.push(network, node, facing, sourcePos, resource, offered, now,
                        level.getRandom(), settings, pipe -> true, colour, transaction);
                if (!attempt.movedAny()) {
                    if (attempt.reason() == TransferResult.Reason.NO_CAPACITY)
                        return attempt;
                    result = attempt;
                    continue;
                }
                if (source.extract(slot, resource, attempt.moved(), transaction) != attempt.moved())
                    continue; // The source gave less than it promised: roll the whole transfer back.
                transaction.commit();
                return attempt;
            }
        }
        return result;
    }

    /**
     * One pulse of the Basic Extractor (docs/TRANSPORT.md 10, #58): like {@link #extract(ServerLevel, PipeNetwork, int,
     * Direction, ResourceHandler)} from the block beside the pipe of node {@code node} on side {@code facing}, but at
     * most {@code maxItems}, and inside {@code outer}, the transaction the engine's pulse arrived in: nothing moves
     * unless that commits. The first slot whose stack routes wins (Q13).
     */
    public static TransferResult extract(ServerLevel level, PipeNetwork network, int node, Direction facing,
            ResourceHandler<ItemResource> source, int maxItems, TransactionContext outer) {
        ResourceRouter.Settings settings = settings();
        long now = level.getGameTime();
        long items = Math.min(maxItems, ROUTER.available(network.nodes[node], now, settings) / ITEM_COST);
        if (items <= 0)
            return new TransferResult(0, TransferResult.Reason.NO_CAPACITY, null);
        long sourcePos = BlockPos.offset(network.nodes[node].pos, facing);

        TransferResult result = TransferResult.SOURCE_EMPTY;
        for (int slot = 0; slot < source.size(); slot++) {
            ItemResource resource = source.getResource(slot);
            if (resource.isEmpty())
                continue;
            int want = (int) Math.min(items, source.getAmountAsInt(slot));
            if (want <= 0)
                continue;
            try (Transaction transaction = Transaction.open(outer)) {
                int offered;
                try (Transaction simulation = Transaction.open(transaction)) {
                    offered = source.extract(slot, resource, want, simulation);
                }
                if (offered <= 0)
                    continue;
                TransferResult attempt = ROUTER.push(network, node, facing, sourcePos, resource, offered, now,
                        level.getRandom(), settings, pipe -> true, PipeWalk.NO_COLOUR, transaction);
                if (!attempt.movedAny()) {
                    if (attempt.reason() == TransferResult.Reason.NO_CAPACITY)
                        return attempt;
                    result = attempt;
                    continue;
                }
                if (source.extract(slot, resource, attempt.moved(), transaction) != attempt.moved())
                    continue;
                transaction.commit();
                return attempt;
            }
        }
        return result;
    }
}
