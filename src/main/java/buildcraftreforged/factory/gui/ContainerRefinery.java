/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: a registered MenuType with a client constructor that reads the block
 * position; the REFINERY_FILTER_SET packet becomes a PacketGuiReturn; sendProgressBarUpdate/updateProgressBar become
 * two DataSlots (0 = no filter, which 4.2.2 never sent); the status panel's widget (plan section 10) is new.
 */
package buildcraftreforged.factory.gui;

import buildcraftreforged.core.gui.BuildCraftContainer;
import buildcraftreforged.core.network.IGuiReturnHandler;
import buildcraftreforged.core.network.PacketGuiReturn;
import buildcraftreforged.core.status.StatusWidget;
import buildcraftreforged.factory.FactoryRegistry;
import buildcraftreforged.factory.TileRefinery;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.material.Fluid;
import org.jspecify.annotations.Nullable;

public class ContainerRefinery extends BuildCraftContainer {

    public TileRefinery refinery;
    public final StatusWidget statusWidget;

    /** Client side: the block position comes with the open-menu packet. */
    public ContainerRefinery(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, clientTile(inventory, extraData.readBlockPos()));
    }

    public ContainerRefinery(int containerId, Inventory inventory, TileRefinery refinery) {
        super(FactoryRegistry.refineryMenu.get(), containerId, 0);

        for (int l = 0; l < 3; l++) {
            for (int k1 = 0; k1 < 9; k1++) {
                addSlot(new Slot(inventory, k1 + l * 9 + 9, 8 + k1 * 18, 123 + l * 18));
            }
        }

        for (int i1 = 0; i1 < 9; i1++) {
            addSlot(new Slot(inventory, i1, 8 + i1 * 18, 181));
        }

        this.refinery = refinery;

        for (int i = 0; i < 2; i++) {
            final int id = i;
            addDataSlot(new DataSlot() {
                @Override
                public int get() {
                    return ContainerRefinery.this.refinery.sendGUINetworkData(id);
                }

                @Override
                public void set(int value) {
                    updateProgressBar(id, value);
                }
            });
        }

        statusWidget = new StatusWidget(refinery);
        addWidget(statusWidget);
    }

    private static TileRefinery clientTile(Inventory inventory, BlockPos pos) {
        if (inventory.player.level().getBlockEntity(pos) instanceof TileRefinery tile) {
            return tile;
        }
        // The block went away before the menu arrived; the menu closes on the next stillValid check.
        return new TileRefinery(pos, FactoryRegistry.refineryBlock.get().defaultBlockState());
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(refinery, player);
    }

    @Override
    public IGuiReturnHandler getGuiReturnHandler() {
        return refinery;
    }

    /* SETTING AND GETTING FILTERS */
    public void setFilter(final int slot, final @Nullable Fluid filter) {

        refinery.setFilter(slot, filter);

        if (refinery.getLevel() != null && refinery.getLevel().isClientSide()) {
            new PacketGuiReturn(containerId, refinery).sendPacket();
        }
    }

    public @Nullable Fluid getFilter(int slot) {
        return refinery.getFilter(slot);
    }

    /* GUI DISPLAY UPDATES */
    public void updateProgressBar(int i, int j) {
        refinery.getGUINetworkData(i, j);
    }
}
