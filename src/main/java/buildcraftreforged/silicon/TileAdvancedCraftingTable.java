/*
 * Ported from buildcraft/silicon/TileAdvancedCraftingTable.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: TileEntity becomes TileBuildCraft's BlockEntity, IInventory and
 * ISidedInventory a vanilla WorldlyContainer; energy is FE as long (4.2.2 MJ as float, × 10); the Ore Dictionary ids
 * of the template become item tags (StackHelper.isOreTag); InventoryCrafting becomes a CraftingInput built from the
 * bound stacks, IRecipe a RecipeHolder<CraftingRecipe>, and SlotCrafting.onPickupFromSlot with its InternalPlayer the
 * recipe's remaining items, which go out with the result as the internal player's inventory did; NBT becomes
 * ValueInput/ValueOutput; the GUI numbers travel as menu data slots; the template slots change through the menu's
 * phantom slots, so ADVANCED_WORKBENCH_SETSLOT is gone.
 */
package buildcraftreforged.silicon;

import buildcraftreforged.api.gates.IAction;
import buildcraftreforged.api.gates.IActionReceptor;
import buildcraftreforged.api.power.ILaserTarget;
import buildcraftreforged.core.CoreRegistry;
import buildcraftreforged.core.IMachine;
import buildcraftreforged.core.TileBuffer;
import buildcraftreforged.core.TileBuildCraft;
import buildcraftreforged.core.inventory.InvUtils;
import buildcraftreforged.core.inventory.InventoryCopy;
import buildcraftreforged.core.inventory.InventoryIterator;
import buildcraftreforged.core.inventory.InventoryIterator.IInvSlot;
import buildcraftreforged.core.inventory.InventoryMapper;
import buildcraftreforged.core.inventory.SimpleInventory;
import buildcraftreforged.core.inventory.StackHelper;
import buildcraftreforged.core.inventory.Transactor;
import buildcraftreforged.core.inventory.filters.CraftingFilter;
import buildcraftreforged.core.inventory.filters.IStackFilter;
import buildcraftreforged.core.triggers.ActionMachineControl;
import buildcraftreforged.core.utils.CraftingHelper;
import buildcraftreforged.core.utils.Utils;
import buildcraftreforged.silicon.gui.ContainerAdvancedCraftingTable;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import static net.minecraft.core.Direction.DOWN;
import static net.minecraft.core.Direction.EAST;
import static net.minecraft.core.Direction.NORTH;
import static net.minecraft.core.Direction.SOUTH;
import static net.minecraft.core.Direction.WEST;

