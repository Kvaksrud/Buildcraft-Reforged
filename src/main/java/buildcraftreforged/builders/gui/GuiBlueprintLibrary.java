/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: a screen of ContainerBlueprintLibrary (the size goes in the constructor);
 * the background is library_rw.png, which 4.2.2 shipped but did not use (it drew template_gui.png, the architect's);
 * initGui is init and the buttons are widgets; the Del and Lock buttons are the owner's, as 4.2.2's commented-out
 * owner check meant; each button and row click goes back as a PacketGuiReturn for the open menu where 4.2.2 sent
 * LIBRARY_ACTION and LIBRARY_SELECT; the unused client player index is dropped.
 */
package buildcraftreforged.builders.gui;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.builders.BuildCraftBuilders;
import buildcraftreforged.builders.TileBlueprintLibrary;
import buildcraftreforged.core.gui.GuiBuildCraft;
import buildcraftreforged.core.gui.buttons.GuiBetterButton;
import buildcraftreforged.core.network.PacketGuiReturn;
import buildcraftreforged.core.utils.StringUtils;
import io.netty.buffer.Unpooled;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/* Client only. */
public class GuiBlueprintLibrary extends GuiBuildCraft<ContainerBlueprintLibrary> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "textures/gui/library_rw.png");
    Player player;
    TileBlueprintLibrary library;
    ContainerBlueprintLibrary container;

    public GuiBlueprintLibrary(ContainerBlueprintLibrary container, Inventory playerInventory, Component title) {
        super(container, container.library, TEXTURE, playerInventory, title, 176, 225);
        this.player = playerInventory.player;

        this.library = container.library;
        this.container = container;
    }

    private @Nullable GuiBetterButton nextPageButton;
    private @Nullable GuiBetterButton prevPageButton;
    private @Nullable GuiBetterButton lockButton;
    private @Nullable GuiBetterButton deleteButton;

    @Override
    protected void init() {
        super.init();

        int j = (width - imageWidth) / 2;
        int k = (height - imageHeight) / 2;
        prevPageButton = new GuiBetterButton(0, j + 100, k + 23, 20, "<");
        nextPageButton = new GuiBetterButton(1, j + 122, k + 23, 20, ">");

        addRenderableWidget(prevPageButton);
        addRenderableWidget(nextPageButton);

        deleteButton = null;
        lockButton = null;
        if (library.isOwner(player)) {
            deleteButton = new GuiBetterButton(2, j + 100, k + 114, 25, StringUtils.localize("gui.del"));
            addRenderableWidget(deleteButton);

            lockButton = new GuiBetterButton(3, j + 127, k + 114, 40, StringUtils.localize("gui.lock"));
            addRenderableWidget(lockButton);
            updateLockButton();
        }
    }

    private void updateLockButton() {
        if (lockButton == null)
            return;
        if (library.locked) {
            lockButton.setMessage(Component.literal(StringUtils.localize("gui.unlock")));
        } else {
            lockButton.setMessage(Component.literal(StringUtils.localize("gui.lock")));
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int par1, int par2) {
        super.drawGuiContainerForegroundLayer(par1, par2);
        // fontRenderer.drawString(library.owner + "'s Library", 6, 6,
        // 0x404040);
        String title = StringUtils.localize("tile.libraryBlock");
        drawString(title, getCenteredOffset(title), 6, 0x404040);

        int c = 0;
        String[] currentNames = library.currentNames;
        for (int i = 0; i < currentNames.length; i++) {
            String name = currentNames[i];
            if (name == null) {
                break;
            }
            if (name.length() > BuildCraftBuilders.MAX_BLUEPRINTS_NAME_SIZE) {
                name = name.substring(0, BuildCraftBuilders.MAX_BLUEPRINTS_NAME_SIZE);
            }

            if (i == library.selected) {
                int l1 = 8;
                int i2 = 24;
                drawGradientRect(l1, i2 + 9 * c, l1 + 88, i2 + 9 * (c + 1), 0x80ffffff, 0x80ffffff);
            }

            drawString(name, 9, 25 + 9 * c, 0x404040);
            c++;
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float f, int x, int y) {
        super.drawGuiContainerBackgroundLayer(f, x, y);

        int j = (width - imageWidth) / 2;
        int k = (height - imageHeight) / 2;

        int inP = (int) (library.progressIn / 100.0 * 22.0);
        int outP = (int) (library.progressOut / 100.0 * 22.0);

        bindTexture(TEXTURE);
        drawTexturedModalRect(j + 128 + 22 - inP, k + 61, 176 + 22 - inP, 16, inP, 16);
        drawTexturedModalRect(j + 128, k + 78, 176, 0, outP, 16);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateLockButton();
    }

    @Override
    public void actionPerformed(AbstractButton button) {
        super.actionPerformed(button);

        if (button == nextPageButton) {
            sendCommand(TileBlueprintLibrary.COMMAND_NEXT, -1);
        } else if (button == prevPageButton) {
            sendCommand(TileBlueprintLibrary.COMMAND_PREV, -1);
        } else if (lockButton != null && button == lockButton) {
            sendCommand(TileBlueprintLibrary.COMMAND_LOCK_UPDATE, -1);
        } else if (deleteButton != null && button == deleteButton) {
            sendCommand(TileBlueprintLibrary.COMMAND_DELETE, -1);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        boolean handled = super.mouseClicked(event, doubleClick);

        int xMin = (width - imageWidth) / 2;
        int yMin = (height - imageHeight) / 2;

        int x = (int) event.x() - xMin;
        int y = (int) event.y() - yMin;

        if (x >= 8 && x <= 88) {
            int ySlot = (y - 24) / 9;

            if (y >= 24 && ySlot >= 0 && ySlot <= 11) {
                if (ySlot < library.currentNames.length) {
                    sendCommand(TileBlueprintLibrary.COMMAND_SELECT, ySlot);
                }
            }
        }
        return handled;
    }

    /** 4.2.2's PacketLibraryAction: the command, and the row for COMMAND_SELECT. */
    private void sendCommand(int command, int row) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeVarInt(command);
            if (command == TileBlueprintLibrary.COMMAND_SELECT) {
                buf.writeVarInt(row);
            }
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            new PacketGuiReturn(menu.containerId, library, bytes).sendPacket();
        } finally {
            buf.release();
        }
    }
}
