/*
 * Ported from buildcraft/core/inventory/Transactor.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: IInventory becomes Container, ISidedInventory becomes WorldlyContainer,
 * ForgeDirection becomes Direction; getTransactorFor also takes an item ResourceHandler (TransactorHandler), the form
 * in which blocks from other mods expose their inventories now.
 */
package buildcraftreforged.core.inventory;

import buildcraftreforged.api.inventory.ISpecialInventory;
import buildcraftreforged.core.utils.Utils;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

public abstract class Transactor implements ITransactor {

    @Override
    public ItemStack add(ItemStack stack, @Nullable Direction orientation, boolean doAdd) {
        ItemStack added = stack.copy();
        added.setCount(inject(stack, orientation, doAdd));
        return added;
    }

    public abstract int inject(ItemStack stack, @Nullable Direction orientation, boolean doAdd);

    @SuppressWarnings("unchecked")
    public static @Nullable ITransactor getTransactorFor(Object object) {

        if (object instanceof ISpecialInventory)
            return new TransactorSpecial((ISpecialInventory) object);
        else if (object instanceof WorldlyContainer)
            return new TransactorSimple((WorldlyContainer) object);
        else if (object instanceof Container)
            return new TransactorSimple(Utils.getInventory((Container) object));
        else if (object instanceof ResourceHandler<?>)
            return new TransactorHandler((ResourceHandler<ItemResource>) object);

        return null;
    }
}
