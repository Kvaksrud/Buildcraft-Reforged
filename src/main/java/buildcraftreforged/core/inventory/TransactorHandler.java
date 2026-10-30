/*
 * Buildcraft Reforged, 2026. New: no 4.2.2 counterpart.
 */
package buildcraftreforged.core.inventory;

import buildcraftreforged.core.inventory.filters.IStackFilter;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * An ITransactor over an item ResourceHandler (Capabilities.Item.BLOCK), for blocks that expose no Container, as blocks
 * from other mods usually do now. The handler was looked up for one side already, so orientation is not used. Adds
 * fill matching stacks first, as TransactorSimple does; remove takes one item from the first slot that matches.
 */
public class TransactorHandler extends Transactor {

    protected ResourceHandler<ItemResource> handler;

    public TransactorHandler(ResourceHandler<ItemResource> handler) {
        this.handler = handler;
    }

    @Override
    public int inject(ItemStack stack, @Nullable Direction orientation, boolean doAdd) {
        if (stack.isEmpty()) {
            return 0;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            int injected = ResourceHandlerUtil.insertStacking(handler, ItemResource.of(stack), stack.getCount(), transaction);
            if (doAdd) {
                transaction.commit();
            }
            return injected;
        }
    }

    @Override
    public ItemStack remove(IStackFilter filter, @Nullable Direction orientation, boolean doRemove) {
        for (int slot = 0; slot < handler.size(); slot++) {
            ItemResource resource = handler.getResource(slot);
            if (resource.isEmpty() || !filter.matches(resource.toStack(handler.getAmountAsInt(slot)))) {
                continue;
            }
            try (Transaction transaction = Transaction.openRoot()) {
                if (handler.extract(slot, resource, 1, transaction) == 1) {
                    if (doRemove) {
                        transaction.commit();
                    }
                    return resource.toStack(1);
                }
            }
        }
        return ItemStack.EMPTY;
    }
}
