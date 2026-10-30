package buildcraftreforged.api.power;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import buildcraftreforged.api.power.PowerHandler.PerditionCalculator;
import buildcraftreforged.api.power.PowerHandler.Type;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.junit.jupiter.api.Test;

/**
 * Pins the FE port of PowerHandler against 4.2.2. Every expected value is worked out from the 4.2.2 code path in MJ
 * (float) and multiplied by 10 (plan section 7). The configure and perdition arguments are those of the 4.2.2 tiles
 * named in each test.
 */
class PowerHandlerTest {

    /** A receptor with its own game clock, standing in for a tile and its level. */
    private static final class Receptor implements IPowerReceptor {
        long time = 0;
        int work = 0;
        final PowerHandler handler;

        Receptor(Type type) {
            handler = new PowerHandler(this, type, () -> time);
        }

        @Override
        public PowerHandler.PowerReceiver getPowerReceiver(Direction side) {
            return handler.getPowerReceiver();
        }

        @Override
        public void doWork(PowerHandler workProvider) {
            work++;
        }

        @Override
        public Level getWorld() {
            return null;
        }

        /** Advances the clock and updates, as TileBuildCraft.updateEntity does every tick. */
        void tickTo(long t) {
            time = t;
            handler.update();
        }
    }

    // TileRefinery: configure(50, 150, 25, 1000); configurePowerPerdition(1, 1)
    private static Receptor refinery() {
        Receptor r = new Receptor(Type.MACHINE);
        r.handler.configure(500, 1500, 250, 10000);
        r.handler.configurePowerPerdition(10, 1);
        return r;
    }

    // TilePump: configure(1, 15, 10, 100); configurePowerPerdition(1, 100)
    private static Receptor pump() {
        Receptor r = new Receptor(Type.MACHINE);
        r.handler.configure(10, 150, 100, 1000);
        r.handler.configurePowerPerdition(10, 100);
        return r;
    }

    // PipePowerWood: configure(2, 500, 1, 1500); configurePowerPerdition(1, 10)
    private static Receptor woodenPowerPipe() {
        Receptor r = new Receptor(Type.PIPE);
        r.handler.configure(20, 5000, 10, 15000);
        r.handler.configurePowerPerdition(10, 10);
        return r;
    }

    // TileEngine: configure(minEnergyReceived(), maxEnergyReceived(), 1, getMaxEnergy()); configurePowerPerdition(1, 100)
    // TileEngineStone: minEnergyReceived 2 (TileEngine default), maxEnergyReceived 200, getMaxEnergy 1000
    private static Receptor stirlingEngine() {
        Receptor r = new Receptor(Type.ENGINE);
        r.handler.configure(20, 2000, 10, 10000);
        r.handler.configurePowerPerdition(10, 100);
        return r;
    }

    // --- Pulse sizes: TileEngine.sendPower -> receiveEnergy(Type.ENGINE, extracted, side) ---

    @Test
    void redstoneEnginePulseIsTenFe() {
        // 4.2.2 TileEngineWood: addEnergy(1) every 16 ticks while powered; maxEnergyExtracted = 1 + MIN_POWERLOSS
        // = 1.01 MJ. extractEnergy(min 1, max 15, false) with energy 1 MJ: actualMax = min(15, 1.01) = 1.01,
        // energy 1 < 1.01 so extracted = energy = 1 MJ. The pump: 1 >= minEnergyReceived 1, 1 <= max 15,
        // addEnergy(1) = 1 MJ. 1 MJ x 10 = 10 FE.
        Receptor pump = pump();
        assertEquals(10, pump.handler.getPowerReceiver().receiveEnergy(Type.ENGINE, 10, Direction.UP));
        assertEquals(10, pump.handler.getEnergyStored());
        assertTrue(pump.handler.isPowerSource(Direction.UP));
        assertFalse(pump.handler.isPowerSource(Direction.DOWN));
    }

