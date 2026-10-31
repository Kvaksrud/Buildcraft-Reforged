/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: a screen of ContainerAssemblyTable (GuiAdvancedInterface is generic in its
 * menu; the size goes in the constructor); the ledger shows FE; the selection goes to the server as a PacketGuiReturn,
 * and the current selection arrives with the table's network payload instead of being requested with
 * SELECTION_ASSEMBLY_GET.
 */

package buildcraftreforged.silicon.gui;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.api.recipes.AssemblyRecipe;
import buildcraftreforged.core.gui.GuiAdvancedInterface;
import buildcraftreforged.core.network.PacketData;
import buildcraftreforged.core.network.PacketGuiReturn;
import buildcraftreforged.core.utils.StringUtils;
import buildcraftreforged.silicon.TileAssemblyTable;
import buildcraftreforged.silicon.TileAssemblyTable.SelectionMessage;
import java.util.Iterator;
import java.util.LinkedList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/* Client only. */
public class GuiAssemblyTable extends GuiAdvancedInterface<ContainerAssemblyTable> {
    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "textures/gui/assembly_table.png");
    private static final Identifier ENERGY_ICON = Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "icons/energy");
    TileAssemblyTable assemblyTable;

    class AssemblyLedger extends Ledger {
        int headerColour = 0xe1c92f;
        int subheaderColour = 0xaaafb8;
        int textColour = 0x000000;

        public AssemblyLedger() {
            maxHeight = 94;
            overlayColor = 0xd46c1f;
        }

        @Override
        public void draw(int x, int y) {

            // Draw background
            drawBackground(x, y);

            // Draw icon
            drawIcon(ENERGY_ICON, x + 3, y + 4);

            if (!isFullyOpened())
                return;

            drawStringWithShadow(StringUtils.localize("gui.energy"), x + 22, y + 8, headerColour);
            drawStringWithShadow(StringUtils.localize("gui.assemblyCurrentRequired") + ":", x + 22, y + 20, subheaderColour);
            drawString(String.format("%d FE", assemblyTable.getRequiredEnergy()), x + 22, y + 32, textColour);
            drawStringWithShadow(StringUtils.localize("gui.stored") + ":", x + 22, y + 44, subheaderColour);
            drawString(String.format("%d FE", assemblyTable.getStoredEnergy()), x + 22, y + 56, textColour);
            drawStringWithShadow(StringUtils.localize("gui.assemblyRate") + ":", x + 22, y + 68, subheaderColour);
            drawString(String.format("%3.2f FE/t", assemblyTable.getRecentEnergyAverage() / 100.0f), x + 22, y + 80, textColour);

        }

        @Override
        public String getTooltip() {
            return String.format("%3.2f FE/t", assemblyTable.getRecentEnergyAverage() / 100.0f);
        }

    }

    class RecipeSlot extends AdvancedSlot {

        public @Nullable AssemblyRecipe recipe;

        public RecipeSlot(int x, int y) {
            super(x, y);
        }

        @Override
        public ItemStack getItemStack() {
            if (this.recipe != null)
                return this.recipe.output;
            else
                return ItemStack.EMPTY;
        }
    }

    @SuppressWarnings("unchecked")
    public GuiAssemblyTable(ContainerAssemblyTable container, Inventory playerInventory, Component title) {
        super(container, container.table, TEXTURE, playerInventory, title, 175, 207);

        this.assemblyTable = container.table;

        // AdvancedSlot is an inner class of a generic screen, so the array is created raw.
        slots = (AdvancedSlot[]) new GuiAdvancedInterface.AdvancedSlot[8];

        int p = 0;

        for (int j = 0; j < 2; ++j) {
            for (int i = 0; i < 4; ++i) {
                slots[p] = new RecipeSlot(134 + 18 * j, 36 + 18 * i);
                p++;
            }
        }

        updateRecipes();

        // 4.2.2 asked the server for the current selection here (SELECTION_ASSEMBLY_GET); it now comes with the
        // table's network payload.
    }

    public void updateRecipes() {
        LinkedList<AssemblyRecipe> potentialRecipes = assemblyTable.getPotentialOutputs();
        Iterator<AssemblyRecipe> cur = potentialRecipes.iterator();

        for (int p = 0; p < 8; ++p)
            if (cur.hasNext()) {
                ((RecipeSlot) slots[p]).recipe = cur.next();
            } else {
                ((RecipeSlot) slots[p]).recipe = null;
            }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int par1, int par2) {
        super.drawGuiContainerForegroundLayer(par1, par2);
        String title = StringUtils.localize("tile.assemblyTableBlock");
        drawString(title, getCenteredOffset(title), 15, 0x404040);
        drawString(StringUtils.localize("gui.inventory"), 8, imageHeight - 97, 0x404040);
        drawForegroundSelection(par1, par2);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float f, int x, int y) {
        setColor(1.0F, 1.0F, 1.0F, 1.0F);
        bindTexture(TEXTURE);
        int cornerX = (width - imageWidth) / 2;
        int cornerY = (height - imageHeight) / 2;
        drawTexturedModalRect(cornerX, cornerY, 0, 0, imageWidth, imageHeight);

        updateRecipes();

        for (int s = 0; s < slots.length; ++s) {
            RecipeSlot slot = (RecipeSlot) slots[s];

            if (assemblyTable.isAssembling(slot.recipe)) {
                drawTexturedModalRect(cornerX + slot.x, cornerY + slot.y, 196, 1, 16, 16);
            } else if (assemblyTable.isPlanned(slot.recipe)) {
                drawTexturedModalRect(cornerX + slot.x, cornerY + slot.y, 177, 1, 16, 16);
            }
        }

        int height = (int) assemblyTable.getCompletionRatio(70);

        drawTexturedModalRect(cornerX + 95, cornerY + 36 + 70 - height, 176, 18, 4, height);

        drawBackgroundSlots();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        boolean handled = super.mouseClicked(event, doubleClick);

        int cornerX = (width - imageWidth) / 2;
        int cornerY = (height - imageHeight) / 2;

        int position = getSlotAtLocation((int) event.x() - cornerX, (int) event.y() - cornerY);

        if (position != -1) {
            RecipeSlot slot = (RecipeSlot) slots[position];

            if (slot.recipe == null)
                return handled;

            SelectionMessage message = new SelectionMessage();

            if (assemblyTable.isPlanned(slot.recipe)) {
                assemblyTable.cancelPlanOutput(slot.recipe);
                message.select = false;
            } else {
                assemblyTable.planOutput(slot.recipe);
                message.select = true;
            }

            message.stack = slot.recipe.output;

            if (assemblyTable.getLevel() != null) {
                byte[] data = PacketData.write(assemblyTable.getLevel().registryAccess(), message::writeData);
                new PacketGuiReturn(container.containerId, assemblyTable, data).sendPacket();
            }
            return true;
        }

        return handled;
    }

    @Override
    protected void initLedgers(@Nullable Object inventory) {
        super.initLedgers(inventory);
        ledgerManager.add(new AssemblyLedger());
    }
}
