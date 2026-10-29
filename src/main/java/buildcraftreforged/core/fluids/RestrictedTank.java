/*
 * Copyright (c) SpaceToad, 2011-2012
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: TileEntity becomes BlockEntity; isValid reports the accepted fluids to the
 * NeoForge transfer API.
 */
package buildcraftreforged.core.fluids;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;

/**
 *
 * @author CovertJaguar <http://www.railcraft.info/>
 */
public class RestrictedTank extends Tank {

    private final Fluid[] acceptedFluids;

    public RestrictedTank(String name, int capacity, @Nullable BlockEntity tile, Fluid... acceptedFluids) {
        super(name, capacity, tile);
        this.acceptedFluids = acceptedFluids;
    }

    @Override
    public int fill(FluidStack resource, boolean doFill) {
        if (!acceptsFluid(resource.getFluid()))
            return 0;
        return super.fill(resource, doFill);
    }

    public boolean acceptsFluid(Fluid fluid) {
        for (Fluid accepted : acceptedFluids) {
            if (accepted.equals(fluid))
                return true;
        }
        return false;
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return super.isValid(index, resource) && acceptsFluid(resource.getFluid());
    }
}
