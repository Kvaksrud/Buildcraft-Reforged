/*
 * Ported from buildcraft/transport/Gate.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: the pipe is the gate holder of the pipe block entity; the gate kind comes
 * from the gate item instead of its damage; triggers and actions save by registry id (ValueInput/ValueOutput in place
 * of NBT) and travel with the gate item as a data component; the payload, GUI and texture plumbing become an
 * Identifier and the gate menu; neighbours are looked up in the level without loading chunks.
 */
package buildcraftreforged.transport;

import buildcraftreforged.api.gates.ActionManager;
import buildcraftreforged.api.gates.IAction;
import buildcraftreforged.api.gates.IActionReceptor;
import buildcraftreforged.api.gates.ITrigger;
import buildcraftreforged.api.gates.ITriggerParameter;
import buildcraftreforged.api.gates.TriggerParameter;
import buildcraftreforged.core.triggers.ActionRedstoneOutput;
import buildcraftreforged.transport.triggers.ActionSignalOutput;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public abstract class Gate {

    /** The gate kinds; the ordinal is 4.2.2's (saved as {@code Kind}, and the kind order of the trigger lists). */
    public static enum GateKind {

        None, Single, AND_2, OR_2, AND_3, OR_3, AND_4, OR_4;

        /** 4.2.2 {@code getKindFromDamage}: item damage 0–6 is Single to OR_4. */
        public static GateKind getKindFromDamage(int damage) {
            switch (damage) {
                case 0:
                    return Single;
                case 1:
                    return AND_2;
                case 2:
                    return OR_2;
                case 3:
                    return AND_3;
                case 4:
                    return OR_3;
                case 5:
                    return AND_4;
                case 6:
                    return OR_4;
                default:
                    return None;
            }
        }

        /** The trigger/action pairs a gate of this kind has: 1, 2, 4 or 8 (4.2.2 {@code sendSelection}). */
        public int getPositions() {
            switch (this) {
                case None:
                    return 0;
                case Single:
                    return 1;
                case AND_2:
                case OR_2:
                    return 2;
                case AND_3:
                case OR_3:
                    return 4;
                default:
                    return 8;
            }
        }
    }

    public static enum GateConditional {

        None, AND, OR
    }
    protected PipeGateHolder pipe;
    public GateKind kind;
    public @Nullable ITrigger[] triggers = new ITrigger[8];
    public @Nullable ITriggerParameter[] triggerParameters = new ITriggerParameter[8];
    public @Nullable IAction[] actions = new IAction[8];
    public boolean broadcastSignal[] = new boolean[4];
    public boolean broadcastRedstone = false;

    // / CONSTRUCTOR
    public Gate(PipeGateHolder pipe, GateKind kind) {
        this.pipe = pipe;
        this.kind = kind;
    }

    public void setTrigger(int position, @Nullable ITrigger trigger) {
        triggers[position] = trigger;
    }

    public @Nullable ITrigger getTrigger(int position) {
        return triggers[position];
    }

    public void setAction(int position, @Nullable IAction action) {
        actions[position] = action;
    }

    public @Nullable IAction getAction(int position) {
        return actions[position];
    }

    public void setTriggerParameter(int position, @Nullable ITriggerParameter p) {
        triggerParameters[position] = p;
    }

    public @Nullable ITriggerParameter getTriggerParameter(int position) {
        return triggerParameters[position];
    }

    // / SAVING & LOADING
    public void writeToNBT(ValueOutput data) {
        data.putInt("Kind", kind.ordinal());

        for (int i = 0; i < 8; ++i) {
            data.storeNullable("trigger[" + i + "]", ActionManager.TRIGGER_CODEC, triggers[i]);
            data.storeNullable("action[" + i + "]", ActionManager.ACTION_CODEC, actions[i]);
            if (triggerParameters[i] != null) {
                triggerParameters[i].writeToNBT(data.child("triggerParameters[" + i + "]"));
            }
        }

        for (int i = 0; i < 4; ++i) {
            data.putBoolean("wireState[" + i + "]", broadcastSignal[i]);
        }
        data.putBoolean("redstoneState", broadcastRedstone);
    }

    public void readFromNBT(ValueInput data) {
        int kindOrdinal = data.getIntOr("Kind", GateKind.Single.ordinal());
        kind = kindOrdinal > 0 && kindOrdinal < GateKind.values().length ? GateKind.values()[kindOrdinal]
                : GateKind.Single;

        for (int i = 0; i < 8; ++i) {
            triggers[i] = data.read("trigger[" + i + "]", ActionManager.TRIGGER_CODEC).orElse(null);
            actions[i] = data.read("action[" + i + "]", ActionManager.ACTION_CODEC).orElse(null);
            triggerParameters[i] = null;
            ValueInput parameter = data.child("triggerParameters[" + i + "]").orElse(null);
            if (parameter != null) {
                triggerParameters[i] = new TriggerParameter();
                triggerParameters[i].readFromNBT(parameter);
            }
        }

        for (int i = 0; i < 4; ++i) {
            broadcastSignal[i] = data.getBooleanOr("wireState[" + i + "]", false);
        }
        broadcastRedstone = data.getBooleanOr("redstoneState", false);
    }

    /** The programming the gate item keeps when the gate is taken off (new; 4.2.2 lost it). */
    public GateSettings getSettings() {
        return GateSettings.of(triggers, triggerParameters, actions);
    }

    public void applySettings(GateSettings settings) {
        settings.applyTo(triggers, triggerParameters, actions, kind.getPositions());
    }

    // GUI
    public abstract void openGui(Player player);

    // / UPDATING
    public abstract void update();

    public abstract ItemStack getGateItem();

    public void dropGate() {
        pipe.dropItem(getGateItem());
    }

    public void resetGate() {
        if (broadcastRedstone) {
            broadcastRedstone = false;
            pipe.updateNeighbors(true);
        }
    }

    // / INFORMATION
    public abstract String getName();

    public abstract GateConditional getConditional();

    public boolean isGateActive() {
        for (boolean b : broadcastSignal) {
            if (b)
                return true;
        }
        return broadcastRedstone;
    }

    public boolean isEmittingRedstone() {
        return broadcastRedstone;
    }

    public abstract void startResolution();

    public void resolveActions() {
        boolean oldBroadcastRedstone = broadcastRedstone;
        boolean[] oldBroadcastSignal = broadcastSignal;

        broadcastRedstone = false;
        broadcastSignal = new boolean[4];

        // Tell the gate to prepare for resolving actions. (Disable pulser)
        startResolution();

        // Insertion order keeps the activation order stable; 4.2.2 used a HashMap.
        Map<IAction, Boolean> activeActions = new LinkedHashMap<IAction, Boolean>();
        Multiset<IAction> actionCount = HashMultiset.create();

        // Computes the actions depending on the triggers
        for (int it = 0; it < 8; ++it) {
            ITrigger trigger = triggers[it];
            IAction action = actions[it];
            ITriggerParameter parameter = triggerParameters[it];

            if (trigger != null && action != null) {
                actionCount.add(action);
                if (!activeActions.containsKey(action)) {
                    activeActions.put(action, isNearbyTriggerActive(trigger, parameter));
                } else if (getConditional() == GateConditional.AND) {
                    activeActions.put(action, activeActions.get(action) && isNearbyTriggerActive(trigger, parameter));
                } else {
                    activeActions.put(action, activeActions.get(action) || isNearbyTriggerActive(trigger, parameter));
                }
            }
        }

        // Activate the actions
        for (Map.Entry<IAction, Boolean> entry : activeActions.entrySet()) {
            if (entry.getValue()) {
                IAction action = entry.getKey();

                // Custom gate actions take precedence over defaults.
                if (resolveAction(action, actionCount.count(action))) {
                    continue;
                }

                if (action instanceof ActionRedstoneOutput) {
                    broadcastRedstone = true;
                } else if (action instanceof ActionSignalOutput) {
                    broadcastSignal[((ActionSignalOutput) action).color.ordinal()] = true;
                } else {
                    for (Direction side : Direction.values()) {
                        BlockEntity tile = pipe.getTile(side);
                        if (tile instanceof IActionReceptor) {
                            IActionReceptor recept = (IActionReceptor) tile;
                            recept.actionActivated(action);
                        }
                    }
                }
            }
        }

        pipe.actionsActivated(activeActions);

        if (oldBroadcastRedstone != broadcastRedstone) {
            pipe.scheduleRenderUpdate();
            pipe.updateNeighbors(true);
        }

        for (int i = 0; i < oldBroadcastSignal.length; ++i) {
            if (oldBroadcastSignal[i] != broadcastSignal[i]) {
                pipe.scheduleRenderUpdate();
                pipe.updateSignalState();
                break;
            }
        }
    }

    public abstract boolean resolveAction(IAction action, int count);

    public boolean isNearbyTriggerActive(@Nullable ITrigger trigger, @Nullable ITriggerParameter parameter) {
        if (trigger instanceof ITriggerPipe)
            return ((ITriggerPipe) trigger).isTriggerActive(pipe.container, parameter);
        else if (trigger != null) {
            for (Direction o : Direction.values()) {
                BlockEntity tile = pipe.getTile(o);

                if (tile != null && !(tile instanceof TilePipe)) {
                    // The tile's side facing the gate (4.2.2 o.getOpposite(), see docs/BUGFIXES.md).
                    if (trigger.isTriggerActive(o.getOpposite(), tile, parameter))
                        return true;
                }
            }
        }

        return false;
    }

    // / TRIGGERS
    public abstract void addTrigger(LinkedList<ITrigger> list);

    // / ACTIONS
    public abstract void addActions(LinkedList<IAction> list);

    // / TEXTURES
    /** The block texture of the gate on the pipe (4.2.2 {@code GateIconProvider} index). */
    public abstract Identifier getTexture(boolean isSignalActive);

    public abstract Identifier getGuiFile();

    public int getGuiHeight() {
        return 207;
    }

    public static boolean isGateItem(ItemStack stack) {
        return stack.getItem() instanceof ItemGate;
    }

    public static Gate makeGate(PipeGateHolder pipe, ValueInput data) {
        Gate gate = new GateVanilla(pipe, GateKind.Single, data.child("Pulser").isPresent());
        gate.readFromNBT(data);
        return gate;
    }

    public static Gate makeGate(PipeGateHolder pipe, ItemStack stack) {
        ItemGate item = (ItemGate) stack.getItem();
        Gate gate = new GateVanilla(pipe, item.kind, item.autarchic);
        GateSettings settings = stack.get(TransportRegistry.gateSettings.get());
        if (settings != null)
            gate.applySettings(settings);
        return gate;
    }
}
