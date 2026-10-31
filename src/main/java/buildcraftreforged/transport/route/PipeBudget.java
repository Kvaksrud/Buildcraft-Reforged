package buildcraftreforged.transport.route;

import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * The throughput allowance of one pipe (docs/TRANSPORT.md 5). An integer accumulator in the kind's unit (1/20 item,
 * 1 mB, 1 FE) that refills lazily by {@code refill} per game tick up to {@code cap}. Spending is part of a transaction,
 * so a simulated transfer (a transaction that is aborted) leaves the budget as it was.
 *
 * <p>Not saved: a new or loaded pipe starts at 0 ({@link #start}).
 */
public final class PipeBudget extends SnapshotJournal<PipeBudget.State> {

    record State(long budget, long tick) {
    }

    private static final long UNSET = Long.MIN_VALUE;

    private long budget;
    private long tick = UNSET;

    /** Starts at 0 at game time {@code now} (docs/TRANSPORT.md 5.2: "a new or loaded pipe"). */
    public void start(long now) {
        budget = 0;
        tick = now;
    }

    /**
     * The budget at game time {@code now}: refilled by {@code refill} for every tick since the last read, never above
     * {@code cap}. A smaller cap applies at once. A budget that was never started starts now, at 0.
     */
    public long available(long now, long refill, long cap) {
        if (tick == UNSET || now < tick) {
            start(now);
        } else if (now != tick) {
            long ticks = now - tick;
            budget = ticks >= cap / Math.max(refill, 1) + 1 ? cap : Math.min(cap, budget + refill * ticks);
            tick = now;
        }
        if (budget > cap)
            budget = cap;
        return budget;
    }

    /** Spends {@code amount} units in {@code transaction}; call {@link #available} first. */
    public void spend(long amount, TransactionContext transaction) {
        updateSnapshots(transaction);
        budget -= amount;
    }

    /**
     * The cap of docs/TRANSPORT.md 5.2: {@code burstSeconds} of refill, at least the cost of one unit of transfer.
     */
    public static long cap(long refill, double burstSeconds, long costPerUnit) {
        return Math.max(costPerUnit, Math.round(refill * 20 * burstSeconds));
    }

    @Override
    protected State createSnapshot() {
        return new State(budget, tick);
    }

    @Override
    protected void revertToSnapshot(State snapshot) {
        budget = snapshot.budget();
        tick = snapshot.tick();
    }

    @Override
    public String toString() {
        return "PipeBudget(" + budget + " at " + tick + ")";
    }
}
