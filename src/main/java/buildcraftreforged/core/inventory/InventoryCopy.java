/*
 * Ported from buildcraft/core/inventory/InventoryCopy.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: IInventory becomes Container; null stacks become ItemStack.EMPTY; the name
 * methods and openChest/closeChest go with IInventory.
 */
package buildcraftreforged.core.inventory;

import java.util.Arrays;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Creates a deep copy of an existing IInventory.
 *
 * Useful for performing inventory manipulations and then examining the results
 * without affecting the original inventory.
 *
 * @author CovertJaguar <http://www.railcraft.info>
 */
public class InventoryCopy implements Container {

    private Container orignal;
    private ItemStack contents[];

    public InventoryCopy(Container orignal) {
        this.orignal = orignal;
        contents = new ItemStack[orignal.getContainerSize()];
        Arrays.fill(contents, ItemStack.EMPTY);
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = orignal.getItem(i);
            if (!stack.isEmpty()) {
                contents[i] = stack.copy();
            }
        }
    }

    @Override
    public int getContainerSize() {
        return contents.length;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : contents) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int i) {
        return contents[i];
    }

    @Override
    public ItemStack removeItem(int i, int j) {
        if (!contents[i].isEmpty()) {
            if (contents[i].getCount() <= j) {
                ItemStack itemstack = contents[i];
                contents[i] = ItemStack.EMPTY;
                setChanged();
                return itemstack;
            }
            ItemStack itemstack1 = contents[i].split(j);
            if (contents[i].getCount() <= 0) {
                contents[i] = ItemStack.EMPTY;
            }
            setChanged();
            return itemstack1;
        } else {
            return ItemStack.EMPTY;
        }
    }

    @Override
    public void setItem(int i, ItemStack itemstack) {
        contents[i] = itemstack;
        if (!itemstack.isEmpty() && itemstack.getCount() > getMaxStackSize()) {
            itemstack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    @Override
    public int getMaxStackSize() {
        return orignal.getMaxStackSize();
    }

    @Override
    public void setChanged() {
    }

    @Override
    public boolean stillValid(Player entityplayer) {
        return true;
    }

    /** As in 4.2.2 this reaches the original inventory, not the copy. */
    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return orignal.removeItemNoUpdate(slot);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return orignal.canPlaceItem(slot, stack);
    }

    @Override
    public void clearContent() {
        Arrays.fill(contents, ItemStack.EMPTY);
    }

    public ItemStack[] getItemStacks() {
        return contents;
    }
}
