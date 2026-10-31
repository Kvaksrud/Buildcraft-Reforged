/*
 * Buildcraft Reforged, 2026: new file, no 4.2.2 original. Counts what a block moves for the status panel's Flow and
 * Power lines (plan section 10).
 */
package buildcraftreforged.core.status;

import java.util.function.LongSupplier;

/**
 * Counts items, fluid and energy moved in and out over the last second (20 ticks). A block entity keeps one, calls
 * {@link #itemsIn} and the others where it moves things, and returns it from
 * {@link IStatusProvider#getStatusFlow()}. Counting is cheap and needs no tick: each count goes into the bucket of the
 * current game tick, and {@link #snapshot()} adds up the buckets of the last {@link #WINDOW} ticks.
 *
 * <p>Server side only; nothing here is saved.
 */
public final class FlowMeter {
    /** Ticks the meter looks back: one second. */
    public static final int WINDOW = 20;

    private static final int ITEMS_IN = 0, ITEMS_OUT = 1, FLUID_IN = 2, FLUID_OUT = 3, ENERGY_IN = 4, ENERGY_OUT = 5;

    private final LongSupplier gameTime;
    private final long[][] buckets = new long[6][WINDOW];
    private final long[] bucketTime = new long[WINDOW];

    /**
     * @param gameTime the level's game time, for example {@code () -> level == null ? 0 : level.getGameTime()}
     */
    public FlowMeter(LongSupplier gameTime) {
        this.gameTime = gameTime;
        java.util.Arrays.fill(bucketTime, Long.MIN_VALUE);
    }

    public void itemsIn(int count) {
        add(ITEMS_IN, count);
    }

    public void itemsOut(int count) {
        add(ITEMS_OUT, count);
    }

    /** @param mb millibuckets */
    public void fluidIn(int mb) {
        add(FLUID_IN, mb);
    }

    /** @param mb millibuckets */
    public void fluidOut(int mb) {
        add(FLUID_OUT, mb);
    }

    /** Energy received or used, in FE. */
    public void energyIn(long fe) {
        add(ENERGY_IN, fe);
    }

    /** Energy produced or sent, in FE. */
    public void energyOut(long fe) {
        add(ENERGY_OUT, fe);
    }

    private void add(int channel, long amount) {
        if (amount <= 0) {
            return;
        }
        long now = gameTime.getAsLong();
        int slot = (int) Math.floorMod(now, (long) WINDOW);
        if (bucketTime[slot] != now) {
            bucketTime[slot] = now;
            for (long[] channelBuckets : buckets) {
                channelBuckets[slot] = 0;
            }
        }
        buckets[channel][slot] += amount;
    }

    /** Total of one channel over the last {@link #WINDOW} ticks, the current tick included. */
    private long total(int channel) {
        long now = gameTime.getAsLong();
        long sum = 0;
        for (int slot = 0; slot < WINDOW; slot++) {
            long age = now - bucketTime[slot];
            if (age >= 0 && age < WINDOW) {
                sum += buckets[channel][slot];
            }
        }
        return sum;
    }

    /** Energy in FE over the last second, for {@link StatusReport.Power}. */
    public long energyInPerSecond() {
        return total(ENERGY_IN);
    }

    public long energyOutPerSecond() {
        return total(ENERGY_OUT);
    }

    /** The Flow line: totals over the last second. */
    public StatusReport.Flow snapshot() {
        return new StatusReport.Flow(clamp(total(ITEMS_IN)), clamp(total(ITEMS_OUT)), clamp(total(FLUID_IN)),
                clamp(total(FLUID_OUT)));
    }

    private static int clamp(long value) {
        return (int) Math.min(Integer.MAX_VALUE, value);
    }
}
