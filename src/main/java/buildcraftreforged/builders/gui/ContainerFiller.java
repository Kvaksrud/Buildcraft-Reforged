/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: an AbstractContainerMenu with a MenuType and a client constructor that finds
 * the block entity from the position in the open-menu packet; the pattern widget draws the pattern's GUI sprite where
 * 4.2.2 drew its block icon; the pattern buttons go through the tile's GUI return handler; the status panel's widget
 * (plan section 10) is new.
 */
package buildcraftreforged.builders.gui;

import buildcraftreforged.builders.BuildersRegistry;
import buildcraftreforged.builders.TileFiller;
import buildcraftreforged.core.gui.BuildCraftContainer;
import buildcraftreforged.core.gui.GuiBuildCraft;
import buildcraftreforged.core.gui.widgets.Widget;
import buildcraftreforged.core.network.IGuiReturnHandler;
import buildcraftreforged.core.status.StatusWidget;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;

public class ContainerFiller extends BuildCraftContainer {

    Inventory playerIInventory;
    public final TileFiller tile;
    public final StatusWidget statusWidget;

    private class PatternWidget extends Widget {

        public PatternWidget() {
            super(80, 30, 0, 0, 16, 16);
        }

        /* Client only. */
        @Override
        public void draw(GuiBuildCraft<?> gui, int guiX, int guiY, int mouseX, int mouseY) {
            gui.getGraphics().blitSprite(RenderPipelines.GUI_TEXTURED, tile.currentPattern.getIcon(), guiX + x, guiY + y,
                    16, 16);
        }
    }

    /** Client side: the block position comes with the open-menu packet. */
    public ContainerFiller(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, clientTile(playerInventory, extraData.readBlockPos()));
    }

    public ContainerFiller(int containerId, Inventory playerInventory, TileFiller tile) {
        super(BuildersRegistry.fillerMenu.get(), containerId, tile.getContainerSize());
        this.playerIInventory = playerInventory;
        this.tile = tile;

        addWidget(new PatternWidget());

        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 9; x++) {
                addSlot(new Slot(tile, x + y * 9, 8 + x * 18, 85 + y * 18));
            }
        }

        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 9; x++) {
                addSlot(new Slot(playerInventory, x + y * 9 + 9, 8 + x * 18, 153 + y * 18));
            }

        }

        for (int x = 0; x < 9; x++) {
            addSlot(new Slot(playerInventory, x, 8 + x * 18, 211));
        }

        statusWidget = new StatusWidget(tile);
        addWidget(statusWidget);
    }

    private static TileFiller clientTile(Inventory playerInventory, BlockPos pos) {
        if (playerInventory.player.level().getBlockEntity(pos) instanceof TileFiller tile) {
            return tile;
        }
        // The block went away before the menu arrived; the menu closes on the next stillValid check.
        return new TileFiller(pos, BuildersRegistry.fillerBlock.get().defaultBlockState());
    }

    @Override
    public boolean stillValid(Player entityplayer) {
        return tile.stillValid(entityplayer);
    }

    @Override
    public IGuiReturnHandler getGuiReturnHandler() {
        return tile;
    }
}
