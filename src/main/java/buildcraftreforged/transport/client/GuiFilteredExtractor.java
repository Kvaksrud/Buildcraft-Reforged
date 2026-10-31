package buildcraftreforged.transport.client;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.core.gui.GuiBuildCraft;
import buildcraftreforged.core.gui.buttons.GuiBetterButton;
import buildcraftreforged.core.gui.buttons.StandardButtonTextureSets;
import buildcraftreforged.core.gui.tooltips.ToolTip;
import buildcraftreforged.core.gui.tooltips.ToolTipLine;
import buildcraftreforged.core.network.PacketGuiReturn;
import buildcraftreforged.transport.module.ContainerFilteredExtractor;
import buildcraftreforged.transport.module.ModuleFilteredExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/** The Filtered Extractor's screen, drawn like 4.2.2 {@code GuiEmeraldPipe}: filters, the mode button, inventory. */
/* Client only. */
public class GuiFilteredExtractor extends GuiBuildCraft<ContainerFilteredExtractor> {

    /** The 4.2.2 {@code filter_2.png}. */
    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "textures/gui/filter_2.png");

    private GuiBetterButton button;
    private ModuleFilteredExtractor.Mode shown;

    public GuiFilteredExtractor(ContainerFilteredExtractor container, Inventory playerInventory, Component title) {
        super(container, container.pipe, TEXTURE, playerInventory, title, 175, 132);
    }

    @Override
    protected void init() {
        super.init();
        shown = container.module.getMode();
        button = new GuiBetterButton(0, leftPos + imageWidth - (80 + 6), topPos + 34, 80,
                StandardButtonTextureSets.SMALL_BUTTON, label(shown));
        updateToolTip();
        addRenderableWidget(button);
    }

    private static String label(ModuleFilteredExtractor.Mode mode) {
        return Component.translatable(mode.label).getString();
    }

    private void updateToolTip() {
        ToolTip tip = new ToolTip(500);
        tip.add(new ToolTipLine(Component.translatable(shown.label + ".tip").getString()));
        button.setToolTip(tip);
    }

    /** The mode arrives through a data slot after the screen opens; keep the label in step. */
    @Override
    protected void containerTick() {
        super.containerTick();
        if (container.module.getMode() != shown) {
            shown = container.module.getMode();
            button.setMessage(Component.literal(label(shown)));
            updateToolTip();
        }
    }

    @Override
    public void actionPerformed(AbstractButton pressed) {
        if (pressed == button) {
            ModuleFilteredExtractor.Mode[] modes = ModuleFilteredExtractor.Mode.values();
            container.module.setMode(modes[(container.module.getMode().ordinal() + 1) % modes.length]);
            new PacketGuiReturn(container.containerId, container.getGuiReturnHandler()).sendPacket();
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int par1, int par2) {
        super.drawGuiContainerForegroundLayer(par1, par2);
        String name = getTitle().getString();
        drawString(name, getCenteredOffset(name), 6, 0x404040);
        drawString(Component.translatable("gui.inventory").getString(), 8, imageHeight - 93, 0x404040);
    }
}
