/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: TileEntity becomes TileBuildCraft's BlockEntity; energy is FE (4.2.2 MJ × 10),
 * kept as a float inside the engine and handed over in whole FE; orientation is BlockEngine.FACING in the block state
 * instead of a saved and synced field; a neighbour without an IPowerReceptor gets the pulse through its Forge Energy
 * handler; an engine does not push into an engine that faces it (BUGFIXES); the @TileNetworkData fields go through
 * writePacketPayload; the GUI progress bars become the guiData ContainerData; NBT becomes ValueInput/ValueOutput;
 * validate/invalidate become clearRemoved/setRemoved; the status panel lines are added (plan section 10); getTriggers
 * waits for the heat triggers (#41); TileEngineLegacy's pre-4.0 conversion is not ported. The overheat timer (plan
 * section 9, #40) replaces the immediate explosion in addEnergy: an engine that can overheat starts a timer in its red
 * stage and explodes or shuts down (engines.explode) when it runs out; heat stops at MAX_HEAT meanwhile; the client
 * plays the rising whine.
 */
package buildcraftreforged.energy;

import buildcraftreforged.api.power.IPowerEmitter;
import buildcraftreforged.api.power.IPowerReceptor;
import buildcraftreforged.api.power.PowerHandler;
import buildcraftreforged.api.power.PowerHandler.PowerReceiver;
import buildcraftreforged.api.power.PowerHandler.Type;
import buildcraftreforged.api.transport.IPipeConnection;
import buildcraftreforged.api.transport.IPipeTile;
import buildcraftreforged.api.transport.IPipeTile.PipeType;
import buildcraftreforged.core.BuildCraftConfiguration;
import buildcraftreforged.core.DefaultProps;
import buildcraftreforged.core.TileBuffer;
import buildcraftreforged.core.TileBuildCraft;
import buildcraftreforged.core.status.FlowMeter;
import buildcraftreforged.core.status.HeatStage;
import buildcraftreforged.core.status.IStatusProvider;
import buildcraftreforged.core.status.MachineStatus;
import buildcraftreforged.core.status.StatusReason;
import buildcraftreforged.core.status.StatusPanel;
import buildcraftreforged.core.status.StatusReport;
import buildcraftreforged.energy.render.EngineWhineSound;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

public abstract class TileEngine extends TileBuildCraft implements IPowerReceptor, IPowerEmitter, IPipeConnection, IStatusProvider {

    public static final Identifier WOOD_TEXTURE = texture("base_wood");
    public static final Identifier STONE_TEXTURE = texture("base_stone");
    public static final Identifier IRON_TEXTURE = texture("base_iron");

    public enum EnergyStage {

        BLUE, GREEN, YELLOW, RED, OVERHEAT
    }
    public static final float MIN_HEAT = 20;
    public static final float IDEAL_HEAT = 100;
    public static final float MAX_HEAT = 250;
    protected int progressPart = 0;
    protected boolean lastPower = false;
    protected PowerHandler powerHandler;
    /** FE per tick (4.2.2: MJ). */
    public float currentOutput = 0;
    public boolean isRedstonePowered = false;
    private TileBuffer @Nullable [] tileCache;
    public float progress;
    /** FE (4.2.2: MJ). A float as in 4.2.2, so fractional outputs and doWork's 95 % keep their fraction. */
    public float energy;
    public float heat = MIN_HEAT;
    // Synced in writePacketPayload (4.2.2 @TileNetworkData). The orientation is BlockEngine.FACING.
    public EnergyStage energyStage = EnergyStage.BLUE;
    public boolean isPumping = false; // Used for SMP synch
    /**
     * Ticks left on the overheat timer (plan section 9), or -1 while it is not running. Synced to the client, which
     * counts down between updates for the whine.
     */
    public int overheatTimer = -1;
    /** The length the running overheat timer started with, in ticks; the whine's pitch follows overheatTimer / this. */
    public int overheatTimerTotal = 0;
    /** The FE this engine hands out, for the status panel's Power line. */
    protected final FlowMeter meter = new FlowMeter(() -> level == null ? 0 : level.getGameTime());
    /** 4.2.2 getGUINetworkData/sendGUINetworkData, for the engine GUIs' menu (ContainerEngine, #38 and #44). */
    public final ContainerData guiData = new ContainerData() {
        @Override
        public int get(int id) {
            return sendGUINetworkData(id);
        }

        @Override
        public void set(int id, int value) {
            getGUINetworkData(id, value);
        }

        @Override
        public int getCount() {
            return getGUINetworkDataCount();
        }
    };

    public TileEngine(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        powerHandler = new PowerHandler(this, Type.ENGINE);
        powerHandler.configurePowerPerdition(10, 100);
    }

    /** 4.2.2 DefaultProps.TEXTURE_PATH_BLOCKS ("textures/blocks") is textures/block now; #39 adds the files. */
    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath(DefaultProps.MOD, "textures/block/" + name + ".png");
    }

    @Override
    public void initialize() {
        if (level != null && !level.isClientSide()) {
            powerHandler.configure((long) minEnergyReceived(), (long) maxEnergyReceived(), 10, (long) getMaxEnergy());
            checkRedstonePower();
        }
    }

    public abstract Identifier getTextureFile();

    /**
     * Called by BlockEngine when a player uses the engine. 4.2.2 returned false here; an engine without a GUI now opens
     * the status panel (plan section 10). Engines with a GUI open it instead, with the status ledger beside it.
     */
    public boolean onBlockActivated(Player player, Direction side) {
        StatusPanel.open(player, this);
        return true;
    }

    public float getEnergyLevel() {
        return energy / getMaxEnergy();
    }

    protected EnergyStage computeEnergyStage() {
        float energyLevel = getHeatLevel();
        if (energyLevel < 0.25f)
            return EnergyStage.BLUE;
        else if (energyLevel < 0.5f)
            return EnergyStage.GREEN;
        else if (energyLevel < 0.75f)
            return EnergyStage.YELLOW;
        else if (energyLevel < 1f)
            return EnergyStage.RED;
        else
            return EnergyStage.OVERHEAT;
    }

    public final EnergyStage getEnergyStage() {
        if (isSimulating()) {
            if (energyStage == EnergyStage.OVERHEAT)
                return energyStage;
            EnergyStage newStage = computeEnergyStage();

            // Plan section 9: OVERHEAT comes only from the overheat timer; until it runs out the engine stays red.
            if (newStage == EnergyStage.OVERHEAT)
                newStage = EnergyStage.RED;

            if (energyStage != newStage) {
                energyStage = newStage;
                sendNetworkUpdate();
            }
        }

        return energyStage;
    }

    public void updateHeatLevel() {
        heat = ((MAX_HEAT - MIN_HEAT) * getEnergyLevel()) + MIN_HEAT;
    }

    public float getHeatLevel() {
        return (heat - MIN_HEAT) / (MAX_HEAT - MIN_HEAT);
    }

    public float getIdealHeatLevel() {
        return heat / IDEAL_HEAT;
    }

    public float getHeat() {
        return heat;
    }

    public float getPistonSpeed() {
        if (isSimulating())
            return Math.max(0.16f * getHeatLevel(), 0.01f);
        switch (getEnergyStage()) {
            case BLUE:
                return 0.02F;
            case GREEN:
                return 0.04F;
            case YELLOW:
                return 0.08F;
            case RED:
                return 0.16F;
            default:
                return 0;
        }
    }

    @Override
    public void updateEntity() {
        super.updateEntity();

        if (level == null || isRemoved())
            return;

        if (level.isClientSide()) {
            if (progressPart != 0) {
                progress += getPistonSpeed();

                if (progress > 1) {
                    progressPart = 0;
                    progress = 0;
                }
            } else if (this.isPumping)
                progressPart = 1;

            if (overheatTimer > 0)
                overheatTimer--;
            if (overheatTimer >= 0)
                EngineWhineSound.play(this);

            return;
        }

        if (energyStage == EnergyStage.OVERHEAT) {
            // Shut down by the overheat timer (engines.explode = false) until a wrench restarts it: no fuel burns and
            // nothing is sent, the stored energy bleeds away (5 MJ a tick) and the engine cools as if unpowered.
            energy = Math.max(energy - 50, 0);
            updateHeatLevel();
            engineUpdate();
            setPumping(false);
            return;
        }

        if (!isRedstonePowered)
            if (energy > 10)
                energy -= 10;

        updateHeatLevel();
        getEnergyStage();
        engineUpdate();
        updateOverheatTimer();

        // An engine whose overheat timer ran out has exploded or shut down.
        if (isRemoved() || energyStage == EnergyStage.OVERHEAT)
            return;

        Direction orientation = getOrientation();
        BlockEntity tile = getTileBuffer(orientation).getTile();

        if (progressPart != 0) {
            progress += getPistonSpeed();

            if (progress > 0.5 && progressPart == 1) {
                progressPart = 2;
                sendPower(); // Comment out for constant power
            } else if (progress >= 1) {
                progress = 0;
                progressPart = 0;
            }
        } else if (isRedstonePowered && isActive())
            if (isPoweredTile(tile, orientation))
                if (getPowerToExtract() > 0) {
                    progressPart = 1;
                    setPumping(true);
                } else
                    setPumping(false);
            else
                setPumping(false);
        else
            setPumping(false);

        burn();
    }

    /**
     * 4.2.2 asked the receptor for its min and max. A neighbour that is only a Forge Energy handler has neither: the
     * pulse is whatever the engine may extract at once, and the handler takes what it can.
     */
    private float getPowerToExtract() {
        Direction orientation = getOrientation();
        BlockEntity tile = getTileBuffer(orientation).getTile();
        if (tile instanceof IPowerReceptor) {
            PowerReceiver receptor = ((IPowerReceptor) tile).getPowerReceiver(orientation.getOpposite());
            if (receptor == null)
                return 0;
            return extractEnergy(receptor.getMinEnergyReceived(), receptor.getMaxEnergyReceived(), false); // Comment out for constant power
        }
        return extractEnergy(0, maxEnergyExtracted(), false);
    }

    private void sendPower() {
        Direction orientation = getOrientation();
        BlockEntity tile = getTileBuffer(orientation).getTile();
        if (isPoweredTile(tile, orientation)) {
            float extracted = getPowerToExtract();
            if (tile instanceof IPowerReceptor) {
                PowerReceiver receptor = ((IPowerReceptor) tile).getPowerReceiver(orientation.getOpposite());

                // Whole FE cross over; the fraction stays in the engine.
                if (extracted >= 1) {
                    long needed = receptor.receiveEnergy(PowerHandler.Type.ENGINE, (long) extracted, orientation.getOpposite());
                    meter.energyOut(needed);
                    extractEnergy(receptor.getMinEnergyReceived(), needed, true); // Comment out for constant power
                }
            } else {
                EnergyHandler handler = getEnergyHandler(orientation);
                if (handler != null && extracted >= 1) {
                    int inserted;
                    try (Transaction transaction = Transaction.openRoot()) {
                        inserted = handler.insert((int) Math.min(extracted, Integer.MAX_VALUE), transaction);
                        transaction.commit();
                    }
                    meter.energyOut(inserted);
                    extractEnergy(0, inserted, true);
                }
            }
        }
    }

    protected void burn() {
    }

    /**
     * Plan section 9. In the red stage a timer of engines.overheatTimerSeconds runs while the engine keeps working; heat
     * stops at MAX_HEAT, where 4.2.2 exploded. Leaving the red stage stops the timer; when it runs out the engine
     * overheats.
     */
    private void updateOverheatTimer() {
        if (heat > MAX_HEAT)
            heat = MAX_HEAT;

        if (energyStage != EnergyStage.RED || !canOverheat()) {
            if (overheatTimer >= 0) {
                overheatTimer = -1;
                sendNetworkUpdate();
            }
            return;
        }

        if (overheatTimer < 0) {
            overheatTimer = overheatTimerTotal = getOverheatTimerTicks();
            sendNetworkUpdate();
        } else if (overheatTimer > 0) {
            overheatTimer--;
            if (overheatTimer % 20 == 0)
                sendNetworkUpdate();
        }

        if (overheatTimer == 0)
            overheat();
    }

    /**
     * The overheat timer ran out: with engines.explode the 4.2.2 explosion (addEnergy's), otherwise the OVERHEAT stage,
     * in which the engine stays shut down until {@link #restartAfterOverheat}.
     */
    protected void overheat() {
        overheatTimer = -1;
        if (level == null)
            return;
        if (explodesWhenOverheated()) {
            level.explode(null, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), explosionRange(),
                    Level.ExplosionInteraction.BLOCK);
            level.removeBlock(worldPosition, false);
        } else {
            energyStage = EnergyStage.OVERHEAT;
            setPumping(false);
            sendNetworkUpdate();
        }
    }

    /** The wrench on an engine shut down by overheating: it starts again from its current heat. */
    public boolean restartAfterOverheat() {
        if (energyStage != EnergyStage.OVERHEAT || !isSimulating())
            return false;
        energyStage = EnergyStage.BLUE;
        getEnergyStage();
        sendNetworkUpdate();
        return true;
    }

    /** False for an engine that never overheated in 4.2.2 (the redstone engine stops at its red stage). */
    protected boolean canOverheat() {
        return true;
    }

    /** engines.overheatTimerSeconds in ticks. */
    protected int getOverheatTimerTicks() {
        return BuildCraftConfiguration.engineOverheatTimerSeconds.get() * 20;
    }

    /** engines.explode. */
    protected boolean explodesWhenOverheated() {
        return BuildCraftConfiguration.engineExplode.get();
    }

    protected void engineUpdate() {
        if (!isRedstonePowered)
            if (energy >= 10)
                energy -= 10;
            else if (energy < 10)
                energy = 0;
    }

    public boolean isActive() {
        return true;
    }

    protected final void setPumping(boolean isActive) {
        if (this.isPumping == isActive)
            return;

        this.isPumping = isActive;
        sendNetworkUpdate();
    }

    public boolean isOrientationValid() {
        Direction orientation = getOrientation();
        BlockEntity tile = getTileBuffer(orientation).getTile();
        return isPoweredTile(tile, orientation);
    }

    /** The wrench: the next of the six faces in data-value order, or the previous one when {@code reverse}. */
    public void cycleOrientation(boolean reverse) {
        int step = reverse ? 5 : 1;
        setOrientation(Direction.from3DDataValue((getOrientation().get3DDataValue() + step) % 6));
    }

    public boolean switchOrientation(boolean preferPipe) {
        if (preferPipe && switchOrientation_do(true))
            return true;
        return switchOrientation_do(false);
    }

    private boolean switchOrientation_do(boolean pipesOnly) {
        Direction orientation = getOrientation();
        for (int i = orientation.ordinal() + 1; i <= orientation.ordinal() + 6; ++i) {
            Direction o = Direction.from3DDataValue(i % 6);

            BlockEntity tile = getTileBuffer(o).getTile();

            if ((!pipesOnly || tile instanceof IPipeTile) && isPoweredTile(tile, o)) {
                setOrientation(o);
                return true;
            }
        }
        return false;
    }

    /** 4.2.2's orientation field: BlockEngine.FACING. */
    public Direction getOrientation() {
        BlockState state = getBlockState();
        return state.hasProperty(BlockEngine.FACING) ? state.getValue(BlockEngine.FACING) : Direction.UP;
    }

    /**
     * Sets BlockEngine.FACING, which also sends the change to clients and notifies the neighbours (4.2.2:
     * markBlockForUpdate and notifyBlocksOfNeighborChange). The output side refuses Forge Energy, so cached
     * capabilities are invalidated.
     */
    public void setOrientation(Direction orientation) {
        if (level == null || orientation == getOrientation() || !getBlockState().hasProperty(BlockEngine.FACING))
            return;
        level.setBlock(worldPosition, getBlockState().setValue(BlockEngine.FACING, orientation), Block.UPDATE_ALL);
        level.invalidateCapabilities(worldPosition);
    }

    public TileBuffer getTileBuffer(Direction side) {
        if (tileCache == null)
            tileCache = TileBuffer.makeBuffer(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), false);
        return tileCache[side.ordinal()];
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        tileCache = null;
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        tileCache = null;
    }

    @Override
    protected void loadAdditional(ValueInput data) {
        super.loadAdditional(data);
        progress = data.getFloatOr("progress", 0);
        energy = data.getFloatOr("energyF", 0);
        heat = data.getFloatOr("heat", heat);
        overheatTimer = data.getIntOr("overheatTimer", -1);
        overheatTimerTotal = data.getIntOr("overheatTimerTotal", 0);
        if (data.getBooleanOr("overheated", false))
            energyStage = EnergyStage.OVERHEAT;
    }

    @Override
    protected void saveAdditional(ValueOutput data) {
        super.saveAdditional(data);
        data.putFloat("progress", progress);
        data.putFloat("energyF", energy);
        data.putFloat("heat", heat);
        data.putInt("overheatTimer", overheatTimer);
        data.putInt("overheatTimerTotal", overheatTimerTotal);
        data.putBoolean("overheated", energyStage == EnergyStage.OVERHEAT);
    }

    @Override
    public void writePacketPayload(RegistryFriendlyByteBuf data) {
        super.writePacketPayload(data);
        data.writeByte(energyStage.ordinal());
        data.writeBoolean(isPumping);
        data.writeVarInt(overheatTimer + 1);
        data.writeVarInt(overheatTimerTotal);
    }

    @Override
    public void readPacketPayload(RegistryFriendlyByteBuf data) {
        super.readPacketPayload(data);
        int stage = data.readByte();
        energyStage = EnergyStage.values()[Math.floorMod(stage, EnergyStage.values().length)];
        isPumping = data.readBoolean();
        overheatTimer = Math.max(data.readVarInt(), 0) - 1;
        overheatTimerTotal = Math.max(data.readVarInt(), 0);
    }

    /**
     * 4.2.2 getGUINetworkData: the client side of the progress bars. Values arrive as 16-bit shorts, as in 4.2.2;
     * energy is whole FE in two halves (4.2.2 sent tenths of an MJ, the same unit).
     */
    public void getGUINetworkData(int id, int value) {
        switch (id) {
            case 0:
                int iEnergy = Math.round(energy);
                iEnergy = (iEnergy & 0xffff0000) | (value & 0xffff);
                energy = iEnergy;
                break;
            case 1:
                iEnergy = Math.round(energy);
                iEnergy = (iEnergy & 0xffff) | ((value & 0xffff) << 16);
                energy = iEnergy;
                break;
            case 2:
                currentOutput = (value & 0xffff) / 10F;
                break;
            case 3:
                heat = (value & 0xffff) / 100F;
                break;
        }
    }

    /** 4.2.2 sendGUINetworkData: the server side of the progress bars, one value per id. */
    public int sendGUINetworkData(int id) {
        switch (id) {
            case 0:
                return Math.round(energy) & 0xffff;
            case 1:
                return (Math.round(energy) & 0xffff0000) >>> 16;
            case 2:
                return Math.round(currentOutput * 10);
            case 3:
                return Math.round(heat * 100);
            default:
                return 0;
        }
    }

    /** Values in guiData: the 4 above, plus any a subclass adds after them (the Stirling engine's burn times). */
    protected int getGUINetworkDataCount() {
        return 4;
    }

    /* STATE INFORMATION */
    public abstract boolean isBurning();

    public abstract int getScaledBurnTime(int scale);

    @Override
    public PowerReceiver getPowerReceiver(@Nullable Direction side) {
        return powerHandler.getPowerReceiver();
    }

    @Override
    public void doWork(PowerHandler workProvider) {
        if (level == null || level.isClientSide())
            return;

        addEnergy(powerHandler.useEnergy(10, (long) maxEnergyReceived(), true) * 0.95F);
    }

    public void addEnergy(float addition) {
        energy += addition;

        // 4.2.2 exploded here once the stage reached OVERHEAT; the overheat timer (updateOverheatTimer) decides now.

        if (energy > getMaxEnergy())
            energy = getMaxEnergy();
    }

    public float extractEnergy(float min, float max, boolean doExtract) {
        if (energy < min)
            return 0;

        float actualMax;

        if (max > maxEnergyExtracted())
            actualMax = maxEnergyExtracted();
        else
            actualMax = max;

        if (actualMax < min)
            return 0;

        float extracted;

        if (energy >= actualMax) {
            extracted = actualMax;
            if (doExtract)
                energy -= actualMax;
        } else {
            extracted = energy;
            if (doExtract)
                energy = 0;
        }

        return extracted;
    }

    /**
     * 4.2.2 accepted only an IPowerReceptor; a block with a Forge Energy handler on that side now counts too (plan
     * section 7). An engine that emits towards this one is not a target: engines facing each other no longer pass
     * power back and forth (BUGFIXES, BuildCraft#1827), the same rule PowerEnergyHandler applies to Forge Energy.
     */
    public boolean isPoweredTile(@Nullable BlockEntity tile, Direction side) {
        if (tile instanceof IPowerEmitter emitter && emitter.canEmitPowerFrom(side.getOpposite()))
            return false;

        if (tile instanceof IPowerReceptor)
            return ((IPowerReceptor) tile).getPowerReceiver(side.getOpposite()) != null;

        return tile != null && getEnergyHandler(side) != null;
    }

    private @Nullable EnergyHandler getEnergyHandler(Direction side) {
        if (level == null)
            return null;
        BlockPos pos = worldPosition.relative(side);
        if (!level.isLoaded(pos))
            return null;
        return level.getCapability(Capabilities.Energy.BLOCK, pos, side.getOpposite());
    }

    /** FE. */
    public abstract float getMaxEnergy();

    /** FE per call (4.2.2: 2 MJ). */
    public float minEnergyReceived() {
        return 20;
    }

    /** FE per call. */
    public abstract float maxEnergyReceived();

    /** FE per pulse. */
    public abstract float maxEnergyExtracted();

    public abstract float explosionRange();

    public float getEnergyStored() {
        return energy;
    }

    /** FE per tick. */
    public abstract float getCurrentOutput();

    // SEAM(#41): IOverrideDefaultTriggers.getTriggers, the four engine heat triggers.

    @Override
    public ConnectOverride overridePipeConnection(PipeType type, Direction with) {
        if (type == PipeType.POWER)
            return ConnectOverride.DEFAULT;
        if (with == getOrientation())
            return ConnectOverride.DISCONNECT;
        return ConnectOverride.DEFAULT;
    }

    @Override
    public boolean canEmitPowerFrom(Direction side) {
        return side == getOrientation();
    }

    public void checkRedstonePower() {
        if (level != null)
            isRedstonePowered = level.hasNeighborSignal(worldPosition);
    }

    private boolean isSimulating() {
        return level != null && !level.isClientSide();
    }

    // Status panel (plan section 10)

    /** Overheating (timer running or shut down), running on a redstone signal, or idle. Fuelled engines add OUT_OF_FUEL. */
    @Override
    public MachineStatus getStatusState() {
        if (energyStage == EnergyStage.OVERHEAT || overheatTimer >= 0)
            return MachineStatus.OVERHEATING;
        if (isRedstonePowered && isActive())
            return MachineStatus.WORKING;
        return MachineStatus.IDLE;
    }

    @Override
    public StatusReport.Power getStatusPower() {
        return StatusReport.Power.produced(meter, (long) energy, (long) getMaxEnergy());
    }

    /**
     * Why a running engine delivers nothing: no power receiver on its output face, or a receiver whose minimum
     * intake is more than this engine can send in one pulse (4.2.2 rule kept: a redstone engine cannot feed a quarry).
     */
    @Override
    public StatusReason getStatusReason() {
        if (!isRedstonePowered || !isActive() || energyStage == EnergyStage.OVERHEAT)
            return StatusReason.NONE;
        Direction orientation = getOrientation();
        BlockEntity tile = getTileBuffer(orientation).getTile();
        if (!isPoweredTile(tile, orientation))
            return StatusReason.NO_POWER_TARGET;
        if (tile instanceof IPowerReceptor receptor) {
            PowerReceiver receiver = receptor.getPowerReceiver(orientation.getOpposite());
            if (receiver != null && receiver.getMinEnergyReceived() > maxEnergyExtracted())
                return StatusReason.PULSE_TOO_SMALL;
        }
        return StatusReason.NONE;
    }

    /** The heat stage, and the seconds left on the overheat timer while it runs ("Red, N s"). */
    @Override
    public StatusReport.Heat getStatusHeat() {
        return new StatusReport.Heat(HeatStage.values()[energyStage.ordinal()],
                overheatTimer >= 0 ? (overheatTimer + 19) / 20 : StatusReport.Heat.NO_COUNTDOWN);
    }

    /** The meter the status panel reads, for tests. */
    public FlowMeter getMeter() {
        return meter;
    }
}
