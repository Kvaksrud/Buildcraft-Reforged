package buildcraftreforged.gametest.gui;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.core.gui.GuiBuildCraft;
import buildcraftreforged.core.gui.buttons.GuiBetterButton;
import buildcraftreforged.core.gui.buttons.StandardButtonTextureSets;
import buildcraftreforged.core.network.PacketGuiReturn;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;

/** The sample screen: drawn like 4.2.2's {@code GuiStoneEngine}, with a ledger like {@code GuiEngine.EngineLedger}. */
/* Client only. */
public class GuiSampleGui extends GuiBuildCraft<ContainerSampleGui> {
    /** A copy of 4.2.2's {@code steam_engine_gui.png}. */
    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "textures/gui/sample_gui.png");
    public static final Identifier ENERGY_ICON = Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "icons/energy");

    private GuiBetterButton stepButton;

    public GuiSampleGui(ContainerSampleGui container, Inventory inventoryplayer, Component title) {
        super(container, container.tile, TEXTURE, inventoryplayer, title);
    }

    @Override
    protected void initLedgers(@Nullable Object inventory) {
        super.initLedgers(inventory);
        ledgerManager.add(new SampleLedger());
    }

    @Override
    protected void init() {
        super.init();
        stepButton = new GuiBetterButton(0, leftPos + 112, topPos + 41, 50, StandardButtonTextureSets.SMALL_BUTTON,
                stepLabel());
        addRenderableWidget(stepButton);
    }

    private String stepLabel() {
        return "Step " + container.tile.step;
    }

    @Override
    public void actionPerformed(AbstractButton button) {
        if (button == stepButton) {
            TileSampleGui tile = container.tile;
            tile.step = tile.step == 1 ? 5 : 1;
            stepButton.setMessage(Component.literal(stepLabel()));
            new PacketGuiReturn(container.containerId, tile).sendPacket();
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int par1, int par2) {
        super.drawGuiContainerForegroundLayer(par1, par2);
        String title = getTitle().getString();
        drawString(title, getCenteredOffset(title), 6, 0x404040);
        drawString(playerInventoryTitle.getString(), 8, (imageHeight - 96) + 2, 0x404040);
    }

    protected class SampleLedger extends Ledger {
        int headerColour = 0xe1c92f;
        int subheaderColour = 0xaaafb8;
        int textColour = 0x000000;

        public SampleLedger() {
            maxHeight = 70;
            overlayColor = 0xd46c1f;
        }

        @Override
        public void draw(int x, int y) {
            drawBackground(x, y);
            drawIcon(ENERGY_ICON, x + 3, y + 4);

            if (!isFullyOpened()) {
                return;
            }

            drawStringWithShadow("Sample", x + 22, y + 8, headerColour);
            drawStringWithShadow("Counter:", x + 22, y + 20, subheaderColour);
            drawString(String.valueOf(container.counterWidget.value), x + 22, y + 32, textColour);
            drawStringWithShadow("Step:", x + 22, y + 44, subheaderColour);
            drawString(String.valueOf(container.tile.step), x + 22, y + 56, textColour);
        }

        @Override
        public String getTooltip() {
            return "Counter " + container.counterWidget.value;
        }
    }
}
