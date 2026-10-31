/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: an AbstractContainerMenu with a MenuType and a client constructor that finds
 * the block entity from the position in the open-menu packet; the progress bar is a vanilla DataSlot
 * (sendProgressBarUpdate); InventoryCraftResult is ResultContainer; onCraftMatrixChanged is slotsChanged and only the
 * server looks the recipe up (the client has no recipe manager; it gets the result slot from the server); slotClick is
 * clicked; the status panel's widget (plan section 10) is new.
 */
package buildcraftreforged.factory.gui;

import buildcraftreforged.core.gui.BuildCraftContainer;
import buildcraftreforged.core.gui.slots.SlotOutput;
import buildcraftreforged.core.gui.slots.SlotUntouchable;
import buildcraftreforged.core.gui.slots.SlotWorkbench;
import buildcraftreforged.core.status.StatusWidget;
import buildcraftreforged.core.utils.StringUtils;
import buildcraftreforged.core.utils.Utils;
import buildcraftreforged.factory.FactoryRegistry;
import buildcraftreforged.factory.TileAutoWorkbench;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class ContainerAutoWorkbench extends BuildCraftContainer {

    public final TileAutoWorkbench tile;
    public Container craftResult;
    public final StatusWidget statusWidget;
    private ItemStack prevOutput = ItemStack.EMPTY;

    /** Client side: the block position comes with the open-menu packet. */
    public ContainerAutoWorkbench(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, clientTile(playerInventory, extraData.readBlockPos()));
    }

    public ContainerAutoWorkbench(int containerId, Inventory inventoryplayer, TileAutoWorkbench t) {
        super(FactoryRegistry.autoWorkbenchMenu.get(), containerId, t.getContainerSize());

        craftResult = new ResultContainer() {
            @Override
            public void setItem(int slot, ItemStack stack) {
                super.setItem(slot, stack);
                if (!stack.isEmpty() && tile.isLast() && isRenderWorld()) {
                    Utils.addItemToolTip(stack, "tip", ChatFormatting.YELLOW + StringUtils.localize("gui.clickcraft"));
                }
            }
        };
        this.tile = t;
        addSlot(new SlotUntouchable(craftResult, 0, 93, 27) {
            @Override
            public void onTake(Player player, ItemStack itemstack) {
                tile.useLast = true;
            }
        });
        addSlot(new SlotOutput(tile, TileAutoWorkbench.SLOT_RESULT, 124, 35));
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                addSlot(new SlotWorkbench(tile.craftMatrix, x + y * 3, 30 + x * 18, 17 + y * 18));
            }
        }

        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 9; x++) {
                addSlot(new Slot(inventoryplayer, x + y * 9 + 9, 8 + x * 18, 84 + y * 18));
            }
        }

        for (int x = 0; x < 9; x++) {
            addSlot(new Slot(inventoryplayer, x, 8 + x * 18, 142));
        }

        // 4.2.2 addCraftingToCrafters, detectAndSendChanges and updateProgressBar for id 0.
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return tile.progress;
            }

            @Override
            public void set(int value) {
                tile.progress = value;
            }
        });

        statusWidget = new StatusWidget(tile);
        addWidget(statusWidget);

        slotsChanged(tile);
    }

    private static TileAutoWorkbench clientTile(Inventory playerInventory, BlockPos pos) {
        if (playerInventory.player.level().getBlockEntity(pos) instanceof TileAutoWorkbench tile) {
            return tile;
        }
        // The block went away before the menu arrived; the menu closes on the next stillValid check.
        return new TileAutoWorkbench(pos, FactoryRegistry.autoWorkbenchBlock.get().defaultBlockState());
    }

    private boolean isRenderWorld() {
        return tile.getLevel() == null || tile.getLevel().isClientSide();
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();

        ItemStack output = craftResult.getItem(0);
        if (output != prevOutput) {
            prevOutput = output;
            slotsChanged(tile.craftMatrix);
        }
    }

    @Override
    public final void slotsChanged(Container inv) {
        super.slotsChanged(inv);
        if (isRenderWorld())
            return;
        ItemStack output = tile.findRecipeOutput();
        craftResult.setItem(0, output);
    }

    @Override
    public void clicked(int slotNum, int mouseButton, ContainerInput modifier, Player entityplayer) {
        super.clicked(slotNum, mouseButton, modifier, entityplayer);
        slotsChanged(tile.craftMatrix);
    }

    @Override
    public boolean stillValid(Player entityplayer) {
        return tile.stillValid(entityplayer);
    }
}
