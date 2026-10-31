/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: a GuiBuildCraft (4.2.2: a plain GuiContainer) of ContainerFilteredBuffer for
 * its 1.6.4 drawing calls, with no ledgers; the size goes to the constructor; the texture is filtered_buffer_gui.png.
 */
package buildcraftreforged.transport.gui;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.core.gui.GuiBuildCraft;
import buildcraftreforged.core.utils.StringUtils;
import buildcraftreforged.transport.TileFilteredBuffer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;

/**
 *
 * @author SandGrainOne
 */
/* Client only. */
public class GuiFilteredBuffer extends GuiBuildCraft<ContainerFilteredBuffer> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "textures/gui/filtered_buffer_gui.png");
    Inventory playerInventory;
    TileFilteredBuffer filteredBuffer;

    public GuiFilteredBuffer(ContainerFilteredBuffer container, Inventory playerInventory, Component title) {
        super(container, container.filteredBuffer, TEXTURE, playerInventory, title, 175, 169);

        this.playerInventory = playerInventory;
        this.filteredBuffer = container.filteredBuffer;
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float f, int x, int y) {
        setColor(1.0F, 1.0F, 1.0F, 1.0F);

        bindTexture(TEXTURE);
        int cornerX = (width - imageWidth) / 2;
        int cornerY = (height - imageHeight) / 2;

        drawTexturedModalRect(cornerX, cornerY, 0, 0, imageWidth, imageHeight);

        Container filters = filteredBuffer.getFilters();

        for (int col = 0; col < filters.getContainerSize(); col++) {
            if (filters.getItem(col).isEmpty()) {
                drawTexturedModalRect(cornerX + 7 + col * 18, cornerY + 60, 176, 0, 18, 18);
            }
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int par1, int par2) {
        super.drawGuiContainerForegroundLayer(par1, par2);
        String title = StringUtils.localize("tile.filteredBufferBlock");
        drawString(title, getCenteredOffset(title), 10, 0x404040);
    }
}
