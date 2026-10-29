/*
 * Ported from buildcraft/core/gui/GuiBuildCraft.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: on AbstractContainerScreen, generic in its menu. 1.6.4 drew straight to the GL
 * state; here each extract pass stores its GuiGraphicsExtractor in `graphics`, and bindTexture, setColor (glColor4f),
 * drawTexturedModalRect and drawGradientRect draw through it, so ported GUIs keep their drawing code. drawScreen is
 * extractRenderState, drawGuiContainerBackgroundLayer runs from extractBackground and
 * drawGuiContainerForegroundLayer after the slots, translated to the GUI corner as in 1.6.4; vanilla's title and
 * inventory labels are not drawn, since 4.2.2 GUIs draw their own. xSize/ySize are imageWidth/imageHeight (set in the
 * constructor), guiLeft/guiTop are leftPos/topPos, fontRenderer is font. zLevel 300 is a new GUI stratum. Mouse buttons
 * reach widgets and ledgers in the 1.6.4 numbering (0 left, 1 right, 2 middle). Ledger icons are GUI sprites
 * (textures/gui/sprites). A middle click on a phantom slot is sent as in 1.6.4, where it cleared the slot in survival
 * too. GuiButton.actionPerformed is actionPerformed(AbstractButton), called by GuiBetterButton.
 */
package buildcraftreforged.core.gui;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.core.gui.slots.IPhantomSlot;
import buildcraftreforged.core.gui.tooltips.IToolTipProvider;
import buildcraftreforged.core.gui.tooltips.ToolTip;
import buildcraftreforged.core.gui.tooltips.ToolTipLine;
import buildcraftreforged.core.gui.widgets.Widget;
import buildcraftreforged.core.utils.SessionVars;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.Collection;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/* Client only. */
public abstract class GuiBuildCraft<T extends BuildCraftContainer> extends AbstractContainerScreen<T> {

