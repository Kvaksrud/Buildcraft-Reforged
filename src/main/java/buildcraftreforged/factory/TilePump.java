/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: TileEntity becomes BlockEntity; IFluidHandler becomes a drain-only
 * ResourceHandler<FluidResource> over the tank (Capabilities.Fluid.BLOCK), with the 4.2.2 fill/drain methods kept;
 * energy is FE (MJ x 10) through PowerEnergyHandler; the tube is no longer a spawned entity but an EntityBlock held by
 * the tile and drawn by RenderPump; the stream payload becomes writePacketPayload; NBT becomes ValueInput/ValueOutput;
 * block ids become BlockState; the dimension list takes the level; the status panel (plan section 10) is new.
 */
package buildcraftreforged.factory;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.api.core.SafeTimeTracker;
import buildcraftreforged.api.gates.IAction;
import buildcraftreforged.api.power.IPowerReceptor;
import buildcraftreforged.api.power.PowerHandler;
import buildcraftreforged.api.power.PowerHandler.PowerReceiver;
import buildcraftreforged.api.power.PowerHandler.Type;
import buildcraftreforged.core.BlockIndex;
import buildcraftreforged.core.BuildCraftConfiguration;
import buildcraftreforged.core.CoreConstants;
import buildcraftreforged.core.EntityBlock;
import buildcraftreforged.core.IMachine;
import buildcraftreforged.core.TileBuffer;
import buildcraftreforged.core.TileBuildCraft;
import buildcraftreforged.core.fluids.FluidUtils;
import buildcraftreforged.core.fluids.SingleUseTank;
import buildcraftreforged.core.status.FlowMeter;
import buildcraftreforged.core.status.IStatusProvider;
import buildcraftreforged.core.status.MachineStatus;
import buildcraftreforged.core.status.StatusReason;
import buildcraftreforged.core.status.StatusReport;
import buildcraftreforged.core.utils.BlockUtil;
import buildcraftreforged.core.utils.Utils;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

