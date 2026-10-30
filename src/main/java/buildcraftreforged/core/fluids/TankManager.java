/*
 * Copyright (c) SpaceToad, 2011-2012
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: Forge's IFluidHandler becomes NeoForge's ResourceHandler<FluidResource>
 * (one index per tank, exposed through Capabilities.Fluid.BLOCK), with the 4.2.2 fill/drain methods kept for internal
 * callers; getTankInfo is gone with FluidTankInfo; NBT becomes ValueInput/ValueOutput; the network data is the
 * FluidStack codec, and the render colour is looked up on the client.
 */
package buildcraftreforged.core.fluids;

import buildcraftreforged.core.render.FluidRenderer;
import com.google.common.collect.ForwardingList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 *
 * @author CovertJaguar <http://www.railcraft.info/>
 */
public class TankManager<T extends Tank> extends ForwardingList<T> implements ResourceHandler<FluidResource>, List<T> {

    private List<T> tanks = new ArrayList<T>();

    public TankManager() {
    }

    @SafeVarargs
    public TankManager(T... tanks) {
        addAll(Arrays.asList(tanks));
    }

    @Override
    protected List<T> delegate() {
        return tanks;
    }

    // The 4.2.2 IFluidHandler methods, for machines that fill and drain their own tanks.

    public int fill(@Nullable Direction from, FluidStack resource, boolean doFill) {
        for (Tank tank : tanks) {
            int used = tank.fill(resource, doFill);
            if (used > 0)
                return used;
        }
        return 0;
    }

    public FluidStack drain(@Nullable Direction from, FluidStack resource, boolean doDrain) {
        if (resource.isEmpty())
            return FluidStack.EMPTY;
        for (Tank tank : tanks) {
            if (!FluidStack.isSameFluidSameComponents(resource, tank.getFluid()))
                continue;
            FluidStack drained = tank.drain(resource.getAmount(), doDrain);
            if (!drained.isEmpty() && drained.getAmount() > 0)
                return drained;
        }
        return FluidStack.EMPTY;
    }

    public FluidStack drain(@Nullable Direction from, int maxDrain, boolean doDrain) {
        for (Tank tank : tanks) {
            FluidStack drained = tank.drain(maxDrain, doDrain);
            if (!drained.isEmpty() && drained.getAmount() > 0)
                return drained;
        }
        return FluidStack.EMPTY;
    }

    public boolean canFill(@Nullable Direction from, Fluid fluid) {
        return true;
    }

    public boolean canDrain(@Nullable Direction from, Fluid fluid) {
        return true;
    }

    // ResourceHandler: index i is tank i. size() is the list's.

    @Override
    public FluidResource getResource(int index) {
        return get(index).getResource(0);
    }

    @Override
    public long getAmountAsLong(int index) {
        return get(index).getAmountAsLong(0);
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        return get(index).getCapacityAsLong(0, resource);
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return get(index).isValid(0, resource);
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        return get(index).insert(0, resource, amount, transaction);
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        return get(index).extract(0, resource, amount, transaction);
    }

    /** As fill above: the first tank that takes any of it takes all it can, and no other tank is tried. */
    @Override
    public int insert(FluidResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        for (Tank tank : tanks) {
            int used = tank.insert(0, resource, amount, transaction);
            if (used > 0)
                return used;
        }
        return 0;
    }

    /** As drain(FluidStack) above: the first tank holding the fluid that gives any of it. */
    @Override
    public int extract(FluidResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        for (Tank tank : tanks) {
            int drained = tank.extract(0, resource, amount, transaction);
            if (drained > 0)
                return drained;
        }
        return 0;
    }

    public void writeToNBT(ValueOutput data) {
        for (Tank tank : tanks) {
            tank.writeToNBT(data);
        }
    }

    public void readFromNBT(ValueInput data) {
        for (Tank tank : tanks) {
            tank.readFromNBT(data);
        }
    }

    public void writeData(RegistryFriendlyByteBuf data) {
        for (Tank tank : tanks) {
            FluidStack.OPTIONAL_STREAM_CODEC.encode(data, tank.getFluid());
        }
    }

    /**
     * Client side. 4.2.2 sent the colour from the server; fluid colours are client data now, so it is looked up
     * here.
     */
    public void readData(RegistryFriendlyByteBuf data) {
        for (Tank tank : tanks) {
            FluidStack fluidStack = FluidStack.OPTIONAL_STREAM_CODEC.decode(data);
            if (!fluidStack.isEmpty()) {
                tank.setFluid(fluidStack);
                tank.colorRenderCache = FluidRenderer.getColorForFluidStack(fluidStack);
            } else {
                tank.setFluid(FluidStack.EMPTY);
                tank.colorRenderCache = 0xFFFFFF;
            }
        }
    }
}
