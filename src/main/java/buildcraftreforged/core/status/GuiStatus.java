/*
 * Buildcraft Reforged, 2026: new file, no 4.2.2 original. The status panel of a block without a GUI (plan section 10).
 */
package buildcraftreforged.core.status;

import buildcraftreforged.core.gui.GuiBuildCraft;
import buildcraftreforged.core.gui.StatusLedger;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** A screen that is only the status ledger, always open, centred. */
/* Client only. */
public class GuiStatus extends GuiBuildCraft<ContainerStatus> {
    private final StatusLedger<ContainerStatus> panel;

    public GuiStatus(ContainerStatus container, Inventory inventoryplayer, Component title) {
        super(container, container.tile, LEDGER_TEXTURE, inventoryplayer, title, StatusLedger.WIDTH,
                StatusLedger.MAX_HEIGHT);
        panel = new StatusLedger<>(this, container.statusWidget, title.getString());
        panel.setFullyOpen();
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float f, int mouseX, int mouseY) {
        // No background texture: the ledger is the whole panel.
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        panel.update();
        panel.draw(0, 0);
    }
}
