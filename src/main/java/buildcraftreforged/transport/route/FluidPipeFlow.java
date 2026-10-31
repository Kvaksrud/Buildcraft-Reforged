package buildcraftreforged.transport.route;

import buildcraftreforged.transport.PipeFlow;
import buildcraftreforged.transport.TilePipe;
import buildcraftreforged.transport.flow.FlowRecord;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * What the Pipe Contents trigger (#60) reads from a fluid pipe: its flow record (docs/TRANSPORT.md 9, 12). Fluid flows
 * while the record's rate is above zero, and the record's content is the fluid. Server only; on the client nothing
 * flows.
 */
public final class FluidPipeFlow implements PipeFlow {

    private final TilePipe pipe;

    public FluidPipeFlow(TilePipe pipe) {
        this.pipe = pipe;
    }

    @Override
    public boolean isFlowing() {
        return pipe.getFlowTracker().record().isFlowing();
    }

    @Override
    public FluidStack fluidContent() {
        FlowRecord record = pipe.getFlowTracker().record();
        return record.isFlowing() && record.content() != null && record.content().value() instanceof Fluid fluid
                ? new FluidStack(fluid, 1) : FluidStack.EMPTY;
    }
}
