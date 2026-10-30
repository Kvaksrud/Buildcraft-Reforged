/*
 * Ported from buildcraft/core/inventory/InventoryConcatenator.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: IInventory becomes Container; the name methods and openChest/closeChest go
 * with it.
 */
package buildcraftreforged.core.inventory;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Allows you to deal with multiple inventories through a single interface.
 *
 * @author CovertJaguar <http://www.railcraft.info/>
 */
public class InventoryConcatenator implements Container {

    private final List<Integer> slotMap = new ArrayList<Integer>();
    private final List<Container> invMap = new ArrayList<Container>();

    private InventoryConcatenator() {
    }

    public static InventoryConcatenator make() {
        return new InventoryConcatenator();
    }

    public InventoryConcatenator add(Container inv) {
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            slotMap.add(slot);
            invMap.add(inv);
        }
        return this;
    }

    @Override
    public int getContainerSize() {
        return slotMap.size();
    }

    @Override
    public boolean isEmpty() {
        for (int slot = 0; slot < getContainerSize(); slot++) {
            if (!getItem(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return invMap.get(slot).getItem(slotMap.get(slot));
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return invMap.get(slot).removeItem(slotMap.get(slot), amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return invMap.get(slot).removeItemNoUpdate(slotMap.get(slot));
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        invMap.get(slot).setItem(slotMap.get(slot), stack);
    }

    @Override
    public int getMaxStackSize() {
        return 64;
    }

    @Override
    public void setChanged() {
    }

    @Override
    public boolean stillValid(Player entityplayer) {
        return true;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return invMap.get(slot).canPlaceItem(slotMap.get(slot), stack);
    }

    @Override
    public void clearContent() {
        InvUtils.wipeInventory(this);
    }
}
