/*
 * Copyright (c) SpaceToad, 2011-2012
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: TileEntity becomes BlockEntity; NBT becomes ValueInput/ValueOutput and the
 * fluid name is the registry id; the accepted fluid is part of the transaction snapshot.
 */
package buildcraftreforged.core.fluids;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;

/**
 *
 * @author CovertJaguar <http://www.railcraft.info/>
 */
public class SingleUseTank extends Tank {

    private @Nullable Fluid acceptedFluid;

    public SingleUseTank(String name, int capacity, @Nullable BlockEntity tile) {
        super(name, capacity, tile);
    }

    @Override
    public int fill(FluidStack resource, boolean doFill) {
        if (resource.isEmpty())
            return 0;
        if (doFill && acceptedFluid == null)
            acceptedFluid = resource.getFluid();
        if (acceptedFluid == null || acceptedFluid == resource.getFluid())
            return super.fill(resource, doFill);
        return 0;
    }

    public void reset() {
        acceptedFluid = null;
    }

    public void setAcceptedFluid(@Nullable Fluid fluid) {
        this.acceptedFluid = fluid;
    }

    public @Nullable Fluid getAcceptedFluid() {
        return acceptedFluid;
    }

    @Override
    public void writeTankToNBT(ValueOutput nbt) {
        super.writeTankToNBT(nbt);
        if (acceptedFluid != null)
            nbt.putString("acceptedFluid", FluidUtils.getFluidName(acceptedFluid));
    }

    @Override
    public void readTankFromNBT(ValueInput nbt) {
        super.readTankFromNBT(nbt);
        acceptedFluid = FluidUtils.getFluid(nbt.getStringOr("acceptedFluid", ""));
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return super.isValid(index, resource) && (acceptedFluid == null || acceptedFluid == resource.getFluid());
    }

    private record Snapshot(Object tank, @Nullable Fluid acceptedFluid) {
    }

    @Override
    protected Object createSnapshot() {
        return new Snapshot(super.createSnapshot(), acceptedFluid);
    }

    @Override
    protected void revertToSnapshot(Object snapshot) {
        Snapshot s = (Snapshot) snapshot;
        super.revertToSnapshot(s.tank());
        acceptedFluid = s.acceptedFluid();
    }
}
