/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: IFluidHandler becomes a one-slot ResourceHandler<FluidResource> over the
 * whole stack of tanks (Capabilities.Fluid.BLOCK), with the 4.2.2 fill/drain methods kept for internal callers;
 * getTankInfo's composite FluidTank is a detached Tank; the stream payload becomes writePacketPayload; NBT becomes
 * ValueInput/ValueOutput; the light level goes through the auxiliary light manager on both sides; comparator output
 * is new (plan section 6). Upstream fixes: canFill/canDrain (aa24a99d9) and drain(FluidStack) checking the bottom
 * tank (08edcf175), see docs/BUGFIXES.md.
 */
package buildcraftreforged.factory;

import buildcraftreforged.api.core.SafeTimeTracker;
import buildcraftreforged.core.BuildCraftConfiguration;
import buildcraftreforged.core.TileBuildCraft;
import buildcraftreforged.core.fluids.Tank;
import buildcraftreforged.core.fluids.TankManager;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.world.AuxiliaryLightManager;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

public class TileTank extends TileBuildCraft implements ResourceHandler<FluidResource> {

    public final Tank tank = new Tank("tank", FluidType.BUCKET_VOLUME * 16, this);
    public final TankManager<Tank> tankManager = new TankManager<>(tank);
    public boolean hasUpdate = false;
    public SafeTimeTracker tracker = new SafeTimeTracker();
    /** The last comparator output, to notify comparators when it changes. Not in 4.2.2. */
    private int cachedComparatorOverride = 0;

    public TileTank(BlockPos pos, BlockState state) {
        super(FactoryRegistry.tankTile.get(), pos, state);
    }

    /* UPDATING */
    @Override
    public void updateEntity() {
        super.updateEntity();

        // 4.2.2 relit only the client world here; the server's light engine needs it too now.
        updateFluidLight();

        if (level == null || level.isClientSide()) {
            return;
        }

        // Have liquid flow down into tanks below if any.
        if (!tank.getFluid().isEmpty()) {
            moveFluidBelow();
        }

        int comparator = calculateComparatorInputOverride();
        if (comparator != cachedComparatorOverride) {
            cachedComparatorOverride = comparator;
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }

        if (hasUpdate && tracker.markTimeIfDelay(level, 2L * BuildCraftConfiguration.updateFactor.get())) {
            sendNetworkUpdate();
            hasUpdate = false;
        }
    }

    /** 4.2.2 kept prevLightValue and called updateLightByType; the light manager holds the last value now. */
    private void updateFluidLight() {
        if (level == null)
            return;
        AuxiliaryLightManager lightManager = level.getAuxLightManager(worldPosition);
        int lightValue = getFluidLightLevel();
        if (lightManager != null && lightManager.getLightAt(worldPosition) != lightValue) {
            lightManager.setLightAt(worldPosition, lightValue);
            level.getLightEngine().checkBlock(worldPosition);
        }
    }

    /**
     * Every change to the tank marks the tile changed (Tank.onContentsChanged, or the root commit of a transaction),
     * which now also asks for a network update. 4.2.2 set hasUpdate by hand in fill and drain.
     */
    @Override
    public void setChanged() {
        hasUpdate = true;
        super.setChanged();
    }

    /* NETWORK */
    @Override
    public void writePacketPayload(RegistryFriendlyByteBuf data) {
        tankManager.writeData(data);
    }

    @Override
    public void readPacketPayload(RegistryFriendlyByteBuf data) {
        tankManager.readData(data);
    }

    /* SAVING & LOADING */
    @Override
    protected void loadAdditional(ValueInput data) {
        super.loadAdditional(data);
        tankManager.readFromNBT(data);
    }

    @Override
    protected void saveAdditional(ValueOutput data) {
        super.saveAdditional(data);
        tankManager.writeToNBT(data);
    }

    /* HELPER FUNCTIONS */
    /**
     * @return Last tank block below this one or this one if it is the last.
     */
    public TileTank getBottomTank() {

        TileTank lastTank = this;

        while (true) {
            TileTank below = getTankBelow(lastTank);
            if (below != null) {
                lastTank = below;
            } else {
                break;
            }
        }

        return lastTank;
    }

    public TileTank getTopTank() {

        TileTank lastTank = this;

        while (true) {
            TileTank above = getTankAbove(lastTank);
            if (above != null) {
                lastTank = above;
            } else {
                break;
            }
        }

        return lastTank;
    }

    public static @Nullable TileTank getTankBelow(TileTank tile) {
        BlockEntity below = tile.level == null ? null : tile.level.getBlockEntity(tile.worldPosition.below());
        if (below instanceof TileTank) {
            return (TileTank) below;
        } else {
            return null;
        }
    }

    public static @Nullable TileTank getTankAbove(TileTank tile) {
        BlockEntity above = tile.level == null ? null : tile.level.getBlockEntity(tile.worldPosition.above());
        if (above instanceof TileTank) {
            return (TileTank) above;
        } else {
            return null;
        }
    }

    public void moveFluidBelow() {
        TileTank below = getTankBelow(this);
        if (below == null) {
            return;
        }

        int used = below.tank.fill(tank.getFluid(), true);
        if (used > 0) {
            hasUpdate = true; // not redundant because tank.drain operates on an IFluidTank, not a tile
            below.hasUpdate = true; // redundant because below.fill sets hasUpdate

            tank.drain(used, true);
        }
    }

