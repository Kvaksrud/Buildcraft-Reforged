/*
 * Ported from buildcraft/core/gui/buttons/GuiBetterButton.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: on AbstractButton. drawButton is extractContents, xPosition/yPosition are
 * getX()/getY(), enabled is active and drawButton (the flag) is visible. A press calls the screen's
 * GuiBuildCraft.actionPerformed, as 1.6.4's GuiScreen did for every GuiButton.
 */
package buildcraftreforged.core.gui.buttons;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.core.gui.GuiBuildCraft;
import buildcraftreforged.core.gui.tooltips.IToolTipProvider;
import buildcraftreforged.core.gui.tooltips.ToolTip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 *
 * @author CovertJaguar <railcraft.wikispaces.com>
 */
/* Client only. */
public class GuiBetterButton extends AbstractButton implements IToolTipProvider {

    public static final Identifier BUTTON_TEXTURES =
            Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "textures/gui/buttons.png");
    public final int id;
    protected final IButtonTextureSet texture;
    private ToolTip toolTip;

    public GuiBetterButton(int id, int x, int y, String label) {
        this(id, x, y, 200, StandardButtonTextureSets.LARGE_BUTTON, label);
    }

    public GuiBetterButton(int id, int x, int y, int width, String label) {
        this(id, x, y, width, StandardButtonTextureSets.LARGE_BUTTON, label);
    }

    public GuiBetterButton(int id, int x, int y, int width, IButtonTextureSet texture, String label) {
        super(x, y, width, texture.getHeight(), Component.literal(label));
        this.id = id;
        this.texture = texture;
    }

    @Override
    public int getHeight() {
        return texture.getHeight();
    }

    public int getTextColor(boolean mouseOver) {
        if (!active) {
            return 0xffa0a0a0;
        } else if (mouseOver) {
            return 0xffffffa0;
        } else {
            return 0xffe0e0e0;
        }
    }

    public boolean isMouseOverButton(int mouseX, int mouseY) {
        return mouseX >= getX() && mouseY >= getY() && mouseX < getX() + width && mouseY < getY() + getHeight();
    }

    /** 1.6.4 {@code GuiButton.getHoverState}: 0 disabled, 1 normal, 2 under the mouse. */
    protected int getHoverState(boolean mouseOver) {
        if (!active) {
            return 0;
        }
        return mouseOver ? 2 : 1;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        Font fontrenderer = Minecraft.getInstance().font;
        int xOffset = texture.getX();
        int yOffset = texture.getY();
        int h = texture.getHeight();
        int w = texture.getWidth();
        boolean mouseOver = isMouseOverButton(mouseX, mouseY);
        int hoverState = getHoverState(mouseOver);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BUTTON_TEXTURES, getX(), getY(), xOffset, yOffset + hoverState * h,
                width / 2, h, 256, 256);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BUTTON_TEXTURES, getX() + width / 2, getY(), xOffset + w - width / 2,
                yOffset + hoverState * h, width / 2, h, 256, 256);
        graphics.centeredText(fontrenderer, getMessage(), getX() + width / 2, getY() + (h - 8) / 2,
                getTextColor(mouseOver));
    }

    @Override
    public void onPress(InputWithModifiers input) {
        if (Minecraft.getInstance().gui.screen() instanceof GuiBuildCraft<?> gui) {
            gui.actionPerformed(this);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    @Override
    public ToolTip getToolTip() {
        return toolTip;
    }

    public void setToolTip(ToolTip tips) {
        this.toolTip = tips;
    }

    @Override
    public boolean isToolTipVisible() {
        return visible;
    }

    @Override
    public boolean isMouseOver(int mouseX, int mouseY) {
        return isMouseOverButton(mouseX, mouseY);
    }
}
