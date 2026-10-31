/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: the GATE_* packets become a widget (server to client: the potential trigger
 * and action lists, the selection and the trigger states) and a GUI return (client to server: one position's
 * selection). The server checks every selection against the lists it offered and the gate's kind, and takes a
 * parameter from the player's own carried stack (plan section 19.2); 4.2.2 trusted the client's packet.
 */
package buildcraftreforged.transport.gui;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.api.gates.ActionManager;
import buildcraftreforged.api.gates.IAction;
import buildcraftreforged.api.gates.ITrigger;
import buildcraftreforged.api.gates.ITriggerParameter;
import buildcraftreforged.api.gates.TriggerParameter;
import buildcraftreforged.api.transport.IPipeTile.PipeType;
import buildcraftreforged.core.gui.BuildCraftContainer;
import buildcraftreforged.core.gui.widgets.Widget;
import buildcraftreforged.core.network.IGuiReturnHandler;
import buildcraftreforged.core.network.InvalidPacketException;
import buildcraftreforged.core.network.PacketData;
import buildcraftreforged.core.network.PacketGuiReturn;
import buildcraftreforged.transport.Gate;
import buildcraftreforged.transport.GateSettings;
import buildcraftreforged.transport.GateVanilla;
import buildcraftreforged.transport.PipeGateHolder;
import buildcraftreforged.transport.PipeMaterial;
import buildcraftreforged.transport.TilePipe;
import buildcraftreforged.transport.TransportRegistry;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

public class ContainerGateInterface extends BuildCraftContainer {

    /** What a selection change does to the position's parameter. */
    public static final int PARAMETER_CLEAR = 0;
    public static final int PARAMETER_FROM_CARRIED = 1;
    public static final int PARAMETER_KEEP = 2;

    public final TilePipe tile;
    public final PipeGateHolder pipe;
    /** The gate this menu was opened for; the menu closes when another gate (or none) is on the pipe. */
    public final Gate gate;
    private final NavigableSet<ITrigger> _potentialTriggers = new TreeSet<ITrigger>(new Comparator<ITrigger>() {
        @Override
        public int compare(ITrigger o1, ITrigger o2) {
            return o1.getUniqueTag().compareTo(o2.getUniqueTag());
        }
    });
    private final NavigableSet<IAction> _potentialActions = new TreeSet<IAction>(new Comparator<IAction>() {
        @Override
        public int compare(IAction o1, IAction o2) {
            return o1.getUniqueTag().compareTo(o2.getUniqueTag());
        }
    });
    public boolean[] triggerState = new boolean[8];
    public final GateWidget widget;
    private final SelectionReturn selectionReturn = new SelectionReturn();