    /* ITANKCONTAINER */
    public int fill(@Nullable Direction from, FluidStack resource, boolean doFill) {
        if (resource.isEmpty()) {
            return 0;
        }

        resource = resource.copy();
        int totalUsed = 0;
        TileTank tankToFill = getBottomTank();

        FluidStack liquid = tankToFill.tank.getFluid();
        if (!liquid.isEmpty() && liquid.getAmount() > 0 && !FluidStack.isSameFluidSameComponents(liquid, resource)) {
            return 0;
        }

        while (tankToFill != null && resource.getAmount() > 0) {
            int used = tankToFill.tank.fill(resource, doFill);
            resource.shrink(used);
            if (used > 0 && doFill) {
                tankToFill.hasUpdate = true;
            }

            totalUsed += used;
            tankToFill = getTankAbove(tankToFill);
        }
        return totalUsed;
    }

    public FluidStack drain(@Nullable Direction from, int maxEmpty, boolean doDrain) {
        TileTank bottom = getBottomTank();
        bottom.hasUpdate = true;
        return bottom.tank.drain(maxEmpty, doDrain);
    }

    public FluidStack drain(@Nullable Direction from, FluidStack resource, boolean doDrain) {
        if (resource.isEmpty())
            return FluidStack.EMPTY;
        // Upstream 08edcf175: compare with the bottom tank, which is the one drained; 4.2.2 compared with this one.
        if (!FluidStack.isSameFluidSameComponents(resource, getBottomTank().tank.getFluid()))
            return FluidStack.EMPTY;
        return drain(from, resource.getAmount(), doDrain);
    }

    /**
     * 4.2.2 returned a FluidTankInfo of a composite FluidTank: the bottom tank's fluid plus every tank above holding the
     * same fluid, and the capacity of the tanks up to the first one holding another fluid. The composite is a detached
     * Tank now.
     */
    public Tank getTankInfo(@Nullable Direction direction) {
        Tank compositeTank = new Tank("tank", tank.getCapacity(), null);

        TileTank tile = getBottomTank();

        int capacity = tank.getCapacity();

        if (!tile.tank.getFluid().isEmpty()) {
            compositeTank.setFluid(tile.tank.getFluid().copy());
        } else {
            return compositeTank;
        }

        tile = getTankAbove(tile);

        while (tile != null) {

            FluidStack liquid = tile.tank.getFluid();
            if (liquid.isEmpty() || liquid.getAmount() == 0) {
                // NOOP
            } else if (!FluidStack.isSameFluidSameComponents(compositeTank.getFluid(), liquid)) {
                break;
            } else {
                compositeTank.getFluid().grow(liquid.getAmount());
            }

            capacity += tile.tank.getCapacity();
            tile = getTankAbove(tile);
        }

        compositeTank.setCapacity(capacity);
        return compositeTank;
    }

    /** Upstream aa24a99d9: 4.2.2 answered true for any fluid. */
    public boolean canFill(@Nullable Direction from, Fluid fluid) {
        Fluid tankFluid = getBottomTank().tank.getFluidType();
        return tankFluid == null || tankFluid == fluid;
    }

    /** Upstream aa24a99d9: 4.2.2 answered false, though drain worked. */
    public boolean canDrain(@Nullable Direction from, Fluid fluid) {
        Fluid tankFluid = getBottomTank().tank.getFluidType();
        return tankFluid != null && tankFluid == fluid;
    }

    public int getFluidLightLevel() {
        FluidStack tankFluid = tank.getFluid();
        return tankFluid.isEmpty() ? 0 : tankFluid.getFluidType().getLightLevel(tankFluid);
    }

    /**
     * Not in 4.2.2. BuildCraft 7.1's formula: the stack's composite fill level, 0 to 15, the same for every tank in
     * the stack.
     */
    public int calculateComparatorInputOverride() {
        Tank info = getTankInfo(null);
        if (!info.getFluid().isEmpty()) {
            return (int) ((long) info.getFluidAmount() * 15 / info.getCapacity());
        } else {
            return 0;
        }
    }

    // ResourceHandler: one slot, the composite tank of getTankInfo. Insert is 4.2.2 fill (bottom tank first, then
    // up), extract is 4.2.2 drain(FluidStack) (bottom tank only). Each Tank journals itself, so a transaction that
    // touches several tanks rolls back as one.

    @Override
    public int size() {
        return 1;
    }

    @Override
    public FluidResource getResource(int index) {
        Objects.checkIndex(index, 1);
        return FluidResource.of(getTankInfo(null).getFluid());
    }

    @Override
    public long getAmountAsLong(int index) {
        Objects.checkIndex(index, 1);
        return getTankInfo(null).getFluidAmount();
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        Objects.checkIndex(index, 1);
        return getTankInfo(null).getCapacity();
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        Objects.checkIndex(index, 1);
        return canFill(null, resource.getFluid());
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        Objects.checkIndex(index, 1);
        int totalUsed = 0;
        TileTank tankToFill = getBottomTank();

        FluidStack liquid = tankToFill.tank.getFluid();
        if (!liquid.isEmpty() && !resource.matches(liquid)) {
            return 0;
        }

        while (tankToFill != null && amount > 0) {
            int used = tankToFill.tank.insert(0, resource, amount, transaction);
            amount -= used;
            totalUsed += used;
            tankToFill = getTankAbove(tankToFill);
        }
        return totalUsed;
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        Objects.checkIndex(index, 1);
        TileTank bottom = getBottomTank();
        if (!resource.matches(bottom.tank.getFluid())) {
            return 0;
        }
        return bottom.tank.extract(0, resource, amount, transaction);
    }
}
