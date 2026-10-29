/*
 * Ported from buildcraft/core/gui/GuiTools.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: the string methods take the frame's GuiGraphicsExtractor (1.6.4 drew through
 * the FontRenderer alone) and colours without alpha are drawn opaque. buttonList is the screen's
 * addRenderableWidget; xPosition is setX.
 */
package buildcraftreforged.core.gui;

import buildcraftreforged.core.gui.buttons.GuiBetterButton;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/* Client only. */
public class GuiTools {

    public static void drawCenteredString(GuiGraphicsExtractor graphics, Font fr, String s, int y) {
        drawCenteredString(graphics, fr, s, y, 176);
    }

    public static void drawCenteredString(GuiGraphicsExtractor graphics, Font fr, String s, int y, int guiWidth) {
        drawCenteredString(graphics, fr, s, y, guiWidth, 0x404040, false);
    }

    public static void drawCenteredString(GuiGraphicsExtractor graphics, Font fr, String s, int y, int guiWidth,
            int color, boolean shadow) {
        int sWidth = fr.width(s);
        int sPos = guiWidth / 2 - sWidth / 2;
        graphics.text(fr, s, sPos, y, (color & 0xFC000000) == 0 ? color | 0xFF000000 : color, shadow);
    }

    public static void newButtonRowAuto(Consumer<? super GuiBetterButton> buttonList, int xStart, int xSize,
            List<? extends GuiBetterButton> buttons) {
        int buttonWidth = 0;
        for (GuiBetterButton b : buttons) {
            buttonWidth += b.getWidth();
        }
        int remaining = xSize - buttonWidth;
        int spacing = remaining / (buttons.size() + 1);
        int pointer = 0;
        for (GuiBetterButton b : buttons) {
            pointer += spacing;
            b.setX(xStart + pointer);
            pointer += b.getWidth();
            buttonList.accept(b);
        }
    }

    public static void newButtonRow(Consumer<? super GuiBetterButton> buttonList, int xStart, int spacing,
            List<? extends GuiBetterButton> buttons) {
        int pointer = 0;
        for (GuiBetterButton b : buttons) {
            b.setX(xStart + pointer);
            pointer += b.getWidth() + spacing;
            buttonList.accept(b);
        }
    }
}
