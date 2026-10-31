package buildcraftreforged.transport.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** docs/TRANSPORT.md section 14, #51: the one-fluid lock of 7.3 and what a pipe records for the flow record (9.2). */
class FluidPipeStateTest {

    private static final int LOCK = 20;

    /** See {@code TankManagerTest.bindFluidComponents}: the unit-test game binds no fluid components. */
    @BeforeAll
    static void bindFluidComponents() {
        BuiltInRegistries.FLUID.listElements()
                .filter(holder -> !holder.areComponentsBound())
                .forEach(holder -> holder.bindComponents(DataComponentMap.EMPTY));
    }

    private static void record(FluidPipeState state, FluidResource fluid, long amount, long now) {
        try (Transaction transaction = Transaction.openRoot()) {
            state.recordPassed(fluid, amount, Direction.WEST, Direction.EAST, now, transaction);
            transaction.commit();
        }
    }

    @Test
    void newPipeIsUnlocked() {
        FluidPipeState state = new FluidPipeState();
        assertTrue(state.lockedFluid(0, LOCK).isEmpty());
        assertTrue(state.canPass(FluidResource.of(Fluids.LAVA), 0, LOCK));
    }

    @Test
    void lockRefusesOtherFluidsUntilItExpires() {
        FluidResource water = FluidResource.of(Fluids.WATER);
        FluidResource lava = FluidResource.of(Fluids.LAVA);
        FluidPipeState state = new FluidPipeState();
        record(state, water, 40, 100);

        assertEquals(water, state.lockedFluid(100, LOCK));
        assertTrue(state.canPass(water, 110, LOCK), "the locked fluid itself");
        assertFalse(state.canPass(lava, 100, LOCK), "lava in the tick water passed");
        assertFalse(state.canPass(lava, 100 + LOCK - 1, LOCK), "lava on the last locked tick");
        assertTrue(state.canPass(lava, 100 + LOCK, LOCK), "lava after fluidLockTicks");
        assertTrue(state.lockedFluid(100 + LOCK, LOCK).isEmpty());

        // Water flowing again renews the lock.
        record(state, water, 40, 115);
        assertFalse(state.canPass(lava, 100 + LOCK, LOCK));
    }

    @Test
    void abortedTransferNeitherLocksNorRecords() {
        FluidPipeState state = new FluidPipeState();
        try (Transaction transaction = Transaction.openRoot()) {
            state.recordPassed(FluidResource.of(Fluids.WATER), 40, Direction.WEST, Direction.EAST, 5, transaction);
        }
        assertTrue(state.lockedFluid(5, LOCK).isEmpty());
        assertEquals(0, state.passed(5));
        assertEquals(Long.MIN_VALUE, state.lastFlowTick());
    }

    @Test
    void recordsAmountPerTickAndSides() {
        FluidResource water = FluidResource.of(Fluids.WATER);
        FluidPipeState state = new FluidPipeState();
        record(state, water, 30, 7);
        record(state, water, 12, 7);
        assertEquals(42, state.passed(7));
        assertEquals(0, state.passed(8), "a new tick starts at 0");
        assertEquals(Direction.WEST.get3DDataValue(), state.entrySide());
        assertEquals(Direction.EAST.get3DDataValue(), state.exitSide());
        record(state, water, 5, 9);
        assertEquals(5, state.passed(9));

        try (Transaction transaction = Transaction.openRoot()) {
            state.recordPassed(water, 1, null, null, 10, transaction);
            transaction.commit();
        }
        assertEquals(FluidPipeState.CENTRE, state.entrySide());
        assertEquals(FluidPipeState.CENTRE, state.exitSide());
        assertEquals(10, state.lastFlowTick());
    }
}
