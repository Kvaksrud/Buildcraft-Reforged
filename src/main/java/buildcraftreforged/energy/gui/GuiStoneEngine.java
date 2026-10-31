/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: the screen gets its menu from the registered MenuType instead of building
 * the ContainerEngine itself; the title and labels are translated components; GL11.glColor4f becomes setColor.
 */
package buildcraftreforged.energy.gui;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.energy.TileEngine;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/* Client only. */
public class GuiStoneEngine extends GuiEngine {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "textures/gui/steam_engine_gui.png");

    public GuiStoneEngine(ContainerEngine container, Inventory inventoryplayer, Component title) {
        super(container, TEXTURE, inventoryplayer, title);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int par1, int par2) {
        super.drawGuiContainerForegroundLayer(par1, par2);
        String title = Component.translatable("tile.engineStone").getString();
        drawString(title, getCenteredOffset(title), 6, 0x404040);
        drawString(Component.translatable("gui.inventory").getString(), 8, (imageHeight - 96) + 2, 0x404040);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float f, int x, int y) {
        setColor(1.0F, 1.0F, 1.0F, 1.0F);
        bindTexture(TEXTURE);
        int j = (width - imageWidth) / 2;
        int k = (height - imageHeight) / 2;
        drawTexturedModalRect(j, k, 0, 0, imageWidth, imageHeight);

        TileEngine engine = (TileEngine) tile;
        if (engine.getScaledBurnTime(12) > 0) {
            int l = engine.getScaledBurnTime(12);

            drawTexturedModalRect(j + 80, (k + 24 + 12) - l, 176, 12 - l, 14, l + 2);
        }
    }
}
