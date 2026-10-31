package buildcraftreforged.gametest.status;

import buildcraftreforged.core.TileBuildCraft;
import buildcraftreforged.core.status.IStatusProvider;
import buildcraftreforged.core.status.MachineStatus;
import buildcraftreforged.core.status.StatusReason;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Cycles Working, Blocked, Idle every {@link #PHASE_TICKS} ticks, unless a test sets {@link #forced}. */
public class TileSampleStatus extends TileBuildCraft implements IStatusProvider {
    public static final int PHASE_TICKS = 100;
    private static final MachineStatus[] CYCLE = {MachineStatus.WORKING, MachineStatus.BLOCKED, MachineStatus.IDLE};

    public @Nullable MachineStatus forced;

    public TileSampleStatus(BlockPos pos, BlockState state) {
        super(SampleStatus.tileType, pos, state);
    }

    @Override
    public MachineStatus getStatusState() {
        if (forced != null) {
            return forced;
        }
        long time = level == null ? 0 : level.getGameTime();
        return CYCLE[(int) ((time / PHASE_TICKS) % CYCLE.length)];
    }

    @Override
    public StatusReason getStatusReason() {
        return getStatusState() == MachineStatus.BLOCKED ? StatusReason.NO_DESTINATION : StatusReason.NONE;
    }
}
