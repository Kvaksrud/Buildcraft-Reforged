/*
 * Ported from buildcraft/energy/gui/GuiEngine.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: GuiBuildCraft is generic over the menu, with the player inventory and title
 * in the constructor; the ledger's icon is the icons/energy GUI sprite instead of CoreIconProvider.ENERGY on the item
 * atlas; the values are FE and FE/t instead of MJ and MJ/t; the status ledger (plan section 10) is added after it.
 */
package buildcraftreforged.energy.gui;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.core.gui.GuiBuildCraft;
import buildcraftreforged.core.gui.StatusLedger;
import buildcraftreforged.energy.TileEngine;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;

/* Client only. */
public abstract class GuiEngine extends GuiBuildCraft<ContainerEngine> {

    private static final Identifier ENERGY_ICON = Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "icons/energy");

    protected class EngineLedger extends Ledger {

        TileEngine engine;
        int headerColour = 0xe1c92f;
        int subheaderColour = 0xaaafb8;
        int textColour = 0x000000;

        public EngineLedger(TileEngine engine) {
            this.engine = engine;
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

            drawStringWithShadow(I18n.get("gui.energy"), x + 22, y + 8, headerColour);
            drawStringWithShadow(I18n.get("gui.currentOutput") + ":", x + 22, y + 20, subheaderColour);
            drawString(String.format("%.1f FE/t", engine.currentOutput), x + 22, y + 32, textColour);
            drawStringWithShadow(I18n.get("gui.stored") + ":", x + 22, y + 44, subheaderColour);
            drawString(String.format("%.1f FE", engine.getEnergyStored()), x + 22, y + 56, textColour);
            drawStringWithShadow(I18n.get("gui.heat") + ":", x + 22, y + 68, subheaderColour);
            drawString(String.format("%.2f °C", engine.getHeat()), x + 22, y + 80, textColour);

        }

        @Override
        public String getTooltip() {
            return String.format("%.1f FE/t", engine.currentOutput);
        }
    }

    public GuiEngine(ContainerEngine container, Identifier texture, Inventory playerInventory, Component title) {
        super(container, container.getEngine(), texture, playerInventory, title);
    }

    @Override
    protected void initLedgers(@Nullable Object inventory) {
        super.initLedgers(inventory);
        ledgerManager.add(new EngineLedger((TileEngine) inventory));
        ledgerManager.add(new StatusLedger<>(this, container.statusWidget));
    }
}
