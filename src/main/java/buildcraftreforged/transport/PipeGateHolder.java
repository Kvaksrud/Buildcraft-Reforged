/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: the gate and wire half of buildcraft/transport/Pipe.java, with the wire and
 * gate parts of TileGenericPipe (redstonePowered, the wire render state, getTriggers) and BlockGenericPipe
 * (addOrStripWire, addOrStripGate, stripEquipment, redstone output). The pipe is the pipe block entity, which owns one
 * holder; ValueInput/ValueOutput in place of NBT; neighbours are looked up without loading chunks.
 */
package buildcraftreforged.transport;

import buildcraftreforged.api.gates.IAction;
import buildcraftreforged.api.gates.ITrigger;
import buildcraftreforged.api.transport.IPipe;
import buildcraftreforged.api.transport.IPipeTile.PipeType;
import buildcraftreforged.core.CoreConstants;
import buildcraftreforged.core.CoreRegistry;
import buildcraftreforged.core.inventory.InvUtils;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A pipe's gate and its four wires. Signals travel as in 4.2.2: a gate broadcasting a colour puts 255 on its pipe's
 * wire, each wired pipe takes the strongest neighbour signal minus one, and a pipe with no stronger neighbour drops to 0
 * and makes its neighbours look again. Two pipes share a wire colour when both have it and they are connected to each
 * other, or one of them is a Structure Pipe.
 */
public class PipeGateHolder implements IPipe {

    public int[] signalStrength = new int[]{0, 0, 0, 0};
    public final TilePipe container;
    private boolean internalUpdateScheduled = false;
    public boolean[] wireSet = new boolean[]{false, false, false, false};
    public @Nullable Gate gate;
    /** 4.2.2 {@code TileGenericPipe.redstonePowered}: the pipe block receives a redstone signal. */
    public boolean redstonePowered = false;

    /**
     * What the Pipe Contents trigger reads: the pipe's flow record (docs/TRANSPORT.md 9), and on power pipes the power
     * request and overload state (7.4). {@code TilePipe} sets it for each kind; {@link PipeFlow#NONE} otherwise.
     */
    public PipeFlow flow = PipeFlow.NONE;

    /**
     * The pipe part that offers and takes module actions (Pipe Direction, Pipe Color, Extraction Preset, Power
     * Limiter). {@code TilePipe.setModule} sets it from {@code PipeModule.gateTarget}.
     */
    public @Nullable IPipeGateTarget target;

    private boolean initialized = false;
    private boolean renderUpdateScheduled = true;

    /** Render state, computed on the server and synced: wire bits 0–3 set, 4–7 lit. */
    private int wireState;
    /** Render state: per colour, the sides its wire runs to (bit d = Direction.get3DDataValue()). */
    private final byte[] wireConnections = new byte[4];
    /** Render state: the gate's kind (None when there is no gate), series and light. */
    private Gate.GateKind gateKind = Gate.GateKind.None;
    private boolean gateAutarchic;
    private boolean gateLit;

    public PipeGateHolder(TilePipe container) {
        this.container = container;
    }

    private @Nullable Level level() {
        return container.getLevel();
    }

    /**
     * The block entity on {@code side}, or null when there is none or its chunk is not loaded. Stands in for 4.2.2's
     * {@code TileGenericPipe.getTile}.
     */
    public @Nullable BlockEntity getTile(Direction side) {
        Level level = level();
        if (level == null)
            return null;
        BlockPos pos = container.getBlockPos().relative(side);
        if (!level.isLoaded(pos))
            return null;
        BlockEntity tile = level.getBlockEntity(pos);
        return tile == null || tile.isRemoved() ? null : tile;
    }

    // / UPDATING

    /** Server side, every tick, from the pipe's update (4.2.2 {@code Pipe.updateEntity}). */
    public void updateEntity() {
        Level level = level();
        if (level == null || level.isClientSide())
            return;

        if (!initialized) {
            initialize();
        }

        if (internalUpdateScheduled) {
            internalUpdateScheduled = false;
            updateSignalState();
        }

        // Update the gate if we have any
        if (gate != null) {
            gate.resolveActions();
            gate.update();
        }

        if (renderUpdateScheduled) {
            renderUpdateScheduled = false;
            refreshRenderState();
        }
    }

