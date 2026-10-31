package buildcraftreforged.transport.module;

import buildcraftreforged.api.transport.IPipeTile.PipeType;
import buildcraftreforged.transport.PipeModuleType;
import buildcraftreforged.transport.TilePipe;
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
 * is an endpoint or a pipe without an Extractor-family module. The wrench, the Pipe Direction gate action (#60) and a
 * change of redstone power (Q11) move it on.
 *
 * <p>On a power pipe the Director is the Power Limiter (Q7, 4.2.2 {@code PipePowerIron}): seven limits, 20 to 1 280
 * FE/t, the wrench moves to the next one.
 */
public class ModuleDirector extends PipeModule {

    /** 4.2.2 {@code PowerMode} M2 … M128 in MJ/t, × 10 FE per MJ (docs/PORTING-NOTES.md "Power"). */
    public static final int[] POWER_LIMITS = {20, 40, 80, 160, 320, 640, 1280};

    private @Nullable Direction facing;
    private int powerMode;
    /** 4.2.2 {@code lastPower}: not saved, read again when the pipe loads. */
    private @Nullable Boolean lastPower;

    ModuleDirector() {
        super(PipeModuleType.DIRECTOR);
    }

    public @Nullable Direction getFacing() {
        return facing;
    }

    /** The Power Limiter's cap in FE/t. SEAM(#52): power routing reads it on a power pipe. */
    public int getPowerLimit() {
        return POWER_LIMITS[powerMode];
    }

    public int getPowerMode() {
        return powerMode;
    }

    /** The Power Limiter gate action (#60) calls this with the index of a limit in {@link #POWER_LIMITS}. */
    public void setPowerMode(TilePipe pipe, int mode) {
        if (mode >= 0 && mode < POWER_LIMITS.length && mode != powerMode) {
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
     * 4.2.2 {@code setFacing}, which the Pipe Direction gate action (#60) calls: sets the facing if it is valid and
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
            setPowerMode(pipe, (powerMode + 1) % POWER_LIMITS.length);
            player.sendOverlayMessage(Component.translatable("chat.pipe.power.limiter.mode", getPowerLimit()));
        } else {
            switchPosition(pipe);
        }
        return true;
    }

    @Override
    public void save(ValueOutput output) {
        output.putInt("facing", ModuleFacing.toIndex(facing));
        output.putInt("powerMode", powerMode);
    }

    @Override
    public void load(ValueInput input) {
        facing = ModuleFacing.fromIndex(input.getIntOr("facing", -1));
        int mode = input.getIntOr("powerMode", 0);
        powerMode = mode >= 0 && mode < POWER_LIMITS.length ? mode : 0;
    }
}
