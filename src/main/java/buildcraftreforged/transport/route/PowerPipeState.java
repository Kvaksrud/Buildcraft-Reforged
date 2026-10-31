package buildcraftreforged.transport.route;

import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * What the power phases leave on one power pipe (docs/TRANSPORT.md 7.4), for rendering (#59) and the Pipe Contents
 * triggers (#60): the FE that passed it this tick, the overload counter, and the answer of the last request phase that
 * went through it (Q21). Recording is part of the transfer's transaction, so a simulated push records nothing.
 *
 * <p>The overload counter is 4.2.2 {@code PipeTransportPower.overload}: every closed tick in which the pipe passed at
 * least 95 % of its refill raises it by one, every other tick lowers it by one, between 0 and {@link #OVERLOAD_TICKS};
 * the pipe is overloaded at {@link #OVERLOAD_TICKS}. Ticks are closed lazily, so an idle pipe costs nothing.
 *
 * <p>Not saved: a loaded pipe starts with nothing flowing.
 */
public final class PowerPipeState extends SnapshotJournal<PowerPipeState.State> {

    /** 4.2.2 {@code PipeTransportPower.OVERLOAD_TICKS}. */
    public static final int OVERLOAD_TICKS = 60;

    record State(long tick, long passed, int overload, boolean requested, long requestTick, long flowTick) {
    }

    private static final long UNSET = Long.MIN_VALUE;

    private long tick = UNSET;
    private long passed;
    private int overload;
    private boolean requested;
    private long requestTick = UNSET;
    private long flowTick = UNSET;

    /** Closes every tick before {@code now} with the given refill (FE/t), 4.2.2 {@code maxPower * 0.95}. */
    private void advance(long now, long refill) {
        if (tick == UNSET || now < tick) {
            tick = now;
            passed = 0;
            return;
        }
        if (now == tick)
            return;
        // The tick that had the flow, then the empty ticks after it.
        overload = passed > 0 && passed * 20 >= refill * 19 ? Math.min(OVERLOAD_TICKS, overload + 1)
                : Math.max(0, overload - 1);
        overload = (int) Math.max(0, overload - Math.min(OVERLOAD_TICKS, now - tick - 1));
        tick = now;
        passed = 0;
    }

    /** Adds {@code amount} FE passed at {@code now}, in {@code transaction}. */
    public void recordPassed(long amount, long now, long refill, TransactionContext transaction) {
        updateSnapshots(transaction);
        advance(now, refill);
        passed += amount;
        if (amount > 0)
            flowTick = now;
    }

    /**
     * Records the outcome of a request phase through this pipe at {@code now}: whether the receivers behind it asked
     * for more than it delivered (docs/TRANSPORT.md Q21).
     */
    public void recordRequest(boolean unmet, long now, TransactionContext transaction) {
        updateSnapshots(transaction);
        requested = unmet;
        requestTick = now;
    }

    /** FE that passed this pipe in tick {@code now} so far. */
    public long passed(long now, long refill) {
        advance(now, refill);
        return passed;
    }

    /** The game time power last passed this pipe, or {@link Long#MIN_VALUE} if never since it loaded. */
    public long lastFlowTick() {
        return flowTick;
    }

    /** The overload counter at {@code now}, over the closed ticks: 0 to {@link #OVERLOAD_TICKS}. */
    public int overload(long now, long refill) {
        advance(now, refill);
        return overload;
    }

    /** 4.2.2 {@code PipeTransportPower.isOverloaded}: the counter is at {@link #OVERLOAD_TICKS}. */
    public boolean isOverloaded(long now, long refill) {
        return overload(now, refill) >= OVERLOAD_TICKS;
    }

    /** The answer of the last request phase through this pipe, see {@link #recordRequest}. */
    public boolean lastRequestUnmet() {
        return requested;
    }

    /** The game time of the last request phase through this pipe, or {@link Long#MIN_VALUE} if none. */
    public long lastRequestTick() {
        return requestTick;
    }

    @Override
    protected State createSnapshot() {
        return new State(tick, passed, overload, requested, requestTick, flowTick);
    }

    @Override
    protected void revertToSnapshot(State snapshot) {
        tick = snapshot.tick();
        passed = snapshot.passed();
        overload = snapshot.overload();
        requested = snapshot.requested();
        requestTick = snapshot.requestTick();
        flowTick = snapshot.flowTick();
    }
}