    private void initialize() {
        Level level = level();
        redstonePowered = level.hasNeighborSignal(container.getBlockPos());
        updateSignalState();
        initialized = true;
    }

    /** 4.2.2 {@code BlockGenericPipe.onNeighborBlockChange} and {@code Pipe.onNeighborBlockChange}. */
    public void onNeighborBlockChange() {
        Level level = level();
        if (level == null || level.isClientSide())
            return;
        redstonePowered = level.hasNeighborSignal(container.getBlockPos());
        updateSignalState();
        scheduleRenderUpdate();
    }

    /** The pipe's connections changed: wires follow them (4.2.2 refreshed the render state after computeConnections). */
    public void onConnectionsChanged() {
        internalUpdateScheduled = true;
        scheduleRenderUpdate();
    }

    /** 4.2.2 {@code TileGenericPipe.scheduleRenderUpdate}: the render state is recomputed and synced on the next tick. */
    public void scheduleRenderUpdate() {
        renderUpdateScheduled = true;
    }

    // / WIRES

    private void readNearbyPipesSignal(WireColor color) {
        boolean foundBiggerSignal = false;

        for (Direction o : Direction.values()) {
            BlockEntity tile = getTile(o);

            if (tile instanceof TilePipe tilePipe) {
                if (isWireConnectedTo(tile, color)) {
                    foundBiggerSignal |= receiveSignal(tilePipe.gateHolder.signalStrength[color.ordinal()] - 1, color);
                }
            }
        }

        if (!foundBiggerSignal && signalStrength[color.ordinal()] != 0) {
            signalStrength[color.ordinal()] = 0;
            scheduleRenderUpdate();

            for (Direction o : Direction.values()) {
                BlockEntity tile = getTile(o);

                if (tile instanceof TilePipe tilePipe) {
                    tilePipe.gateHolder.internalUpdateScheduled = true;
                }
            }
        }
    }

    public void updateSignalState() {
        for (IPipe.WireColor c : IPipe.WireColor.values()) {
            updateSignalStateForColor(c);
        }
    }

    private void updateSignalStateForColor(IPipe.WireColor color) {
        if (!wireSet[color.ordinal()])
            return;

        // STEP 1: compute internal signal strength

        if (gate != null && gate.broadcastSignal[color.ordinal()]) {
            receiveSignal(255, color);
        } else {
            readNearbyPipesSignal(color);
        }

        // STEP 2: transmit signal in nearby blocks

        if (signalStrength[color.ordinal()] > 1) {
            for (Direction o : Direction.values()) {
                BlockEntity tile = getTile(o);

                if (tile instanceof TilePipe tilePipe && tilePipe.gateHolder.wireSet[color.ordinal()]) {
                    if (isWireConnectedTo(tile, color)) {
                        tilePipe.gateHolder.receiveSignal(signalStrength[color.ordinal()] - 1, color);
                    }
                }
            }
        }
    }

    private boolean receiveSignal(int signal, IPipe.WireColor color) {
        if (level() == null)
            return false;

        int oldSignal = signalStrength[color.ordinal()];

        if (signal >= signalStrength[color.ordinal()] && signal != 0) {
            signalStrength[color.ordinal()] = signal;
            internalUpdateScheduled = true;

            if (oldSignal == 0) {
                scheduleRenderUpdate();
            }

            return true;
        } else
            return false;
    }

    public boolean isWired() {
        for (WireColor color : WireColor.values()) {
            if (isWired(color))
                return true;
        }

        return false;
    }

    @Override
    public boolean isWired(WireColor color) {
        return wireSet[color.ordinal()];
    }

    /**
     * 4.2.2: the other pipe has this colour, and either one of the two is a Structure Pipe or they are connected to
     * each other ({@code Utils.checkPipesConnections}).
     */
    @Override
    public boolean isWireConnectedTo(@Nullable BlockEntity tile, WireColor color) {
        if (!(tile instanceof TilePipe tilePipe) || tilePipe.isRemoved())
            return false;

        if (!tilePipe.gateHolder.wireSet[color.ordinal()])
            return false;

        return tilePipe.kind == PipeType.STRUCTURE || container.kind == PipeType.STRUCTURE
                || checkPipesConnections(container, tilePipe);
    }