public class TilePump extends TileBuildCraft implements IMachine, IPowerReceptor, ResourceHandler<FluidResource>,
        IStatusProvider {

    /** 4.2.2's FactoryProxyClient.pumpTexture, "buildcraft:pump_tube". */
    public static final Identifier TUBE_TEXTURE =
            Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "textures/block/pump_tube.png");

    public static int REBUID_DELAY = 512;
    public static int MAX_LIQUID = FluidType.BUCKET_VOLUME * 16;
    @Nullable EntityBlock tube;
    private TreeMap<Integer, Deque<BlockIndex>> pumpLayerQueues = new TreeMap<Integer, Deque<BlockIndex>>();
    SingleUseTank tank = new SingleUseTank("tank", MAX_LIQUID, this);
    double tubeY = Double.NaN;
    int aimY = 0;
    private PowerHandler powerHandler;
    private TileBuffer @Nullable [] tileBuffer = null;
    private SafeTimeTracker timer = new SafeTimeTracker();
    private int tick = Utils.RANDOM.nextInt();
    private int numFluidBlocksFound = 0;
    private boolean powered = false;
    /** Not in 4.2.2: the next block's fluid did not fit in the tank (status panel, Machine trigger). */
    private boolean isBlocked;
    /** Not in 4.2.2: the status panel's Flow and Power lines. */
    public final FlowMeter flowMeter = new FlowMeter(() -> level == null ? 0 : level.getGameTime());

    public TilePump(BlockPos pos, BlockState state) {
        super(FactoryRegistry.pumpTile.get(), pos, state);
        powerHandler = new PowerHandler(this, Type.MACHINE);
        initPowerProvider();
    }

    /** 4.2.2 configure(1, 15, 10, 100) and configurePowerPerdition(1, 100), in FE. */
    private void initPowerProvider() {
        powerHandler.configure(10, 150, 100, 1000);
        powerHandler.configurePowerPerdition(10, 100);
    }

    @Override
    public void updateEntity() {
        super.updateEntity();

        if (level == null)
            return;

        if (powered) {
            pumpLayerQueues.clear();
            destroyTube();
        } else
            createTube();

        if (level.isClientSide())
            return;

        pushToConsumers();

        if (powered)
            return;

        if (tube == null)
            return;

        if (tube.posY - aimY > 0.01) {
            tubeY = tube.posY - 0.01;
            setTubePosition();
            sendNetworkUpdate();
            return;
        }

        tick++;
        if (tick % 16 != 0)
            return;

        BlockIndex index = getNextIndexToPump(false);

        FluidStack fluidToPump = index != null ? BlockUtil.drainBlock(level, index.x, index.y, index.z, false) : null;
        isBlocked = false;
        if (fluidToPump != null) {
            if (isFluidAllowed(fluidToPump.getFluid()) && tank.fill(fluidToPump, false) == fluidToPump.getAmount()) {

                if (powerHandler.useEnergy(100, 100, true) == 100) {
                    flowMeter.energyIn(100);

                    if (fluidToPump.getFluid() != Fluids.WATER || BuildCraftConfiguration.consumeWaterSources.get() || numFluidBlocksFound < 9) {
                        index = Objects.requireNonNull(getNextIndexToPump(true));
                        BlockUtil.drainBlock(level, index.x, index.y, index.z, true);
                    }

                    tank.fill(fluidToPump, true);
                    flowMeter.fluidIn(fluidToPump.getAmount());
                }
            } else if (isFluidAllowed(fluidToPump.getFluid())) {
                isBlocked = true;
            }
        } else {
            if (tick % 128 == 0) {
                // TODO: improve that decision

                rebuildQueue();

                if (getNextIndexToPump(false) == null) {
                    for (int y = worldPosition.getY() - 1; y > level.getMinY(); --y) {
                        if (isPumpableFluid(worldPosition.getX(), y, worldPosition.getZ())) {
                            aimY = y;
                            return;
                        } else if (isBlocked(worldPosition.getX(), y, worldPosition.getZ())) {
                            return;
                        }
                    }
                }
            }
        }
    }

    public void onNeighborBlockChange() {
        if (level == null)
            return;
        boolean p = level.hasNeighborSignal(worldPosition);
        if (powered != p) {
            powered = p;
            if (!level.isClientSide())
                sendNetworkUpdate();
        }
    }

    private boolean isBlocked(int x, int y, int z) {
        // Material.blocksMovement: the legacy solid flag that replaced it.
        return level.getBlockState(new BlockPos(x, y, z)).isSolid();
    }

    private void pushToConsumers() {
        if (tileBuffer == null)
            tileBuffer = TileBuffer.makeBuffer(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), false);
        int before = tank.getFluidAmount();
        FluidUtils.pushFluidToConsumers(tank, 400, tileBuffer);
        if (tank.getFluidAmount() < before)
            flowMeter.fluidOut(before - tank.getFluidAmount());
    }

    private @Nullable BlockEntity getTile(Direction side) {
        if (tileBuffer == null)
            tileBuffer = TileBuffer.makeBuffer(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), false);
        return tileBuffer[side.ordinal()].getTile();
    }

    /** 4.2.2's FactoryProxy.newPumpTube, with the client proxy's texture. */
    private EntityBlock newPumpTube(Level w) {
        EntityBlock eb = new EntityBlock(w);
        eb.texture = TUBE_TEXTURE;
        return eb;
    }

    private void createTube() {
        if (tube == null) {
            tube = newPumpTube(level);

            if (!Double.isNaN(tubeY)) {
                tube.posY = tubeY;
            } else {
                tube.posY = worldPosition.getY();
            }

            tubeY = tube.posY;

            if (aimY == 0) {
                aimY = worldPosition.getY();
            }

            setTubePosition();

            // 4.2.2 spawned the tube entity here; RenderPump draws it from the tile now.

            if (!level.isClientSide())
                sendNetworkUpdate();
        }
    }

    private void destroyTube() {
        if (tube != null) {
            tube.setDead();
            tube = null;
            tubeY = Double.NaN;
            aimY = 0;
        }
    }

    private @Nullable BlockIndex getNextIndexToPump(boolean remove) {
        if (pumpLayerQueues.isEmpty()) {
            if (timer.markTimeIfDelay(level, REBUID_DELAY)) {
                rebuildQueue();
            }
            return null;
        }

        Deque<BlockIndex> topLayer = pumpLayerQueues.lastEntry().getValue();

        if (topLayer != null) {
            if (topLayer.isEmpty())
                pumpLayerQueues.pollLastEntry();
            if (remove) {
                BlockIndex index = topLayer.pollLast();
                return index;
            }
            return topLayer.peekLast();
        }

        return null;
    }

    private Deque<BlockIndex> getLayerQueue(int layer) {
        Deque<BlockIndex> pumpQueue = pumpLayerQueues.get(layer);
        if (pumpQueue == null) {
            pumpQueue = new LinkedList<BlockIndex>();
            pumpLayerQueues.put(layer, pumpQueue);
        }
        return pumpQueue;
    }

    public void rebuildQueue() {
        numFluidBlocksFound = 0;
        pumpLayerQueues.clear();
        if (level == null)
            return;
        int x = worldPosition.getX();
        int y = aimY;
        int z = worldPosition.getZ();
        Fluid pumpingFluid = BlockUtil.getFluid(level.getBlockState(new BlockPos(x, y, z)));
        if (pumpingFluid == null)
            return;

        if (pumpingFluid != tank.getAcceptedFluid() && tank.getAcceptedFluid() != null)
            return;

        Set<BlockIndex> visitedBlocks = new HashSet<BlockIndex>();
        Deque<BlockIndex> fluidsFound = new LinkedList<BlockIndex>();

        queueForPumping(x, y, z, visitedBlocks, fluidsFound, pumpingFluid);

//        long timeoutTime = System.nanoTime() + 10000;

        while (!fluidsFound.isEmpty()) {
            Deque<BlockIndex> fluidsToExpand = fluidsFound;
            fluidsFound = new LinkedList<BlockIndex>();

            for (BlockIndex index : fluidsToExpand) {
                queueForPumping(index.x, index.y + 1, index.z, visitedBlocks, fluidsFound, pumpingFluid);
                queueForPumping(index.x + 1, index.y, index.z, visitedBlocks, fluidsFound, pumpingFluid);
                queueForPumping(index.x - 1, index.y, index.z, visitedBlocks, fluidsFound, pumpingFluid);
                queueForPumping(index.x, index.y, index.z + 1, visitedBlocks, fluidsFound, pumpingFluid);
                queueForPumping(index.x, index.y, index.z - 1, visitedBlocks, fluidsFound, pumpingFluid);

                if (pumpingFluid == Fluids.WATER && !BuildCraftConfiguration.consumeWaterSources.get() && numFluidBlocksFound >= 9)
                    return;

//                if (System.nanoTime() > timeoutTime)
//                    return;
            }
        }
    }

    public void queueForPumping(int x, int y, int z, Set<BlockIndex> visitedBlocks, Deque<BlockIndex> fluidsFound, Fluid pumpingFluid) {
        BlockIndex index = new BlockIndex(x, y, z);
        if (visitedBlocks.add(index)) {
            if ((x - worldPosition.getX()) * (x - worldPosition.getX()) + (z - worldPosition.getZ()) * (z - worldPosition.getZ()) > 64 * 64)
                return;

            BlockState state = level.getBlockState(new BlockPos(x, y, z));
            if (BlockUtil.getFluid(state) == pumpingFluid) {
                fluidsFound.add(index);
            }
            if (canDrainBlock(state, x, y, z, pumpingFluid)) {
                getLayerQueue(y).add(index);
                numFluidBlocksFound++;
            }
        }
    }

    private boolean isPumpableFluid(int x, int y, int z) {
        Fluid fluid = BlockUtil.getFluid(level.getBlockState(new BlockPos(x, y, z)));
        if (fluid == null)
            return false;
        if (!isFluidAllowed(fluid))
            return false;
        if (tank.getAcceptedFluid() != null && tank.getAcceptedFluid() != fluid)
            return false;
        return true;
    }

    private boolean canDrainBlock(BlockState state, int x, int y, int z, Fluid fluid) {
        if (!isFluidAllowed(fluid))
            return false;

        FluidStack fluidStack = BlockUtil.drainBlock(state, level, x, y, z, false);
        if (fluidStack == null || fluidStack.getAmount() <= 0)
            return false;

        return fluidStack.getFluid() == fluid;
    }

    private boolean isFluidAllowed(Fluid fluid) {
        return PumpDimensionList.get().isFluidAllowed(fluid, level);
    }

    @Override
    protected void loadAdditional(ValueInput data) {
        super.loadAdditional(data);

        powerHandler.readFromNBT(data);
        tank.readFromNBT(data);

        powered = data.getBooleanOr("powered", false);

        aimY = data.getIntOr("aimY", 0);
        tubeY = data.getFloatOr("tubeY", worldPosition.getY());

        initPowerProvider();
    }

    @Override
    protected void saveAdditional(ValueOutput data) {
        super.saveAdditional(data);

        powerHandler.writeToNBT(data);
        tank.writeToNBT(data);

        data.putBoolean("powered", powered);

        data.putInt("aimY", aimY);

        if (tube != null) {
            data.putFloat("tubeY", (float) tube.posY);
        } else {
            data.putFloat("tubeY", worldPosition.getY());
        }
    }

    @Override
    public boolean isActive() {
        BlockIndex next = getNextIndexToPump(false);

        if (next != null) {
            return isPumpableFluid(next.x, next.y, next.z);
        }

        return false;
    }

    @Override
    public boolean isBlocked() {
        return isBlocked;
    }

    @Override
    public PowerReceiver getPowerReceiver(@Nullable Direction side) {
        return powerHandler.getPowerReceiver();
    }

    @Override
    public void doWork(PowerHandler workProvider) {
    }

    @Override
    public void writePacketPayload(RegistryFriendlyByteBuf data) {
        data.writeInt(aimY);
        data.writeFloat((float) tubeY);
        data.writeBoolean(powered);
    }

    @Override
    public void readPacketPayload(RegistryFriendlyByteBuf data) {
        aimY = data.readInt();
        tubeY = data.readFloat();
        powered = data.readBoolean();

        setTubePosition();
    }

    private void setTubePosition() {
        if (tube != null) {
            tube.iSize = CoreConstants.PIPE_MAX_POS - CoreConstants.PIPE_MIN_POS;
            tube.kSize = CoreConstants.PIPE_MAX_POS - CoreConstants.PIPE_MIN_POS;
            tube.jSize = worldPosition.getY() - tube.posY;

            tube.setPosition(worldPosition.getX() + CoreConstants.PIPE_MIN_POS, tubeY, worldPosition.getZ() + CoreConstants.PIPE_MIN_POS);
        }
    }

    /** The tube RenderPump draws, or null while the pump is switched off by redstone. Client side. */
    public @Nullable EntityBlock getTube() {
        return tube;
    }

    /** The y the tube is moving to (4.2.2 aimY). */
    public int getAimY() {
        return aimY;
    }

    /** Where the tube's end is (4.2.2 tubeY), NaN without a tube. */
    public double getTubeY() {
        return tubeY;
    }

    public boolean isPowered() {
        return powered;
    }

    public SingleUseTank getTank() {
        return tank;
    }

    /** 1.6.4 invalidate called destroy; setRemoved is its counterpart (chunk unload included). */
    @Override
    public void setRemoved() {
        super.setRemoved();
        destroy();
    }

    @Override
    public void clearRemoved() {
        tileBuffer = null;
        super.clearRemoved();
    }

    @Override
    public void destroy() {
        tileBuffer = null;
        pumpLayerQueues.clear();
        destroyTube();
    }

    @Override
    public boolean manageFluids() {
        return true;
    }

    @Override
    public boolean manageSolids() {
        return false;
    }

    @Override
    public boolean allowAction(IAction action) {
        return false;
    }

    // IFluidHandler implementation.
    public int fill(@Nullable Direction from, FluidStack resource, boolean doFill) {
        // not acceptable
        return 0;
    }

    public FluidStack drain(@Nullable Direction from, int maxDrain, boolean doDrain) {
        return tank.drain(maxDrain, doDrain);
    }

    public FluidStack drain(@Nullable Direction from, FluidStack resource, boolean doDrain) {
        if (resource.isEmpty())
            return FluidStack.EMPTY;
        if (!FluidStack.isSameFluidSameComponents(resource, tank.getFluid()))
            return FluidStack.EMPTY;
        return drain(from, resource.getAmount(), doDrain);
    }

    public boolean canFill(@Nullable Direction from, Fluid fluid) {
        return false;
    }

    public boolean canDrain(@Nullable Direction from, Fluid fluid) {
        return true;
    }

    // ResourceHandler: the one tank, drained from any side and never filled from outside (4.2.2 fill returned 0).

    @Override
    public int size() {
        return 1;
    }

    @Override
    public FluidResource getResource(int index) {
        return tank.getResource(index);
    }

    @Override
    public long getAmountAsLong(int index) {
        return tank.getAmountAsLong(index);
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        return tank.getCapacityAsLong(index, resource);
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return false;
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        Objects.checkIndex(index, size());
        return 0;
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        return tank.extract(index, resource, amount, transaction);
    }

    // Status panel (plan section 10), not in 4.2.2.

    @Override
    public MachineStatus getStatusState() {
        if (isBlocked)
            return MachineStatus.BLOCKED;
        if (powered || !isActive())
            return MachineStatus.IDLE;
        if (powerHandler.getEnergyStored() < 100)
            return MachineStatus.NO_POWER;
        return MachineStatus.WORKING;
    }

    @Override
    public StatusReason getStatusReason() {
        return isBlocked ? StatusReason.OUTPUT_FULL : StatusReason.NONE;
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