    public static final Identifier LEDGER_TEXTURE =
            Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "textures/gui/ledger.png");
    public final LedgerManager ledgerManager = new LedgerManager(this);
    public final @Nullable BlockEntity tile;
    public final Identifier texture;
    public final T container;

    /** The frame being drawn, set at the start of each extract pass. */
    protected GuiGraphicsExtractor graphics;
    private Identifier boundTexture;
    private int color = -1;
    private long lastClickTime;

    /** A 176 by 166 GUI, the 1.6.4 {@code GuiContainer} default size. */
    public GuiBuildCraft(T container, @Nullable Object inventory, Identifier texture, Inventory playerInventory,
            Component title) {
        this(container, inventory, texture, playerInventory, title, DEFAULT_IMAGE_WIDTH, DEFAULT_IMAGE_HEIGHT);
    }

    /**
     * @param inventory what 4.2.2 passed as the {@code IInventory}: usually the block entity, which becomes
     *     {@link #tile}, and is handed to {@link #initLedgers}
     */
    public GuiBuildCraft(T container, @Nullable Object inventory, Identifier texture, Inventory playerInventory,
            Component title, int xSize, int ySize) {
        super(container, playerInventory, title, xSize, ySize);
        this.container = container;

        this.texture = texture;
        this.boundTexture = texture;

        if (inventory instanceof BlockEntity blockEntity) {
            tile = blockEntity;
        } else {
            tile = null;
        }

        initLedgers(inventory);
    }

    protected void initLedgers(@Nullable Object inventory) {
    }

    /**
     * Draws the screen and all the components in it.
     */
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float par3) {
        this.graphics = graphics;
        super.extractRenderState(graphics, mouseX, mouseY, par3);
        int left = this.leftPos;
        int top = this.topPos;

        graphics.pose().pushMatrix();
        graphics.pose().translate(left, top);
        setColor(1.0F, 1.0F, 1.0F, 1.0F);

        if (menu.getCarried().isEmpty()) {
            drawToolTips(container.getWidgets(), mouseX, mouseY);
            drawToolTips(children(), mouseX, mouseY);
            drawToolTips(menu.slots, mouseX, mouseY);
        }

        graphics.pose().popMatrix();
    }

    private void drawToolTips(Collection<?> objects, int mouseX, int mouseY) {
        for (Object obj : objects) {
            if (!(obj instanceof IToolTipProvider provider)) {
                continue;
            }
            if (!provider.isToolTipVisible()) {
                continue;
            }
            ToolTip tips = provider.getToolTip();
            if (tips == null) {
                continue;
            }
            boolean mouseOver = provider.isMouseOver(mouseX - leftPos, mouseY - topPos);
            tips.onTick(mouseOver);
            if (mouseOver && tips.isReady()) {
                tips.refresh();
                drawToolTips(tips, mouseX, mouseY);
            }
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        this.graphics = graphics;
        drawGuiContainerBackgroundLayer(a, mouseX, mouseY);
    }

    protected void drawGuiContainerBackgroundLayer(float f, int mouseX, int mouseY) {
        setColor(1.0F, 1.0F, 1.0F, 1.0F);
        bindTexture(texture);
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        drawTexturedModalRect(x, y, 0, 0, imageWidth, imageHeight);

        int mX = mouseX - leftPos;
        int mY = mouseY - topPos;

        for (Widget widget : container.getWidgets()) {
            if (widget.hidden) {
                continue;
            }
            bindTexture(texture);
            widget.draw(this, x, y, mX, mY);
        }
    }

    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        this.graphics = graphics;
        super.extractContents(graphics, mouseX, mouseY, a);
        graphics.pose().pushMatrix();
        graphics.pose().translate(leftPos, topPos);
        drawGuiContainerForegroundLayer(mouseX, mouseY);
        graphics.pose().popMatrix();
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
    }

    protected void drawGuiContainerForegroundLayer(int par1, int par2) {
        ledgerManager.drawLedgers(par1, par2);
    }

    protected int getCenteredOffset(String string) {
        return getCenteredOffset(string, imageWidth);
    }

    protected int getCenteredOffset(String string, int xWidth) {
        return (xWidth - font.width(string)) / 2;
    }

    /**
     * Returns if the passed mouse position is over the specified slot.
     */
    private boolean isMouseOverSlot(Slot slot, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;
        mouseX -= left;
        mouseY -= top;
        return mouseX >= slot.x - 1 && mouseX < slot.x + 16 + 1 && mouseY >= slot.y - 1 && mouseY < slot.y + 16 + 1;
    }

    // / MOUSE CLICKS
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();
        int mouseButton = toLegacyButton(event.button());
        int mX = mouseX - leftPos;
        int mY = mouseY - topPos;
        lastClickTime = Util.getMillis();

        for (Widget widget : container.getWidgets()) {
            if (widget.hidden) {
                continue;
            }
            if (!widget.isMouseOver(mX, mY)) {
                continue;
            }
            if (widget.handleMouseClick(mX, mY, mouseButton)) {
                return true;
            }
        }

        // 1.6.4 sent the pick-block click to the server in survival too, and phantom slots cleared on it.
        Slot slot = getSlotAtPosition(mouseX, mouseY);
        if (slot instanceof IPhantomSlot && minecraft.options.keyPickItem.matchesMouse(event)) {
            slotClicked(slot, slot.index, 2, ContainerInput.CLONE);
            return true;
        }

        boolean handled = super.mouseClicked(event, doubleClick);

        // / Handle ledger clicks
        ledgerManager.handleMouseClicked(mouseX, mouseY, mouseButton);
        return handled;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();
        int mouseButton = toLegacyButton(event.button());
        int mX = mouseX - leftPos;
        int mY = mouseY - topPos;
        long time = Util.getMillis() - lastClickTime;
        for (Widget widget : container.getWidgets()) {
            if (widget.hidden) {
                continue;
            }
            widget.handleMouseMove(mX, mY, mouseButton, time);
        }

        Slot slot = getSlotAtPosition(mouseX, mouseY);
        if (mouseButton == 1 && slot instanceof IPhantomSlot) {
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean handled = super.mouseReleased(event);

        int mX = (int) event.x() - leftPos;
        int mY = (int) event.y() - topPos;
        int eventType = toLegacyButton(event.button());
        for (Widget widget : container.getWidgets()) {
            if (widget.hidden) {
                continue;
            }
            widget.handleMouseRelease(mX, mY, eventType);
        }
        return handled;
    }

    /** 26.x numbers mouse buttons from 1 (left 1, middle 2, right 3); 1.6.4 from 0 (left 0, right 1, middle 2). */
    public static int toLegacyButton(int button) {
        return switch (button) {
            case InputConstants.MOUSE_BUTTON_LEFT -> 0;
            case InputConstants.MOUSE_BUTTON_RIGHT -> 1;
            case InputConstants.MOUSE_BUTTON_MIDDLE -> 2;
            default -> button - 1;
        };
    }

    /** Called when a {@link buildcraftreforged.core.gui.buttons.GuiBetterButton} is pressed. */
    public void actionPerformed(AbstractButton button) {
    }

    public @Nullable Slot getSlotAtPosition(int x, int y) {
        for (int slotIndex = 0; slotIndex < this.menu.slots.size(); ++slotIndex) {
            Slot slot = this.menu.slots.get(slotIndex);
            if (isMouseOverSlot(slot, x, y)) {
                return slot;
            }
        }
        return null;
    }

    public void bindTexture(Identifier texture) {
        this.boundTexture = texture;
    }

    /** 1.6.4 {@code GL11.glColor4f}: tints what {@link #drawTexturedModalRect} draws next. */
    public void setColor(float red, float green, float blue, float alpha) {
        this.color = ARGB.colorFromFloat(alpha, red, green, blue);
    }

    /** Draws part of the bound 256 by 256 texture, as 1.6.4's {@code Gui.drawTexturedModalRect}. */
    public void drawTexturedModalRect(int x, int y, int u, int v, int width, int height) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, boundTexture, x, y, u, v, width, height, 256, 256, color);
    }

    public void drawGradientRect(int left, int top, int right, int bottom, int colorTop, int colorBottom) {
        graphics.fillGradient(left, top, right, bottom, colorTop, colorBottom);
    }

    /** 1.6.4 {@code FontRenderer.drawString}; colours without alpha are drawn opaque, as 1.6.4 did. */
    public void drawString(String text, int x, int y, int color) {
        graphics.text(font, text, x, y, opaque(color), false);
    }

    public void drawStringWithShadow(String text, int x, int y, int color) {
        graphics.text(font, text, x, y, opaque(color), true);
    }

    private static int opaque(int color) {
        return (color & 0xFC000000) == 0 ? color | 0xFF000000 : color;
    }

    public GuiGraphicsExtractor getGraphics() {
        return graphics;
    }

    private void drawToolTips(ToolTip toolTips, int mouseX, int mouseY) {
        if (toolTips.size() > 0) {
            int left = this.leftPos;
            int top = this.topPos;
            int lenght = 0;
            int x;
            int y;

            for (ToolTipLine tip : toolTips) {
                y = this.font.width(tip.text);

                if (y > lenght) {
                    lenght = y;
                }
            }

            x = mouseX - left + 12;
            y = mouseY - top - 12;
            int var14 = 8;

            if (toolTips.size() > 1) {
                var14 += 2 + (toolTips.size() - 1) * 10;
            }

            graphics.nextStratum();
            int var15 = -267386864;
            this.drawGradientRect(x - 3, y - 4, x + lenght + 3, y - 3, var15, var15);
            this.drawGradientRect(x - 3, y + var14 + 3, x + lenght + 3, y + var14 + 4, var15, var15);
            this.drawGradientRect(x - 3, y - 3, x + lenght + 3, y + var14 + 3, var15, var15);
            this.drawGradientRect(x - 4, y - 3, x - 3, y + var14 + 3, var15, var15);
            this.drawGradientRect(x + lenght + 3, y - 3, x + lenght + 4, y + var14 + 3, var15, var15);
            int var16 = 1347420415;
            int var17 = (var16 & 16711422) >> 1 | var16 & -16777216;
            this.drawGradientRect(x - 3, y - 3 + 1, x - 3 + 1, y + var14 + 3 - 1, var16, var17);
            this.drawGradientRect(x + lenght + 2, y - 3 + 1, x + lenght + 3, y + var14 + 3 - 1, var16, var17);
            this.drawGradientRect(x - 3, y - 3, x + lenght + 3, y - 3 + 1, var16, var16);
            this.drawGradientRect(x - 3, y + var14 + 2, x + lenght + 3, y + var14 + 3, var17, var17);

            for (ToolTipLine tip : toolTips) {
                String line = tip.text;

                if (tip.color == -1) {
                    line = "§7" + line;
                } else {
                    line = "§" + Integer.toHexString(tip.color) + line;
                }

                this.drawStringWithShadow(line, x, y, -1);

                y += 10 + tip.getSpacing();
            }
        }
    }

    protected class LedgerManager {

        private GuiBuildCraft<?> gui;
        protected ArrayList<Ledger> ledgers = new ArrayList<>();

        public LedgerManager(GuiBuildCraft<?> gui) {
            this.gui = gui;
        }

        public void add(Ledger ledger) {
            this.ledgers.add(ledger);
            if (SessionVars.getOpenedLedger() != null && ledger.getClass().equals(SessionVars.getOpenedLedger())) {
                ledger.setFullyOpen();
            }
        }

        /**
         * Inserts a ledger into the next-to-last position.
         *
         * @param ledger
         */
        public void insert(Ledger ledger) {
            this.ledgers.add(ledgers.size() - 1, ledger);
        }

        protected Ledger getAtPosition(int mX, int mY) {

            int xShift = ((gui.width - gui.imageWidth) / 2) + gui.imageWidth;
            int yShift = ((gui.height - gui.imageHeight) / 2) + 8;

            for (int i = 0; i < ledgers.size(); i++) {
                Ledger ledger = ledgers.get(i);
                if (!ledger.isVisible()) {
                    continue;
                }

                ledger.currentShiftX = xShift;
                ledger.currentShiftY = yShift;
                if (ledger.intersectsWith(mX, mY, xShift, yShift)) {
                    return ledger;
                }

                yShift += ledger.getHeight();
            }

            return null;
        }

        protected void drawLedgers(int mouseX, int mouseY) {

            int xPos = 8;
            for (Ledger ledger : ledgers) {

                ledger.update();
                if (!ledger.isVisible()) {
                    continue;
                }

                ledger.draw(imageWidth, xPos);
                xPos += ledger.getHeight();
            }

            Ledger ledger = getAtPosition(mouseX, mouseY);
            if (ledger != null) {
                int startX = mouseX - ((gui.width - gui.imageWidth) / 2) + 12;
                int startY = mouseY - ((gui.height - gui.imageHeight) / 2) - 12;

                String tooltip = ledger.getTooltip();
                int textWidth = font.width(tooltip);
                graphics.nextStratum();
                drawGradientRect(startX - 3, startY - 3, startX + textWidth + 3, startY + 8 + 3, 0xc0000000, 0xc0000000);
                drawStringWithShadow(tooltip, startX, startY, -1);
            }
        }

        public void handleMouseClicked(int x, int y, int mouseButton) {

            if (mouseButton == 0) {

                Ledger ledger = this.getAtPosition(x, y);

                // Default action only if the mouse click was not handled by the
                // ledger itself.
                if (ledger != null && !ledger.handleMouseClicked(x, y, mouseButton)) {

                    for (Ledger other : ledgers) {
                        if (other != ledger && other.isOpen()) {
                            other.toggleOpen();
                        }
                    }
                    ledger.toggleOpen();
                }
            }

        }
    }

    /**
     * Side ledger for guis
     */
    protected abstract class Ledger {

        private boolean open;
        protected int overlayColor = 0xffffff;
        public int currentShiftX = 0;
        public int currentShiftY = 0;
        protected int limitWidth = 128;
        protected int maxWidth = 124;
        protected int minWidth = 24;
        protected int currentWidth = minWidth;
        protected int maxHeight = 24;
        protected int minHeight = 24;
        protected int currentHeight = minHeight;

        public void update() {
            // Width
            if (open && currentWidth < maxWidth) {
                currentWidth += 4;
            } else if (!open && currentWidth > minWidth) {
                currentWidth -= 4;
            }

            // Height
            if (open && currentHeight < maxHeight) {
                currentHeight += 4;
            } else if (!open && currentHeight > minHeight) {
                currentHeight -= 4;
            }
        }

        public int getHeight() {
            return currentHeight;
        }

        public abstract void draw(int x, int y);

        public abstract String getTooltip();

        public boolean handleMouseClicked(int x, int y, int mouseButton) {
            return false;
        }

        public boolean intersectsWith(int mouseX, int mouseY, int shiftX, int shiftY) {

            if (mouseX >= shiftX && mouseX <= shiftX + currentWidth && mouseY >= shiftY && mouseY <= shiftY + getHeight()) {
                return true;
            }

            return false;
        }

        public void setFullyOpen() {
            open = true;
            currentWidth = maxWidth;
            currentHeight = maxHeight;
        }

        public void toggleOpen() {
            if (open) {
                open = false;
                SessionVars.setOpenedLedger(null);
            } else {
                open = true;
                SessionVars.setOpenedLedger(this.getClass());
            }
        }

        public boolean isVisible() {
            return true;
        }

        public boolean isOpen() {
            return this.open;
        }

        protected boolean isFullyOpened() {
            return currentWidth >= maxWidth;
        }

        protected void drawBackground(int x, int y) {

            float colorR = (overlayColor >> 16 & 255) / 255.0F;
            float colorG = (overlayColor >> 8 & 255) / 255.0F;
            float colorB = (overlayColor & 255) / 255.0F;

            setColor(colorR, colorG, colorB, 1.0F);

            bindTexture(LEDGER_TEXTURE);
            drawTexturedModalRect(x, y, 0, 256 - currentHeight, 4, currentHeight);
            drawTexturedModalRect(x + 4, y, 256 - currentWidth + 4, 0, currentWidth - 4, 4);
            // Add in top left corner again
            drawTexturedModalRect(x, y, 0, 0, 4, 4);

            drawTexturedModalRect(x + 4, y + 4, 256 - currentWidth + 4, 256 - currentHeight + 4, currentWidth - 4, currentHeight - 4);

            setColor(1.0f, 1.0f, 1.0f, 1.0F);
        }

        /** @param icon a GUI sprite, {@code textures/gui/sprites/<path>.png} in its namespace */
        protected void drawIcon(Identifier icon, int x, int y) {

            setColor(1.0f, 1.0f, 1.0f, 1.0F);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon, x, y, 16, 16);
        }
    }
}
