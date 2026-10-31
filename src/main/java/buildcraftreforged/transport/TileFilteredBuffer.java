/*
 * Ported from buildcraft/transport/TileFilteredBuffer.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: IInventory becomes Container over the same two SimpleInventory fields, and
 * other blocks reach the storage through the item capability on every face (plain Container rules, so canPlaceItem
 * still filters what goes in); the tile listens to both inventories so a change marks it to be saved; itemID and
 * isDamageable become the item and isDamageableItem; isUseableByPlayer adds the usual reach check; NBT becomes
 * ValueInput/ValueOutput with the same tag names; the GUI is a MenuProvider.
 */
package buildcraftreforged.transport;

import buildcraftreforged.core.TileBuildCraft;
import buildcraftreforged.core.inventory.SimpleInventory;
import buildcraftreforged.transport.gui.ContainerFilteredBuffer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class TileFilteredBuffer extends TileBuildCraft implements Container, MenuProvider {

    private final SimpleInventory inventoryFilters = new SimpleInventory(9, "FilteredBufferFilters", 1);
    private final SimpleInventory inventoryStorage = new SimpleInventory(9, "FilteredBufferStorage", 64);

    public TileFilteredBuffer(BlockPos pos, BlockState state) {
        super(TransportRegistry.filteredBufferTile.get(), pos, state);
        inventoryFilters.addListener(this);
        inventoryStorage.addListener(this);
    }

    @Override
    public void updateEntity() {
        super.updateEntity();
    }

    public Container getFilters() {
        return inventoryFilters;
    }

    /** IInventory Implementation **/

    @Override
    public int getContainerSize() {
        return inventoryStorage.getContainerSize();
    }

    @Override
    public boolean isEmpty() {
        return inventoryStorage.isEmpty();
    }

    @Override
    public ItemStack getItem(int slotId) {
        return inventoryStorage.getItem(slotId);
    }

    @Override
    public ItemStack removeItem(int slotId, int count) {
        return inventoryStorage.removeItem(slotId, count);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slotId) {
        return inventoryStorage.removeItemNoUpdate(slotId);
    }

    @Override
    public void setItem(int slotId, ItemStack itemStack) {
        inventoryStorage.setItem(slotId, itemStack);
    }

    @Override
    public void clearContent() {
        inventoryStorage.clearContent();
    }

    public String getInvName() {
        return inventoryStorage.getInvName();
    }

    @Override
    public int getMaxStackSize() {
        return inventoryStorage.getMaxStackSize();
    }

    @Override
    public boolean stillValid(Player entityPlayer) {
        return Container.stillValidBlockEntity(this, entityPlayer, 8);
    }

    @Override
    public boolean canPlaceItem(int i, ItemStack itemstack) {

        ItemStack filterItemStack = inventoryFilters.getItem(i);

        if (filterItemStack.isEmpty() || filterItemStack.getItem() != itemstack.getItem())
            return false;

        if (itemstack.isDamageableItem())
            return true;

        if (filterItemStack.getDamageValue() == itemstack.getDamageValue())
            return true;

        return false;
    }

    @Override
    protected void loadAdditional(ValueInput nbtTagCompound) {
        super.loadAdditional(nbtTagCompound);

        nbtTagCompound.child("inventoryStorage").ifPresent(inventoryStorage::readFromNBT);
        nbtTagCompound.child("inventoryFilters").ifPresent(inventoryFilters::readFromNBT);
    }

    @Override
    protected void saveAdditional(ValueOutput nbtTagCompound) {
        super.saveAdditional(nbtTagCompound);

        inventoryStorage.writeToNBT(nbtTagCompound.child("inventoryStorage"));
        inventoryFilters.writeToNBT(nbtTagCompound.child("inventoryFilters"));
    }

    /* MENU: 4.2.2's GuiHandler, GuiIds.FILTERED_BUFFER. */
    @Override
    public Component getDisplayName() {
        return Component.translatable("tile.filteredBufferBlock");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ContainerFilteredBuffer(containerId, playerInventory, this);
    }
}
