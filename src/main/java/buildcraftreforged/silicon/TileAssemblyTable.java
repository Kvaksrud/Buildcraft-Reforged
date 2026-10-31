/*
 * Ported from buildcraft/silicon/TileAssemblyTable.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: TileEntity becomes TileBuildCraft's BlockEntity and IInventory a vanilla
 * Container; recipes come from AssemblyRecipeManager (JSON recipes plus AssemblyRecipe.assemblyRecipes); energy is FE
 * as long (4.2.2 MJ as float, × 10); NBT becomes ValueInput/ValueOutput, and the saved recipe outputs are looked up
 * again once the level is known and after a datapack reload; the selection messages travel in a PacketGuiReturn (client
 * to server) and the network payload (server to client) instead of the SELECTION_ASSEMBLY packets; the GUI numbers
 * travel as menu data slots; ore lists become item tags; useItems removes every tag item it needs and the overflow
 * drop is what is left (docs/BUGFIXES.md, silicon); addToRandomPipeAround waits for #50.
 */
package buildcraftreforged.silicon;

import buildcraftreforged.api.gates.IAction;
import buildcraftreforged.api.power.ILaserTarget;
import buildcraftreforged.api.recipes.AssemblyRecipe;
import buildcraftreforged.core.IMachine;
import buildcraftreforged.core.TileBuildCraft;
import buildcraftreforged.core.inventory.ITransactor;
import buildcraftreforged.core.inventory.Transactor;
import buildcraftreforged.core.inventory.filters.ArrayStackFilter;
import buildcraftreforged.core.network.IGuiReturnHandler;
import buildcraftreforged.core.network.InvalidPacketException;
import buildcraftreforged.core.network.PacketData;
import buildcraftreforged.core.utils.Utils;
import buildcraftreforged.silicon.gui.ContainerAssemblyTable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class TileAssemblyTable extends TileBuildCraft implements IMachine, Container, ILaserTarget, MenuProvider,
        IGuiReturnHandler {

    ItemStack[] items = new ItemStack[12];
    LinkedHashSet<AssemblyRecipe> plannedOutput = new LinkedHashSet<AssemblyRecipe>();
    public @Nullable AssemblyRecipe currentRecipe;
    /** FE. */
    private long currentRequiredEnergy = 0;
    /** FE. */
    private long energyStored = 0;
    private long[] recentEnergy = new long[20];
    private int tick = 0;
    /** FE per tick × 100, as the GUI shows it. */
    private int recentEnergyAverage;

    /**
     * The outputs of the planned and current recipes as last read (saved data or the network payload), and the
     * AssemblyRecipeManager generation they were looked up in. 4.2.2 looked them up in readFromNBT, which now runs
     * before the block entity has a level; and a datapack reload replaces the recipe objects.
     */
    private @Nullable List<ItemStack> pendingPlanned = new ArrayList<>();
    private ItemStack pendingRecipe = ItemStack.EMPTY;
    private int recipeGeneration = -1;
    /** The selection changed on the server: tell the clients on the next tick. */
    private boolean selectionChanged = false;

    public static class SelectionMessage {

        public boolean select;
        public ItemStack stack = ItemStack.EMPTY;

        /** 4.2.2 getNBT. */
        public void writeData(RegistryFriendlyByteBuf data) {
            data.writeBoolean(select);
            ItemStack.STREAM_CODEC.encode(data, stack);
        }

        /** 4.2.2 fromNBT. */
        public void readData(RegistryFriendlyByteBuf data) throws InvalidPacketException {
            select = PacketData.readBoolean(data);
            stack = ItemStack.STREAM_CODEC.decode(data);
        }
    }

    public TileAssemblyTable(BlockPos pos, BlockState state) {
        super(SiliconRegistry.assemblyTableTile.get(), pos, state);
        Arrays.fill(items, ItemStack.EMPTY);
    }

    public LinkedList<AssemblyRecipe> getPotentialOutputs() {
        LinkedList<AssemblyRecipe> result = new LinkedList<AssemblyRecipe>();

        if (level == null)
            return result;

        resolveRecipes();

        for (AssemblyRecipe recipe : AssemblyRecipeManager.getRecipes(level)) {
            if (recipe.canBeDone(items)) {
                result.add(recipe);
            }
        }

        return result;
    }

    @Override
    public boolean canUpdate() {
        return level != null && !level.isClientSide();
    }

    @Override
    public void updateEntity() { // WARNING: run only server-side, see canUpdate()
        super.updateEntity();

        resolveRecipes();

        if (selectionChanged) {
            selectionChanged = false;
            sendNetworkUpdate();
        }

        tick++;
        tick = tick % recentEnergy.length;
        recentEnergy[tick] = 0;

        if (currentRecipe == null)
            return;

        if (!currentRecipe.canBeDone(items)) {
            setNextCurrentRecipe();

            if (currentRecipe == null)
                return;
        }

        if (energyStored >= currentRecipe.energy) {
            energyStored = 0;

            if (currentRecipe.canBeDone(items)) {

                useItems();

                ItemStack remaining = currentRecipe.output.copy();
                remaining.shrink(Utils.addToRandomInventoryAround(level, worldPosition.getX(), worldPosition.getY(),
                        worldPosition.getZ(), remaining));

                if (remaining.getCount() > 0) {
                    remaining.shrink(Utils.addToRandomPipeAround(level, worldPosition.getX(), worldPosition.getY(),
                            worldPosition.getZ(), null, remaining));
                }

                if (remaining.getCount() > 0) {
                    // 4.2.2 dropped currentRecipe.output.copy(), the whole output, whatever had been inserted.
                    ItemEntity entityitem = new ItemEntity(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.7,
                            worldPosition.getZ() + 0.5, remaining);

                    level.addFreshEntity(entityitem);
                }

                setNextCurrentRecipe();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void useItems() {
        ITransactor tran = Transactor.getTransactorFor(this);
        Object[] input = currentRecipe.input;
        for (int i = 0; i < input.length; i++) {
            if (input[i] instanceof ItemStack) {
                ItemStack requirement = (ItemStack) input[i];
                for (int num = 0; num < requirement.getCount(); num++) {
                    tran.remove(new ArrayStackFilter(requirement), null, true);
                }
            } else if (input[i] instanceof TagKey) {
                // 4.2.2 walked the Ore Dictionary list and, counting down `required` inside a loop bounded by it, took
                // about half of what it needed from each ore stack.
                TagKey<Item> oreTag = (TagKey<Item>) input[i];
                int required = (Integer) input[i + 1];
                for (int num = 0; num < required; num++) {
                    tran.remove(stack -> stack.is(oreTag), null, true);
                }
            }
        }
    }

    /** ratio for a full bar; 0 without a recipe. */
    public float getCompletionRatio(float ratio) {
        if (currentRecipe == null)
            return 0;
        else if (energyStored >= currentRequiredEnergy)
            return ratio;
        else
            return (float) energyStored / currentRequiredEnergy * ratio;
    }

    /* IINVENTORY */
    @Override
    public int getContainerSize() {
        return items.length;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty())
                return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int i) {
        return items[i];
    }

    @Override
    public ItemStack removeItem(int i, int j) {
        if (items[i].isEmpty())
            return ItemStack.EMPTY;
        ItemStack stack = items[i].split(j);
        if (items[i].isEmpty()) {
            items[i] = ItemStack.EMPTY;
        }
        setChanged();
        return stack;
    }

    @Override
    public void setItem(int i, ItemStack itemstack) {
        items[i] = itemstack;
        setChanged();

        if (currentRecipe == null) {
            setNextCurrentRecipe();
        }
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (this.items[slot].isEmpty())
            return ItemStack.EMPTY;

        ItemStack stackToTake = this.items[slot];
        this.items[slot] = ItemStack.EMPTY;
        return stackToTake;
    }

    public String getInvName() {
        return "Assembly Table";
    }

    @Override
    public int getMaxStackSize() {
        return 64;
    }

    @Override
    public boolean stillValid(Player entityplayer) {
        return Container.stillValidBlockEntity(this, entityplayer);
    }

    @Override
    public void clearContent() {
        Arrays.fill(items, ItemStack.EMPTY);
    }

    @Override
    protected void loadAdditional(ValueInput nbttagcompound) {
        super.loadAdditional(nbttagcompound);

        Utils.readStacksFromNBT(nbttagcompound, "items", items);

        energyStored = nbttagcompound.getLongOr("energyStored", 0);

        // The recipes are looked up in resolveRecipes, once the level is known.
        pendingPlanned = new ArrayList<>(nbttagcompound.listOrEmpty("planned", ItemStack.CODEC).stream().toList());
        pendingRecipe = nbttagcompound.read("recipe", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        recipeGeneration = -1;
    }

    @Override
    protected void saveAdditional(ValueOutput nbttagcompound) {
        super.saveAdditional(nbttagcompound);

        Utils.writeStacksToNBT(nbttagcompound, "items", items);

        nbttagcompound.putLong("energyStored", energyStored);

        ValueOutput.TypedOutputList<ItemStack> list = nbttagcompound.list("planned", ItemStack.CODEC);

        for (ItemStack stack : getPlannedStacks()) {
            list.add(stack);
        }

        ItemStack recipe = getCurrentStack();
        if (!recipe.isEmpty()) {
            nbttagcompound.store("recipe", ItemStack.CODEC, recipe);
        }
    }

    private List<ItemStack> getPlannedStacks() {
        if (pendingPlanned != null)
            return pendingPlanned;
        List<ItemStack> stacks = new ArrayList<>();
        for (AssemblyRecipe recipe : plannedOutput) {
            stacks.add(recipe.output);
        }
        return stacks;
    }

    private ItemStack getCurrentStack() {
        if (pendingPlanned != null)
            return pendingRecipe;
        return currentRecipe == null ? ItemStack.EMPTY : currentRecipe.output;
    }

    /**
     * 4.2.2 readFromNBT's lookup: the planned recipes by output, then the current one among them. Runs again whenever
     * AssemblyRecipeManager replaced its recipe objects.
     */
    private void resolveRecipes() {
        if (level == null)
            return;

        List<AssemblyRecipe> recipes = AssemblyRecipeManager.getRecipes(level);
        int generation = AssemblyRecipeManager.getGeneration();
        if (pendingPlanned == null && recipeGeneration == generation)
            return;

        List<ItemStack> planned = getPlannedStacks();
        ItemStack current = getCurrentStack();
        pendingPlanned = null;
        recipeGeneration = generation;

        plannedOutput.clear();
        for (ItemStack stack : planned) {
            for (AssemblyRecipe r : recipes) {
                if (ItemStack.isSameItemSameComponents(r.output, stack)) {
                    plannedOutput.add(r);
                    break;
                }
            }
        }

        setCurrentRecipe(null);
        if (!current.isEmpty()) {
            for (AssemblyRecipe r : plannedOutput) {
                if (ItemStack.isSameItemSameComponents(r.output, current)) {
                    setCurrentRecipe(r);
                    break;
                }
            }
        }
    }

    public boolean isPlanned(@Nullable AssemblyRecipe recipe) {
        if (recipe == null)
            return false;

        return plannedOutput.contains(recipe);
    }

    public boolean isAssembling(@Nullable AssemblyRecipe recipe) {
        return recipe != null && recipe == currentRecipe;
    }

    private void setCurrentRecipe(@Nullable AssemblyRecipe recipe) {
        this.currentRecipe = recipe;
        if (recipe != null) {
            this.currentRequiredEnergy = (long) recipe.energy;
        } else {
            this.currentRequiredEnergy = 0;
        }
        onSelectionChanged();
    }

    public void planOutput(@Nullable AssemblyRecipe recipe) {
        if (recipe != null && !isPlanned(recipe)) {
            plannedOutput.add(recipe);
            onSelectionChanged();

            if (!isAssembling(currentRecipe) || !isPlanned(currentRecipe)) {
                setCurrentRecipe(recipe);
            }
        }
    }

    public void cancelPlanOutput(@Nullable AssemblyRecipe recipe) {
        if (isAssembling(recipe)) {
            setCurrentRecipe(null);
        }

        plannedOutput.remove(recipe);
        onSelectionChanged();

        if (!plannedOutput.isEmpty()) {
            setCurrentRecipe(plannedOutput.iterator().next());
        }
    }

    public void setNextCurrentRecipe() {
        boolean takeNext = false;

        for (AssemblyRecipe recipe : plannedOutput) {
            if (recipe == currentRecipe) {
                takeNext = true;
            } else if (takeNext && recipe.canBeDone(items)) {
                setCurrentRecipe(recipe);
                return;
            }
        }

        for (AssemblyRecipe recipe : plannedOutput) {
            if (recipe.canBeDone(items)) {
                setCurrentRecipe(recipe);
                return;
            }
        }

        setCurrentRecipe(null);
    }

    /** New: save the selection, and send it to the clients on the next tick (4.2.2 sent it only on request). */
    private void onSelectionChanged() {
        if (level != null && !level.isClientSide()) {
            selectionChanged = true;
            setChanged();
        }
    }

    public void handleSelectionMessage(SelectionMessage message) {
        if (level == null)
            return;

        resolveRecipes();

        for (AssemblyRecipe recipe : AssemblyRecipeManager.getRecipes(level)) {
            if (ItemStack.isSameItemSameComponents(recipe.output, message.stack)) {
                if (message.select) {
                    planOutput(recipe);
                } else {
                    cancelPlanOutput(recipe);
                }

                break;
            }
        }
    }

    /*
     * 4.2.2 sendSelectionTo(player) answered the GUI's SELECTION_ASSEMBLY_GET with one SELECTION_ASSEMBLY_SEND per
     * recipe. The selection is now part of the network payload, sent to every player near the table when it changes.
     */
    @Override
    public void writePacketPayload(RegistryFriendlyByteBuf data) {
        super.writePacketPayload(data);
        List<ItemStack> planned = getPlannedStacks();
        data.writeVarInt(planned.size());
        for (ItemStack stack : planned) {
            ItemStack.STREAM_CODEC.encode(data, stack);
        }
        ItemStack.OPTIONAL_STREAM_CODEC.encode(data, getCurrentStack());
    }

    @Override
    public void readPacketPayload(RegistryFriendlyByteBuf data) {
        super.readPacketPayload(data);
        int size = data.readVarInt();
        List<ItemStack> planned = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            planned.add(ItemStack.STREAM_CODEC.decode(data));
        }
        pendingPlanned = planned;
        pendingRecipe = ItemStack.OPTIONAL_STREAM_CODEC.decode(data);
        resolveRecipes();
    }

    /* GUI RETURN: 4.2.2's SELECTION_ASSEMBLY packet. The GUI sends a SelectionMessage as the extra data. */
    @Override
    public void writeGuiData(RegistryFriendlyByteBuf data) {
    }

    @Override
    public void readGuiData(RegistryFriendlyByteBuf data, @Nullable Player player) throws InvalidPacketException {
        SelectionMessage message = new SelectionMessage();
        message.readData(data);
        handleSelectionMessage(message);
    }

    /* SMP GUI */
    /** Client side: 4.2.2's updateProgressBar values, 16 bits at a time. */
    public void getGUINetworkData(int i, int j) {
        int currentStored = (int) energyStored;
        int requiredEnergy = (int) currentRequiredEnergy;
        switch (i) {
            case 0:
                requiredEnergy = (requiredEnergy & 0xFFFF0000) | (j & 0xFFFF);
                currentRequiredEnergy = requiredEnergy;
                break;
            case 1:
                currentStored = (currentStored & 0xFFFF0000) | (j & 0xFFFF);
                energyStored = currentStored;
                break;
            case 2:
                requiredEnergy = (requiredEnergy & 0xFFFF) | ((j & 0xFFFF) << 16);
                currentRequiredEnergy = requiredEnergy;
                break;
            case 3:
                currentStored = (currentStored & 0xFFFF) | ((j & 0xFFFF) << 16);
                energyStored = currentStored;
                break;
            case 4:
                recentEnergyAverage = recentEnergyAverage & 0xFFFF0000 | (j & 0xFFFF);
                break;
            case 5:
                recentEnergyAverage = (recentEnergyAverage & 0xFFFF) | ((j & 0xFFFF) << 16);
                break;
        }
    }

    /**
     * Server side: the value of progress bar i, as 4.2.2's sendGUINetworkData sent it. 4.2.2 sent MJ × 100; FE are
     * whole numbers and go as they are, except the rate, FE/t × 100.
     */
    public int sendGUINetworkData(int i) {
        int requiredEnergy = (int) Math.min(currentRequiredEnergy, Integer.MAX_VALUE);
        int currentStored = (int) Math.min(energyStored, Integer.MAX_VALUE);
        int lRecentEnergy = 0;
        for (int k = 0; k < recentEnergy.length; k++) {
            lRecentEnergy += (int) (recentEnergy[k] * 100 / (recentEnergy.length - 1));
        }
        switch (i) {
            case 0:
                return requiredEnergy & 0xFFFF;
            case 1:
                return currentStored & 0xFFFF;
            case 2:
                return (requiredEnergy >>> 16) & 0xFFFF;
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
    public boolean isActive() {
        return currentRecipe != null;
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
        return false;
    }

    public int getRecentEnergyAverage() {
        return recentEnergyAverage;
    }

    /** FE. */
    public long getStoredEnergy() {
        return energyStored;
    }

    /** FE. */
    public long getRequiredEnergy() {
        return currentRequiredEnergy;
    }

    @Override
    public boolean requiresLaserEnergy() {
        return currentRecipe != null && energyStored < currentRequiredEnergy * 5;
    }

    @Override
    public void receiveLaserEnergy(long energy) {
        energyStored += energy;
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
    public boolean canPlaceItem(int i, ItemStack itemstack) {
        return true;
    }

    @Override
    public boolean isInvalidTarget() {
        return isRemoved();
    }

    /* MENU: 4.2.2's GuiHandler, GUI id 0. */
    @Override
    public Component getDisplayName() {
        return Component.translatable("tile.assemblyTableBlock");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ContainerAssemblyTable(containerId, playerInventory, this);
    }
}
