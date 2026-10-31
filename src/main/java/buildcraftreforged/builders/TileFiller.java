/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: power in FE (MJ x 10); IInventory becomes Container over the same
 * SimpleInventory; the patterns break and place as the owner's fake player and skip what the owner may not change
 * (the skipped spots are kept until the pattern starts over); the stack is placed through the item's own use, so it
 * shrinks in its slot; @TileNetworkData/PacketPayload becomes writePacketPayload; NBT becomes ValueInput/ValueOutput
 * with the same tag names; invalidate becomes setRemoved; the GUI is a MenuProvider and its PacketGuiReturn checks the
 * pattern tag; the block's top light and pattern face are block state properties, set here on the server; the status
 * panel (plan section 10) is new.
 */
package buildcraftreforged.builders;

import buildcraftreforged.api.core.IAreaProvider;
import buildcraftreforged.api.core.LaserKind;
import buildcraftreforged.api.filler.FillerManager;
import buildcraftreforged.api.filler.IFillerPattern;
import buildcraftreforged.api.filler.IPatternIterator;
import buildcraftreforged.api.gates.IAction;
import buildcraftreforged.api.gates.IActionReceptor;
import buildcraftreforged.api.power.IPowerReceptor;
import buildcraftreforged.api.power.PowerHandler;
import buildcraftreforged.api.power.PowerHandler.PowerReceiver;
import buildcraftreforged.api.power.PowerHandler.Type;
import buildcraftreforged.builders.filler.pattern.PatternFill;
import buildcraftreforged.builders.gui.ContainerFiller;
import buildcraftreforged.builders.triggers.ActionFiller;
import buildcraftreforged.core.Box;
import buildcraftreforged.core.CoreRegistry;
import buildcraftreforged.core.IMachine;
import buildcraftreforged.core.TileBuildCraft;
import buildcraftreforged.core.inventory.InventoryIterator;
import buildcraftreforged.core.inventory.InventoryIterator.IInvSlot;
import buildcraftreforged.core.inventory.SimpleInventory;
import buildcraftreforged.core.network.IGuiReturnHandler;
import buildcraftreforged.core.network.InvalidPacketException;
import buildcraftreforged.core.network.PacketData;
import buildcraftreforged.core.status.FlowMeter;
import buildcraftreforged.core.status.IStatusProvider;
import buildcraftreforged.core.status.MachineStatus;
import buildcraftreforged.core.status.StatusReason;
import buildcraftreforged.core.status.StatusReport;
import buildcraftreforged.core.triggers.ActionMachineControl;
import buildcraftreforged.core.triggers.ActionMachineControl.Mode;
import buildcraftreforged.core.utils.Utils;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class TileFiller extends TileBuildCraft implements Container, IPowerReceptor, IMachine, IActionReceptor,
        IGuiReturnHandler, MenuProvider, IStatusProvider {

    /** The longest pattern tag a GUI may send back. */
    private static final int MAX_TAG_LENGTH = 256;

    public IFillerPattern currentPattern = PatternFill.INSTANCE;
    /** 4.2.2 25 MJ per block, in FE. */
    private static int POWER_USAGE = 250;
    private final Box box = new Box();
    private boolean done = false;
    private @Nullable IPatternIterator patternIterator;
    private PowerHandler powerHandler;
    private ActionMachineControl.Mode lastMode = ActionMachineControl.Mode.Unknown;
    private SimpleInventory inv = new SimpleInventory(27, "Filler", 64);

    /**
     * Not in 4.2.2: spots the owner may not change, or where using the item placed nothing; the pattern passes over
     * them until it starts over (new pattern, Loop, reload). Not saved.
     */
    private final Set<BlockPos> skipped = new HashSet<>();
    /** Not in 4.2.2: the last step wanted to place a block and the inventory was empty (status panel). */
    private boolean missingMaterial;
    /** Not in 4.2.2: the status panel's Power line. */
    public final FlowMeter flowMeter = new FlowMeter(() -> level == null ? 0 : level.getGameTime());

    public TileFiller(BlockPos pos, BlockState state) {
        super(BuildersRegistry.fillerTile.get(), pos, state);
        inv.addListener(this);
        powerHandler = new PowerHandler(this, Type.MACHINE);
        initPowerProvider();
    }

    /** 4.2.2 configure(30, POWER_USAGE * 2, POWER_USAGE, POWER_USAGE * 4) and configurePowerPerdition(1, 1), in FE. */
    private void initPowerProvider() {
        powerHandler.configure(300, POWER_USAGE * 2, POWER_USAGE, POWER_USAGE * 4);
        powerHandler.configurePowerPerdition(10, 1);
    }

    @Override
    public void initialize() {
        super.initialize();

        if (level != null && !level.isClientSide()) {
            IAreaProvider a = Utils.getNearbyAreaProvider(level, worldPosition.getX(), worldPosition.getY(),
                    worldPosition.getZ());

            if (a != null) {
                box.initialize(a);

                if (a instanceof TileMarker) {
                    ((TileMarker) a).removeFromWorld();
                }

                if (box.isInitialized()) {
                    box.createLasers(level, LaserKind.Stripes);
                }
                sendNetworkUpdate();
            }
        }
    }

    @Override
    public void updateEntity() {
        super.updateEntity();
        if (done) {
            if (lastMode == Mode.Loop) {
                done = false;
            }
        }
        updateBlockState();
    }

    @Override
    public void doWork(PowerHandler workProvider) {
        if (level == null || level.isClientSide())
            return;
        if (done)
            return;
        if (lastMode == Mode.Off)
            return;
        if (powerHandler.useEnergy(POWER_USAGE, POWER_USAGE, false) != POWER_USAGE)
            return;
        if (!box.isInitialized())
            return;

        if (patternIterator == null) {
            patternIterator = currentPattern.createPatternIterator(this, box, Direction.NORTH);
            skipped.clear();
        }

        ItemStack stackToUse = null;
        int slotNum = 0;

        for (IInvSlot slot : InventoryIterator.getIterable(inv, null)) {
            ItemStack stack = slot.getStackInSlot();
            if (!stack.isEmpty()) {
                stackToUse = stack;
                slotNum = slot.getIndex();
                break;
            }
        }

        missingMaterial = false;
        done = patternIterator.iteratePattern(stackToUse);
        powerHandler.useEnergy(POWER_USAGE, POWER_USAGE, true);
        flowMeter.energyIn(POWER_USAGE);

        if (stackToUse != null) {
            if (stackToUse.isEmpty()) {
                setItem(slotNum, ItemStack.EMPTY);
            } else {
                setChanged();
            }
        }

        if (done) {
            patternIterator = null;
            sendNetworkUpdate();
        } else if (powerHandler.getEnergyStored() >= POWER_USAGE) {
            doWork(workProvider);
        }
    }

    /* CONTAINER */
    @Override
    public final int getContainerSize() {
        return inv.getContainerSize();
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
    public ItemStack removeItem(int slot, int amount) {
        return inv.removeItem(slot, amount);
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

    public String getInvName() {
        return "Filler";
    }

    @Override
    protected void loadAdditional(ValueInput nbt) {
        super.loadAdditional(nbt);

        inv.readFromNBT(nbt);

        IFillerPattern pattern = nbt.getString("pattern").map(FillerManager.registry::getPattern).orElse(null);
        currentPattern = pattern != null ? pattern : PatternFill.INSTANCE;

        nbt.child("box").ifPresent(box::initialize);

        done = nbt.getBooleanOr("done", false);
        int mode = nbt.getByteOr("lastMode", (byte) 0);
        lastMode = mode >= 0 && mode < Mode.values().length ? Mode.values()[mode] : Mode.Unknown;
    }

    @Override
    protected void saveAdditional(ValueOutput nbt) {
        super.saveAdditional(nbt);

        inv.writeToNBT(nbt);

        if (currentPattern != null)
            nbt.putString("pattern", currentPattern.getUniqueTag());

        box.writeToNBT(nbt.child("box"));

        nbt.putBoolean("done", done);
        nbt.putByte("lastMode", (byte) lastMode.ordinal());
    }

    @Override
    public int getMaxStackSize() {
        return inv.getMaxStackSize();
    }

    @Override
    public boolean stillValid(Player entityplayer) {
        return Container.stillValidBlockEntity(this, entityplayer, 8);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        destroy();
    }

    @Override
    public void destroy() {
        box.deleteLasers();
    }

    public void setPattern(@Nullable IFillerPattern pattern) {
        if (pattern != null && currentPattern != pattern) {
            currentPattern = pattern;
            patternIterator = null;
            done = false;
            sendNetworkUpdate();
            setChanged();
        }
    }

    @Override
    public void writePacketPayload(RegistryFriendlyByteBuf data) {
        box.writeToStream(data);
        data.writeBoolean(done);
        data.writeUtf(currentPattern.getUniqueTag());
    }

    @Override
    public void readPacketPayload(RegistryFriendlyByteBuf data) {
        boolean initialized = box.isInitialized();
        box.readFromStream(data);
        done = data.readBoolean();
        setPattern(FillerManager.registry.getPattern(data.readUtf()));

        if (!initialized && box.isInitialized() && level != null) {
            box.createLasers(level, LaserKind.Stripes);
        }
    }

    @Override
    public PowerReceiver getPowerReceiver(@Nullable Direction side) {
        return powerHandler.getPowerReceiver();
    }

    @Override
    public boolean isActive() {
        return !done && lastMode != Mode.Off;
    }

    @Override
    public boolean manageFluids() {
        return false;
    }

    @Override
    public boolean manageSolids() {
        return true;
    }

    @Override
    public void actionActivated(IAction action) {
        if (action == CoreRegistry.actionOn.get()) {
            lastMode = ActionMachineControl.Mode.On;
        } else if (action == CoreRegistry.actionOff.get()) {
            lastMode = ActionMachineControl.Mode.Off;
        } else if (action == CoreRegistry.actionLoop.get()) {
            lastMode = ActionMachineControl.Mode.Loop;
        } else if (action instanceof ActionFiller) {
            ActionFiller actFill = (ActionFiller) action;
            setPattern(actFill.pattern);
        }
    }

    @Override
    public boolean allowAction(IAction action) {
        return true;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return true;
    }

    @Override
    public void writeGuiData(RegistryFriendlyByteBuf data) {
        data.writeUtf(currentPattern.getUniqueTag());
    }

    @Override
    public void readGuiData(RegistryFriendlyByteBuf data, @Nullable Player player) throws InvalidPacketException {
        String tag = PacketData.readUtf(data, MAX_TAG_LENGTH);
        IFillerPattern pattern = FillerManager.registry.getPattern(tag);
        if (pattern == null)
            throw new InvalidPacketException("Unknown filler pattern " + tag);
        setPattern(pattern);
    }

    /* MENU: 4.2.2's GuiHandler, GuiIds.FILLER. */
    @Override
    public Component getDisplayName() {
        return Component.translatable("tile.fillerBlock");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ContainerFiller(containerId, playerInventory, this);
    }

    // Not in 4.2.2.

    public Box getBox() {
        return box;
    }

    public boolean isDone() {
        return done;
    }

    public PowerHandler getPowerHandler() {
        return powerHandler;
    }

    /** Called by the patterns: pass over pos until the pattern starts over. */
    public void skip(BlockPos pos) {
        skipped.add(pos.immutable());
    }

    public boolean isSkipped(BlockPos pos) {
        return skipped.contains(pos);
    }

    /** Called by the patterns when a step wanted a block and the inventory had none. */
    public void setMissingMaterial() {
        missingMaterial = true;
    }

    /**
     * 4.2.2's BlockFiller.getBlockTexture read the tile on the client: the top lit while active, the sides showing the
     * pattern. The block state carries both now; the server keeps them in step.
     */
    private void updateBlockState() {
        if (level == null || level.isClientSide())
            return;
        BlockState state = getBlockState();
        if (!state.hasProperty(BlockFiller.ACTIVE))
            return;
        BlockState wanted = state.setValue(BlockFiller.ACTIVE, isActive())
                .setValue(BlockFiller.PATTERN, BlockFiller.PatternFace.of(currentPattern));
        if (wanted != state) {
            level.setBlock(worldPosition, wanted, Block.UPDATE_CLIENTS);
        }
    }

    // Status panel (plan section 10), not in 4.2.2.

    /** Waiting for blocks to place: the step found a spot and the inventory was empty. */
    @Override
    public boolean isBlocked() {
        return missingMaterial && isActive() && box.isInitialized();
    }

    @Override
    public MachineStatus getStatusState() {
        if (!box.isInitialized())
            return MachineStatus.NO_WORK_AREA;
        if (done)
            return MachineStatus.DONE;
        if (lastMode == Mode.Off)
            return MachineStatus.IDLE;
        if (isBlocked())
            return MachineStatus.BLOCKED;
        // A working filler spends what it has every tick, so look at the last second too.
        if (powerHandler.getEnergyStored() < POWER_USAGE && flowMeter.energyInPerSecond() == 0)
            return MachineStatus.NO_POWER;
        return MachineStatus.WORKING;
    }

    @Override
    public StatusReason getStatusReason() {
        return isBlocked() ? StatusReason.MISSING_MATERIALS : StatusReason.NONE;
    }

    @Override
    public FlowMeter getStatusFlow() {
        return flowMeter;
    }

    @Override
    public StatusReport.Power getStatusPower() {
        return StatusReport.Power.drawn(flowMeter, powerHandler.getEnergyStored(), powerHandler.getMaxEnergyStored());
    }
}
