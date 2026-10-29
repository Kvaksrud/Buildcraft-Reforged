/*
 * Ported from buildcraft/core/gui/GuiAdvancedInterface.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: generic in its menu, as GuiBuildCraft. Icons are atlas sprites, which know
 * their atlas, so AdvancedSlot.getTexture is gone. Empty stacks are ItemStack.EMPTY. The item renderer is
 * GuiGraphicsExtractor.item/itemDecorations, drawCreativeTabHoveringText is a deferred tooltip and the GL lighting
 * calls are gone. IInventorySlot reads a vanilla Container until #23 ports the inventory layer. FluidSlot draws
 * nothing until #24 ports FluidRenderer (SEAM(#24)); 4.2.2 marked it unfinished too.
 */
package buildcraftreforged.core.gui;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import org.jspecify.annotations.Nullable;

/* Client only. */
public abstract class GuiAdvancedInterface<T extends BuildCraftContainer> extends GuiBuildCraft<T> {

    public abstract class AdvancedSlot {

        final public int x, y;

        public AdvancedSlot(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public String getDescription() {
            if (!getItemStack().isEmpty()) {
                return getItemStack().getHoverName().getString();
            } else {
                return "";
            }
        }

        public @Nullable TextureAtlasSprite getIcon() {
            return null;
        }

        public ItemStack getItemStack() {
            return ItemStack.EMPTY;
        }

        public boolean isDefined() {
            return true;
        }

        public void drawSprite(int cornerX, int cornerY) {
            if (!isDefined()) {
                return;
            }

            if (!getItemStack().isEmpty()) {
                drawStack(getItemStack());
            } else if (getIcon() != null) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, getIcon(), cornerX + x, cornerY + y, 16, 16);
            }

        }

        public void drawStack(ItemStack item) {
            if (!item.isEmpty()) {
                int cornerX = (width - imageWidth) / 2;
                int cornerY = (height - imageHeight) / 2;

                graphics.item(item, cornerX + x, cornerY + y);
                graphics.itemDecorations(font, item, cornerX + x, cornerY + y);
            }
        }
    }

    public class ItemSlot extends AdvancedSlot {

        public ItemStack stack = ItemStack.EMPTY;

        public ItemSlot(int x, int y) {
            super(x, y);
        }

        @Override
        public ItemStack getItemStack() {
            return stack;
        }
    }

    /**
     * More dynamic slot displaying an inventory fluid at specified position in
     * the passed IInventory
     */
    public class IInventorySlot extends AdvancedSlot {

        private Container tile;
        private int slot;

        public IInventorySlot(int x, int y, Container tile, int slot) {
            super(x, y);
            this.tile = tile;
            this.slot = slot;
        }

        @Override
        public ItemStack getItemStack() {
            return tile.getItem(slot);
        }
    }

    public AdvancedSlot[] slots;

    public GuiAdvancedInterface(T container, @Nullable Object inventory, Identifier texture, Inventory playerInventory,
            Component title) {
        super(container, inventory, texture, playerInventory, title);
    }

    public GuiAdvancedInterface(T container, @Nullable Object inventory, Identifier texture, Inventory playerInventory,
            Component title, int xSize, int ySize) {
        super(container, inventory, texture, playerInventory, title, xSize, ySize);
    }

    public int getSlotAtLocation(int i, int j) {
        for (int position = 0; position < slots.length; ++position) {
            AdvancedSlot s = slots[position];
            if (i >= s.x && i <= s.x + 16 && j >= s.y && j <= s.y + 16) {
                return position;
            }
        }
        return -1;
    }

    protected void drawBackgroundSlots() {
        int cornerX = (width - imageWidth) / 2;
        int cornerY = (height - imageHeight) / 2;

        setColor(1.0F, 1.0F, 1.0F, 1.0F);

        for (int s = 0; s < slots.length; ++s) {
            AdvancedSlot slot = slots[s];

            if (slot != null) {
                slot.drawSprite(cornerX, cornerY);
            }
        }
    }

    protected void drawForegroundSelection(int mouseX, int mouseY) {
        String s = "";

        int cornerX = (width - imageWidth) / 2;
        int cornerY = (height - imageHeight) / 2;

        int position = getSlotAtLocation(mouseX - cornerX, mouseY - cornerY);

        if (position != -1) {
            AdvancedSlot slot = slots[position];

            if (slot != null) {
                s = slot.getDescription();
            }
        }

        if (s.length() > 0) {
            // Deferred tooltips are drawn in screen coordinates, after the frame.
            graphics.setTooltipForNextFrame(font, Component.literal(s), mouseX, mouseY);
        }
    }

    /**
     * For the refinery, a king of phantom slot for fluid.
     */
    //TODO Get this class working well (Now it's just here to let the refinery compil)
    public class FluidSlot extends AdvancedSlot {

        public @Nullable Fluid fluid;

        public FluidSlot(int x, int y) {
            super(x, y);
        }

        @Override
        public void drawSprite(int cornerX, int cornerY) {
            // SEAM(#24): FluidRenderer.setColorForFluidStack(new FluidStack(fluid, 100)) tints the sprite.
            super.drawSprite(cornerX, cornerY);
        }

        @Override
        public @Nullable TextureAtlasSprite getIcon() {
            // SEAM(#24): FluidRenderer.getFluidTexture(fluid, false).
            return null;
        }
    }
}