    /** 4.2.2 {@code Utils.checkPipesConnections}: two adjacent pipes that are connected towards each other. */
    private static boolean checkPipesConnections(TilePipe a, TilePipe b) {
        for (Direction side : Direction.values()) {
            if (a.getBlockPos().relative(side).equals(b.getBlockPos()))
                return a.isPipeConnected(side) && b.isPipeConnected(side.getOpposite());
        }
        return false;
    }

    // / GATE

    @Override
    public boolean hasGate() {
        return gate != null;
    }

    /**
     * 4.2.2 {@code Pipe.hasGate(side)}, the gate's box for the ray trace ({@link PipeParts}): not on a side with a
     * facade or a plug, nor opposite a pipe's only connection.
     */
    public boolean hasGate(Direction side) {
        if (!hasGate() && gateKind == Gate.GateKind.None)
            return false;
        if (container.hasFacade(side))
            return false;
        if (container.hasPlug(side))
            return false;

        int connections = 0;
        Direction targetOrientation = null;
        for (Direction o : Direction.values()) {
            if (container.isPipeConnected(o)) {
                connections++;
                if (connections == 1)
                    targetOrientation = o;
            }
        }

        if (connections > 1 || connections == 0)
            return true;

        return targetOrientation.getOpposite() != side;
    }

    /** 4.2.2 {@code Pipe.canConnectRedstone}. */
    public boolean canConnectRedstone() {
        return hasGate();
    }

    /**
     * 4.2.2 {@code Pipe.isPoweringTo}: 15 towards every side while the gate emits redstone, except towards a pipe this
     * one is connected to. {@code direction} is the vanilla {@code getSignal} direction, from the block asking towards
     * this pipe.
     */
    public int isPoweringTo(Direction direction) {
        if (gate != null && gate.isEmittingRedstone()) {
            Direction o = direction.getOpposite();
            BlockEntity tile = getTile(o);

            if (tile instanceof TilePipe && container.isPipeConnected(o))
                return 0;

            return 15;
        }
        return 0;
    }

    public void resetGate() {
        if (gate == null)
            return;
        gate.resetGate();
        gate = null;
        internalUpdateScheduled = true;
        scheduleRenderUpdate();
        container.setChanged();
    }

    /** Called by the gate after resolving its actions; the module takes its own (4.2.2 pipes overrode this). */
    protected void actionsActivated(Map<IAction, Boolean> actions) {
        if (target != null)
            target.actionsActivated(actions);
    }

    /** The actions the gate can choose from on this pipe (4.2.2 {@code Pipe.getActions}). */
    public LinkedList<IAction> getActions() {
        LinkedList<IAction> result = new LinkedList<IAction>();

        if (hasGate()) {
            gate.addActions(result);
        }
        if (target != null) {
            target.addActions(result);
        }

        return result;
    }

    /** 4.2.2 {@code TileGenericPipe.getTriggers}: the redstone input triggers, offered on a pipe with a gate. */
    public LinkedList<ITrigger> getTriggers() {
        LinkedList<ITrigger> result = new LinkedList<ITrigger>();

        if (hasGate()) {
            result.add(CoreRegistry.triggerRedstoneActive.get());
            result.add(CoreRegistry.triggerRedstoneInactive.get());
        }

        return result;
    }

    /** 4.2.2 {@code Pipe.updateNeighbors}: this block and every neighbour tell their neighbours, for strong power. */
    protected void updateNeighbors(boolean needSelf) {
        Level level = level();
        if (level == null)
            return;
        Block block = container.getBlockState().getBlock();
        BlockPos pos = container.getBlockPos();
        if (needSelf) {
            level.updateNeighborsAt(pos, block);
        }
        for (Direction side : Direction.values()) {
            level.updateNeighborsAt(pos.relative(side), block);
        }
    }

    public void dropItem(ItemStack stack) {
        Level level = level();
        if (level == null || level.isClientSide() || stack.isEmpty())
            return;
        BlockPos pos = container.getBlockPos();
        InvUtils.dropItems(level, stack, pos.getX(), pos.getY(), pos.getZ());
    }

    /** 4.2.2 {@code Pipe.onBlockRemoval}: wires, gate, facades and plugs drop with the pipe. */
    public void onBlockRemoval() {
        for (IPipe.WireColor color : IPipe.WireColor.values()) {
            if (wireSet[color.ordinal()]) {
                dropItem(new ItemStack(TransportRegistry.getPipeWire(color)));
            }
        }

        if (hasGate()) {
            gate.dropGate();
            resetGate();
        }

        for (Direction direction : Direction.values()) {
            if (container.hasFacade(direction)) {
                container.dropFacade(direction);
            }
            if (container.hasPlug(direction)) {
                container.removeAndDropPlug(direction);
            }
        }
    }

