package buildcraftreforged.transport.route;

import buildcraftreforged.transport.PipeFlow;
import buildcraftreforged.transport.TilePipe;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * What the Pipe Contents trigger (#60) reads from a fluid pipe, from {@link FluidPipeState} (docs/TRANSPORT.md 7.3,
 * 12): fluid flowed through the pipe in the last {@link PowerPipeFlow#FLOW_TICKS} ticks (the measuring window of 9.2),
 * and which fluid. Server only; on the client nothing flows. #59 may replace both with its flow record.
 */
public final class FluidPipeFlow implements PipeFlow {

    private final TilePipe pipe;

    public FluidPipeFlow(TilePipe pipe) {
        this.pipe = pipe;
    }

    @Override
    public boolean isFlowing() {
        long last = pipe.getFluidState().lastFlowTick();
        return pipe.getLevel() instanceof ServerLevel level && last != Long.MIN_VALUE
                && level.getGameTime() - last < PowerPipeFlow.FLOW_TICKS;
    }

    @Override
    public FluidStack fluidContent() {
        return isFlowing() ? pipe.getFluidState().lastFluid().toStack(1) : FluidStack.EMPTY;
    }
}
