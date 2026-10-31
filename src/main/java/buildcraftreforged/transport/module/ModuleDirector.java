package buildcraftreforged.transport.module;

import buildcraftreforged.api.gates.IAction;
import buildcraftreforged.api.transport.IPipeTile.PipeType;
import buildcraftreforged.transport.IPipeGateTarget;
import buildcraftreforged.transport.PipeModuleType;
import buildcraftreforged.transport.PowerMode;
import buildcraftreforged.transport.TilePipe;
import buildcraftreforged.transport.TransportRegistry;
import buildcraftreforged.transport.triggers.ActionPipeDirection;
import buildcraftreforged.transport.triggers.ActionPowerLimiter;
import java.util.LinkedList;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The Director (docs/TRANSPORT.md 8.4; 4.2.2 {@code PipeItemsIron}, {@code PipeLogicIron}, {@code PipeFluidsIron}):
 * the walk may leave the pipe only by {@link #getFacing() facing}, so everything goes out of that side; when the side
 * cannot take it, the walk backs up to the previous junction (4.3). A valid facing is a connected side whose neighbour
 * is an endpoint or a pipe without an Extractor-family module. The wrench, the Pipe Direction gate action and a
 * change of redstone power (Q11) move it on.
 *
 * <p>On a power pipe the Director is the Power Limiter (Q7, 4.2.2 {@code PipePowerIron}): the seven {@link PowerMode}s,
 * 20 to 1 280 FE/t, set by the wrench (next one) and the Power Limiter gate action.
 */
public class ModuleDirector extends PipeModule {

    private @Nullable Direction facing;
    /** A fresh 4.2.2 iron kinesis pipe had metadata 0, the lowest limit. */
    private PowerMode powerMode = PowerMode.M2;
    /** 4.2.2 {@code lastPower}: not saved, read again when the pipe loads. */
    private @Nullable Boolean lastPower;

    ModuleDirector() {
        super(PipeModuleType.DIRECTOR);
    }

    public @Nullable Direction getFacing() {
        return facing;
    }

    /** The Power Limiter's cap in FE/t; a power pipe's routing reads it through {@link TilePipe#getPowerLimit()}. */
    public int getPowerLimit() {
        return powerMode.maxPower;
    }

    public PowerMode getPowerMode() {
        return powerMode;
    }

    /** 4.2.2 {@code PipePowerIron.setMode}, for the wrench and the Power Limiter gate action. */
    public void setPowerMode(TilePipe pipe, PowerMode mode) {
        if (mode != powerMode) {
            powerMode = mode;
            pipe.setChanged();
        }
    }

    public boolean isValidFacing(TilePipe pipe, Direction side) {
        Level level = pipe.getLevel();
        if (level == null || !pipe.isPipeConnected(side))
            return false;
        if (!(level.getBlockEntity(pipe.getBlockPos().relative(side)) instanceof TilePipe neighbour))
            return true;
        PipeModuleType module = neighbour.getModuleType();
        return module != PipeModuleType.EXTRACTOR && module != PipeModuleType.FILTERED_EXTRACTOR;
    }

    /**
     * 4.2.2 {@code setFacing}, which the Pipe Direction gate action calls: sets the facing if it is valid and
     * different; returns whether it changed.
     */
    public boolean setFacing(TilePipe pipe, Direction side) {
        if (side == facing || !isValidFacing(pipe, side))
            return false;
        facing = side;
        pipe.setChanged();
        return true;
    }

    /** 4.2.2 {@code switchPosition}: the next valid facing after the current one. */
    public void switchPosition(TilePipe pipe) {
        Direction next = ModuleFacing.next(facing, side -> isValidFacing(pipe, side));
        if (next != null)
            setFacing(pipe, next);
    }

    @Override
    public boolean allowsOutput(TilePipe pipe, Direction side) {
        // The Power Limiter does not direct (power routing is a tree, docs/TRANSPORT.md 7.4); it only caps.
        return pipe.kind == PipeType.POWER || facing == side;
    }

    /** 4.2.2 {@code onBlockPlaced}: start at UP, then move to the first valid side. */
    @Override
    public void onInstalled(TilePipe pipe) {
        if (pipe.kind == PipeType.POWER)
            return;
        facing = Direction.UP;
        switchPosition(pipe);
        if (!isValidFacing(pipe, facing))
            facing = null;
    }

    @Override
    public void onNeighbourChange(TilePipe pipe) {
        if (pipe.kind == PipeType.POWER || pipe.getLevel() == null)
            return;
        // A Director fitted before its pipe's connections settled has no facing yet; give it the first valid one.
        if (facing == null)
            switchPosition(pipe);
        // 4.2.2 switchOnRedstone (Q11): every change of redstone power moves the facing on.
        boolean power = pipe.getLevel().hasNeighborSignal(pipe.getBlockPos());
        if (lastPower != null && power != lastPower)
            switchPosition(pipe);
        lastPower = power;
    }

    @Override
    public boolean onWrench(TilePipe pipe, Player player) {
        if (pipe.kind == PipeType.POWER) {
            setPowerMode(pipe, powerMode.getNext());
            player.sendOverlayMessage(Component.translatable("chat.pipe.power.limiter.mode", getPowerLimit()));
        } else {
            switchPosition(pipe);
        }
        return true;
    }

    /**
     * The gate actions (docs/TRANSPORT.md 8.4, 12): one Pipe Direction per connected side (4.2.2
     * {@code PipeItemsIron.getActions}), or on a power pipe the seven Power Limiter actions ({@code PipePowerIron}).
     * The first active one is applied after each gate resolution.
     */
    @Override
    public IPipeGateTarget gateTarget(TilePipe pipe) {
        return new IPipeGateTarget() {
            @Override
            public void addActions(LinkedList<IAction> list) {
                if (pipe.kind == PipeType.POWER) {
                    TransportRegistry.actionPowerLimiter.forEach(action -> list.add(action.get()));
                    return;
                }
                for (Direction side : Direction.values()) {
                    if (pipe.isPipeConnected(side))
                        list.add(TransportRegistry.actionPipeDirection.get(side.ordinal()).get());
                }
            }

            @Override
            public void actionsActivated(Map<IAction, Boolean> actions) {
                for (Map.Entry<IAction, Boolean> action : actions.entrySet()) {
                    if (!Boolean.TRUE.equals(action.getValue()))
                        continue;
                    if (action.getKey() instanceof ActionPipeDirection direction && pipe.kind != PipeType.POWER) {
                        setFacing(pipe, direction.direction);
                        break;
                    }
                    if (action.getKey() instanceof ActionPowerLimiter limiter && pipe.kind == PipeType.POWER) {
                        setPowerMode(pipe, limiter.limit);
                        break;
                    }
                }
            }
        };
    }

    @Override
    public void save(ValueOutput output) {
        output.putInt("facing", ModuleFacing.toIndex(facing));
        output.putInt("powerMode", powerMode.ordinal());
    }

    @Override
    public void load(ValueInput input) {
        facing = ModuleFacing.fromIndex(input.getIntOr("facing", -1));
        int mode = input.getIntOr("powerMode", 0);
        powerMode = mode >= 0 && mode < PowerMode.VALUES.length ? PowerMode.VALUES[mode] : PowerMode.M2;
    }
}
