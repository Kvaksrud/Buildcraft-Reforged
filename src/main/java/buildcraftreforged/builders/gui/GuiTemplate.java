/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: a screen of ContainerTemplate (the size goes in the constructor); keyTyped is
 * split into keyPressed (Enter, Backspace, and every other key swallowed while editing so E does not close the screen)
 * and charTyped; each character goes back as a PacketGuiReturn for the open menu where 4.2.2 sent ARCHITECT_NAME; the
 * status ledger (plan section 10) is new.
 */
package buildcraftreforged.builders.gui;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.builders.TileArchitect;
import buildcraftreforged.core.gui.GuiBuildCraft;
import buildcraftreforged.core.gui.StatusLedger;
import buildcraftreforged.core.network.PacketGuiReturn;
import buildcraftreforged.core.utils.StringUtils;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.Date;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;

/* Client only. */
public class GuiTemplate extends GuiBuildCraft<ContainerTemplate> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "textures/gui/template_gui.png");
    Inventory playerInventory;
    TileArchitect template;
    boolean editMode = false;

    public GuiTemplate(ContainerTemplate container, Inventory playerInventory, Component title) {
        super(container, container.template, TEXTURE, playerInventory, title, 175, 225);
        this.playerInventory = playerInventory;
        this.template = container.template;
    }

    @Override
    protected void initLedgers(@Nullable Object inventory) {
        super.initLedgers(inventory);
        ledgerManager.add(new StatusLedger<>(this, ((ContainerTemplate) menu).statusWidget));
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int par1, int par2) {
        super.drawGuiContainerForegroundLayer(par1, par2);
        String title = StringUtils.localize("tile.architectBlock");
        drawString(title, getCenteredOffset(title), 6, 0x404040);
        drawString(StringUtils.localize("gui.inventory"), 8, imageHeight - 152, 0x404040);

        if (editMode && ((new Date()).getTime() / 100) % 8 >= 4) {
            drawString(template.name + "|", 51, 62, 0x404040);
        } else {
            drawString(template.name, 51, 62, 0x404040);
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float f, int x, int y) {
        super.drawGuiContainerBackgroundLayer(f, x, y);
        int j = (width - imageWidth) / 2;
        int k = (height - imageHeight) / 2;
        int i1 = template.getComputingProgressScaled(24);
        bindTexture(TEXTURE);
        drawTexturedModalRect(j + 79, k + 34, 176, 14, i1 + 1, 16);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        boolean handled = super.mouseClicked(event, doubleClick);

        int xMin = (width - imageWidth) / 2;
        int yMin = (height - imageHeight) / 2;

        int x = (int) event.x() - xMin;
        int y = (int) event.y() - yMin;

        if (editMode) {
            editMode = false;
        } else if (x >= 50 && y >= 61 && x <= 137 && y <= 139) {
            editMode = true;
        }
        return handled;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!event.isEscape() && editMode) {
            if (event.isConfirmation()) {
                editMode = false;
            } else if (event.key() == InputConstants.KEY_BACKSPACE) {
                sendChar((char) 8);
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (editMode && Character.isBmpCodePoint(event.codepoint())) {
            sendChar((char) event.codepoint());
            return true;
        }
        return super.charTyped(event);
    }

    private void sendChar(char c) {
        new PacketGuiReturn(menu.containerId, template, new byte[] { (byte) (c >> 8), (byte) c }).sendPacket();
    }
}
