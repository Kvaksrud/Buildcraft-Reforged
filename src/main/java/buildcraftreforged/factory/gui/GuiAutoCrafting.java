/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: a screen of ContainerAutoWorkbench; onGuiClosed's second onContainerClosed is
 * dropped (closing the screen already closes the menu); the foreground calls super so the ledgers, with the status
 * ledger (plan section 10), are drawn.
 */
package buildcraftreforged.factory.gui;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.core.gui.GuiBuildCraft;
import buildcraftreforged.core.gui.StatusLedger;
import buildcraftreforged.core.utils.StringUtils;
import buildcraftreforged.factory.TileAutoWorkbench;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;

/* Client only. */
public class GuiAutoCrafting extends GuiBuildCraft<ContainerAutoWorkbench> {

    public static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "textures/gui/autobench.png");
    private TileAutoWorkbench bench;

    public GuiAutoCrafting(ContainerAutoWorkbench container, Inventory inventoryplayer, Component title) {
        super(container, container.tile, TEXTURE, inventoryplayer, title);
        this.bench = container.tile;
    }

    @Override
    protected void initLedgers(@Nullable Object inventory) {
        super.initLedgers(inventory);
        ledgerManager.add(new StatusLedger<>(this, ((ContainerAutoWorkbench) menu).statusWidget));
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int par1, int par2) {
        super.drawGuiContainerForegroundLayer(par1, par2);
        String title = StringUtils.localize("tile.autoWorkbenchBlock");
        drawString(title, getCenteredOffset(title), 6, 0x404040);
        drawString(StringUtils.localize("gui.inventory"), 8, (imageHeight - 96) + 2, 0x404040);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float f, int mouseX, int mouseY) {
        setColor(1.0F, 1.0F, 1.0F, 1.0F);
        bindTexture(TEXTURE);
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        drawTexturedModalRect(x, y, 0, 0, imageWidth, imageHeight);
        if (bench.progress > 0) {
            int progress = bench.getProgressScaled(23);
            drawTexturedModalRect(x + 89, y + 45, 176, 0, progress + 1, 12);
        }
    }
}
