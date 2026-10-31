package buildcraftreforged.transport.module;

import buildcraftreforged.transport.PipeModuleType;
import buildcraftreforged.transport.TilePipe;
import buildcraftreforged.transport.graph.PipeNetwork;
import buildcraftreforged.transport.route.ItemRouting;
import buildcraftreforged.transport.route.PipeWalk;
import buildcraftreforged.transport.route.ResourceRouter;
import buildcraftreforged.transport.route.TransferResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * The Pickup (docs/TRANSPORT.md 8.6; 4.2.2 {@code PipeItemsObsidian}): a source that pulls item entities into the
 * network. It works only with exactly one connection; its open side is the opposite one. Each tick it looks in the
 * pipe's own block (4.2.2's touch, distance 0), then in slabs 1 to {@link #range} blocks out on the open side, nearest
 * first, and routes from the first entity that moves anything: an item entity, an arrow that may be picked up, or (at
 * distance 1) a container minecart's first stack. It moves up to the pipe's budget in items, at the tier rate, with no
 * engine; what cannot be routed stays where it is.
 *
 * <p>4.2.2 also skipped the last 32 entities the pipe itself had dropped ({@code entitiesDropped}); a module never
 * drops anything, so there is nothing to skip.
 */
public class ModulePickup extends PipeModule {

    ModulePickup() {
        super(PipeModuleType.PICKUP);
    }

    /** Q12: tiers 1-3 reach 1 block, 4-6 two, 7-9 three, 10 four (4.2.2's fixed range). */
    public static int range(int tier) {
        return 1 + Math.max(0, tier - 1) / 3;
    }

    /**
     * 4.2.2 {@code getSuckingBox}: the slab {@code distance} blocks out from {@code pos} on side {@code open}, one block
     * thick and {@code 2 × distance + 1} blocks wide on the other two axes.
     */
    public static AABB suckingBox(BlockPos pos, Direction open, int distance) {
        AABB slab = new AABB(pos.relative(open, distance));
        return switch (open.getAxis()) {
        case X -> slab.inflate(0, distance, distance);
        case Y -> slab.inflate(distance, 0, distance);
        case Z -> slab.inflate(distance, distance, 0);
        };
    }

    /** The open side: the opposite of the only connection, or null without exactly one connection. */
    public static @Nullable Direction openSide(byte connectionMask) {
        if (Integer.bitCount(connectionMask & 63) != 1)
            return null;
        return Direction.from3DDataValue(Integer.numberOfTrailingZeros(connectionMask & 63)).getOpposite();
    }

    @Override
    public void tickSource(ServerLevel level, PipeNetwork network, int node, TilePipe pipe) {
        Direction open = openSide(pipe.getConnectionMask());
        if (open == null)
            return;
        ResourceRouter.Settings settings = ItemRouting.settings();
        long items = ItemRouting.ROUTER.available(network.nodes[node], level.getGameTime(), settings)
                / ItemRouting.ITEM_COST;
        if (items <= 0)
            return;
        int range = range(pipe.getTier());
        BlockPos pos = pipe.getBlockPos();
        for (int distance = 0; distance <= range; distance++) {
            AABB box = distance == 0 ? new AABB(pos) : suckingBox(pos, open, distance);
            for (Entity entity : level.getEntities((Entity) null, box, Entity::isAlive)) {
                TransferResult result = suck(level, network, node, open, entity, (int) items, distance, settings);
                if (result == null)
                    continue;
                if (result.movedAny() || result.reason() == TransferResult.Reason.NO_CAPACITY)
                    return;
            }
        }
    }

    /** One entity: what moved, or null when the entity is nothing this module picks up. */
    private static @Nullable TransferResult suck(ServerLevel level, PipeNetwork network, int node, Direction open,
            Entity entity, int items, int distance, ResourceRouter.Settings settings) {
        if (entity instanceof ItemEntity item) {
            ItemStack stack = item.getItem();
            if (stack.isEmpty())
                return null;
            TransferResult result = route(level, network, node, open, stack, Math.min(items, stack.getCount()), settings);
            if (result.movedAny()) {
                ItemStack left = stack.copyWithCount(stack.getCount() - result.moved());
                if (left.isEmpty())
                    item.discard();
                else
                    item.setItem(left);
            }
            return result;
        }
        if (entity instanceof AbstractArrow arrow) {
            if (arrow.pickup != AbstractArrow.Pickup.ALLOWED)
                return null;
            TransferResult result = route(level, network, node, open, arrow.getPickupItemStackOrigin(), 1, settings);
            if (result.movedAny())
                arrow.discard();
            return result;
        }
        if (distance == 1 && entity instanceof AbstractMinecartContainer cart) {
            cart.unpackChestVehicleLootTable(null);
            for (int slot = 0; slot < cart.getContainerSize(); slot++) {
                ItemStack stack = cart.getItem(slot);
                if (stack.isEmpty())
                    continue;
                // 4.2.2 took the first stack only (Transactor.remove).
                TransferResult result = route(level, network, node, open, stack, Math.min(items, stack.getCount()),
                        settings);
                if (result.movedAny())
                    cart.removeItem(slot, result.moved());
                return result;
            }
        }
        return null;
    }

    /** Routes up to {@code amount} of {@code stack} into the pipe from its open side, in its own transaction. */
    private static TransferResult route(ServerLevel level, PipeNetwork network, int node, Direction open, ItemStack stack,
            int amount, ResourceRouter.Settings settings) {
        try (Transaction transaction = Transaction.openRoot()) {
            TransferResult result = ItemRouting.ROUTER.push(network, node, open, PipeWalk.NO_SOURCE,
                    ItemResource.of(stack), amount, level.getGameTime(), level.getRandom(), settings, transaction);
            if (result.movedAny())
                transaction.commit();
            return result;
        }
    }
}
