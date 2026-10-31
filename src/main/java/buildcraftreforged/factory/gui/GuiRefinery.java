/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: generic GuiAdvancedInterface with the size in the constructor; the title
 * and labels are translated components; FluidContainerRegistry.getFluidForFilledItem becomes the held stack's fluid
 * item capability; mouseClicked takes a MouseButtonEvent (1.6.4 button numbers through toLegacyButton); the result
 * slot looks the recipe up in RefineryRecipeManager; the foreground calls super so the ledgers, with the status
 * ledger (plan section 10), are drawn.
 */
package buildcraftreforged.factory.gui;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.api.recipes.RefineryRecipes.Recipe;
import buildcraftreforged.core.gui.GuiAdvancedInterface;
import buildcraftreforged.core.gui.StatusLedger;
import buildcraftreforged.factory.RefineryRecipeManager;
import buildcraftreforged.factory.TileRefinery;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;

/* Client only. */
public class GuiRefinery extends GuiAdvancedInterface<ContainerRefinery> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "textures/gui/refinery_filter.png");
    ContainerRefinery container;

    @SuppressWarnings("unchecked")
    public GuiRefinery(ContainerRefinery container, Inventory inventory, Component title) {
        super(container, container.refinery, TEXTURE, inventory, title, 175, 207);

        this.container = container;

        // An array of an inner class of a generic class cannot be created directly.
        this.slots = (AdvancedSlot[]) new GuiAdvancedInterface<?>.AdvancedSlot[3];

        this.slots[0] = new FluidSlot(38, 54);
        this.slots[1] = new FluidSlot(126, 54);
        this.slots[2] = new FluidSlot(82, 54);
    }

    @Override
    protected void initLedgers(@Nullable Object inventory) {
        super.initLedgers(inventory);
        ledgerManager.add(new StatusLedger<>(this, ((ContainerRefinery) menu).statusWidget));
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int par1, int par2) {
        super.drawGuiContainerForegroundLayer(par1, par2);
        String title = Component.translatable("tile.refineryBlock").getString();
        drawString(title, getCenteredOffset(title), 6, 0x404040);
        drawString("->", 63, 59, 0x404040);
        drawString("<-", 106, 59, 0x404040);
        drawString(Component.translatable("gui.inventory").getString(), 8, (imageHeight - 96) + 2, 0x404040);

        drawForegroundSelection(par1, par2);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float f, int x, int y) {
        setColor(1.0F, 1.0F, 1.0F, 1.0F);
        bindTexture(TEXTURE);
        int j = (width - imageWidth) / 2;
        int k = (height - imageHeight) / 2;
        drawTexturedModalRect(j, k, 0, 0, imageWidth, imageHeight);

        updateSlots();
        drawBackgroundSlots();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        boolean handled = super.mouseClicked(event, doubleClick);

        int i = (int) event.x();
        int j = (int) event.y();
        int k = toLegacyButton(event.button());

        int cornerX = (width - imageWidth) / 2;
        int cornerY = (height - imageHeight) / 2;

        int position = getSlotAtLocation(i - cornerX, j - cornerY);

        if (position >= 0 && position < 2) {
            if (k == 0) {
                if (!event.hasShiftDown()) {
                    FluidStack liquid = getFluidForFilledItem(menu.getCarried());

                    if (liquid.isEmpty()) {
                        return handled;
                    }

                    container.setFilter(position, liquid.getFluid());
                } else {
                    container.setFilter(position, null);
                }
            } else {
                TileRefinery ref = (TileRefinery) this.tile;

                if (position == 0)
                    container.setFilter(position, ref.tank1.getFluidType());
                else if (position == 1)
                    container.setFilter(position, ref.tank2.getFluidType());
            }
            return true;
        }
        return handled;
    }

    /** 1.6.4 {@code FluidContainerRegistry.getFluidForFilledItem}: the first fluid the stack's fluid handler holds. */
    private static FluidStack getFluidForFilledItem(ItemStack stack) {
        if (stack.isEmpty())
            return FluidStack.EMPTY;
        ResourceHandler<FluidResource> handler = ItemAccess.forStack(stack.copy()).getCapability(Capabilities.Fluid.ITEM);
        if (handler == null)
            return FluidStack.EMPTY;
        for (int index = 0; index < handler.size(); index++) {
            FluidResource resource = handler.getResource(index);
            if (!resource.isEmpty() && handler.getAmountAsLong(index) > 0)
                return resource.toStack(handler.getAmountAsInt(index));
        }
        return FluidStack.EMPTY;
    }

    private void updateSlots() {

        Fluid filter0 = container.getFilter(0);
        Fluid filter1 = container.getFilter(1);

        ((FluidSlot) slots[0]).fluid = filter0;
        ((FluidSlot) slots[1]).fluid = filter1;

        FluidStack liquid0 = null;
        FluidStack liquid1 = null;

        if (filter0 != null) {
            liquid0 = new FluidStack(filter0, FluidType.BUCKET_VOLUME);
        }
        if (filter1 != null) {
            liquid1 = new FluidStack(filter1, FluidType.BUCKET_VOLUME);
        }

        Recipe recipe = RefineryRecipeManager.findRefineryRecipe(null, liquid0, liquid1);

        if (recipe != null) {
            ((FluidSlot) slots[2]).fluid = recipe.result.getFluid();
        } else {
            ((FluidSlot) slots[2]).fluid = null;
        }
    }

}
