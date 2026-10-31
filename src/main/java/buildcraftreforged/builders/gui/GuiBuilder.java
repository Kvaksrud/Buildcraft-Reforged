/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: a screen of ContainerBuilder (GuiAdvancedInterface is generic in its menu; the
 * size goes in the constructor); the needed items are the ones the builder's network payload carries; the menu's
 * widgets are drawn with the background, as GuiBuildCraft does; the status ledger (plan section 10) is new.
 */
package buildcraftreforged.builders.gui;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.builders.TileBuilder;
import buildcraftreforged.core.gui.GuiAdvancedInterface;
import buildcraftreforged.core.gui.StatusLedger;
import buildcraftreforged.core.gui.widgets.Widget;
import buildcraftreforged.core.utils.StringUtils;
import java.util.Collection;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/* Client only. */
public class GuiBuilder extends GuiAdvancedInterface<ContainerBuilder> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "textures/gui/builder.png");
    private static final Identifier BLUEPRINT_TEXTURE =
            Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "textures/gui/builder_blueprint.png");
    Inventory playerInventory;
    TileBuilder builder;

    @SuppressWarnings("unchecked")
    public GuiBuilder(ContainerBuilder container, Inventory playerInventory, Component title) {
        super(container, container.builder, TEXTURE, playerInventory, title, 176, 225);
        this.playerInventory = playerInventory;
        this.builder = container.builder;

        // AdvancedSlot is an inner class of a generic screen, so the array is created raw.
        slots = (AdvancedSlot[]) new GuiAdvancedInterface.AdvancedSlot[7 * 4];

        for (int i = 0; i < 7; ++i) {
            for (int j = 0; j < 4; ++j) {
                slots[i * 4 + j] = new ItemSlot(179 + j * 18, 18 + i * 18);
            }
        }
    }

    @Override
    protected void initLedgers(@Nullable Object inventory) {
        super.initLedgers(inventory);
        ledgerManager.add(new StatusLedger<>(this, ((ContainerBuilder) menu).statusWidget));
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int par1, int par2) {
        super.drawGuiContainerForegroundLayer(par1, par2);

        String title = StringUtils.localize("tile.builderBlock");
        drawString(title, getCenteredOffset(title), 12, 0x404040);
        drawString(StringUtils.localize("gui.building.resources"), 8, 60, 0x404040);
        drawString(StringUtils.localize("gui.inventory"), 8, imageHeight - 97, 0x404040);

        if (builder.isBuildingBlueprint()) {
            drawString(StringUtils.localize("gui.needed"), 185, 7, 0x404040);
        }

        drawForegroundSelection(par1, par2);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float f, int x, int y) {
        setColor(1.0F, 1.0F, 1.0F, 1.0F);
        int j = (width - imageWidth) / 2;
        int k = (height - imageHeight) / 2;
        int realXSize = 0;

        if (builder.isBuildingBlueprint()) {
            bindTexture(BLUEPRINT_TEXTURE);
            realXSize = 256;
        } else {
            bindTexture(TEXTURE);
            realXSize = 176;
        }

        drawTexturedModalRect(j, k, 0, 0, realXSize, imageHeight);

        for (Widget widget : container.getWidgets()) {
            if (!widget.hidden) {
                widget.draw(this, j, k, x - leftPos, y - topPos);
            }
        }

        for (int s = 0; s < slots.length; ++s) {
            ((ItemSlot) slots[s]).stack = ItemStack.EMPTY;
        }

        Collection<ItemStack> needs = builder.getNeededItems();

        if (needs != null) {
            int s = 0;

            for (ItemStack stack : needs) {
                if (s >= slots.length) {
                    break;
                }

                ((ItemSlot) slots[s]).stack = stack.copy();
                s++;
            }
        }

        drawBackgroundSlots();
    }
    int inventoryRows = 6;
}