    @Test
    void pulseBelowMinEnergyReceivedIsRefused() {
        // 4.2.2 receiveEnergy: source ENGINE and used < minEnergyReceived -> return 0, nothing stored.
        // Redstone engine into the refinery: 1 MJ < 50 MJ. Into the wooden power pipe: 1 MJ < 2 MJ.
        Receptor refinery = refinery();
        assertEquals(0, refinery.handler.getPowerReceiver().receiveEnergy(Type.ENGINE, 10, Direction.UP));
        assertEquals(0, refinery.handler.getEnergyStored());
        assertFalse(refinery.handler.isPowerSource(Direction.UP));

        Receptor pipe = woodenPowerPipe();
        assertEquals(0, pipe.handler.getPowerReceiver().receiveEnergy(Type.ENGINE, 10, Direction.UP));

        // Exactly the minimum passes: 49.9 MJ refused, 50 MJ accepted.
        assertEquals(0, refinery.handler.getPowerReceiver().receiveEnergy(Type.ENGINE, 499, Direction.UP));
        assertEquals(500, refinery.handler.getPowerReceiver().receiveEnergy(Type.ENGINE, 500, Direction.UP));
    }

    @Test
    void pulseAboveMaxEnergyReceivedIsCapped() {
        // TileEngineIron.maxEnergyExtracted = 500 MJ. 4.2.2 receiveEnergy into the refinery: 500 > max 150 ->
        // used = 150, addEnergy(150) stores 150 MJ; MACHINE eats engine excess -> returns min(500, 150) = 150 MJ.
        Receptor refinery = refinery();
        assertEquals(1500, refinery.handler.getPowerReceiver().receiveEnergy(Type.ENGINE, 5000, Direction.NORTH));
        assertEquals(1500, refinery.handler.getEnergyStored());
    }

    @Test
    void stirlingPulseIntoWoodenPowerPipe() {
        // TileEngineStone.maxEnergyExtracted = 100 MJ. Pipe: 2 <= 100 <= 500 -> addEnergy(100) = 100 MJ; PIPE does
        // not eat engine excess, so the return is what was stored.
        Receptor pipe = woodenPowerPipe();
        assertEquals(1000, pipe.handler.getPowerReceiver().receiveEnergy(Type.ENGINE, 1000, Direction.WEST));
        assertEquals(1000, pipe.handler.getEnergyStored());
    }

    @Test
    void machineEatsEngineExcessWhenFull() {
        // Pump full at 100 MJ, 10 MJ pulse: addEnergy(10) clamps and returns 0, but MACHINE eats engine excess, so
        // receiveEnergy returns min(quantity 10, maxEnergyReceived 15) = 10 MJ and the engine loses it.
        Receptor pump = pump();
        pump.handler.setEnergy(1000);
        assertEquals(100, pump.handler.getPowerReceiver().receiveEnergy(Type.ENGINE, 100, Direction.UP));
        assertEquals(1000, pump.handler.getEnergyStored());

        // A 20 MJ pulse: used capped to 15, still returns min(20, 15) = 15 MJ.
        assertEquals(150, pump.handler.getPowerReceiver().receiveEnergy(Type.ENGINE, 200, Direction.UP));
    }

    @Test
    void engineDoesNotEatExcessWhenFull() {
        // Stirling engine powered by another engine, buffer full at 1000 MJ: addEnergy returns 0; ENGINE does not
        // eat engine excess, so 0 is returned and the sending engine keeps its energy.
        Receptor engine = stirlingEngine();
        engine.handler.setEnergy(10000);
        assertEquals(0, engine.handler.getPowerReceiver().receiveEnergy(Type.ENGINE, 1000, Direction.UP));

        // Partly full at 995 MJ, 10 MJ pulse: addEnergy(10) stores 5 and returns 5 MJ.
        engine.handler.setEnergy(9950);
        assertEquals(50, engine.handler.getPowerReceiver().receiveEnergy(Type.ENGINE, 100, Direction.UP));
        assertEquals(10000, engine.handler.getEnergyStored());
    }

