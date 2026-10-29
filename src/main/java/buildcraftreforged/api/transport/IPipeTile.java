/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: Direction in place of ForgeDirection; IFluidHandler super-interface replaced
 * by the fluid capability; section 8 notes.
 */
package buildcraftreforged.api.transport;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

/**
 * The block entity of a pipe. Ported from 4.2.2 {@code buildcraft.api.transport.IPipeTile}.
 *
 * <p>4.2.2 extended Forge's {@code IFluidHandler}. Fluids now go through the NeoForge fluid capability
 * ({@code Capabilities.Fluid.BLOCK}), which a pipe block entity exposes on its sides instead.
 *
 * <p><b>Plan section 8:</b> {@link PipeType} survives (item, fluid and power tiers plus the Structure Pipe, 8.1).
 * {@link #injectItem} survives as the entry point for machine outputs, but P3 makes it route instantly along the graph
 * (8.3) instead of starting a travelling item. {@link #getPipe} was already deprecated in 4.2.2 and is not expected to
 * survive.
 */
public interface IPipeTile extends ISolidSideTile {

    public enum PipeType {

        ITEM, FLUID, POWER, STRUCTURE;
    }

    /** <b>Plan section 8:</b> not expected to survive P3. */
    @Deprecated
    IPipe getPipe();

    PipeType getPipeType();

    /**
     * Offers an ItemStack for addition to the pipe. Will be rejected if the
     * pipe doesn't accept items from that side.
     *
     * @param stack ItemStack offered for addition. Do not manipulate this!
     * @param doAdd If false no actual addition should take place. Implementors
     * should simulate.
     * @param from Orientation the ItemStack is offered from.
     * @return Amount of items used from the passed stack.
     */
    int injectItem(ItemStack stack, boolean doAdd, Direction from);

    boolean isPipeConnected(Direction with);
}
