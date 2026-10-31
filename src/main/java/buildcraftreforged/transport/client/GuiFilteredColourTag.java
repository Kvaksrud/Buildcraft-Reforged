package buildcraftreforged.transport.client;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.core.gui.GuiBuildCraft;
import buildcraftreforged.core.utils.EnumColor;
import buildcraftreforged.transport.TransportRegistry;
import buildcraftreforged.transport.module.ContainerFilteredColourTag;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Filtered Colour Tag's screen, drawn like 4.2.2 {@code GuiEmzuliPipe}: four filters with their paint buttons, each
 * showing its colour as the Pipe Color action icon.
 */
/* Client only. */
public class GuiFilteredColourTag extends GuiBuildCraft<ContainerFilteredColourTag> {

    /** The 4.2.2 {@code pipe_emzuli.png}. */
    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "textures/gui/pipe_emzuli.png");

    public GuiFilteredColourTag(ContainerFilteredColourTag container, Inventory playerInventory, Component title) {
        super(container, container.pipe, TEXTURE, playerInventory, title, 176, 166);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float f, int mouseX, int mouseY) {
        super.drawGuiContainerBackgroundLayer(f, mouseX, mouseY);
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        for (int slot = 0; slot < container.paintWidgets.length; slot++) {
            EnumColor colour = container.paint(slot);
            if (colour == null)
                continue;
            ContainerFilteredColourTag.PaintWidget widget = container.paintWidgets[slot];
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                    TransportRegistry.actionPipeColor.get(colour.ordinal()).get().getIcon(), x + widget.x + 2,
                    y + widget.y + 2, 16, 16);
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int par1, int par2) {
        super.drawGuiContainerForegroundLayer(par1, par2);
        String title = Component.translatable("gui.pipes.emzuli.title").getString();
        drawString(title, getCenteredOffset(title), 6, 0x404040);
        drawString(Component.translatable("gui.inventory").getString(), 8, imageHeight - 93, 0x404040);
    }
}
