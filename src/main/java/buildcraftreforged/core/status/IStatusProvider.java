/*
 * Buildcraft Reforged, 2026: new file, no 4.2.2 original. The data side of the status panel (plan section 10).
 */
package buildcraftreforged.core.status;

import buildcraftreforged.core.IMachine;
import buildcraftreforged.core.TileBuildCraft;
import org.jspecify.annotations.Nullable;

/**
 * Implemented by every block entity that works (plan section 10; porting checklist step 4). Each method is one line of
 * the status panel and is read on the server, only while a player has the panel open, at most every
 * {@link StatusWidget#SEND_INTERVAL} ticks. Return null (or {@link StatusReason#NONE}) for a line the block does not
 * have; the panel leaves it out.
 *
 * <p>The defaults read what the block entity already has: the State line follows {@link IMachine} (blocked, active or
 * idle) and the Owner line is {@link TileBuildCraft#getOwner()}. A block entity whose {@link #getStatusState()} is
 * {@link MachineStatus#BLOCKED} lights the red overlay on its model when its block has {@link StatusPanel#BLOCKED}.
 */
public interface IStatusProvider {

    /** The State line. Default: {@link IMachine#isBlocked()} → BLOCKED, {@link IMachine#isActive()} → WORKING, else IDLE. */
    default MachineStatus getStatusState() {
        if (this instanceof IMachine machine) {
            if (machine.isBlocked()) {
                return MachineStatus.BLOCKED;
            }
            return machine.isActive() ? MachineStatus.WORKING : MachineStatus.IDLE;
        }
        return MachineStatus.IDLE;
    }

    /** The Reason line: what is blocked. Shown only when not {@link StatusReason#NONE}. */
    default StatusReason getStatusReason() {
        return StatusReason.NONE;
    }

    /** The Flow line: the block's meter, or null for a block that moves nothing. */
    default @Nullable FlowMeter getStatusFlow() {
        return null;
    }

    /**
     * The Power line, or null for a block without power. Usually {@link StatusReport.Power#drawn} or
     * {@link StatusReport.Power#produced} with the {@code PowerHandler}'s stored and maximum energy.
     */
    default StatusReport.@Nullable Power getStatusPower() {
        return null;
    }

    /** The Heat line, engines only. */
    default StatusReport.@Nullable Heat getStatusHeat() {
        return null;
    }

    /** The Owner line: the player who placed the block (#27 records it; the fake player acts as them). */
    default @Nullable String getStatusOwner() {
        return this instanceof TileBuildCraft tile ? tile.getOwner() : null;
    }
}