    @Test
    void pipeSourceIgnoresReceiveLimits() {
        // 4.2.2 receiveEnergy applies min/max only to Type.ENGINE: a power pipe may deliver 1 MJ to the refinery
        // (min 50) and 200 MJ (max 150).
        Receptor refinery = refinery();
        assertEquals(10, refinery.handler.getPowerReceiver().receiveEnergy(Type.PIPE, 10, Direction.UP));
        assertEquals(2000, refinery.handler.getPowerReceiver().receiveEnergy(Type.PIPE, 2000, Direction.UP));
        assertEquals(2010, refinery.handler.getEnergyStored());
    }

    @Test
    void configureRaisesMaxToMin() {
        // 4.2.2 configure: minEnergyReceived > maxEnergyReceived -> maxEnergyReceived = minEnergyReceived.
        Receptor r = new Receptor(Type.MACHINE);
        r.handler.configure(300, 100, 0, 1000);
        assertEquals(300, r.handler.getMaxEnergyReceived());
    }

    @Test
    void powerRequestIsMaxReceivedOrFreeSpace() {
        // 4.2.2 powerRequest = min(maxEnergyReceived, maxEnergyStored - energyStored). Refinery with 900 MJ:
        // min(150, 100) = 100 MJ; empty: min(150, 1000) = 150 MJ.
        Receptor refinery = refinery();
        assertEquals(1500, refinery.handler.getPowerReceiver().powerRequest());
        refinery.handler.setEnergy(9000);
        assertEquals(1000, refinery.handler.getPowerReceiver().powerRequest());
    }

    // --- Activation energy: applyWork calls doWork once per tick while energyStored >= activationEnergy ---

    @Test
    void doWorkStartsAtActivationEnergy() {
        // TilePump activation 10 MJ = 100 FE. 9.9 MJ: no work; 10 MJ: work.
        Receptor pump = pump();
        pump.handler.setEnergy(99);
        pump.tickTo(0);
        assertEquals(0, pump.work);

        pump.handler.setEnergy(100);
        pump.tickTo(1);
        assertEquals(1, pump.work);
    }

    @Test
    void doWorkRunsOncePerTick() {
        // doWorkTracker.markTimeIfDelay(world, 1): a second update or receiveEnergy in the same tick does not call
        // doWork again.
        Receptor refinery = refinery();
        refinery.handler.setEnergy(5000);
        refinery.tickTo(0);
        refinery.handler.update();
        refinery.handler.getPowerReceiver().receiveEnergy(Type.ENGINE, 500, Direction.UP);
        assertEquals(1, refinery.work);

        refinery.tickTo(1);
        assertEquals(2, refinery.work);
    }

    @Test
    void receivingTriggersWork() {
        // receiveEnergy calls applyWork after addEnergy: a 50 MJ pulse into the empty refinery (activation 25 MJ)
        // runs doWork at once.
        Receptor refinery = refinery();
        refinery.handler.getPowerReceiver().receiveEnergy(Type.ENGINE, 500, Direction.UP);
        assertEquals(1, refinery.work);
    }

    // --- useEnergy ---

    @Test
    void useEnergyTakesMaxOrAllAboveMin() {
        // 4.2.2 useEnergy(min, max, doUse): stored < min -> 0; stored <= max -> all; else max.
        Receptor refinery = refinery();
        refinery.handler.setEnergy(300);
        assertEquals(0, refinery.handler.useEnergy(400, 1000, true));
        assertEquals(300, refinery.handler.getEnergyStored());
        assertEquals(300, refinery.handler.useEnergy(100, 1000, false));
        assertEquals(300, refinery.handler.getEnergyStored());
        assertEquals(250, refinery.handler.useEnergy(100, 250, true));
        assertEquals(50, refinery.handler.getEnergyStored());
        assertEquals(50, refinery.handler.useEnergy(0, 250, true));
        assertEquals(0, refinery.handler.getEnergyStored());
    }

    // --- Perdition: applyPerdition once per tick, loss = powerLoss * ticksPassed ---

