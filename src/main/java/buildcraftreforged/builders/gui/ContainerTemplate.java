/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: an AbstractContainerMenu with a MenuType and a client constructor that finds
 * the block entity from the position in the open-menu packet; detectAndSendChanges/updateProgressBar become a
 * DataSlot for computingTime; the slots take any item (1.6.4 Slot ignored isItemValidForSlot); the name goes back
 * through the tile's GUI return handler; the status panel's widget (plan section 10) is new.
 */

package buildcraftreforged.builders.gui;

import buildcraftreforged.builders.BuildersRegistry;
import buildcraftreforged.builders.TileArchitect;
import buildcraftreforged.core.gui.BuildCraftContainer;
import buildcraftreforged.core.network.IGuiReturnHandler;
import buildcraftreforged.core.status.StatusWidget;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class ContainerTemplate extends BuildCraftContainer {

    protected Inventory playerIInventory;
    public final TileArchitect template;
    public final StatusWidget statusWidget;

    /** 1.6.4's Slot put anything in; TileArchitect.canPlaceItem keeps pipes and hoppers out. */
    private static class TemplateSlot extends Slot {
        TemplateSlot(TileArchitect template, int slot, int x, int y) {
            super(template, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return true;
        }
    }

    /** Client side: the block position comes with the open-menu packet. */
    public ContainerTemplate(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, clientTile(playerInventory, extraData.readBlockPos()));
    }

    public ContainerTemplate(int containerId, Inventory playerInventory, TileArchitect template) {
        super(BuildersRegistry.architectMenu.get(), containerId, template.getContainerSize());
        this.playerIInventory = playerInventory;
        this.template = template;

        addSlot(new TemplateSlot(template, 0, 55, 35));
        addSlot(new TemplateSlot(template, 1, 114, 35));

        for (int l = 0; l < 3; l++) {
            for (int k1 = 0; k1 < 9; k1++) {
                addSlot(new Slot(playerInventory, k1 + l * 9 + 9, 8 + k1 * 18, 84 + l * 18));
            }

        }

        for (int i1 = 0; i1 < 9; i1++) {
            addSlot(new Slot(playerInventory, i1, 8 + i1 * 18, 142));
        }

        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return ContainerTemplate.this.template.computingTime;
            }

            @Override
            public void set(int value) {
                updateProgressBar(0, value);
            }
        });

        statusWidget = new StatusWidget(template);
        addWidget(statusWidget);
    }

    private static TileArchitect clientTile(Inventory playerInventory, BlockPos pos) {
        if (playerInventory.player.level().getBlockEntity(pos) instanceof TileArchitect tile) {
            return tile;
        }
        // The block went away before the menu arrived; the menu closes on the next stillValid check.
        return new TileArchitect(pos, BuildersRegistry.architectBlock.get().defaultBlockState());
    }

    public void updateProgressBar(int i, int j) {
        if (i == 0) {
            template.computingTime = j;
        }
    }

    @Override
    public boolean stillValid(Player entityplayer) {
        return template.stillValid(entityplayer);
    }

    @Override
    public IGuiReturnHandler getGuiReturnHandler() {
        return template;
    }
}
