package buildcraftreforged.transport.route;

import buildcraftreforged.transport.PipeFlow;
import buildcraftreforged.transport.TilePipe;
import net.minecraft.server.level.ServerLevel;

/**
 * What the Pipe Contents trigger (#60) reads from a power pipe, from the power phases (docs/TRANSPORT.md 7.4, 12):
 * power flowed through the pipe in the last {@link #FLOW_TICKS} ticks (the measuring window of 9.2), the Requests
 * Energy answer of {@link PowerRouting#requestsEnergy} and the overload of {@link PowerRouting#isOverloaded}. Server
 * only; on the client everything reads false. #59 may replace {@link #isFlowing} with the flow record.
 */
public final class PowerPipeFlow implements PipeFlow {

    /** Two 10-tick buckets, as the flow record's rate (docs/TRANSPORT.md 9.2). */
    public static final int FLOW_TICKS = 20;

    private final TilePipe pipe;

    public PowerPipeFlow(TilePipe pipe) {
        this.pipe = pipe;
    }

    @Override
    public boolean isFlowing() {
        long last = pipe.getPowerState().lastFlowTick();
        return pipe.getLevel() instanceof ServerLevel level && last != Long.MIN_VALUE
                && level.getGameTime() - last < FLOW_TICKS;
    }

    @Override
    public boolean requestsEnergy() {
        return pipe.getLevel() instanceof ServerLevel level && PowerRouting.requestsEnergy(level, pipe);
    }

    @Override
    public boolean isOverloaded() {
        return pipe.getLevel() instanceof ServerLevel level && PowerRouting.isOverloaded(level, pipe);
    }
}