    @Test
    void perditionRatesAreMjTimesTen() {
        // DEFAULT_POWERLOSS 1 MJ/t, MIN_POWERLOSS 0.01 MJ/t.
        assertEquals(10F, PerditionCalculator.DEFAULT_POWERLOSS);
        assertEquals(0.1F, PerditionCalculator.MIN_POWERLOSS);
    }

    @Test
    void defaultPerditionIsTenFePerTick() {
        // 1 MJ/t: 100 MJ -> 99 after one tick -> 89 after ten more.
        Receptor r = new Receptor(Type.MACHINE);
        r.handler.configure(0, 1000, 100000, 10000);
        r.handler.setEnergy(1000);
        r.tickTo(0); // first mark: durationOfLastDelay is 0, nothing lost
        assertEquals(1000, r.handler.getEnergyStored());
        r.tickTo(1);
        assertEquals(990, r.handler.getEnergyStored());
        r.tickTo(11);
        assertEquals(890, r.handler.getEnergyStored());
    }

    @Test
    void refineryPerditionIsTenFePerTick() {
        // configurePowerPerdition(1, 1): 1 / 1 = 1 MJ/t.
        Receptor r = refinery();
        r.handler.setEnergy(1000);
        r.tickTo(0);
        r.tickTo(5);
        assertEquals(950, r.handler.getEnergyStored());
    }

    @Test
    void woodenPowerPipePerditionIsOneFePerTick() {
        // configurePowerPerdition(1, 10): 1 / 10 = 0.1 MJ/t = 1 FE/t.
        Receptor r = woodenPowerPipe();
        r.handler.setEnergy(1000);
        r.tickTo(0);
        r.tickTo(7);
        assertEquals(993, r.handler.getEnergyStored());
    }

    @Test
    void enginePerditionLosesOneFeEveryTenTicks() {
        // TileEngine and TilePump: configurePowerPerdition(1, 100) = 0.01 MJ/t = 0.1 FE/t. 4.2.2 held 99.99 MJ
        // after one tick; here 999.9 FE is reported as 1000 until a whole FE is gone, at tick 10 (99.9 MJ).
        // After 100 ticks 4.2.2 lost 1 MJ = 10 FE.
        Receptor r = pump();
        r.handler.setEnergy(1000);
        r.tickTo(0);
        for (long t = 1; t < 10; t++) {
            r.tickTo(t);
            assertEquals(1000, r.handler.getEnergyStored(), "tick " + t);
        }
        r.tickTo(10);
        assertEquals(999, r.handler.getEnergyStored());
        for (long t = 11; t <= 100; t++)
            r.tickTo(t);
        assertEquals(990, r.handler.getEnergyStored());
    }

    @Test
    void fractionalPerditionAcrossSkippedTicks() {
        // A tile that is not ticked: 25 ticks at 0.01 MJ/t = 0.25 MJ, leaving 99.75 MJ = 997.5 FE, shown as 998;
        // 5 more ticks reach 99.7 MJ = 997 FE.
        Receptor r = pump();
        r.handler.setEnergy(1000);
        r.tickTo(0);
        r.tickTo(25);
        assertEquals(998, r.handler.getEnergyStored());
        r.tickTo(30);
        assertEquals(997, r.handler.getEnergyStored());
    }

    @Test
    void zeroPerditionIsRaisedToMinimum() {
        // PipeFluidsWood/PipeItemsWood: configurePowerPerdition(0, 0) -> PerditionCalculator(0) -> MIN_POWERLOSS
        // 0.01 MJ/t. 1000 ticks lose 10 MJ = 100 FE.
        Receptor r = new Receptor(Type.PIPE);
        r.handler.configure(10, 1000, 10, 2500);
        r.handler.configurePowerPerdition(0, 0);
        r.handler.setEnergy(2500);
        r.tickTo(0);
        r.tickTo(1000);
        assertEquals(2400, r.handler.getEnergyStored());
    }

