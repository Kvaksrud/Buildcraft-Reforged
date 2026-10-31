/*
 * Buildcraft Reforged, 2026: new file, no 4.2.2 original. Blocked output (#63, docs/PLAN.md 8.3, docs/TRANSPORT.md 6.3).
 */
package buildcraftreforged.core.status;

import buildcraftreforged.api.power.IPowerEmitter;
import buildcraftreforged.core.inventory.InvUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * A machine's item output and its Blocked state (plan section 8.3, docs/TRANSPORT.md 6.3). The machine {@link #add}s
 * what it makes and calls {@link #push} every server tick; the output offers it to the neighbouring blocks' item
 * capabilities (inventories, item pipes, other machines) in random order, as 4.2.2 {@code addToRandomInventoryAround}
 * and {@code addToRandomPipeAround} did. What nobody takes stays here, saved with the machine; nothing is dropped in the
 * world. While something is pending and the last push moved nothing, the output is blocked: the machine stops making
 * more, returns {@link #isBlocked()} from {@code IMachine.isBlocked} (status panel State, red light, Machine trigger)
 * and {@link #getReason()} from {@code IStatusProvider.getStatusReason}. The first push that moves anything clears it.
 *
 * <p>A machine whose output is not items (a full tank) reports the same state with {@link #setBlocked}.
 */
public final class MachineOutput {
    private static final String TAG = "pendingOutput";

    private final List<ItemStack> pending = new ArrayList<>();
    private final @Nullable FlowMeter meter;
    private StatusReason blocked = StatusReason.NONE;

    public MachineOutput() {
        this(null);
    }

    /** {@code meter} counts the items pushed out ({@link FlowMeter#itemsOut}), or null. */
    public MachineOutput(@Nullable FlowMeter meter) {
        this.meter = meter;
    }

    /** Queues {@code stack} (copied) for the next {@link #push}, merged with a pending stack of the same item. */
    public void add(ItemStack stack) {
        if (stack.isEmpty())
            return;
        ItemStack rest = stack.copy();
        for (ItemStack queued : pending) {
            if (ItemStack.isSameItemSameComponents(queued, rest) && queued.getCount() < queued.getMaxStackSize()) {
                int moved = Math.min(rest.getCount(), queued.getMaxStackSize() - queued.getCount());
                queued.grow(moved);
                rest.shrink(moved);
                if (rest.isEmpty())
                    return;
            }
        }
        pending.add(rest);
    }

    /** True while items wait to be pushed out. A machine makes nothing new while this holds. */
    public boolean hasPending() {
        return !pending.isEmpty();
    }

    /** The items waiting, for drops and tests. Do not change them. */
    public List<ItemStack> getPending() {
        return Collections.unmodifiableList(pending);
    }

    public boolean isBlocked() {
        return blocked != StatusReason.NONE;
    }

    /** {@link StatusReason#OUTPUT_FULL} or {@link StatusReason#NO_DESTINATION} while blocked, else NONE. */
    public StatusReason getReason() {
        return blocked;
    }

    /** Sets the state by hand, for outputs that are not items; {@link StatusReason#NONE} clears it. */
    public void setBlocked(StatusReason reason) {
        blocked = reason;
    }

    /**
     * Offers every pending stack to the item handlers of the blocks around {@code pos}, server side. Returns the number
     * of items moved and updates the Blocked state: blocked when items are pending and none moved, else not. Engines
     * (power emitters) are skipped, as 4.2.2 skipped {@code TileEngine}.
     */
    public int push(Level level, BlockPos pos) {
        if (pending.isEmpty()) {
            blocked = StatusReason.NONE;
            return 0;
        }

        Direction[] sides = Direction.values();
        RandomSource random = level.getRandom();
        for (int i = sides.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Direction swap = sides[i];
            sides[i] = sides[j];
            sides[j] = swap;
        }
        List<ResourceHandler<ItemResource>> neighbours = new ArrayList<>(6);
        for (Direction side : sides) {
            BlockPos next = pos.relative(side);
            if (!level.isLoaded(next) || level.getBlockEntity(next) instanceof IPowerEmitter)
                continue;
            ResourceHandler<ItemResource> handler = level.getCapability(Capabilities.Item.BLOCK, next, side.getOpposite());
            if (handler != null)
                neighbours.add(handler);
        }

        int moved = 0;
        @Nullable ItemResource firstRefused = null;
        for (int i = 0; i < pending.size(); i++) {
            ItemStack stack = pending.get(i);
            ItemResource resource = ItemResource.of(stack);
            for (ResourceHandler<ItemResource> neighbour : neighbours) {
                if (stack.isEmpty())
                    break;
                try (Transaction transaction = Transaction.openRoot()) {
                    int inserted = ResourceHandlerUtil.insertStacking(neighbour, resource, stack.getCount(),
                            transaction);
                    transaction.commit();
                    stack.shrink(inserted);
                    moved += inserted;
                }
            }
            if (!stack.isEmpty() && firstRefused == null)
                firstRefused = resource;
        }
        pending.removeIf(ItemStack::isEmpty);

        if (meter != null && moved > 0)
            meter.itemsOut(moved);
        blocked = moved > 0 || pending.isEmpty() ? StatusReason.NONE : reason(neighbours, firstRefused);
        return moved;
    }

    /** Full when any neighbour could take items but is full; no destination when nothing around takes items. */
    private static StatusReason reason(List<ResourceHandler<ItemResource>> neighbours, @Nullable ItemResource resource) {
        for (ResourceHandler<ItemResource> neighbour : neighbours) {
            if (!(neighbour instanceof IOutputRefusal refusal) || resource == null
                    || refusal.whyRefused(resource) == StatusReason.OUTPUT_FULL)
                return StatusReason.OUTPUT_FULL;
        }
        return StatusReason.NO_DESTINATION;
    }

    /** Drops the pending items at {@code pos}, as an inventory's contents drop when its block is broken. */
    public void dropAll(Level level, BlockPos pos) {
        for (ItemStack stack : pending) {
            InvUtils.dropItems(level, stack, pos.getX(), pos.getY(), pos.getZ());
        }
        pending.clear();
        blocked = StatusReason.NONE;
    }

    public void save(ValueOutput output) {
        ValueOutput.TypedOutputList<ItemStack> list = output.list(TAG, ItemStack.CODEC);
        for (ItemStack stack : pending) {
            list.add(stack);
        }
    }

    public void load(ValueInput input) {
        pending.clear();
        input.listOrEmpty(TAG, ItemStack.CODEC).forEach(pending::add);
        blocked = StatusReason.NONE;
    }
}
