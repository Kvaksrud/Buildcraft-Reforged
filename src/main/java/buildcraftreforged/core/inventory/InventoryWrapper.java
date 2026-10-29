/*
 * Ported from buildcraft/core/inventory/InventoryWrapper.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: IInventory becomes Container and ISidedInventory becomes WorldlyContainer;
 * openChest/closeChest become startOpen/stopOpen; the name methods go with IInventory.
 */
package buildcraftreforged.core.inventory;

import buildcraftreforged.core.utils.Utils;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public abstract class InventoryWrapper implements WorldlyContainer {

    Container inventory;

    public InventoryWrapper(Container inventory) {
        this.inventory = inventory;
    }

    /* DIRECT MAPPING */
    @Override public int getContainerSize() { return inventory.getContainerSize(); }
    @Override public boolean isEmpty() { return inventory.isEmpty(); }
    @Override public ItemStack getItem(int slotIndex) { return inventory.getItem(slotIndex); }
    @Override public ItemStack removeItem(int slotIndex, int amount) { return inventory.removeItem(slotIndex, amount); }
    @Override public ItemStack removeItemNoUpdate(int slotIndex) { return inventory.removeItemNoUpdate(slotIndex); }
    @Override public void setItem(int slotIndex, ItemStack itemstack) { inventory.setItem(slotIndex, itemstack); }
    @Override public int getMaxStackSize() { return inventory.getMaxStackSize(); }
    @Override public void setChanged() { inventory.setChanged(); }
    @Override public boolean stillValid(Player entityplayer) { return inventory.stillValid(entityplayer); }
    @Override public void startOpen(ContainerUser user) { inventory.startOpen(user); }
    @Override public void stopOpen(ContainerUser user) { inventory.stopOpen(user); }
    @Override public boolean canPlaceItem(int slotIndex, ItemStack itemstack) { return inventory.canPlaceItem(slotIndex, itemstack); }
    @Override public void clearContent() { inventory.clearContent(); }

    /* STATIC HELPER */
    public static @Nullable WorldlyContainer getWrappedInventory(Object inventory) {
        if(inventory instanceof WorldlyContainer)
            return (WorldlyContainer)inventory;
        else if(inventory instanceof Container)
            return new InventoryWrapperSimple(Utils.getInventory((Container)inventory));
        else
            return null;
    }
}
