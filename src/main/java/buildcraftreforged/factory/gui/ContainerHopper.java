/*
 * Ported from buildcraft/factory/gui/ContainerHopper.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: an AbstractContainerMenu with a MenuType and a client constructor that finds
 * the block entity from the position in the open-menu packet; the status panel's widget (plan section 10) is new.
 */
package buildcraftreforged.factory.gui;

import buildcraftreforged.core.gui.BuildCraftContainer;
import buildcraftreforged.core.status.StatusWidget;
import buildcraftreforged.factory.FactoryRegistry;
import buildcraftreforged.factory.TileHopper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;

public class ContainerHopper extends BuildCraftContainer {

    Inventory playerIInventory;
    public final TileHopper hopper;
    public final StatusWidget statusWidget;

    /** Client side: the block position comes with the open-menu packet. */
    public ContainerHopper(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, clientTile(playerInventory, extraData.readBlockPos()));
    }

    public ContainerHopper(int containerId, Inventory inventory, TileHopper tile) {
        super(FactoryRegistry.hopperMenu.get(), containerId, tile.getContainerSize());
        playerIInventory = inventory;
        hopper = tile;

        // Adding hopper inventory
        addSlot(new Slot(tile, 0, 62, 18));
        addSlot(new Slot(tile, 1, 80, 18));
        addSlot(new Slot(tile, 2, 98, 18));
        addSlot(new Slot(tile, 3, 80, 36));

        // Player inventory
        for (int i1 = 0; i1 < 3; i1++) {
            for (int l1 = 0; l1 < 9; l1++) {
                addSlot(new Slot(inventory, l1 + i1 * 9 + 9, 8 + l1 * 18, 71 + i1 * 18));
            }
        }

        // Player hotbar
        for (int j1 = 0; j1 < 9; j1++) {
            addSlot(new Slot(inventory, j1, 8 + j1 * 18, 129));
        }

        statusWidget = new StatusWidget(tile);
        addWidget(statusWidget);
    }

    private static TileHopper clientTile(Inventory playerInventory, BlockPos pos) {
        if (playerInventory.player.level().getBlockEntity(pos) instanceof TileHopper tile) {
            return tile;
        }
        // The block went away before the menu arrived; the menu closes on the next stillValid check.
        return new TileHopper(pos, FactoryRegistry.hopperBlock.get().defaultBlockState());
    }

    @Override
    public boolean stillValid(Player entityPlayer) {
        return hopper.stillValid(entityPlayer);
    }
}
