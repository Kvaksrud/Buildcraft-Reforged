package buildcraftreforged.api.power;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import buildcraftreforged.api.power.PowerHandler.Type;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.Test;

/** Forge Energy on top of PowerHandler (plan section 7): what other mods' cables see. */
class PowerEnergyHandlerTest {

    /** An engine-like receptor: receives on every side, emits from {@code output}. */
    private static final class Receptor implements IPowerReceptor, IPowerEmitter {
        long time = 0;
        int work = 0;
        Direction output = null;
        boolean receives = true;
        final PowerHandler handler;

        Receptor(Type type) {
            handler = new PowerHandler(this, type, () -> time);
        }

        @Override
        public PowerHandler.PowerReceiver getPowerReceiver(Direction side) {
            return receives ? handler.getPowerReceiver() : null;
        }

        @Override
        public void doWork(PowerHandler workProvider) {
            work++;
        }

        @Override
        public Level getWorld() {
            return null;
        }

        @Override
        public boolean canEmitPowerFrom(Direction side) {
            return side == output;
        }
    }

    // TileRefinery: configure(50, 150, 25, 1000) MJ.
    private static Receptor refinery() {
        Receptor r = new Receptor(Type.MACHINE);
        r.handler.configure(500, 1500, 250, 10000);
        return r;
    }

    private static int insert(PowerEnergyHandler handler, int amount, boolean commit) {
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = handler.insert(amount, tx);
            if (commit)
                tx.commit();
            return inserted;
        }
    }

    @Test
    void reportsStoredAndCapacityInFe() {
        Receptor r = refinery();
        r.handler.setEnergy(4200);
        PowerEnergyHandler handler = new PowerEnergyHandler(r, Direction.UP);
        assertEquals(4200, handler.getAmountAsLong());
        assertEquals(10000, handler.getCapacityAsLong());
    }

    @Test
    void insertHasNoMinimumAndCapsAtMaxEnergyReceived() {
        // Like 4.2.2's power pipes (Type.PIPE, capped by powerRequest = min(150 MJ, free space)): 1 FE is
        // accepted although the refinery's engine minimum is 500 FE; 4000 FE is cut to 1500 FE per call.
        Receptor r = refinery();
        PowerEnergyHandler handler = new PowerEnergyHandler(r, Direction.UP);
        assertEquals(1, insert(handler, 1, true));
        assertEquals(1500, insert(handler, 4000, true));
        assertEquals(1501, r.handler.getEnergyStored());
    }

    @Test
    void insertStopsAtCapacity() {
        Receptor r = refinery();
        r.handler.setEnergy(9800);
        PowerEnergyHandler handler = new PowerEnergyHandler(r, Direction.UP);
        assertEquals(200, insert(handler, 1000, true));
        assertEquals(10000, r.handler.getEnergyStored());
        assertEquals(0, insert(handler, 1000, true));
    }

    @Test
    void abortedTransactionLeavesNothing() {
        Receptor r = refinery();
        PowerEnergyHandler handler = new PowerEnergyHandler(r, Direction.UP);
        assertEquals(1000, insert(handler, 1000, false));
        assertEquals(0, r.handler.getEnergyStored());
        assertEquals(0, r.work);
        assertFalse(r.handler.isPowerSource(Direction.UP));
    }

    @Test
    void commitMarksTheSourceAndRunsWork() {
        // receiveEnergy's updateSources and applyWork, deferred to the commit.
        Receptor r = refinery();
        PowerEnergyHandler handler = new PowerEnergyHandler(r, Direction.EAST);
        assertEquals(300, insert(handler, 300, true));
        assertTrue(r.handler.isPowerSource(Direction.EAST));
        assertEquals(1, r.work);
    }

    @Test
    void nestedAbortRevertsOnlyTheInnerInsert() {
        Receptor r = refinery();
        PowerEnergyHandler handler = new PowerEnergyHandler(r, Direction.UP);
        try (Transaction outer = Transaction.openRoot()) {
            handler.insert(100, outer);
            try (Transaction inner = Transaction.open(outer)) {
                handler.insert(200, inner);
            }
            outer.commit();
        }
        assertEquals(100, r.handler.getEnergyStored());
    }

    @Test
    void emittingSideRefusesEnergy() {
        // Plan section 7: an engine's output side does not receive (canReceive() == false).
        Receptor engine = new Receptor(Type.ENGINE);
        engine.handler.configure(20, 2000, 10, 10000);
        engine.output = Direction.NORTH;
        assertFalse(new PowerEnergyHandler(engine, Direction.NORTH).canReceive());
        assertEquals(0, insert(new PowerEnergyHandler(engine, Direction.NORTH), 100, true));
        assertTrue(new PowerEnergyHandler(engine, Direction.SOUTH).canReceive());
        assertEquals(100, insert(new PowerEnergyHandler(engine, Direction.SOUTH), 100, true));

        // The side is checked on every call: after rotating, the old output side receives.
        engine.output = Direction.SOUTH;
        assertEquals(0, insert(new PowerEnergyHandler(engine, Direction.SOUTH), 100, true));
        assertEquals(100, insert(new PowerEnergyHandler(engine, Direction.NORTH), 100, true));
    }

    @Test
    void nullSideAndNullReceiverRefuseEnergy() {
        Receptor r = refinery();
        assertFalse(new PowerEnergyHandler(r, null).canReceive());
        assertEquals(0, insert(new PowerEnergyHandler(r, null), 100, true));

        r.receives = false;
        PowerEnergyHandler handler = new PowerEnergyHandler(r, Direction.UP);
        assertEquals(0, insert(handler, 100, true));
        assertEquals(0, handler.getCapacityAsLong());
    }

    @Test
    void extractAlwaysReturnsZero() {
        Receptor r = refinery();
        r.handler.setEnergy(5000);
        PowerEnergyHandler handler = new PowerEnergyHandler(r, Direction.UP);
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(0, handler.extract(1000, tx));
            tx.commit();
        }
        assertEquals(5000, r.handler.getEnergyStored());
    }

    @Test
    void negativeAmountsAreRejected() {
        PowerEnergyHandler handler = new PowerEnergyHandler(refinery(), Direction.UP);
        try (Transaction tx = Transaction.openRoot()) {
            assertThrows(IllegalArgumentException.class, () -> handler.insert(-1, tx));
            assertThrows(IllegalArgumentException.class, () -> handler.extract(-1, tx));
        }
    }
}
