/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: ISidedInventory becomes WorldlyContainer; InventoryCrafting becomes a
 * TransientCraftingContainer whose dummy menu marks the tile changed; the internal "[BuildCraft]" player is the owner's
 * fake player (#27) and SlotCrafting is ResultSlot, through which the craft fires the crafting event as
 * onPickupFromSlot did; IRecipe is a RecipeHolder of the server's recipe manager; hasContainerItem is a crafting
 * remainder; NBT becomes ValueInput/ValueOutput with the same tag names (the pre-4.x "stackList" tag is not read); the
 * GUI is a MenuProvider; the status panel (plan section 10) is new.
 */
package buildcraftreforged.factory;

import buildcraftreforged.core.TileBuildCraft;
import buildcraftreforged.core.inventory.InvUtils;
import buildcraftreforged.core.inventory.InventoryConcatenator;
import buildcraftreforged.core.inventory.InventoryIterator;
import buildcraftreforged.core.inventory.InventoryIterator.IInvSlot;
import buildcraftreforged.core.inventory.SimpleInventory;
import buildcraftreforged.core.inventory.StackHelper;
import buildcraftreforged.core.status.IStatusProvider;
import buildcraftreforged.core.status.MachineStatus;
import buildcraftreforged.core.utils.BuildCraftPlayer;
import buildcraftreforged.core.utils.CraftingHelper;
import buildcraftreforged.core.utils.Utils;
import buildcraftreforged.factory.gui.ContainerAutoWorkbench;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class TileAutoWorkbench extends TileBuildCraft implements WorldlyContainer, MenuProvider, IStatusProvider {

    public static final int SLOT_RESULT = 0;
    public static final int CRAFT_TIME = 256;
    public static final int UPDATE_TIME = 16;
    private static final int[] SLOTS = Utils.createSlotArray(0, 10);
    private SimpleInventory resultInv = new SimpleInventory(1, "Auto Workbench", 64);
    public TransientCraftingContainer craftMatrix = new LocalInventoryCrafting();
    private Container inv = InventoryConcatenator.make().add(resultInv).add(craftMatrix);
    public boolean useLast;
    private @Nullable Player internalPlayer;
    private @Nullable CraftSlot craftSlot;
    private ResultContainer craftResult = new ResultContainer();
    public int progress;
    private int update = Utils.RANDOM.nextInt();

    private class LocalInventoryCrafting extends TransientCraftingContainer {

        public LocalInventoryCrafting() {
            super(new AbstractContainerMenu(null, 0) {
                @Override
                public ItemStack quickMoveStack(Player player, int slotIndex) {
                    return ItemStack.EMPTY;
                }

                @Override
                public boolean stillValid(Player entityplayer) {
                    return false;
                }

                @Override
                public void slotsChanged(Container container) {
                    // 1.6.4 saved every loaded chunk; the matrix now marks the tile for saving when it changes.
                    TileAutoWorkbench.this.setChanged();
                }
            }, 3, 3);
        }

        @Override
        public void setChanged() {
            TileAutoWorkbench.this.setChanged();
        }
    }

    /**
     * 4.2.2 called SlotCrafting.onPickupFromSlot, which fired the crafting handlers for the whole stack. ResultSlot only
     * does that for what was taken out of it, so the craft is counted as a quick craft of the stack first.
     */
    private static final class CraftSlot extends ResultSlot {

        public CraftSlot(Player player, TransientCraftingContainer craftSlots, Container container) {
            super(player, craftSlots, container, 0, 0, 0);
        }

        public void onPickupFromSlot(Player player, ItemStack stack) {
            onQuickCraft(stack, stack.getCount());
            onTake(player, stack);
        }
    }

    public TileAutoWorkbench(BlockPos pos, BlockState state) {
        super(FactoryRegistry.autoWorkbenchTile.get(), pos, state);
        resultInv.addListener(this);
    }

    @Override
    public int getContainerSize() {
        return 10;
    }

    @Override
    public boolean isEmpty() {
        return inv.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return inv.getItem(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        return inv.removeItem(slot, count);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        inv.setItem(slot, stack);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return inv.removeItemNoUpdate(slot);
    }

    @Override
    public void clearContent() {
        inv.clearContent();
    }

    @Override
    public int getMaxStackSize() {
        return 64;
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player, 8);
    }

    @Override
    protected void loadAdditional(ValueInput data) {
        super.loadAdditional(data);
        resultInv.readFromNBT(data);
        Utils.readInvFromNBT(craftMatrix, "matrix", data);
    }

    @Override
    protected void saveAdditional(ValueOutput data) {
        super.saveAdditional(data);
        resultInv.writeToNBT(data);
        Utils.writeInvToNBT(craftMatrix, "matrix", data);
    }

    /** EMPTY where no recipe matches, and on the client, which has no recipe manager. */
    public ItemStack findRecipeOutput() {
        RecipeHolder<CraftingRecipe> recipe = findRecipe();
        if (recipe == null) {
            return ItemStack.EMPTY;
        }
        ItemStack result = recipe.value().assemble(craftMatrix.asCraftInput());
        if (!result.isEmpty()) {
            result = result.copy();
        }
        return result;
    }

    public @Nullable RecipeHolder<CraftingRecipe> findRecipe() {
        for (IInvSlot slot : InventoryIterator.getIterable(craftMatrix, Direction.UP)) {
            ItemStack stack = slot.getStackInSlot();
            if (stack.isEmpty()) {
                continue;
            }
            if (!stack.isStackable()) {
                return null;
            }
            if (stack.getItem().getCraftingRemainder(stack) != null) {
                return null;
            }
        }

        if (level == null) {
            return null;
        }
        return CraftingHelper.findMatchingRecipe(craftMatrix.asCraftInput(), level);
    }

    @Override
    public void updateEntity() {
        super.updateEntity();
        if (level == null || level.isClientSide()) {
            return;
        }

        balanceSlots();

        if (craftSlot == null) {
            internalPlayer = BuildCraftPlayer.get(this);
            if (internalPlayer == null) {
                return;
            }
            craftSlot = new CraftSlot(internalPlayer, craftMatrix, craftResult);
        }
        if (!resultInv.getItem(SLOT_RESULT).isEmpty()) {
            return;
        }
        update++;
        if (update % UPDATE_TIME == 0) {
            updateCrafting();
        }
    }

    public int getProgressScaled(int i) {
        return (progress * i) / CRAFT_TIME;
    }

    /**
     * Evenly redistributes items between all the slots.
     */
    private void balanceSlots() {
        for (IInvSlot slotA : InventoryIterator.getIterable(craftMatrix, Direction.UP)) {
            ItemStack stackA = slotA.getStackInSlot();
            if (stackA.isEmpty())
                continue;
            for (IInvSlot slotB : InventoryIterator.getIterable(craftMatrix, Direction.UP)) {
                if (slotA.getIndex() == slotB.getIndex())
                    continue;
                ItemStack stackB = slotB.getStackInSlot();
                if (stackB.isEmpty())
                    continue;
                if (StackHelper.instance().canStacksMerge(stackA, stackB)) {
                    if (stackA.getCount() > stackB.getCount() + 1) {
                        stackA.shrink(1);
                        stackB.grow(1);
                        return;
                    }
                }
            }
        }
    }

    /**
     * Increment craft job, find recipes, produce output
     */
    private void updateCrafting() {
        RecipeHolder<CraftingRecipe> recipe = findRecipe();
        if (recipe == null) {
            progress = 0;
            return;
        }
        if (!useLast && isLast()) {
            progress = 0;
            return;
        }
        progress += UPDATE_TIME;
        if (progress < CRAFT_TIME) {
            return;
        }
        progress = 0;
        useLast = false;
        ItemStack result = recipe.value().assemble(craftMatrix.asCraftInput());
        if (result.isEmpty()) {
            return;
        }
        result = result.copy();
        craftSlot.onPickupFromSlot(internalPlayer, result);
        resultInv.setItem(SLOT_RESULT, result);

        // clean fake player inventory (crafting handler support)
        for (IInvSlot slot : InventoryIterator.getIterable(internalPlayer.getInventory(), Direction.UP)) {
            ItemStack stack = slot.getStackInSlot();
            if (!stack.isEmpty()) {
                slot.setStackInSlot(ItemStack.EMPTY);
                InvUtils.dropItems(level, stack, worldPosition.getX(), worldPosition.getY() + 1, worldPosition.getZ());
            }
        }
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == SLOT_RESULT)
            return false;
        if (stack.isEmpty())
            return false;
        if (!stack.isStackable())
            return false;
        if (stack.getItem().getCraftingRemainder(stack) != null)
            return false;
        if (getItem(slot).isEmpty())
            return false;
        return true;
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == SLOT_RESULT;
    }

    /**
     * Returns true if there are only enough inputs for a single craft job.
     *
     * @return true or false
     */
    public boolean isLast() {
        int minStackSize = 64;
        for (IInvSlot slot : InventoryIterator.getIterable(craftMatrix, Direction.UP)) {
            ItemStack stack = slot.getStackInSlot();
            if (!stack.isEmpty() && stack.getCount() < minStackSize) {
                minStackSize = stack.getCount();
            }
        }
        return minStackSize <= 1;
    }

    /* MENU: 4.2.2's GuiHandler, GuiIds.AUTO_CRAFTING_TABLE. */
    @Override
    public Component getDisplayName() {
        return Component.translatable("tile.autoWorkbenchBlock");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ContainerAutoWorkbench(containerId, playerInventory, this);
    }

    // Status panel (plan section 10), not in 4.2.2.

    /** Working while a craft job runs; idle while it waits for ingredients or for its result to be taken. */
    @Override
    public MachineStatus getStatusState() {
        return progress > 0 ? MachineStatus.WORKING : MachineStatus.IDLE;
    }
}
