/*
 * Ported from buildcraft/transport/PipeTriggerProvider.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: the pipe is the gate holder of a pipe block entity and its kind replaces the
 * transport class checks; Power Requested is offered on every power pipe (docs/TRANSPORT.md Q21; 4.2.2: wooden
 * kinesis pipes only); Structure Pipes carry nothing and get no Pipe Contents triggers.
 */
package buildcraftreforged.transport;

import buildcraftreforged.api.gates.IOverrideDefaultTriggers;
import buildcraftreforged.api.gates.ITrigger;
import buildcraftreforged.api.gates.ITriggerProvider;
import buildcraftreforged.api.transport.IPipe;
import java.util.LinkedList;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

public class PipeTriggerProvider implements ITriggerProvider {

    @Override
    public @Nullable LinkedList<ITrigger> getPipeTriggers(IPipe iPipe) {
        if (iPipe instanceof IOverrideDefaultTriggers)
            return ((IOverrideDefaultTriggers) iPipe).getTriggers();

        LinkedList<ITrigger> result = new LinkedList<ITrigger>();

        if (!(iPipe instanceof PipeGateHolder pipe))
            return result;

        if (pipe.hasGate()) {
            pipe.gate.addTrigger(result);
        }

        switch (pipe.container.kind) {
            case ITEM:
                result.add(TransportRegistry.triggerPipeEmpty.get());
                result.add(TransportRegistry.triggerPipeItems.get());
                break;
            case POWER:
                result.add(TransportRegistry.triggerPipeEmpty.get());
                result.add(TransportRegistry.triggerPipeContainsEnergy.get());
                result.add(TransportRegistry.triggerPipeTooMuchEnergy.get());
                result.add(TransportRegistry.triggerPipeRequestsEnergy.get());
                break;
            case FLUID:
                result.add(TransportRegistry.triggerPipeEmpty.get());
                result.add(TransportRegistry.triggerPipeFluids.get());
                break;
            default:
                break;
        }

        return result;
    }

    @Override
    public @Nullable LinkedList<ITrigger> getNeighborTriggers(Block block, @Nullable BlockEntity tile) {
        return null;
    }
}
