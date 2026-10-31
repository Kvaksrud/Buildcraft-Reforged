/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: an AbstractContainerMenu with a MenuType and a client constructor that finds
 * the block entity from the position in the open-menu packet; the progress bars of detectAndSendChanges and
 * updateProgressBar are vanilla data slots; the selection message goes through the tile's GUI return handler.
 */

package buildcraftreforged.silicon.gui;

import buildcraftreforged.core.gui.BuildCraftContainer;
import buildcraftreforged.core.network.IGuiReturnHandler;
import buildcraftreforged.silicon.SiliconRegistry;
import buildcraftreforged.silicon.TileAssemblyTable;
import buildcraftreforged.silicon.TileAssemblyTable.SelectionMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;

public class ContainerAssemblyTable extends BuildCraftContainer {
    Inventory playerIInventory;
    public final TileAssemblyTable table;

    boolean networkSynchronized = false;

    /** Client side: the block position comes with the open-menu packet. */
    public ContainerAssemblyTable(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, clientTile(playerInventory, extraData.readBlockPos()));
    }

    public ContainerAssemblyTable(int containerId, Inventory playerInventory, TileAssemblyTable table) {
        super(SiliconRegistry.assemblyTableMenu.get(), containerId, table.getContainerSize());
        this.playerIInventory = playerInventory;

        for (int l = 0; l < 4; l++) {
            for (int k1 = 0; k1 < 3; k1++) {
                addSlot(new Slot(table, k1 + l * 3, 8 + k1 * 18, 36 + l * 18));
            }

        }

        for (int l = 0; l < 3; l++) {
            for (int k1 = 0; k1 < 9; k1++) {
                addSlot(new Slot(playerInventory, k1 + l * 9 + 9, 8 + k1 * 18, 123 + l * 18));
            }

        }

        for (int i1 = 0; i1 < 9; i1++) {
            addSlot(new Slot(playerInventory, i1, 8 + i1 * 18, 181));
        }

        this.table = table;

        // 4.2.2: sendGUINetworkData in detectAndSendChanges, getGUINetworkData in updateProgressBar.
        addDataSlots(new ContainerData() {
            @Override
            public int get(int i) {
                return table.sendGUINetworkData(i);
            }

            @Override
            public void set(int i, int j) {
                table.getGUINetworkData(i, j);
            }

            @Override
            public int getCount() {
                return 6;
            }
        });
    }

    private static TileAssemblyTable clientTile(Inventory playerInventory, BlockPos pos) {
        if (playerInventory.player.level().getBlockEntity(pos) instanceof TileAssemblyTable tile) {
            return tile;
        }
        // The block went away before the menu arrived; the menu closes on the next stillValid check.
        return new TileAssemblyTable(pos, SiliconRegistry.assemblyTableBlock.get().defaultBlockState());
    }

    @Override
    public boolean stillValid(Player entityplayer) {
        return Container.stillValidBlockEntity(table, entityplayer);
    }

    public void handleSelectionMessage(SelectionMessage message) {
        table.handleSelectionMessage(message);
    }

    @Override
    public IGuiReturnHandler getGuiReturnHandler() {
        return table;
    }
}
