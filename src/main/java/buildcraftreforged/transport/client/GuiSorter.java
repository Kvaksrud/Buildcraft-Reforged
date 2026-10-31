package buildcraftreforged.transport.client;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.core.BuildCraftConfiguration;
import buildcraftreforged.core.gui.GuiBuildCraft;
import buildcraftreforged.transport.module.ContainerSorter;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Sorter's screen, drawn like 4.2.2 {@code GuiDiamondPipe}: six coloured filter rows (the colour-blind texture
 * when {@code colorBlindMode} is set) over the player inventory.
 */
/* Client only. */
public class GuiSorter extends GuiBuildCraft<ContainerSorter> {

    /** The 4.2.2 {@code filter.png} and {@code filter_cb.png}. */
    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "textures/gui/filter.png");
    private static final Identifier TEXTURE_CB =
            Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "textures/gui/filter_cb.png");

    public GuiSorter(ContainerSorter container, Inventory playerInventory, Component title) {
        super(container, container.pipe, colorBlind() ? TEXTURE_CB : TEXTURE, playerInventory, title, 175, 225);
    }

    private static boolean colorBlind() {
        return BuildCraftConfiguration.CLIENT_SPEC.isLoaded() && BuildCraftConfiguration.colorBlindMode.get();
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int par1, int par2) {
        super.drawGuiContainerForegroundLayer(par1, par2);
        String name = getTitle().getString();
        drawString(name, getCenteredOffset(name), 6, 0x404040);
        drawString(Component.translatable("gui.inventory").getString(), 8, imageHeight - 97, 0x404040);
    }
}
