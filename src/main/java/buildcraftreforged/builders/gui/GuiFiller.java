/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: a screen of ContainerFiller (the size goes in the constructor); initGui is
 * init and the buttons are added as widgets; the pattern goes back as a PacketGuiReturn for the open menu; the status
 * ledger (plan section 10) is new.
 */
package buildcraftreforged.builders.gui;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.api.filler.FillerManager;
import buildcraftreforged.builders.TileFiller;
import buildcraftreforged.core.gui.GuiBuildCraft;
import buildcraftreforged.core.gui.GuiTools;
import buildcraftreforged.core.gui.StatusLedger;
import buildcraftreforged.core.gui.buttons.GuiBetterButton;
import buildcraftreforged.core.gui.buttons.StandardButtonTextureSets;
import buildcraftreforged.core.network.PacketGuiReturn;
import buildcraftreforged.core.utils.StringUtils;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;

/* Client only. */
public class GuiFiller extends GuiBuildCraft<ContainerFiller> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "textures/gui/filler.png");
    Inventory playerInventory;
    TileFiller filler;

    public GuiFiller(ContainerFiller container, Inventory playerInventory, Component title) {
        super(container, container.tile, TEXTURE, playerInventory, title, 175, 240);
        this.playerInventory = playerInventory;
        this.filler = container.tile;
    }

    @Override
    protected void initLedgers(@Nullable Object inventory) {
        super.initLedgers(inventory);
        ledgerManager.add(new StatusLedger<>(this, ((ContainerFiller) menu).statusWidget));
    }

    @Override
    protected void init() {
        super.init();
        int w = (width - imageWidth) / 2;
        int h = (height - imageHeight) / 2;

        addRenderableWidget(new GuiBetterButton(0, w + 80 - 18, h + 30, 10, StandardButtonTextureSets.LEFT_BUTTON, ""));
        addRenderableWidget(new GuiBetterButton(1, w + 80 + 16 + 8, h + 30, 10, StandardButtonTextureSets.RIGHT_BUTTON, ""));
    }

    @Override
    public void actionPerformed(AbstractButton button) {
        super.actionPerformed(button);

        if (!(button instanceof GuiBetterButton betterButton))
            return;

        if (betterButton.id == 0)
            filler.currentPattern = FillerManager.registry.getPreviousPattern(filler.currentPattern);
        if (betterButton.id == 1)
            filler.currentPattern = FillerManager.registry.getNextPattern(filler.currentPattern);

        new PacketGuiReturn(menu.containerId, filler).sendPacket();
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int par1, int par2) {
        super.drawGuiContainerForegroundLayer(par1, par2);
        String title = StringUtils.localize("tile.fillerBlock");
        drawString(title, getCenteredOffset(title), 6, 0x404040);
        drawString(StringUtils.localize("gui.filling.resources"), 8, 74, 0x404040);
        drawString(StringUtils.localize("gui.inventory"), 8, 142, 0x404040);
        GuiTools.drawCenteredString(graphics, font, filler.currentPattern.getDisplayName().getString(), 56, imageWidth);
    }
}
