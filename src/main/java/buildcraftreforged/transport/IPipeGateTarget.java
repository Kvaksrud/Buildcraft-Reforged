package buildcraftreforged.transport;

import buildcraftreforged.api.gates.IAction;
import java.util.LinkedList;
import java.util.Map;

/**
 * The part of a pipe that takes gate actions of its own: 4.2.2's {@code Pipe.getActions} and
 * {@code Pipe.actionsActivated} overrides, which the iron, lapis, daizuli, emzuli and iron kinesis pipes used. In
 * Buildcraft Reforged these are modules (docs/TRANSPORT.md 8.1, 12): the Director takes Pipe Direction and, on a power
 * pipe, Power Limiter; the Colour Tag and Colour Sorter take Pipe Color; the Filtered Colour Tag takes Extraction
 * Preset. Module fitting (#55–#57) sets {@link PipeGateHolder#target}.
 */
public interface IPipeGateTarget {

    /** Adds the actions this pipe part offers to the gate GUI. */
    void addActions(LinkedList<IAction> list);

    /**
     * Called by the gate after every resolution with each action that has at least one trigger, and whether it is
     * active, as 4.2.2 {@code Pipe.actionsActivated}.
     */
    void actionsActivated(Map<IAction, Boolean> actions);
}
