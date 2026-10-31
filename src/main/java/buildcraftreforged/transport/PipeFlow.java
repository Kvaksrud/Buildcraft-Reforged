package buildcraftreforged.transport;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * What the Pipe Contents trigger reads from a pipe (docs/TRANSPORT.md 12). Pipes hold nothing any more, so "contents"
 * means the flow through the pipe: the flow record of section 9 for items and fluids, and the power phases of 7.4.
 *
 * <p>{@code TilePipe} sets {@link PipeGateHolder#flow} from the flow record (#59) and the power phases (#52); a pipe
 * without one reports {@link #NONE} (empty).
 */
public interface PipeFlow {

    /** Nothing flows: the Empty trigger is true, every other one false. */
    PipeFlow NONE = new PipeFlow() {
    };

    /** The record's {@code rate > 0}: items, fluid or power moved through this pipe in the last measuring window. */
    default boolean isFlowing() {
        return false;
    }

    /** Item pipes: the item the record shows, empty when none. */
    default ItemStack itemContent() {
        return ItemStack.EMPTY;
    }

    /** Fluid pipes: the fluid the record shows, empty when none. */
    default FluidStack fluidContent() {
        return FluidStack.EMPTY;
    }

    /** Power pipes: the last request phase through this pipe found more demand than it delivered (Q21). */
    default boolean requestsEnergy() {
        return false;
    }

    /** Power pipes: the overload counter of docs/TRANSPORT.md 7.4 is up. */
    default boolean isOverloaded() {
        return false;
    }
}
