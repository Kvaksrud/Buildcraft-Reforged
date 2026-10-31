/*
 * Ported from buildcraft/silicon/gui/GuiAdvancedCraftingTable.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: a screen of ContainerAdvancedCraftingTable (GuiBuildCraft is generic in its
 * menu; the size goes in the constructor); the ledger shows FE; the energy icon is a sprite of the GUI atlas.
 */
package buildcraftreforged.silicon.gui;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.core.gui.GuiBuildCraft;
import buildcraftreforged.core.utils.StringUtils;
import buildcraftreforged.silicon.TileAdvancedCraftingTable;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;

/* Client only. */
public class GuiAdvancedCraftingTable extends GuiBuildCraft<ContainerAdvancedCraftingTable> {

    public static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "textures/gui/assembly_advancedworkbench.png");
    private static final Identifier ENERGY_ICON = Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "icons/energy");

    class AssemblyWorkbenchLedger extends Ledger {

        int headerColour = 0xe1c92f;
        int subheaderColour = 0xaaafb8;
        int textColour = 0x000000;

        public AssemblyWorkbenchLedger() {
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
            drawString(String.format("%d FE", workbench.getRequiredEnergy()), x + 22, y + 32, textColour);
            drawStringWithShadow(StringUtils.localize("gui.stored") + ":", x + 22, y + 44, subheaderColour);
            drawString(String.format("%d FE", workbench.getStoredEnergy()), x + 22, y + 56, textColour);
            drawStringWithShadow(StringUtils.localize("gui.assemblyRate") + ":", x + 22, y + 68, subheaderColour);
            drawString(String.format("%3.2f FE/t", workbench.getRecentEnergyAverage() / 100.0f), x + 22, y + 80, textColour);

        }

        @Override
        public String getTooltip() {
            return String.format("%3.2f FE/t", workbench.getRecentEnergyAverage() / 100.0f);
        }
    }

    TileAdvancedCraftingTable workbench;

    public GuiAdvancedCraftingTable(ContainerAdvancedCraftingTable container, Inventory playerInventory, Component title) {
        super(container, container.workbench, TEXTURE, playerInventory, title, 175, 240);
        this.workbench = container.workbench;
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int par1, int par2) {
        super.drawGuiContainerForegroundLayer(par1, par2);
        String title = StringUtils.localize("tile.assemblyWorkbenchBlock");
        drawString(title, getCenteredOffset(title), 6, 0x404040);
        drawString(StringUtils.localize("gui.inventory"), 8, imageHeight - 97, 0x404040);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float f, int mouseX, int mouseY) {
        setColor(1.0F, 1.0F, 1.0F, 1.0F);
        bindTexture(TEXTURE);
        int cornerX = (width - imageWidth) / 2;
        int cornerY = (height - imageHeight) / 2;
        drawTexturedModalRect(cornerX, cornerY, 0, 0, imageWidth, imageHeight);
        if (workbench.getStoredEnergy() > 0) {
            // 4.2.2 drew past the arrow once more than one craft's energy was stored (it stores up to ten).
            int progress = Math.min(workbench.getProgressScaled(24), 24);
            drawTexturedModalRect(cornerX + 93, cornerY + 32, 176, 0, progress + 1, 18);
        }
    }

    @Override
    protected void initLedgers(@Nullable Object inventory) {
        super.initLedgers(inventory);
        ledgerManager.add(new AssemblyWorkbenchLedger());
    }
}
