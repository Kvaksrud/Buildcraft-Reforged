package buildcraftreforged.transport.route;

import buildcraftreforged.transport.PipeFlow;
import buildcraftreforged.transport.TilePipe;
import net.minecraft.server.level.ServerLevel;

/**
 * What the Pipe Contents trigger (#60) reads from a power pipe (docs/TRANSPORT.md 7.4, 9, 12): power flows while its flow
 * record's rate is above zero; the Requests Energy answer of {@link PowerRouting#requestsEnergy} and the overload of
 * {@link PowerRouting#isOverloaded} come from the power phases. Server only; on the client everything reads false.
 */
public final class PowerPipeFlow implements PipeFlow {

    private final TilePipe pipe;

    public PowerPipeFlow(TilePipe pipe) {
        this.pipe = pipe;
    }

    @Override
    public boolean isFlowing() {
        return pipe.getFlowTracker().record().isFlowing();
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