public class TileAdvancedCraftingTable extends TileBuildCraft implements WorldlyContainer, ILaserTarget, IMachine,
        IActionReceptor, MenuProvider {

    private final class CraftingGrid extends SimpleInventory {

        /** 4.2.2: the Ore Dictionary id of each template stack, -1 for none; now its ore tag, null for none. */
        @SuppressWarnings("unchecked")
        public @Nullable TagKey<Item>[] oreIDs = new TagKey[9];

        public CraftingGrid() {
            super(9, "CraftingSlots", 1);
            Arrays.fill(oreIDs, null);
        }

        @Override
        public void setItem(int slotId, ItemStack itemstack) {
            super.setItem(slotId, itemstack);
            if (TileAdvancedCraftingTable.this.level == null || !TileAdvancedCraftingTable.this.level.isClientSide())
                oreIDs[slotId] = itemstack.isEmpty() ? null : getOreID(itemstack);
        }
    }

    /**
     * 4.2.2 OreDictionary.getOreID: the first Ore Dictionary name of the stack. Its counterpart is the stack's first
     * ore tag (a c tag with a slash, StackHelper.isOreTag), by tag id, so the choice does not depend on load order.
     */
    private static @Nullable TagKey<Item> getOreID(ItemStack stack) {
        return stack.typeHolder().tags().filter(StackHelper::isOreTag)
                .min(Comparator.comparing(tag -> tag.location().toString())).orElse(null);
    }

    /**
     * 4.2.2's InventoryCrafting over the bound stacks. Vanilla's CraftingContainer is no longer what a recipe reads, so
     * this keeps only what the table used: the grid view (recipe stack or bound stack) and decrStackSize on the
     * bindings, and makes the CraftingInput a recipe matches.
     */
    private final class InternalInventoryCrafting {

        int[] bindings = new int[9];
        ItemStack @Nullable [] tempStacks;
        public int @Nullable [] hitCount;
        private boolean useRecipeStack;

        private InternalInventoryCrafting() {
        }

        public ItemStack getStackInSlot(int slot) {
            if (slot >= 0 && slot < 9) {
                if (useRecipeStack || tempStacks == null) {
                    return craftingSlots.getItem(slot);
                } else {
                    if (bindings[slot] >= 0) {
                        return tempStacks[bindings[slot]];
                    }
                }
            }

            // vanilla returns null for out of bound stacks in InventoryCrafting as well
            return ItemStack.EMPTY;
        }

        public ItemStack decrStackSize(int slot, int amount) {
            if (tempStacks != null && slot >= 0 && slot < 9 && bindings[slot] >= 0) {
                if (tempStacks[bindings[slot]].getCount() <= amount) {
                    ItemStack result = tempStacks[bindings[slot]];
                    tempStacks[bindings[slot]] = ItemStack.EMPTY;
                    return result;
                } else {
                    ItemStack result = tempStacks[bindings[slot]].split(amount);
                    if (tempStacks[bindings[slot]].getCount() <= 0) {
                        tempStacks[bindings[slot]] = ItemStack.EMPTY;
                    }
                    return result;
                }
            } else {
                return ItemStack.EMPTY;
            }
        }

        public void recipeUpdate(boolean flag) {
            useRecipeStack = flag;
        }

        /** New: the 3 × 3 grid as a recipe reads it, trimmed to its filled part. */
        public CraftingInput.Positioned asPositionedCraftInput() {
            List<ItemStack> items = new ArrayList<>(9);
            for (int slot = 0; slot < 9; slot++) {
                items.add(getStackInSlot(slot));
            }
            return CraftingInput.ofPositioned(3, 3, items);
        }

        public CraftingInput asCraftInput() {
            return asPositionedCraftInput().input();
        }
    }

    public ResultContainer craftResult;
    private @Nullable InternalInventoryCrafting internalInventoryCrafting;

    public TileAdvancedCraftingTable(BlockPos pos, BlockState state) {
        super(SiliconRegistry.advancedCraftingTableTile.get(), pos, state);
        craftingSlots = new CraftingGrid();
        storageSlots = new SimpleInventory(24, "StorageSlots", 64);
        storageSlots.addListener(this);
        invInput = new InventoryMapper(storageSlots, 0, 15);
        invOutput = new InventoryMapper(storageSlots, 15, 9);
        craftResult = new ResultContainer();
    }

    private static final int[] SLOTS = Utils.createSlotArray(0, 24);
    private static final EnumSet<Direction> SEARCH_SIDES = EnumSet.of(DOWN, NORTH, SOUTH, EAST, WEST);
    /** FE (4.2.2: 500 MJ). */
    private static final long REQUIRED_POWER = 5000;
    private final CraftingGrid craftingSlots;
    private final SimpleInventory storageSlots;
    private final InventoryMapper invInput;
    private final InventoryMapper invOutput;
    /** FE. */
    private long storedEnergy;
    private long[] recentEnergy = new long[20];
    private boolean craftable;
    private boolean justCrafted;
    private int tick;
    /** FE per tick × 100, as the GUI shows it. */
    private int recentEnergyAverage;
    private @Nullable RecipeHolder<CraftingRecipe> currentRecipe;
    private ActionMachineControl.Mode lastMode = ActionMachineControl.Mode.Unknown;
    private TileBuffer @Nullable [] cache;

    @Override
    public int getContainerSize() {
        return storageSlots.getContainerSize();
    }

    @Override
    public boolean isEmpty() {
        return storageSlots.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return storageSlots.getItem(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return storageSlots.removeItem(slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return storageSlots.removeItemNoUpdate(slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        storageSlots.setItem(slot, stack);
    }

    @Override
    public void clearContent() {
        storageSlots.clearContent();
    }

    @Override
    protected void saveAdditional(ValueOutput data) {
        super.saveAdditional(data);
        storageSlots.writeToNBT(data, "StorageSlots");
        craftingSlots.writeToNBT(data);
        data.putLong("StoredEnergy", storedEnergy);
    }

    @Override
    protected void loadAdditional(ValueInput data) {
        super.loadAdditional(data);
        storageSlots.readFromNBT(data, "StorageSlots");
        craftingSlots.readFromNBT(data);
        storedEnergy = data.getLongOr("StoredEnergy", 0);
    }

    public String getInvName() {
        return "AdvancedWorkbench";
    }

    /** 4.2.2 onInventoryChanged. */
    @Override
    public void setChanged() {
        super.setChanged();
        craftable = !craftResult.getItem(0).isEmpty();
    }

    @Override
    public int getMaxStackSize() {
        return storageSlots.getMaxStackSize();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    public int getRecentEnergyAverage() {
        return recentEnergyAverage;
    }

    /** FE. */
    public long getStoredEnergy() {
        return storedEnergy;
    }

    /** FE. */
    public long getRequiredEnergy() {
        return !craftResult.getItem(0).isEmpty() ? REQUIRED_POWER : 0;
    }

    public int getProgressScaled(int i) {
        return (int) ((storedEnergy * i) / REQUIRED_POWER);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        cache = null;
    }

    @Override
    public boolean canUpdate() {
        return level != null && !level.isClientSide();
    }

    @Override
    public void updateEntity() { // WARNING: run only server-side, see canUpdate()
        super.updateEntity();
        if (internalInventoryCrafting == null) {
            internalInventoryCrafting = new InternalInventoryCrafting();
            updateRecipe();
        }
        if (lastMode == ActionMachineControl.Mode.Off)
            return;
        updateRecipe();
        searchNeighborsForIngredients();
        locateAndBindIngredients();
        updateRecipeOutputDisplay();
        justCrafted = false;
        tick++;
        tick = tick % recentEnergy.length;
        recentEnergy[tick] = 0;
        if (canCraftAndOutput()) {
            if (storedEnergy >= getRequiredEnergy()) {
                craftItem();
                justCrafted = true;
            }
        } else {
            craftable = false;
            internalInventoryCrafting.tempStacks = null;
            internalInventoryCrafting.hitCount = null;
            storedEnergy = 0;
        }
    }

    private boolean canCraftAndOutput() {
        if (!hasIngredients())
            return false;
        ItemStack output = getRecipeOutput();
        if (output.isEmpty())
            return false;
        return InvUtils.isRoomForStack(output, Direction.UP, invOutput);
    }

    private void locateAndBindIngredients() {
        internalInventoryCrafting.tempStacks = new InventoryCopy(storageSlots).getItemStacks();
        internalInventoryCrafting.hitCount = new int[internalInventoryCrafting.tempStacks.length];
        ItemStack[] inputSlots = internalInventoryCrafting.tempStacks;
        for (int gridSlot = 0; gridSlot < craftingSlots.getContainerSize(); gridSlot++) {
            internalInventoryCrafting.bindings[gridSlot] = -1;
            if (craftingSlots.getItem(gridSlot).isEmpty())
                continue;
            boolean foundMatch = false;
            for (int inputSlot = 0; inputSlot < inputSlots.length; inputSlot++) {
                if (!isMatchingIngredient(gridSlot, inputSlot))
                    continue;
                if (internalInventoryCrafting.hitCount[inputSlot] < inputSlots[inputSlot].getCount()
                        && internalInventoryCrafting.hitCount[inputSlot] < inputSlots[inputSlot].getMaxStackSize()) {
                    internalInventoryCrafting.bindings[gridSlot] = inputSlot;
                    internalInventoryCrafting.hitCount[inputSlot]++;
                    foundMatch = true;
                    break;
                }
            }
            if (!foundMatch)
                return;
        }
    }

    private boolean isMatchingIngredient(int gridSlot, int inputSlot) {
        ItemStack inputStack = internalInventoryCrafting.tempStacks[inputSlot];
        if (inputStack.isEmpty())
            return false;
        if (StackHelper.instance().isMatchingItem(craftingSlots.getItem(gridSlot), inputStack, true, false))
            return true;
        if (StackHelper.instance().isCraftingEquivalent(craftingSlots.oreIDs[gridSlot], inputStack))
            return true;
        return false;
    }

    private boolean hasIngredients() {
        return currentRecipe != null && currentRecipe.value().matches(internalInventoryCrafting.asCraftInput(), level);
    }

    private void craftItem() {
        ItemStack recipeOutput = getRecipeOutput();
        // 4.2.2 craftSlot.onPickupFromSlot(internalPlayer, recipeOutput): the result's onCreated, then one of each grid
        // stack is used, and container items (buckets and the like) went to the internal player, whose inventory
        // joined the outputs. Vanilla's crafter calls onCraftedBySystem where no player crafts.
        recipeOutput.onCraftedBySystem(level);
        List<ItemStack> outputs = Lists.newArrayList(recipeOutput.copy());
        CraftingInput.Positioned positioned = internalInventoryCrafting.asPositionedCraftInput();
        CraftingInput input = positioned.input();
        NonNullList<ItemStack> remaining = currentRecipe.value().getRemainingItems(input);
        for (int y = 0; y < input.height(); y++) {
            for (int x = 0; x < input.width(); x++) {
                int slot = x + positioned.left() + (y + positioned.top()) * 3;
                if (!internalInventoryCrafting.getStackInSlot(slot).isEmpty()) {
                    internalInventoryCrafting.decrStackSize(slot, 1);
                }
                ItemStack container = remaining.get(x + y * input.width());
                if (!container.isEmpty()) {
                    outputs.add(container);
                }
            }
        }
        ItemStack[] tempStorage = internalInventoryCrafting.tempStacks;
        for (int i = 0; i < tempStorage.length; i++) {
            if (!tempStorage[i].isEmpty() && tempStorage[i].getCount() <= 0) {
                tempStorage[i] = ItemStack.EMPTY;
            }
            storageSlots.getItemStacks()[i] = tempStorage[i];
        }
        storageSlots.setChanged();
        storedEnergy -= getRequiredEnergy();
        for (ItemStack output : outputs) {
            output.shrink(Transactor.getTransactorFor(invOutput).add(output, Direction.UP, true).getCount());
            if (output.getCount() > 0) {
                output.shrink(Utils.addToRandomInventoryAround(level, worldPosition.getX(), worldPosition.getY(),
                        worldPosition.getZ(), output));
            }
            if (output.getCount() > 0) {
                InvUtils.dropItems(level, output, worldPosition.getX(), worldPosition.getY() + 1, worldPosition.getZ());
            }
        }
    }

    private void searchNeighborsForIngredients() {
        if (cache == null) {
            cache = TileBuffer.makeBuffer(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), false);
        }
        for (IInvSlot slot : InventoryIterator.getIterable(craftingSlots, Direction.UP)) {
            ItemStack ingred = slot.getStackInSlot();
            if (ingred.isEmpty())
                continue;
            IStackFilter filter = new CraftingFilter(ingred);
            if (InvUtils.countItems(invInput, Direction.UP, filter) < InvUtils.countItems(craftingSlots, Direction.UP, filter)) {
                for (Direction side : SEARCH_SIDES) {
                    BlockEntity tile = cache[side.ordinal()].getTile();
                    if (tile instanceof Container) {
                        Container inv = Utils.getInventory(((Container) tile));
                        ItemStack result = InvUtils.moveOneItem(inv, side.getOpposite(), invInput, side, filter);
                        if (!result.isEmpty()) {
                            return;
                        }
                    }
                }
            }
        }
    }

    /** 4.2.2 also sent ADVANCED_WORKBENCH_SETSLOT from the client; the menu's phantom slots now do that. */
    public void updateCraftingMatrix(int slot, ItemStack stack) {
        craftingSlots.setItem(slot, stack);
        updateRecipe();
    }

    private void updateRecipe() {
        if (internalInventoryCrafting == null) {
            return;
        }
        internalInventoryCrafting.recipeUpdate(true);
        CraftingInput input = internalInventoryCrafting.asCraftInput();
        if (this.currentRecipe == null || !this.currentRecipe.value().matches(input, level)) {
            currentRecipe = CraftingHelper.findMatchingRecipe(input, level);
        }
        internalInventoryCrafting.recipeUpdate(false);
        setChanged();
    }

    private void updateRecipeOutputDisplay() {
        if (internalInventoryCrafting == null || currentRecipe == null) {
            craftResult.setItem(0, ItemStack.EMPTY);
            return;
        }
        ItemStack resultStack = getRecipeOutput();
        if (resultStack.isEmpty()) {
            internalInventoryCrafting.recipeUpdate(true);
            resultStack = getRecipeOutput();
            internalInventoryCrafting.recipeUpdate(false);
        }
        craftResult.setItem(0, resultStack);
        setChanged();
    }

    private ItemStack getRecipeOutput() {
        if (internalInventoryCrafting == null || currentRecipe == null) {
            return ItemStack.EMPTY;
        }
        return currentRecipe.value().assemble(internalInventoryCrafting.asCraftInput());
    }

    public Container getCraftingSlots() {
        return craftingSlots;
    }

    public Container getOutputSlot() {
        return craftResult;
    }

    @Override
    public boolean requiresLaserEnergy() {
        return craftable && !justCrafted && lastMode != ActionMachineControl.Mode.Off && storedEnergy < REQUIRED_POWER * 10;
    }

    @Override
    public void receiveLaserEnergy(long energy) {
        storedEnergy += energy;
        recentEnergy[tick] += energy;
    }

    @Override
    public int getXCoord() {
        return worldPosition.getX();
    }

    @Override
    public int getYCoord() {
        return worldPosition.getY();
    }

    @Override
    public int getZCoord() {
        return worldPosition.getZ();
    }

    @Override
    public boolean isActive() {
        return requiresLaserEnergy();
    }

    @Override
    public boolean manageFluids() {
        return false;
    }

    @Override
    public boolean manageSolids() {
        return false;
    }

    @Override
    public boolean allowAction(IAction action) {
        return action == CoreRegistry.actionOn.get() || action == CoreRegistry.actionOff.get();
    }

    /* SMP GUI */
    /** Client side: 4.2.2's updateProgressBar values, 16 bits at a time (ids 0 and 2 are unused, as in 4.2.2). */
    public void getGUINetworkData(int id, int data) {
        int currentStored = (int) storedEnergy;
        switch (id) {
            case 1:
                currentStored = (currentStored & 0xFFFF0000) | (data & 0xFFFF);
                storedEnergy = currentStored;
                break;
            case 3:
                currentStored = (currentStored & 0xFFFF) | ((data & 0xFFFF) << 16);
                storedEnergy = currentStored;
                break;
            case 4:
                recentEnergyAverage = recentEnergyAverage & 0xFFFF0000 | (data & 0xFFFF);
                break;
            case 5:
                recentEnergyAverage = (recentEnergyAverage & 0xFFFF) | ((data & 0xFFFF) << 16);
                break;
        }
    }

    /**
     * Server side: the value of progress bar id, as 4.2.2's sendGUINetworkData sent it. 4.2.2 sent MJ × 100; FE are
     * whole numbers and go as they are, except the rate, FE/t × 100.
     */
    public int sendGUINetworkData(int id) {
        int currentStored = (int) Math.min(storedEnergy, Integer.MAX_VALUE);
        int lRecentEnergy = 0;
        for (int i = 0; i < recentEnergy.length; i++) {
            lRecentEnergy += (int) (recentEnergy[i] * 100 / (recentEnergy.length - 1));
        }
        switch (id) {
            case 1:
                return currentStored & 0xFFFF;
            case 3:
                return (currentStored >>> 16) & 0xFFFF;
            case 4:
                return lRecentEnergy & 0xFFFF;
            case 5:
                return (lRecentEnergy >>> 16) & 0xFFFF;
            default:
                return 0;
        }
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
        return slot >= 15;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot < 15;
    }

    @Override
    public void actionActivated(IAction action) {
        if (action == CoreRegistry.actionOn.get()) {
            lastMode = ActionMachineControl.Mode.On;
        } else if (action == CoreRegistry.actionOff.get()) {
            lastMode = ActionMachineControl.Mode.Off;
        }
    }

    @Override
    public boolean isInvalidTarget() {
        return isRemoved();
    }

    /* MENU: 4.2.2's silicon GuiHandler, GUI id 0 with block metadata 1. */
    @Override
    public Component getDisplayName() {
        return Component.translatable("tile.assemblyWorkbenchBlock");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ContainerAdvancedCraftingTable(containerId, playerInventory, this);
    }
}