    // / FITTING (4.2.2 BlockGenericPipe)

    /**
     * Uses a gate item on the pipe: fits it when the pipe has no gate, or, sneaking with the gate clicked, takes the
     * gate off. Both sides answer alike; only the server changes anything.
     */
    public boolean addOrStripGate(@Nullable Player player, ItemStack stack, Vec3 hit) {
        if (!hasGate()) {
            if (!isClientSide()) {
                gate = Gate.makeGate(this, stack);
                if (player == null || !player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                internalUpdateScheduled = true;
                scheduleRenderUpdate();
                container.setChanged();
            }
            return true;
        }
        if (player != null && player.isShiftKeyDown() && isGateHit(hit)) {
            return stripGate();
        }
        return false;
    }

    public boolean stripGate() {
        if (hasGate()) {
            if (!isClientSide()) {
                gate.dropGate();
                resetGate();
            }
            return true;
        }
        return false;
    }

    /** Uses a wire item on the pipe: lays the colour, or, sneaking on a pipe that has it, takes it off. */
    public boolean addOrStripWire(@Nullable Player player, ItemStack stack, IPipe.WireColor color) {
        if (addWire(color)) {
            if (!isClientSide() && (player == null || !player.getAbilities().instabuild)) {
                stack.shrink(1);
            }
            return true;
        }
        return player != null && player.isShiftKeyDown() && stripWire(color);
    }

    private boolean addWire(IPipe.WireColor color) {
        if (!wireSet[color.ordinal()]) {
            if (!isClientSide()) {
                wireSet[color.ordinal()] = true;
                signalStrength[color.ordinal()] = 0;
                onWiresChanged();
            }
            return true;
        }
        return false;
    }

    public boolean stripWire(IPipe.WireColor color) {
        if (wireSet[color.ordinal()]) {
            if (!isClientSide()) {
                dropItem(new ItemStack(TransportRegistry.getPipeWire(color)));
                wireSet[color.ordinal()] = false;
                onWiresChanged();
            }
            return true;
        }
        return false;
    }

    /** 4.2.2 {@code stripEquipment} after the facade ({@code BlockPipe.useWithoutItem}): wires, red to yellow, then the gate. */
    public boolean stripEquipment() {
        for (IPipe.WireColor color : IPipe.WireColor.values()) {
            if (stripWire(color))
                return true;
        }

        return stripGate();
    }

    /** A wire was laid or taken off: this pipe and its neighbours redo signals and wire render state. */
    private void onWiresChanged() {
        internalUpdateScheduled = true;
        scheduleRenderUpdate();
        container.setChanged();
        for (Direction side : Direction.values()) {
            if (getTile(side) instanceof TilePipe neighbour) {
                neighbour.gateHolder.internalUpdateScheduled = true;
                neighbour.gateHolder.scheduleRenderUpdate();
            }
        }
    }

    /**
     * Whether a click at {@code hit} lands on the gate: 4.2.2 ray-traced the gate boxes on the unconnected faces of the
     * pipe centre. Here: the hit is on the centre box, on a face whose side is not connected.
     */
    public boolean isGateHit(Vec3 hit) {
        if (!hasGate() && gateKind == Gate.GateKind.None)
            return false;
        BlockPos pos = container.getBlockPos();
        double x = hit.x - pos.getX(), y = hit.y - pos.getY(), z = hit.z - pos.getZ();
        double min = CoreConstants.PIPE_MIN_POS - 0.001, max = CoreConstants.PIPE_MAX_POS + 0.001;
        if (x < min || x > max || y < min || y > max || z < min || z > max)
            return false;
        for (Direction side : Direction.values()) {
            double along = side.getAxis().choose(x, y, z);
            double face = side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? CoreConstants.PIPE_MAX_POS
                    : CoreConstants.PIPE_MIN_POS;
            if (Math.abs(along - face) < 0.01 && !container.isPipeConnected(side) && !container.hasFacade(side)
                    && !container.hasPlug(side))
                return true;
        }
        return false;
    }

    private boolean isClientSide() {
        Level level = level();
        return level != null && level.isClientSide();
    }

    // / IPipe

    @Override
    public TilePipe getContainer() {
        return container;
    }

    // / SAVING & LOADING

    public void writeToNBT(ValueOutput data) {
        if (gate != null) {
            gate.writeToNBT(data.child("Gate"));
        }

        for (int i = 0; i < 4; ++i) {
            data.putBoolean("wireSet[" + i + "]", wireSet[i]);
        }
        data.putBoolean("redstonePowered", redstonePowered);
    }

    public void readFromNBT(ValueInput data) {
        ValueInput gateNBT = data.child("Gate").orElse(null);
        gate = gateNBT == null ? null : Gate.makeGate(this, gateNBT);

        for (int i = 0; i < 4; ++i) {
            wireSet[i] = data.getBooleanOr("wireSet[" + i + "]", false);
        }
        redstonePowered = data.getBooleanOr("redstonePowered", false);
        initialized = false;
        scheduleRenderUpdate();
    }

    // / RENDER STATE (4.2.2 TileGenericPipe.refreshRenderState and PipeRenderState's wire and gate parts)

    /** Server: recomputes what the client draws, and syncs the pipe when it changed. */
    private void refreshRenderState() {
        int wires = 0;
        byte[] connections = new byte[4];
        for (WireColor color : WireColor.values()) {
            int c = color.ordinal();
            if (!wireSet[c])
                continue;
            wires |= 1 << c;
            if (signalStrength[c] > 0)
                wires |= 1 << (4 + c);
            for (Direction side : Direction.values()) {
                if (isWireConnectedTo(getTile(side), color))
                    connections[c] |= (byte) (1 << side.get3DDataValue());
            }
        }
        Gate.GateKind kind = gate == null ? Gate.GateKind.None : gate.kind;
        boolean autarchic = gate instanceof GateVanilla vanilla && vanilla.hasPulser();
        boolean lit = gate instanceof GateVanilla vanilla && vanilla.isLit(gate.isGateActive());

        if (wires != wireState || !Arrays.equals(connections, wireConnections) || kind != gateKind
                || autarchic != gateAutarchic || lit != gateLit) {
            wireState = wires;
            System.arraycopy(connections, 0, wireConnections, 0, 4);
            gateKind = kind;
            gateAutarchic = autarchic;
            gateLit = lit;
            container.sendNetworkUpdate();
        }
    }

    public void writeRenderState(RegistryFriendlyByteBuf data) {
        data.writeByte(wireState);
        data.writeBytes(wireConnections);
        data.writeByte(gateKind.ordinal() | (gateAutarchic ? 0x10 : 0) | (gateLit ? 0x20 : 0));
    }

    /** Client: reads the render state, and keeps a gate of the synced kind for the gate GUI. */
    public void readRenderState(RegistryFriendlyByteBuf data) {
        wireState = data.readByte() & 0xFF;
        data.readBytes(wireConnections);
        int gateByte = data.readByte();
        Gate.GateKind[] kinds = Gate.GateKind.values();
        gateKind = kinds[Math.min(gateByte & 0x0F, kinds.length - 1)];
        gateAutarchic = (gateByte & 0x10) != 0;
        gateLit = (gateByte & 0x20) != 0;
        for (WireColor color : WireColor.values()) {
            wireSet[color.ordinal()] = hasWire(color);
        }

        if (gateKind == Gate.GateKind.None) {
            gate = null;
        } else if (!(gate instanceof GateVanilla vanilla) || gate.kind != gateKind
                || vanilla.hasPulser() != gateAutarchic) {
            gate = new GateVanilla(this, gateKind, gateAutarchic);
        }
    }

    public boolean hasWire(WireColor color) {
        return (wireState & 1 << color.ordinal()) != 0;
    }

    public boolean isWireLit(WireColor color) {
        return (wireState & 1 << (4 + color.ordinal())) != 0;
    }

    public boolean isWireConnected(WireColor color, Direction side) {
        return (wireConnections[color.ordinal()] & 1 << side.get3DDataValue()) != 0;
    }

    public Gate.GateKind getRenderedGateKind() {
        return gateKind;
    }

    public boolean isRenderedGateAutarchic() {
        return gateAutarchic;
    }

    public boolean isRenderedGateLit() {
        return gateLit;
    }
}