    /** Client side: the block position comes with the open-menu packet. */
    public ContainerGateInterface(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, clientTile(playerInventory, extraData.readBlockPos()));
    }

    public ContainerGateInterface(int containerId, Inventory playerInventory, TilePipe tile) {
        super(TransportRegistry.gateMenu.get(), containerId, 0);
        this.tile = tile;
        this.pipe = tile.gateHolder;
        // The client may not have the gate yet if the menu arrived first; a stand-in keeps the screen drawable.
        this.gate = pipe.gate != null ? pipe.gate : new GateVanilla(pipe, Gate.GateKind.Single, false);

        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 9; x++) {
                addSlot(new Slot(playerInventory, x + y * 9 + 9, 8 + x * 18, gate.getGuiHeight() - 84 + y * 18));
            }
        }

        for (int x = 0; x < 9; x++) {
            addSlot(new Slot(playerInventory, x, 8 + x * 18, gate.getGuiHeight() - 26));
        }

        // Do not attempt to create a list of potential actions and triggers on
        // the client.
        Level level = tile.getLevel();
        if (level != null && !level.isClientSide()) {
            _potentialActions.addAll(pipe.getActions());
            _potentialTriggers.addAll(ActionManager.getPipeTriggers(pipe));
            _potentialTriggers.addAll(pipe.getTriggers());

            for (Direction o : Direction.values()) {
                BlockPos pos = tile.getBlockPos().relative(o);
                if (!level.isLoaded(pos))
                    continue;
                BlockEntity neighbour = pipe.getTile(o);
                Block block = level.getBlockState(pos).getBlock();
                _potentialTriggers.addAll(ActionManager.getNeighborTriggers(block, neighbour));
                _potentialActions.addAll(ActionManager.getNeighborActions(block, neighbour));
            }

            if (getGateOrdinal() < Gate.GateKind.AND_3.ordinal()) {
                Iterator<ITrigger> it = _potentialTriggers.iterator();
                while (it.hasNext()) {
                    ITrigger trigger = it.next();
                    try {
                        if (trigger.requiresParameter())
                            it.remove();
                    } catch (Throwable error) {
                        BuildcraftReforged.LOGGER.error("Trigger {} failed", trigger.getUniqueTag(), error);
                    }
                }
            }
        }

        widget = new GateWidget();
        addWidget(widget);
    }

    private static TilePipe clientTile(Inventory playerInventory, BlockPos pos) {
        if (playerInventory.player.level().getBlockEntity(pos) instanceof TilePipe tile) {
            return tile;
        }
        // The pipe went away before the menu arrived; the menu closes on the next stillValid check.
        return new TilePipe(pos, TransportRegistry.getPipe(PipeType.ITEM, PipeMaterial.WOOD).defaultBlockState());
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(tile, player) && pipe.gate == gate;
    }

    @Override
    public IGuiReturnHandler getGuiReturnHandler() {
        return selectionReturn;
    }

    /**
     * SERVER SIDE *
     */
    private int calculateTriggerState() {
        if (pipe.gate == null)
            return 0;
        int state = 0;
        for (int i = 0; i < triggerState.length; i++) {
            if (pipe.gate.triggers[i] != null) {
                triggerState[i] = isNearbyTriggerActive(pipe.gate.triggers[i], pipe.gate.getTriggerParameter(i));
            }
            state |= triggerState[i] ? 0x01 << i : 0x0;
        }
        return state;
    }

    /**
     * Server: applies one position's selection from a client, after checking it (4.2.2 {@code setSelection} from a
     * {@code GATE_SELECTION_CHANGE} packet).
     */
    public void applySelection(int position, @Nullable ITrigger trigger, @Nullable IAction action, int parameterMode,
            @Nullable Player player) throws InvalidPacketException {
        if (pipe.gate != gate)
            throw new InvalidPacketException("gate changed");
        if (position < 0 || position >= gate.kind.getPositions())
            throw new InvalidPacketException("position " + position + " outside the " + gate.kind + " gate");
        if (trigger != null && !_potentialTriggers.contains(trigger))
            throw new InvalidPacketException("trigger " + trigger.getUniqueTag() + " not offered");
        if (action != null && !_potentialActions.contains(action))
            throw new InvalidPacketException("action " + action.getUniqueTag() + " not offered");
        ITriggerParameter parameter = gate.getTriggerParameter(position);
        if (parameterMode == PARAMETER_CLEAR) {
            parameter = null;
        } else if (parameterMode == PARAMETER_FROM_CARRIED) {
            if (getGateOrdinal() < Gate.GateKind.AND_3.ordinal() || trigger == null || !trigger.hasParameter())
                throw new InvalidPacketException("no parameter slot");
            ItemStack carried = getCarried();
            parameter = carried.isEmpty() ? null : TriggerParameter.of(carried);
        }

        gate.setTrigger(position, trigger);
        gate.setAction(position, action);
        gate.setTriggerParameter(position, parameter);
        tile.setChanged();
    }

    /**
     * TRIGGERS *
     */
    public boolean hasTriggers() {
        return _potentialTriggers.size() > 0;
    }

    public @Nullable ITrigger getFirstTrigger() {
        if (_potentialTriggers.isEmpty())
            return null;
        return _potentialTriggers.first();
    }

    public @Nullable ITrigger getLastTrigger() {
        if (_potentialTriggers.isEmpty())
            return null;
        return _potentialTriggers.last();
    }

    public Iterator<ITrigger> getTriggerIterator(boolean descending) {
        return descending ? _potentialTriggers.descendingIterator() : _potentialTriggers.iterator();
    }

    public NavigableSet<ITrigger> getPotentialTriggers() {
        return _potentialTriggers;
    }

    public boolean isNearbyTriggerActive(ITrigger trigger, @Nullable ITriggerParameter parameter) {
        return pipe.gate.isNearbyTriggerActive(trigger, parameter);
    }

    /** Client: sets a trigger and sends the position's selection; 4.2.2 cleared the parameter with it on gold+. */
    public void setTrigger(int position, @Nullable ITrigger trigger, boolean notify) {
        gate.setTrigger(position, trigger);
        int parameterMode = PARAMETER_KEEP;
        if (getGateOrdinal() >= Gate.GateKind.AND_3.ordinal()) {
            gate.setTriggerParameter(position, null);
            parameterMode = PARAMETER_CLEAR;
        }
        if (notify)
            sendSelectionChange(position, parameterMode);
    }

    /** Client: puts the carried stack (or nothing) in the position's parameter slot and sends it. */
    public void setTriggerParameterFromCarried(int position, ItemStack carried, boolean notify) {
        gate.setTriggerParameter(position, carried.isEmpty() ? null : TriggerParameter.of(carried));
        if (notify)
            sendSelectionChange(position, PARAMETER_FROM_CARRIED);
    }

    /**
     * ACTIONS *
     */
    public boolean hasActions() {
        return !_potentialActions.isEmpty();
    }

    public @Nullable IAction getFirstAction() {
        if (_potentialActions.isEmpty())
            return null;
        return _potentialActions.first();
    }

    public @Nullable IAction getLastAction() {
        if (_potentialActions.isEmpty())
            return null;
        return _potentialActions.last();
    }

    public Iterator<IAction> getActionIterator(boolean descending) {
        return descending ? _potentialActions.descendingIterator() : _potentialActions.iterator();
    }

    public NavigableSet<IAction> getPotentialActions() {
        return _potentialActions;
    }

    public void setAction(int position, @Nullable IAction action, boolean notify) {
        gate.setAction(position, action);
        if (notify)
            sendSelectionChange(position, PARAMETER_KEEP);
    }

    /** Client only: sends one position's selection to the server. */
    private void sendSelectionChange(int position, int parameterMode) {
        selectionReturn.position = position;
        selectionReturn.parameterMode = parameterMode;
        new PacketGuiReturn(containerId, selectionReturn).sendPacket();
    }

    /**
     * GATE INFORMATION *
     */
    public final int getGateOrdinal() {
        return gate.kind.ordinal();
    }

    public String getGateName() {
        return gate.getName();
    }

    /** The GUI return: one position's trigger, action and parameter mode. */
    public final class SelectionReturn implements IGuiReturnHandler {
        private int position;
        private int parameterMode = PARAMETER_KEEP;

        @Override
        public @Nullable Level getLevel() {
            return tile.getLevel();
        }

        @Override
        public BlockPos getBlockPos() {
            return tile.getBlockPos();
        }

        @Override
        public boolean isRemoved() {
            return tile.isRemoved();
        }

        @Override
        public void writeGuiData(RegistryFriendlyByteBuf data) {
            writeSelection(data, position, gate.getTrigger(position), gate.getAction(position), parameterMode);
        }

        @Override
        public void readGuiData(RegistryFriendlyByteBuf data, @Nullable Player player) throws InvalidPacketException {
            int position = PacketData.readVarInt(data, 0, 7);
            ITrigger trigger;
            IAction action;
            try {
                trigger = ByteBufCodecs.optional(ActionManager.TRIGGER_STREAM_CODEC).decode(data).orElse(null);
                action = ByteBufCodecs.optional(ActionManager.ACTION_STREAM_CODEC).decode(data).orElse(null);
            } catch (RuntimeException e) {
                throw new InvalidPacketException("malformed selection: " + e.getMessage());
            }
            int parameterMode = PacketData.readVarInt(data, PARAMETER_CLEAR, PARAMETER_KEEP);
            applySelection(position, trigger, action, parameterMode, player);
        }
    }

    public static void writeSelection(RegistryFriendlyByteBuf data, int position, @Nullable ITrigger trigger,
            @Nullable IAction action, int parameterMode) {
        data.writeVarInt(position);
        ByteBufCodecs.optional(ActionManager.TRIGGER_STREAM_CODEC).encode(data, java.util.Optional.ofNullable(trigger));
        ByteBufCodecs.optional(ActionManager.ACTION_STREAM_CODEC).encode(data, java.util.Optional.ofNullable(action));
        data.writeVarInt(parameterMode);
    }

    /**
     * Server to client, as 4.2.2's {@code GATE_ACTIONS}, {@code GATE_TRIGGERS}, {@code GATE_SELECTION} packets and the
     * trigger-state progress bar: the lists once when the menu opens, then the selection and the trigger states
     * whenever they change.
     */
    public final class GateWidget extends Widget {
        private final Map<Player, Integer> lastState = new WeakHashMap<>();
        private final Map<Player, GateSettings> lastSelection = new WeakHashMap<>();

        GateWidget() {
            super(0, 0, 0, 0, 0, 0);
            hidden = true;
        }

        @Override
        public void initWidget(Player player) {
            send(player, true);
        }

        @Override
        public void updateWidget(Player player) {
            int state = calculateTriggerState();
            if (!Integer.valueOf(state).equals(lastState.get(player))
                    || !gate.getSettings().equals(lastSelection.get(player)))
                send(player, false);
        }

        private void send(Player player, boolean lists) {
            lastState.put(player, calculateTriggerState());
            lastSelection.put(player, gate.getSettings());
            sendWidgetDataToClient(this, player, getData(player.registryAccess(), lists));
        }

        /** The bytes for the client; with {@code lists}, the potential triggers and actions first. */
        public byte[] getData(RegistryAccess registryAccess, boolean lists) {
            int state = calculateTriggerState();
            return PacketData.write(registryAccess, data -> {
                data.writeBoolean(lists);
                if (lists) {
                    data.writeVarInt(_potentialActions.size());
                    for (IAction action : _potentialActions)
                        ActionManager.ACTION_STREAM_CODEC.encode(data, action);
                    data.writeVarInt(_potentialTriggers.size());
                    for (ITrigger trigger : _potentialTriggers)
                        ActionManager.TRIGGER_STREAM_CODEC.encode(data, trigger);
                }
                GateSettings.STREAM_CODEC.encode(data, gate.getSettings());
                data.writeByte(state);
            });
        }

        @Override
        public void handleClientPacketData(RegistryFriendlyByteBuf data) {
            if (data.readBoolean()) {
                _potentialActions.clear();
                int actions = data.readVarInt();
                for (int i = 0; i < actions; i++)
                    _potentialActions.add(ActionManager.ACTION_STREAM_CODEC.decode(data));
                _potentialTriggers.clear();
                int triggers = data.readVarInt();
                for (int i = 0; i < triggers; i++)
                    _potentialTriggers.add(ActionManager.TRIGGER_STREAM_CODEC.decode(data));
            }
            GateSettings selection = GateSettings.STREAM_CODEC.decode(data);
            for (int i = 0; i < 8; i++) {
                gate.setTrigger(i, null);
                gate.setAction(i, null);
                gate.setTriggerParameter(i, null);
            }
            gate.applySettings(selection);
            int state = data.readByte();
            for (int i = 0; i < 8; i++) {
                /* Bit mask of triggers */
                triggerState[i] = ((state >> i) & 0x01) == 0x01;
            }
        }
    }
}
