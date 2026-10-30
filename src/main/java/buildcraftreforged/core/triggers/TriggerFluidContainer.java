/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: IFluidHandler.getTankInfo and fill(simulate) read the fluid handler
 * capability, FluidContainerRegistry becomes FluidUtil; legacy id gone; BlockEntity and Direction in place of
 * TileEntity and ForgeDirection.
 */
package buildcraftreforged.core.triggers;

import buildcraftreforged.api.gates.ITriggerParameter;
import java.util.Locale;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

public class TriggerFluidContainer extends BCTrigger {

    public enum State {

        Empty, Contains, Space, Full
    };
    public State state;

    public TriggerFluidContainer(State state) {
        super("buildcraft.fluid." + state.name().toLowerCase(Locale.ENGLISH));
        this.state = state;
    }

    @Override
    public boolean hasParameter() {
        if (state == State.Contains || state == State.Space)
            return true;
        else
            return false;
    }

    @Override
    public String getDescription() {
        switch (state) {
            case Empty:
                return "Tank Empty";
            case Contains:
                return "Fluid in Tank";
            case Space:
                return "Space for Fluid";
            default:
                return "Tank Full";
        }
    }

    @Override
    public boolean isTriggerActive(@Nullable Direction side, BlockEntity tile, @Nullable ITriggerParameter parameter) {
        ResourceHandler<FluidResource> container = getCapability(Capabilities.Fluid.BLOCK, tile, side);
        if (container != null) {
            FluidResource searchedFluid = FluidResource.EMPTY;

            if (parameter != null && !parameter.getItemStack().isEmpty()) {
                searchedFluid = FluidResource.of(FluidUtil.getFirstStackContained(parameter.getItemStack()));
            }

            // One handler index per 4.2.2 FluidTankInfo.
            int liquids = container.size();
            if (liquids == 0)
                return false;

            switch (state) {
                case Empty:
                    for (int c = 0; c < liquids; c++) {
                        if (container.getAmountAsLong(c) > 0 && (searchedFluid.isEmpty() || searchedFluid.equals(container.getResource(c))))
                            return false;
                    }
                    return true;
                case Contains:
                    for (int c = 0; c < liquids; c++) {
                        if (container.getAmountAsLong(c) > 0 && (searchedFluid.isEmpty() || searchedFluid.equals(container.getResource(c))))
                            return true;
                    }
                    return false;
                case Space:
                    if (searchedFluid.isEmpty()) {
                        for (int c = 0; c < liquids; c++) {
                            if (container.getAmountAsLong(c) < container.getCapacityAsLong(c, container.getResource(c)))
                                return true;
                        }
                        return false;
                    }
                    return fill(container, searchedFluid) > 0;
                case Full:
                    if (searchedFluid.isEmpty()) {
                        for (int c = 0; c < liquids; c++) {
                            if (container.getAmountAsLong(c) < container.getCapacityAsLong(c, container.getResource(c)))
                                return false;
                        }
                        return true;
                    }
                    return fill(container, searchedFluid) <= 0;
            }
        }

        return false;
    }

    /** 4.2.2 {@code container.fill(side, searchedFluid, false)} with an amount of 1: a simulated insert. */
    private static int fill(ResourceHandler<FluidResource> container, FluidResource searchedFluid) {
        try (Transaction transaction = Transaction.openRoot()) {
            return container.insert(searchedFluid, 1, transaction);
        }
    }

    @Override
    public int getIconIndex() {
        switch (state) {
            case Empty:
                return ActionTriggerIconProvider.Trigger_FluidContainer_Empty;
            case Contains:
                return ActionTriggerIconProvider.Trigger_FluidContainer_Contains;
            case Space:
                return ActionTriggerIconProvider.Trigger_FluidContainer_Space;
            default:
                return ActionTriggerIconProvider.Trigger_FluidContainer_Full;
        }
    }
}
