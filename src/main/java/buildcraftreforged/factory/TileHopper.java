/*
 * Ported from buildcraft/factory/TileHopper.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: IInventory becomes Container over the same SimpleInventory; the block below
 * is reached through its item capability on its top face (item pipes, machines, chests and other mods' inventories
 * alike), where 4.2.2 asked Transactor.getTransactorFor for the tile entity below; NBT becomes ValueInput/ValueOutput
 * with the same tag names; the GUI is a MenuProvider; the status panel (plan section 10) is new.
 */
package buildcraftreforged.factory;

import buildcraftreforged.core.TileBuildCraft;
import buildcraftreforged.core.inventory.ITransactor;
import buildcraftreforged.core.inventory.SimpleInventory;
import buildcraftreforged.core.inventory.Transactor;
import buildcraftreforged.core.status.FlowMeter;
import buildcraftreforged.core.status.IOutputRefusal;
import buildcraftreforged.core.status.IStatusProvider;
import buildcraftreforged.core.status.MachineStatus;
import buildcraftreforged.core.status.StatusReason;
import buildcraftreforged.factory.gui.ContainerHopper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

public class TileHopper extends TileBuildCraft implements Container, MenuProvider, IStatusProvider {

    private final SimpleInventory _inventory = new SimpleInventory(4, "Hopper", 64);

    /** Not in 4.2.2: why the last try moved nothing, for the status panel; NONE after a move or with nothing to move. */
    private StatusReason blocked = StatusReason.NONE;
    /** Not in 4.2.2: the status panel's Flow line. */
    public final FlowMeter flowMeter = new FlowMeter(() -> level == null ? 0 : level.getGameTime());

    public TileHopper(BlockPos pos, BlockState state) {
        super(FactoryRegistry.hopperTile.get(), pos, state);
        _inventory.addListener(this);
    }

    @Override
    protected void loadAdditional(ValueInput nbtTagCompound) {
        super.loadAdditional(nbtTagCompound);
        nbtTagCompound.child("inventory").ifPresent(_inventory::readFromNBT);
    }

    @Override
    protected void saveAdditional(ValueOutput nbtTagCompound) {
        super.saveAdditional(nbtTagCompound);
        _inventory.writeToNBT(nbtTagCompound.child("inventory"));
    }

    @Override
    public void updateEntity() {
        super.updateEntity();
        if (level == null || level.isClientSide() || level.getGameTime() % 2 != 0)
            return;

        ResourceHandler<ItemResource> tile = level.getCapability(Capabilities.Item.BLOCK, worldPosition.below(), Direction.UP);

        if (tile == null) {
            blocked = _inventory.isEmpty() ? StatusReason.NONE : StatusReason.NO_DESTINATION;
            return;
        }

        ITransactor transactor = Transactor.getTransactorFor(tile);

        if (transactor == null)
            return;

        @Nullable ItemStack refused = null;
        for (int internalSlot = 0; internalSlot < _inventory.getContainerSize(); internalSlot++) {
            ItemStack stackInSlot = _inventory.getItem(internalSlot);
            if (stackInSlot.isEmpty())
                continue;

            ItemStack clonedStack = stackInSlot.copyWithCount(1);
            if (transactor.add(clonedStack, Direction.UP, true).getCount() > 0) {
                _inventory.removeItem(internalSlot, 1);
                flowMeter.itemsOut(1);
                blocked = StatusReason.NONE;
                return;
            }
            if (refused == null)
                refused = clonedStack;
        }

        blocked = refused == null ? StatusReason.NONE
                : tile instanceof IOutputRefusal refusal ? refusal.whyRefused(ItemResource.of(refused))
                : StatusReason.OUTPUT_FULL;
    }

    /**
     * IInventory Implementation *
     */
    @Override
    public int getContainerSize() {
        return _inventory.getContainerSize();
    }

    @Override
    public boolean isEmpty() {
        return _inventory.isEmpty();
    }

    @Override
    public ItemStack getItem(int slotId) {
        return _inventory.getItem(slotId);
    }

    @Override
    public ItemStack removeItem(int slotId, int count) {
        return _inventory.removeItem(slotId, count);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slotId) {
        return _inventory.removeItemNoUpdate(slotId);
    }

    @Override
    public void setItem(int slotId, ItemStack itemStack) {
        _inventory.setItem(slotId, itemStack);
    }

    @Override
    public void clearContent() {
        _inventory.clearContent();
    }

    public String getInvName() {
        return _inventory.getInvName();
    }

    @Override
    public int getMaxStackSize() {
        return _inventory.getMaxStackSize();
    }

    @Override
    public boolean stillValid(Player entityPlayer) {
        return Container.stillValidBlockEntity(this, entityPlayer, 8);
    }

    @Override
    public boolean canPlaceItem(int i, ItemStack itemstack) {
        return true;
    }

    /* MENU: 4.2.2's GuiHandler, GuiIds.HOPPER. */
    @Override
    public Component getDisplayName() {
        return Component.translatable("tile.blockHopper");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ContainerHopper(containerId, playerInventory, this);
    }

    // Status panel (plan section 10), not in 4.2.2.

    /** Blocked while it holds items and the block below takes none of them. */
    @Override
    public MachineStatus getStatusState() {
        if (_inventory.isEmpty())
            return MachineStatus.IDLE;
        return blocked != StatusReason.NONE ? MachineStatus.BLOCKED : MachineStatus.WORKING;
    }

    @Override
    public StatusReason getStatusReason() {
        return _inventory.isEmpty() ? StatusReason.NONE : blocked;
    }

    @Override
    public FlowMeter getStatusFlow() {
        return flowMeter;
    }
}
