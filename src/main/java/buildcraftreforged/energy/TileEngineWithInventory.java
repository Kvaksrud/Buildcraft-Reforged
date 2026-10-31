/*
 * Ported from buildcraft/energy/TileEngineWithInventory.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: IInventory becomes Container (getSizeInventory → getContainerSize,
 * getStackInSlot → getItem, decrStackSize → removeItem, getStackInSlotOnClosing → removeItemNoUpdate,
 * setInventorySlotContents → setItem, isItemValidForSlot → canPlaceItem, getInventoryStackLimit → getMaxStackSize,
 * isUseableByPlayer → stillValid; openChest/closeChest are vanilla's defaults); NBT becomes ValueInput/ValueOutput.
 * TileBuildCraft drops the contents when the block is removed and exposes them through the item capability.
 */
package buildcraftreforged.energy;

import buildcraftreforged.core.inventory.SimpleInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public abstract class TileEngineWithInventory extends TileEngine implements Container {

    private final SimpleInventory inv;

    public TileEngineWithInventory(BlockEntityType<?> type, BlockPos pos, BlockState state, int invSize) {
        super(type, pos, state);
        inv = new SimpleInventory(invSize, "Engine", 64);
    }

    /* IINVENTORY IMPLEMENTATION */
    @Override
    public int getContainerSize() {
        return inv.getContainerSize();
    }

    @Override
    public boolean isEmpty() {
        return inv.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return inv.getItem(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return inv.removeItem(slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return inv.removeItemNoUpdate(slot);
    }

    @Override
    public void setItem(int slot, ItemStack itemstack) {
        inv.setItem(slot, itemstack);
    }

    @Override
    public boolean canPlaceItem(int i, ItemStack itemstack) {
        return true;
    }

    public String getInvName() {
        return "Engine";
    }

    @Override
    public int getMaxStackSize() {
        return 64;
    }

    @Override
    public boolean stillValid(Player entityplayer) {
        return level != null && level.getBlockEntity(worldPosition) == this;
    }

    @Override
    public void clearContent() {
        inv.clearContent();
    }

    @Override
    protected void loadAdditional(ValueInput data) {
        super.loadAdditional(data);
        inv.readFromNBT(data);
    }

    @Override
    protected void saveAdditional(ValueOutput data) {
        super.saveAdditional(data);
        inv.writeToNBT(data);
    }
}
