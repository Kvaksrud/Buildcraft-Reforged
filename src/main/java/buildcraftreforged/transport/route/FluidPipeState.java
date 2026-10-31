package buildcraftreforged.transport.route;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * What fluid transfers leave on one fluid pipe (docs/TRANSPORT.md 7.2, 7.3, 9.2): the fluid that last passed it and
 * when, which is both the one-fluid lock and the content of the flow record, the sides it came in and went out by, and
 * the mB that passed it this tick. Recording is part of the transfer's transaction, so a simulated transfer neither
 * locks nor records.
 *
 * <p>The lock (7.3): after fluid {@code F} passes, the pipe refuses every other fluid while
 * {@code now - lastFlowTick < transport.fluidLockTicks}; an expired lock counts as no lock.
 *
 * <p>Not saved: a loaded pipe starts unlocked, with nothing flowing.
 */
public final class FluidPipeState extends SnapshotJournal<FluidPipeState.State> {

    /** {@link #entrySide()} of the pipe a transfer started in from inside, and {@link #exitSide()} at a Void sink. */
    public static final int CENTRE = 6;

    record State(FluidResource fluid, long flowTick, long tick, long passed, byte sides) {
    }

    private static final long UNSET = Long.MIN_VALUE;

    private FluidResource fluid = FluidResource.EMPTY;
    private long flowTick = UNSET;
    private long tick = UNSET;
    private long passed;
    /** Bits 0-2 entry side, bits 3-5 exit side: the {@code sides} byte of the flow record (9.1). */
    private byte sides = (byte) (CENTRE | CENTRE << 3);

    /**
     * Records {@code amount} mB of {@code resource} passing at {@code now}, entering by {@code entry} and leaving by
     * {@code exit} (null: the centre, for a transfer that starts inside the pipe or ends at a Void sink), in
     * {@code transaction}. Locks the pipe to {@code resource} (7.3).
     */
    public void recordPassed(FluidResource resource, long amount, @Nullable Direction entry, @Nullable Direction exit,
            long now, TransactionContext transaction) {
        if (amount <= 0)
            return;
        updateSnapshots(transaction);
        if (tick != now) {
            tick = now;
            passed = 0;
        }
        passed += amount;
        fluid = resource;
        flowTick = now;
        sides = (byte) (index(entry) | index(exit) << 3);
    }

    private static int index(@Nullable Direction side) {
        return side == null ? CENTRE : side.get3DDataValue();
    }

    /** The fluid this pipe is locked to at {@code now}, or empty when the lock has expired or there never was one. */
    public FluidResource lockedFluid(long now, int lockTicks) {
        return fluid.isEmpty() || flowTick == UNSET || now - flowTick >= lockTicks ? FluidResource.EMPTY : fluid;
    }

    /** Whether {@code resource} may pass this pipe at {@code now}: no live lock, or a lock on the same fluid. */
    public boolean canPass(FluidResource resource, long now, int lockTicks) {
        FluidResource locked = lockedFluid(now, lockTicks);
        return locked.isEmpty() || locked.equals(resource);
    }

    /** The fluid that last passed this pipe, whether or not the lock is still live; empty if none since it loaded. */
    public FluidResource lastFluid() {
        return fluid;
    }

    /** The game time fluid last passed this pipe, or {@link Long#MIN_VALUE} if never since it loaded. */
    public long lastFlowTick() {
        return flowTick;
    }

    /** mB that passed this pipe in tick {@code now} so far. */
    public long passed(long now) {
        return tick == now ? passed : 0;
    }

    /** The side the last transfer entered by, 0-5 in side order, or {@link #CENTRE}. */
    public int entrySide() {
        return sides & 7;
    }

    /** The side the last transfer left by, 0-5 in side order, or {@link #CENTRE} for a Void sink. */
    public int exitSide() {
        return sides >> 3 & 7;
    }

    @Override
    protected State createSnapshot() {
        return new State(fluid, flowTick, tick, passed, sides);
    }

    @Override
    protected void revertToSnapshot(State snapshot) {
        fluid = snapshot.fluid();
        flowTick = snapshot.flowTick();
        tick = snapshot.tick();
        passed = snapshot.passed();
        sides = snapshot.sides();
    }
}
