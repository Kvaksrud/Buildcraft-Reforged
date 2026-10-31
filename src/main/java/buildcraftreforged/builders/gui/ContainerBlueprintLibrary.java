/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: an AbstractContainerMenu with a MenuType and a client constructor that finds
 * the block entity from the position in the open-menu packet; detectAndSendChanges/updateProgressBar become two
 * DataSlots for progressIn and progressOut; the slots take any item (1.6.4 Slot ignored isItemValidForSlot); the
 * buttons and row clicks go back through the tile's GUI return handler.
 */

package buildcraftreforged.builders.gui;

import buildcraftreforged.builders.BuildersRegistry;
import buildcraftreforged.builders.TileBlueprintLibrary;
import buildcraftreforged.core.gui.BuildCraftContainer;
import buildcraftreforged.core.network.IGuiReturnHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class ContainerBlueprintLibrary extends BuildCraftContainer {

    protected Container playerInventory;
    public final TileBlueprintLibrary library;

    /** 1.6.4's Slot put anything in; TileBlueprintLibrary.canPlaceItem keeps pipes and hoppers out. */
    private static class LibrarySlot extends Slot {
        LibrarySlot(TileBlueprintLibrary library, int slot, int x, int y) {
            super(library, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return true;
        }
    }

    /** Client side: the block position comes with the open-menu packet. */
    public ContainerBlueprintLibrary(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, clientTile(playerInventory, extraData.readBlockPos()));
    }

    public ContainerBlueprintLibrary(int containerId, Inventory player, TileBlueprintLibrary library) {
        super(BuildersRegistry.libraryMenu.get(), containerId, library.getContainerSize());
        this.playerInventory = player;
        this.library = library;

        // if (player.username.equals(library.owner)) {
        addSlot(new LibrarySlot(library, 0, 153, 61));
        addSlot(new LibrarySlot(library, 1, 109, 61));
        // }

        addSlot(new LibrarySlot(library, 2, 109, 79));
        addSlot(new LibrarySlot(library, 3, 153, 79));

        // Player inventory
        for (int l = 0; l < 3; l++) {
            for (int k1 = 0; k1 < 9; k1++) {
                addSlot(new Slot(playerInventory, k1 + l * 9 + 9, 8 + k1 * 18, 140 + l * 18));
            }
        }

        for (int i1 = 0; i1 < 9; i1++) {
            addSlot(new Slot(playerInventory, i1, 8 + i1 * 18, 198));
        }

        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return ContainerBlueprintLibrary.this.library.progressIn;
            }

            @Override
            public void set(int value) {
                updateProgressBar(0, value);
            }
        });
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return ContainerBlueprintLibrary.this.library.progressOut;
            }

            @Override
            public void set(int value) {
                updateProgressBar(1, value);
            }
        });
    }

    private static TileBlueprintLibrary clientTile(Inventory playerInventory, BlockPos pos) {
        if (playerInventory.player.level().getBlockEntity(pos) instanceof TileBlueprintLibrary tile) {
            return tile;
        }
        // The block went away before the menu arrived; the menu closes on the next stillValid check.
        return new TileBlueprintLibrary(pos, BuildersRegistry.libraryBlock.get().defaultBlockState());
    }

    public void updateProgressBar(int i, int j) {
        if (i == 0) {
            library.progressIn = j;
        } else if (i == 1) {
            library.progressOut = j;
        }
    }

    @Override
    public boolean stillValid(Player entityplayer) {
        return library.stillValid(entityplayer);
    }

    @Override
    public IGuiReturnHandler getGuiReturnHandler() {
        return library;
    }
}
