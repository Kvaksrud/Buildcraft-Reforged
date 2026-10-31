/*
 * Ported from buildcraft/factory/gui/GuiHopper.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: a GuiBuildCraft (4.2.2: a plain GuiContainer) of ContainerHopper, so it can
 * carry the status ledger (plan section 10); as in 4.2.2 it draws no title or labels of its own.
 */
package buildcraftreforged.factory.gui;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.core.gui.GuiBuildCraft;
import buildcraftreforged.core.gui.StatusLedger;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;

/* Client only. */
public class GuiHopper extends GuiBuildCraft<ContainerHopper> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "textures/gui/hopper_gui.png");

    public GuiHopper(ContainerHopper container, Inventory inventory, Component title) {
        super(container, container.hopper, TEXTURE, inventory, title);
    }

    @Override
    protected void initLedgers(@Nullable Object inventory) {
        super.initLedgers(inventory);
        ledgerManager.add(new StatusLedger<>(this, ((ContainerHopper) menu).statusWidget));
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float f, int x, int y) {
        setColor(1.0F, 1.0F, 1.0F, 1.0F);
        bindTexture(TEXTURE);
        int j = (width - imageWidth) / 2;
        int k = (height - imageHeight) / 2;
        drawTexturedModalRect(j, k, 0, 0, imageWidth, imageHeight);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int par1, int par2) {
        super.drawGuiContainerForegroundLayer(par1, par2);
    }
}
