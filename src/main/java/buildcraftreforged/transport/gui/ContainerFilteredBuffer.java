/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: an AbstractContainerMenu with a MenuType and a client constructor that finds
 * the block entity from the position in the open-menu packet; IInventory is the vanilla Container; the buffer slots
 * are a nested slot whose mayPlace asks canPlaceItem, in place of core.gui.slots.SlotValidated (not ported, see
 * docs/PORTING-NOTES.md).
 */
package buildcraftreforged.transport.gui;

import buildcraftreforged.core.gui.BuildCraftContainer;
import buildcraftreforged.core.gui.slots.SlotPhantom;
import buildcraftreforged.transport.TileFilteredBuffer;
import buildcraftreforged.transport.TransportRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 *
 * @author SandGrainOne
 */
public class ContainerFilteredBuffer extends BuildCraftContainer {

    private static class SlotPhantomLockable extends SlotPhantom {

        final Container locks;

        public SlotPhantomLockable(Container storage, Container locks, int par2, int par3, int par4) {
            super(storage, par2, par3, par4);
            this.locks = locks;
        }

        @Override
        public boolean canAdjust() {
            return locks.getItem(this.getContainerSlot()).isEmpty();
        }
    }

    /** 4.2.2 SlotValidated: takes only what the inventory accepts in this slot. */
    private static class SlotValidated extends Slot {

        public SlotValidated(Container inv, int id, int x, int y) {
            super(inv, id, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack itemStack) {
            return container.canPlaceItem(this.getContainerSlot(), itemStack);
        }
    }

    Inventory playerInventory;
    public final TileFilteredBuffer filteredBuffer;

    /** Client side: the block position comes with the open-menu packet. */
    public ContainerFilteredBuffer(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, clientTile(playerInventory, extraData.readBlockPos()));
    }

    public ContainerFilteredBuffer(int containerId, Inventory playerInventory, TileFilteredBuffer tile) {
        super(TransportRegistry.filteredBufferMenu.get(), containerId, tile.getContainerSize());

        this.playerInventory = playerInventory;
        this.filteredBuffer = tile;

        Container filters = tile.getFilters();

        for (int col = 0; col < 9; col++) {
            // Filtered Buffer filter slots
            addSlot(new SlotPhantomLockable(filters, tile, col, 8 + col * 18, 27));
            // Filtered Buffer inventory slots
            addSlot(new SlotValidated(tile, col, 8 + col * 18, 61));
        }

        // Player inventory
        for (int l = 0; l < 3; l++) {
            for (int k1 = 0; k1 < 9; k1++) {
                addSlot(new Slot(playerInventory, k1 + l * 9 + 9, 8 + k1 * 18, 86 + l * 18));
            }
        }

        // Player hot bar
        for (int i1 = 0; i1 < 9; i1++) {
            addSlot(new Slot(playerInventory, i1, 8 + i1 * 18, 144));
        }
    }

    private static TileFilteredBuffer clientTile(Inventory playerInventory, BlockPos pos) {
        if (playerInventory.player.level().getBlockEntity(pos) instanceof TileFilteredBuffer tile) {
            return tile;
        }
        // The block went away before the menu arrived; the menu closes on the next stillValid check.
        return new TileFilteredBuffer(pos, TransportRegistry.filteredBuffer.get().defaultBlockState());
    }

    @Override
    public boolean stillValid(Player entityPlayer) {
        return filteredBuffer.stillValid(entityPlayer);
    }
}
