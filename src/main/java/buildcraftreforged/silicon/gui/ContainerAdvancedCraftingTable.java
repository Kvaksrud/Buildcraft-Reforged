/*
 * Ported from buildcraft/silicon/gui/ContainerAdvancedCraftingTable.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: an AbstractContainerMenu with a MenuType and a client constructor that finds
 * the block entity from the position in the open-menu packet; the progress bars of detectAndSendChanges and
 * updateProgressBar are vanilla data slots.
 */
package buildcraftreforged.silicon.gui;

import buildcraftreforged.core.gui.BuildCraftContainer;
import buildcraftreforged.core.gui.slots.SlotOutput;
import buildcraftreforged.core.gui.slots.SlotPhantom;
import buildcraftreforged.core.gui.slots.SlotUntouchable;
import buildcraftreforged.silicon.SiliconRegistry;
import buildcraftreforged.silicon.TileAdvancedCraftingTable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;

public class ContainerAdvancedCraftingTable extends BuildCraftContainer {

    public final TileAdvancedCraftingTable workbench;

    /** Client side: the block position comes with the open-menu packet. */
    public ContainerAdvancedCraftingTable(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, clientTile(playerInventory, extraData.readBlockPos()));
    }

    public ContainerAdvancedCraftingTable(int containerId, Inventory playerInventory, TileAdvancedCraftingTable table) {
        super(SiliconRegistry.advancedCraftingTableMenu.get(), containerId, table.getContainerSize());
        this.workbench = table;

        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                addSlot(new SlotPhantom(table.getCraftingSlots(), x + y * 3, 33 + x * 18, 16 + y * 18));
            }
        }

        addSlot(new SlotUntouchable(table.getOutputSlot(), 0, 127, 34));

        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 5; x++) {
                addSlot(new Slot(workbench, x + y * 5, 15 + x * 18, 85 + y * 18));
            }
        }

        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                addSlot(new SlotOutput(workbench, 15 + x + y * 3, 109 + x * 18, 85 + y * 18));
            }
        }

        for (int l = 0; l < 3; l++) {
            for (int k1 = 0; k1 < 9; k1++) {
                addSlot(new Slot(playerInventory, k1 + l * 9 + 9, 8 + k1 * 18, 153 + l * 18));
            }

        }

        for (int i1 = 0; i1 < 9; i1++) {
            addSlot(new Slot(playerInventory, i1, 8 + i1 * 18, 211));
        }

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

    private static TileAdvancedCraftingTable clientTile(Inventory playerInventory, BlockPos pos) {
        if (playerInventory.player.level().getBlockEntity(pos) instanceof TileAdvancedCraftingTable tile) {
            return tile;
        }
        // The block went away before the menu arrived; the menu closes on the next stillValid check.
        return new TileAdvancedCraftingTable(pos, SiliconRegistry.advancedCraftingTableBlock.get().defaultBlockState());
    }

    @Override
    public boolean stillValid(Player var1) {
        return workbench.stillValid(var1);
    }
}
