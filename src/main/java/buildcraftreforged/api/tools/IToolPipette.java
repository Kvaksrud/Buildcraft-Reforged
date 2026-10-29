/*
 * Modified by Buildcraft Reforged, 2026: ported from buildcraft/api/tools/IToolPipette.java (no file header at 4.2.2);
 * NeoForge FluidStack.
 */

package buildcraftreforged.api.tools;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * An item that can pick up and release fluid. Ported from 4.2.2 {@code buildcraft.api.tools.IToolPipette}. BuildCraft
 * 4.2.2 itself has no implementation; the interface is kept for other mods.
 */
public interface IToolPipette {

    /**
     * @param pipette
     *            ItemStack of the pipette.
     * @return Capacity of the pipette.
     */
    int getCapacity(ItemStack pipette);

    /**
     * @param pipette
     * @return true if the pipette can pipette.
     */
    boolean canPipette(ItemStack pipette);

    /**
     * Fills the pipette with the given liquid stack.
     *
     * @param pipette
     * @param liquid
     * @param doFill
     * @return Amount of liquid used in filling the pipette.
     */
    int fill(ItemStack pipette, FluidStack liquid, boolean doFill);

    /**
     * Drains liquid from the pipette
     *
     * @param pipette
     * @param maxDrain
     * @param doDrain
     * @return Fluid stack representing the liquid and amount drained from the pipette.
     */
    FluidStack drain(ItemStack pipette, int maxDrain, boolean doDrain);
}
