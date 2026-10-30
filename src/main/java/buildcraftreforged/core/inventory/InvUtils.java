/*
 * Ported from buildcraft/core/inventory/InvUtils.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: IInventory becomes Container, ForgeDirection becomes Direction, null stacks
 * become ItemStack.EMPTY, EntityItem becomes ItemEntity. New: getItemHandler, the item capability of an inventory per
 * side, as 4.2.2's ISidedInventory exposed it.
 */
package buildcraftreforged.core.inventory;

import buildcraftreforged.core.inventory.InventoryIterator.IInvSlot;
import buildcraftreforged.core.inventory.filters.ArrayStackFilter;
import buildcraftreforged.core.inventory.filters.IStackFilter;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;
import org.jspecify.annotations.Nullable;

/**
 *
 * @author CovertJaguar <http://www.railcraft.info/>
 */
public class InvUtils {

    public static int countItems(Container inv, @Nullable Direction side, ItemStack... filter) {
        return countItems(inv, side, new ArrayStackFilter(filter));
    }

    public static int countItems(Container inv, @Nullable Direction side, IStackFilter filter) {
        int count = 0;
        for (IInvSlot slot : InventoryIterator.getIterable(inv, side)) {
            ItemStack stack = slot.getStackInSlot();
            if (!stack.isEmpty() && filter.matches(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public static boolean containsItem(Container inv, @Nullable Direction side, ItemStack... filter) {
        return containsItem(inv, side, new ArrayStackFilter(filter));
    }

    public static boolean containsItem(Container inv, @Nullable Direction side, IStackFilter filter) {
        for (IInvSlot slot : InventoryIterator.getIterable(inv, side)) {
            ItemStack stack = slot.getStackInSlot();
            if (!stack.isEmpty() && filter.matches(stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if there is room for the ItemStack in the inventory.
     *
     * @param stack The ItemStack
     * @param dest The IInventory
     * @return true if room for stack
     */
    public static boolean isRoomForStack(ItemStack stack, @Nullable Direction side, @Nullable Container dest) {
        if (stack.isEmpty() || dest == null) {
            return false;
        }
        ITransactor tran = Transactor.getTransactorFor(dest);
        return tran.add(stack, side, false).getCount() > 0;
    }

    /**
     * Attempts to move a single item from one inventory to another.
     *
     * @param source
     * @param dest
     * @param filer an IStackFilter to match against
     * @return ItemStack.EMPTY if nothing was moved, the stack moved otherwise
     */
    public static ItemStack moveOneItem(Container source, @Nullable Direction output, Container dest, @Nullable Direction intput, IStackFilter filter) {
        ITransactor imSource = Transactor.getTransactorFor(source);
        ItemStack stack = imSource.remove(filter, output, false);
        if (!stack.isEmpty()) {
            ITransactor imDest = Transactor.getTransactorFor(dest);
            int moved = imDest.add(stack, intput, true).getCount();
            if (moved > 0) {
                imSource.remove(filter, output, true);
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack moveOneItem(Container source, @Nullable Direction output, Container dest, @Nullable Direction intput, ItemStack... filter) {
        return moveOneItem(source, output, dest, intput, new ArrayStackFilter(filter));
    }

    /* STACK DROPS */
    public static void dropItems(Level world, ItemStack stack, int i, int j, int k) {
        if (stack.isEmpty() || stack.getCount() <= 0)
            return;

        float f1 = 0.7F;
        double d = (world.getRandom().nextFloat() * f1) + (1.0F - f1) * 0.5D;
        double d1 = (world.getRandom().nextFloat() * f1) + (1.0F - f1) * 0.5D;
        double d2 = (world.getRandom().nextFloat() * f1) + (1.0F - f1) * 0.5D;
        ItemEntity entityitem = new ItemEntity(world, i + d, j + d1, k + d2, stack);
        entityitem.setPickUpDelay(10);

        world.addFreshEntity(entityitem);
    }

    public static void dropItems(Level world, Container inv, int i, int j, int k) {
        for (int slot = 0; slot < inv.getContainerSize(); ++slot) {
            ItemStack items = inv.getItem(slot);

            if (!items.isEmpty() && items.getCount() > 0) {
                dropItems(world, inv.getItem(slot).copy(), i, j, k);
            }
        }
    }

    public static void wipeInventory(Container inv) {
        for (int slot = 0; slot < inv.getContainerSize(); ++slot) {
            inv.setItem(slot, ItemStack.EMPTY);
        }
    }

    /* CAPABILITY */
    /**
     * The item handler other blocks see on side, with 4.2.2's ISidedInventory rules: a WorldlyContainer shows the
     * slots of getSlotsForFace and checks canPlaceItemThroughFace and canTakeItemThroughFace; any other Container
     * shows every slot from every side and checks canPlaceItem, as 4.2.2 treated a plain IInventory. A null side (the
     * owner's own access) shows every slot. Null if inv is not a Container.
     */
    public static @Nullable ResourceHandler<ItemResource> getItemHandler(Object inv, @Nullable Direction side) {
        if (inv instanceof WorldlyContainer worldly && side != null) {
            return new WorldlyContainerWrapper(worldly, side);
        } else if (inv instanceof Container container) {
            return VanillaContainerWrapper.of(container);
        }
        return null;
    }
}
