package buildcraftreforged.transport;

/**
 * The throughput caps of one tier (docs/TRANSPORT.md 2.1, PLAN 8.1): items per second for item pipes, mB per tick for
 * fluid pipes and FE per tick for power pipes. The same tier has the same row whatever the pipe carries.
 */
public record PipeTierStats(int itemsPerSecond, int fluidPerTick, int powerPerTick) {

    static final int TIER_1_ITEMS_PER_SECOND = 1;
    static final int TIER_1_FLUID_PER_TICK = 20;
    static final int TIER_1_POWER_PER_TICK = 80;

    /** The decided ladder: tier 1 is 1 item/s, 20 mB/t and 80 FE/t, and every tier doubles the one below it. */
    public static PipeTierStats defaults(PipeMaterial material) {
        int factor = 1 << (material.tier - 1);
        return new PipeTierStats(TIER_1_ITEMS_PER_SECOND * factor, TIER_1_FLUID_PER_TICK * factor,
                TIER_1_POWER_PER_TICK * factor);
    }
}
