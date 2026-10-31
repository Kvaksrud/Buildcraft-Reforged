/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: TileEntity becomes BlockEntity; IFluidHandler becomes a fill-only
 * ResourceHandler<FluidResource> over the tank (Capabilities.Fluid.BLOCK), with the 4.2.2 fill/drain methods kept;
 * block ids become BlockState and the fluid block is FluidUtils.getFluidBlock; canBePlacedInWorld becomes
 * FluidType.canBePlacedInLevel; dimension -1 becomes Level.NETHER; NBT becomes ValueInput/ValueOutput; positions
 * outside the build height are not queued (BuildCraft#2165, docs/BUGFIXES.md); the status panel (plan section 10) is
 * new.
 */
package buildcraftreforged.factory;

import buildcraftreforged.core.BlockIndex;
import buildcraftreforged.core.TileBuildCraft;
import buildcraftreforged.core.fluids.FluidUtils;
import buildcraftreforged.core.fluids.Tank;
import buildcraftreforged.core.status.FlowMeter;
import buildcraftreforged.core.status.IStatusProvider;
import buildcraftreforged.core.status.MachineStatus;
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
import net.minecraft.world.level.Level;
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

public class TileFloodGate extends TileBuildCraft implements ResourceHandler<FluidResource>, IStatusProvider {

    public static final int[] REBUILD_DELAY = new int[8];
    public static final int MAX_LIQUID = FluidType.BUCKET_VOLUME * 2;
    private final TreeMap<Integer, Deque<BlockIndex>> pumpLayerQueues = new TreeMap<Integer, Deque<BlockIndex>>();
    private final Set<BlockIndex> visitedBlocks = new HashSet<BlockIndex>();
    private Deque<BlockIndex> fluidsFound = new LinkedList<BlockIndex>();
    private final Tank tank = new Tank("tank", MAX_LIQUID, this);
    private int rebuildDelay;
    private int tick = Utils.RANDOM.nextInt();
    private boolean powered = false;
    /** Not in 4.2.2: the status panel's Flow line, and the State line's "placed a block lately". */
    public final FlowMeter flowMeter = new FlowMeter(() -> level == null ? 0 : level.getGameTime());
    private long lastPlaced = Long.MIN_VALUE;

    static {
        REBUILD_DELAY[0] = 128;
        REBUILD_DELAY[1] = 256;
        REBUILD_DELAY[2] = 512;
        REBUILD_DELAY[3] = 1024;
        REBUILD_DELAY[4] = 2048;
        REBUILD_DELAY[5] = 4096;
        REBUILD_DELAY[6] = 8192;
        REBUILD_DELAY[7] = 16384;
    }

    public TileFloodGate(BlockPos pos, BlockState state) {
        super(FactoryRegistry.floodGateTile.get(), pos, state);
    }

    @Override
    public void updateEntity() {
        super.updateEntity();

        if (level == null || level.isClientSide())
            return;

        if (powered)
            return;

        tick++;
        if (tick % 16 == 0) {
            FluidStack fluidtoFill = tank.drain(FluidType.BUCKET_VOLUME, false);
            if (!fluidtoFill.isEmpty() && fluidtoFill.getAmount() == FluidType.BUCKET_VOLUME) {
                Fluid fluid = fluidtoFill.getFluid();
                if (fluid == Fluids.EMPTY || !fluid.getFluidType().canBePlacedInLevel(level, worldPosition, fluidtoFill))
                    return;

                if (fluid == Fluids.WATER && level.dimension() == Level.NETHER) {
                    tank.drain(FluidType.BUCKET_VOLUME, true);
                    return;
                }

                if (tick % REBUILD_DELAY[rebuildDelay] == 0) {
                    rebuildDelay++;
                    if (rebuildDelay >= REBUILD_DELAY.length)
                        rebuildDelay = REBUILD_DELAY.length - 1;
                    rebuildQueue();
                }
                BlockIndex index = getNextIndexToFill(true);

                if (index != null && placeFluid(index.x, index.y, index.z, fluid)) {
                    tank.drain(FluidType.BUCKET_VOLUME, true);
                    rebuildDelay = 0;
                    flowMeter.fluidOut(FluidType.BUCKET_VOLUME);
                    lastPlaced = level.getGameTime();
                }
            }
        }
    }

    private boolean placeFluid(int x, int y, int z, Fluid fluid) {
        BlockState state = level.getBlockState(new BlockPos(x, y, z));
        if (canPlaceFluidAt(state, x, y, z)) {
            boolean placed = level.setBlockAndUpdate(new BlockPos(x, y, z), FluidUtils.getFluidBlock(fluid, true));
            if (placed) {
                queueAdjacent(x, y, z);
                expandQueue();
            }
            return placed;
        }
        return false;
    }

    private @Nullable BlockIndex getNextIndexToFill(boolean remove) {
        if (pumpLayerQueues.isEmpty()) {
            return null;
        }

        Deque<BlockIndex> bottomLayer = pumpLayerQueues.firstEntry().getValue();

        if (bottomLayer != null) {
            if (bottomLayer.isEmpty())
                pumpLayerQueues.pollFirstEntry();
            if (remove) {
                BlockIndex index = bottomLayer.pollFirst();
                return index;
            }
            return bottomLayer.peekFirst();
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

    /**
     * Nasty expensive function, don't call if you don't have to.
     */
    void rebuildQueue() {
        pumpLayerQueues.clear();
        visitedBlocks.clear();
        fluidsFound.clear();

        queueAdjacent(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());

        expandQueue();
    }

    private void expandQueue() {
        if (tank.getFluidType() == null)
            return;
        while (!fluidsFound.isEmpty()) {
            Deque<BlockIndex> fluidsToExpand = fluidsFound;
            fluidsFound = new LinkedList<BlockIndex>();

            for (BlockIndex index : fluidsToExpand) {
                queueAdjacent(index.x, index.y, index.z);
            }
        }
    }

    public void queueAdjacent(int x, int y, int z) {
        if (tank.getFluidType() == null)
            return;
        queueForFilling(x, y - 1, z);
        queueForFilling(x + 1, y, z);
        queueForFilling(x - 1, y, z);
        queueForFilling(x, y, z + 1);
        queueForFilling(x, y, z - 1);
    }

    public void queueForFilling(int x, int y, int z) {
        // BuildCraft#2165: nothing below or above the world is queued.
        if (level == null || level.isOutsideBuildHeight(y))
            return;
        BlockIndex index = new BlockIndex(x, y, z);
        if (visitedBlocks.add(index)) {
            if ((x - worldPosition.getX()) * (x - worldPosition.getX()) + (z - worldPosition.getZ()) * (z - worldPosition.getZ()) > 64 * 64)
                return;

            BlockState state = level.getBlockState(new BlockPos(x, y, z));
            if (BlockUtil.getFluid(state) == tank.getFluidType()) {
                fluidsFound.add(index);
            }
            if (canPlaceFluidAt(state, x, y, z)) {
                getLayerQueue(y).addLast(index);
            }
        }
    }

    private boolean canPlaceFluidAt(BlockState state, int x, int y, int z) {
        return BlockUtil.isSoftBlock(state, level, x, y, z) && !BlockUtil.isFullFluidBlock(state, level, x, y, z);
    }

    public void onNeighborBlockChange() {
        if (level == null)
            return;
        boolean p = level.hasNeighborSignal(worldPosition);
        if (powered != p) {
            powered = p;
            if (!p)
                rebuildQueue();
        }
    }

    public Tank getTank() {
        return tank;
    }

    public boolean isPowered() {
        return powered;
    }

    @Override
    protected void loadAdditional(ValueInput data) {
        super.loadAdditional(data);
        tank.readFromNBT(data);
        rebuildDelay = Math.clamp(data.getByteOr("rebuildDelay", (byte) 0), 0, REBUILD_DELAY.length - 1);
        powered = data.getBooleanOr("powered", false);
    }

    @Override
    protected void saveAdditional(ValueOutput data) {
        super.saveAdditional(data);
        tank.writeToNBT(data);
        data.putByte("rebuildDelay", (byte) rebuildDelay);
        data.putBoolean("powered", powered);
    }

    /** 1.6.4 invalidate called destroy; setRemoved is its counterpart. */
    @Override
    public void setRemoved() {
        super.setRemoved();
        destroy();
    }

    @Override
    public void destroy() {
        pumpLayerQueues.clear();
    }

    // IFluidHandler implementation.
    public int fill(@Nullable Direction from, FluidStack resource, boolean doFill) {
        return tank.fill(resource, doFill);
    }

    public FluidStack drain(@Nullable Direction from, int maxDrain, boolean doDrain) {
        return FluidStack.EMPTY;
    }

    public FluidStack drain(@Nullable Direction from, FluidStack resource, boolean doDrain) {
        return FluidStack.EMPTY;
    }

    public boolean canFill(@Nullable Direction from, Fluid fluid) {
        return true;
    }

    public boolean canDrain(@Nullable Direction from, Fluid fluid) {
        return false;
    }

    // ResourceHandler: the one tank, filled from any side and never drained from outside (4.2.2 drain returned null).

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
        return tank.isValid(index, resource);
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        return tank.insert(index, resource, amount, transaction);
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        Objects.checkIndex(index, size());
        return 0;
    }

    // Status panel (plan section 10), not in 4.2.2. The gate has no power and no blocked state: Working while it has
    // placed fluid within the last rebuild interval, Idle otherwise.

    @Override
    public MachineStatus getStatusState() {
        if (powered || level == null || lastPlaced == Long.MIN_VALUE || level.getGameTime() - lastPlaced > REBUILD_DELAY[0])
            return MachineStatus.IDLE;
        return MachineStatus.WORKING;
    }

    @Override
    public FlowMeter getStatusFlow() {
        return flowMeter;
    }
}