    @Test
    void laserAndQuarryCalculators() {
        // TileLaser / PipeItemsObsidian: new PerditionCalculator(0.5F) MJ/t = 5 FE/t.
        // TileQuarry: new PerditionCalculator(2 * miningMultiplier) MJ/t = 20 FE/t at multiplier 1.
        Receptor laser = new Receptor(Type.MACHINE);
        laser.handler.configure(250, 1500, 250, 10000);
        laser.handler.setPerdition(new PerditionCalculator(5F));
        laser.handler.setEnergy(1000);
        laser.tickTo(0);
        laser.tickTo(4);
        assertEquals(980, laser.handler.getEnergyStored());

        Receptor quarry = new Receptor(Type.MACHINE);
        quarry.handler.configure(500, 1000, 10000, 100000);
        quarry.handler.setPerdition(new PerditionCalculator(20F));
        quarry.handler.setEnergy(1000);
        quarry.tickTo(0);
        quarry.tickTo(3);
        assertEquals(940, quarry.handler.getEnergyStored());
    }

    @Test
    void perditionThatLosesNothingFallsBackToDefault() {
        // 4.2.2 applyPerdition: if the calculator neither empties nor reduces the store, DEFAULT_PERDITION (1 MJ/t)
        // is applied instead: "It is not possible to create a Zero perdition algorithm."
        Receptor r = new Receptor(Type.MACHINE);
        r.handler.configure(0, 1000, 100000, 10000);
        r.handler.setPerdition(new PerditionCalculator() {
            @Override
            public double applyPerdition(PowerHandler powerHandler, double current, long ticksPassed) {
                return current;
            }
        });
        r.handler.setEnergy(1000);
        r.tickTo(0);
        r.tickTo(2);
        assertEquals(980, r.handler.getEnergyStored());
    }

    @Test
    void perditionStopsAtZero() {
        // current -= powerLoss * ticksPassed; if (current < 0) current = 0. 0.5 MJ with 1 MJ/t is empty after one
        // tick, and the fraction left from a fractional rate is cleared with it.
        Receptor r = new Receptor(Type.MACHINE);
        r.handler.configure(0, 1000, 100000, 10000);
        r.handler.setEnergy(5);
        r.tickTo(0);
        r.tickTo(1);
        assertEquals(0, r.handler.getEnergyStored());

        Receptor pump = pump();
        pump.handler.setEnergy(10);
        pump.tickTo(0);
        pump.tickTo(5); // 0.95 MJ, debt of 0.5 FE
        assertEquals(10, pump.handler.useEnergy(0, 100, true));
        pump.handler.addEnergy(10);
        pump.tickTo(10); // a fresh 1 MJ minus 5 ticks: 0.95 MJ, still reported as 10 FE
        assertEquals(10, pump.handler.getEnergyStored());
    }

    @Test
    void sourcesFadeAfterTenTicks() {
        // updateSources: the receiving side is set to 10 and every side counts down by the ticks since the last
        // call; isPowerSource is true until it reaches 0.
        Receptor pump = pump();
        pump.handler.getPowerReceiver().receiveEnergy(Type.ENGINE, 10, Direction.SOUTH);
        pump.time = 9;
        pump.handler.getPowerReceiver().receiveEnergy(Type.ENGINE, 10, null);
        assertTrue(pump.handler.isPowerSource(Direction.SOUTH));
        pump.time = 10;
        pump.handler.getPowerReceiver().receiveEnergy(Type.ENGINE, 10, null);
        assertFalse(pump.handler.isPowerSource(Direction.SOUTH));
    }

    @Test
    void savesStoredEnergyUnderTheOldTagNames() {
        Receptor r = refinery();
        r.handler.setEnergy(1234);
        TagValueOutput output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        r.handler.writeToNBT(output);
        CompoundTag tag = output.buildResult();
        assertEquals(1234L, tag.getCompoundOrEmpty("powerProvider").getLongOr("storedEnergy", -1));

        Receptor loaded = refinery();
        loaded.handler.readFromNBT(TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, tag));
        assertEquals(1234, loaded.handler.getEnergyStored());
    }
}
