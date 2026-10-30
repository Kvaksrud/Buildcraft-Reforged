/*
 * Copyright (c) SpaceToad, 2011-2012
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: Forge's FluidTank is gone, so its fields and its fill/drain/save logic live
 * here; the tank is a one-slot NeoForge ResourceHandler<FluidResource> whose transactions roll back through
 * SnapshotJournal; NBT becomes ValueInput/ValueOutput; the tooltip waits for the GUI port (#25).
 */
package buildcraftreforged.core.fluids;

import java.util.Objects;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 *
 * @author CovertJaguar <http://www.railcraft.info/>
 */
public class Tank extends SnapshotJournal<Object> implements ResourceHandler<FluidResource> {

    private final String name;
    public int colorRenderCache = 0xFFFFFF;

    // Forge 1.6.4 FluidTank's fields. An empty tank holds FluidStack.EMPTY where FluidTank held null.
    protected FluidStack fluid = FluidStack.EMPTY;
    protected int capacity;
    protected @Nullable BlockEntity tile;

    public Tank(String name, int capacity, @Nullable BlockEntity tile) {
        this.capacity = capacity;
        this.name = name;
        this.tile = tile;
    }

    public boolean isEmpty() {
        return getFluid().isEmpty() || getFluid().getAmount() <= 0;
    }

    public boolean isFull() {
        return !getFluid().isEmpty() && getFluid().getAmount() >= getCapacity();
    }

    public @Nullable Fluid getFluidType() {
        return !getFluid().isEmpty() ? getFluid().getFluid() : null;
    }

    public String getName() {
        return name;
    }

    // FluidTank

    public FluidStack getFluid() {
        return fluid;
    }

    public void setFluid(FluidStack fluid) {
        this.fluid = fluid;
    }

    public int getFluidAmount() {
        return fluid.getAmount();
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    /** FluidTank.fill: returns the amount filled, or that would be filled when doFill is false. */
    public int fill(FluidStack resource, boolean doFill) {
        if (resource.isEmpty())
            return 0;

        if (!doFill) {
            if (fluid.isEmpty())
                return Math.min(capacity, resource.getAmount());
            if (!FluidStack.isSameFluidSameComponents(fluid, resource))
                return 0;
            return Math.min(capacity - fluid.getAmount(), resource.getAmount());
        }

        if (fluid.isEmpty()) {
            fluid = resource.copyWithAmount(Math.min(capacity, resource.getAmount()));
            onContentsChanged();
            return fluid.getAmount();
        }

        if (!FluidStack.isSameFluidSameComponents(fluid, resource))
            return 0;

        int filled = capacity - fluid.getAmount();
        if (resource.getAmount() < filled) {
            fluid.grow(resource.getAmount());
            filled = resource.getAmount();
        } else {
            fluid.setAmount(capacity);
        }
        if (filled > 0)
            onContentsChanged();
        return filled;
    }

    /** FluidTank.drain: returns what was drained, or would be when doDrain is false; EMPTY for nothing. */
    public FluidStack drain(int maxDrain, boolean doDrain) {
        if (fluid.isEmpty())
            return FluidStack.EMPTY;

        int drained = maxDrain;
        if (fluid.getAmount() < drained)
            drained = fluid.getAmount();

        FluidStack stack = fluid.copyWithAmount(drained);
        if (doDrain && drained > 0) {
            fluid.shrink(drained);
            if (fluid.getAmount() <= 0)
                fluid = FluidStack.EMPTY;
            onContentsChanged();
        }
        return stack;
    }

    /**
     * 1.6.4's FluidTank posted a FluidEvent naming the tile; now the tile is marked changed so the chunk saves it.
     * Inside a transaction this waits for the root commit.
     */
    protected void onContentsChanged() {
        if (!isInTransaction() && tile != null)
            tile.setChanged();
    }

    // NBT. The tank writes into a compound named after it, with Forge's FluidTank tag names inside.

    public final ValueOutput writeToNBT(ValueOutput nbt) {
        ValueOutput tankData = nbt.child(name);
        if (!fluid.isEmpty()) {
            tankData.putString("FluidName", FluidUtils.getFluidName(fluid.getFluid()));
            tankData.putInt("Amount", fluid.getAmount());
            if (!fluid.isComponentsPatchEmpty())
                tankData.store("Tag", DataComponentPatch.CODEC, fluid.getComponentsPatch());
        } else {
            tankData.putString("Empty", "");
        }
        writeTankToNBT(tankData);
        return nbt;
    }

    public final Tank readFromNBT(ValueInput nbt) {
        nbt.child(name).ifPresent(tankData -> {
            if (tankData.getString("Empty").isEmpty()) {
                FluidStack loaded = loadFluidStackFromNBT(tankData);
                if (!loaded.isEmpty())
                    setFluid(loaded);
            }
            readTankFromNBT(tankData);
        });
        return this;
    }

    private static FluidStack loadFluidStackFromNBT(ValueInput nbt) {
        Fluid loaded = FluidUtils.getFluid(nbt.getStringOr("FluidName", ""));
        int amount = nbt.getIntOr("Amount", 0);
        if (loaded == null || loaded == Fluids.EMPTY || amount <= 0)
            return FluidStack.EMPTY;
        DataComponentPatch patch = nbt.read("Tag", DataComponentPatch.CODEC).orElse(DataComponentPatch.EMPTY);
        return new FluidStack(loaded, amount, patch);
    }

    public void writeTankToNBT(ValueOutput nbt) {
    }

    public void readTankFromNBT(ValueInput nbt) {
    }

    // SEAM(#25): getToolTip/refreshTooltip and the ToolTip field come back with core/gui/tooltips.

    // ResourceHandler: one slot, mapped onto fill and drain.

    @Override
    public int size() {
        return 1;
    }

    @Override
    public FluidResource getResource(int index) {
        Objects.checkIndex(index, 1);
        return FluidResource.of(fluid);
    }

    @Override
    public long getAmountAsLong(int index) {
        Objects.checkIndex(index, 1);
        return fluid.getAmount();
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        Objects.checkIndex(index, 1);
        return capacity;
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        Objects.checkIndex(index, 1);
        return true;
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        Objects.checkIndex(index, 1);
        FluidStack stack = resource.toStack(amount);
        int filled = fill(stack, false);
        if (filled > 0) {
            updateSnapshots(transaction);
            fill(stack, true);
        }
        return filled;
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        Objects.checkIndex(index, 1);
        if (fluid.isEmpty() || !resource.matches(fluid))
            return 0;
        int drained = drain(amount, false).getAmount();
        if (drained > 0) {
            updateSnapshots(transaction);
            drain(amount, true);
        }
        return drained;
    }

    @Override
    protected Object createSnapshot() {
        return fluid.copy();
    }

    @Override
    protected void revertToSnapshot(Object snapshot) {
        fluid = (FluidStack) snapshot;
    }

    @Override
    protected void onRootCommit(Object originalState) {
        if (tile != null)
            tile.setChanged();
    }
}
