/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: energy is Forge Energy (FE) held as long, every MJ value × 10 (plan section
 * 7); perdition keeps its fractional rate through a thousandths-of-FE remainder; ForgeDirection becomes Direction;
 * NBT becomes ValueInput/ValueOutput; the Forge Energy capability inserts through a transaction journal.
 */
package buildcraftreforged.api.power;

import buildcraftreforged.api.core.SafeTimeTracker;
import java.util.function.LongSupplier;
import net.minecraft.core.Direction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * The PowerHandler is similar to FluidTank in that it holds your power and
 * allows standardized interaction between machines.
 *
 * To receive power to your machine you needs create an instance of PowerHandler
 * and implement IPowerReceptor on the TileEntity.
 *
 * If you plan emit power, you need only implement IPowerEmitter. You do not
 * need a PowerHandler. Engines have a PowerHandler because they can also
 * receive power from other Engines.
 *
 * See TileRefinery for a simple example of a power using machine.
 *
 * All amounts are Forge Energy (FE): 4.2.2's MJ values × 10. Register {@link PowerEnergyHandler} so that FE sources
 * from other mods can fill the handler.
 *
 * @see IPowerReceptor
 * @see IPowerEmitter
 *
 * @author CovertJaguar <http://www.railcraft.info/>
 */
public final class PowerHandler {

    public static enum Type {

        ENGINE, GATE, MACHINE, PIPE, STORAGE;

        public boolean canReceiveFromPipes() {
            switch (this) {
                case MACHINE:
                case STORAGE:
                    return true;
                default:
                    return false;
            }
        }

        public boolean eatsEngineExcess() {
            switch (this) {
                case MACHINE:
                case STORAGE:
                    return true;
                default:
                    return false;
            }
        }
    }

    /**
     * Extend this class to create custom Perdition algorithms (its not final).
     *
     * NOTE: It is not possible to create a Zero perdition algorithm.
     *
     * The rate is FE per tick and may be fractional (4.2.2's 0.01 MJ/t is 0.1 FE/t). The PowerHandler keeps the
     * fraction between calls, to a thousandth of an FE, so a fractional rate loses exactly that much over time.
     */
    public static class PerditionCalculator {

        /** 4.2.2: 1 MJ/t. */
        public static final float DEFAULT_POWERLOSS = 10F;
        /** 4.2.2: 0.01 MJ/t. */
        public static final float MIN_POWERLOSS = 0.1F;
        private final float powerLoss;

        public PerditionCalculator() {
            powerLoss = DEFAULT_POWERLOSS;
        }

        /**
         * Simple constructor for simple Perdition per tick.
         *
         * @param powerLoss power loss per tick, in FE
         */
        public PerditionCalculator(float powerLoss) {
            if (powerLoss < MIN_POWERLOSS) {
                powerLoss = MIN_POWERLOSS;
            }
            this.powerLoss = powerLoss;
        }

        /**
         * Apply the perdition algorithm to the current stored energy. This
         * function can only be called once per tick, but it might not be called
         * every tick. It is triggered by any manipulation of the stored energy.
         *
         * @param powerHandler the PowerHandler requesting the perdition update
         * @param current the current stored energy, in FE, including the fraction already lost
         * @param ticksPassed ticks since the last time this function was called
         * @return the new stored energy, in FE
         */
        public double applyPerdition(PowerHandler powerHandler, double current, long ticksPassed) {
            current -= (double) powerLoss * ticksPassed;
            if (current < 0) {
                current = 0;
            }
            return current;
        }

        /**
         * Taxes a flat rate on all incoming power.
         *
         * Defaults to 0% tax rate.
         *
         * @return percent of input to tax
         */
        public float getTaxPercent() {
            return 0;
        }
    }
    public static final PerditionCalculator DEFAULT_PERDITION = new PerditionCalculator();
    /** Perdition fractions are kept in thousandths of an FE. */
    static final long PERDITION_PRECISION = 1000;
    private long minEnergyReceived;
    private long maxEnergyReceived;
    private long maxEnergyStored;
    private long activationEnergy;
    private long energyStored = 0;
    /** Thousandths of an FE already lost to perdition from the top whole FE of energyStored; 0 when it is empty. */
    private long perditionDebt = 0;
    private final SafeTimeTracker doWorkTracker = new SafeTimeTracker();
    private final SafeTimeTracker sourcesTracker = new SafeTimeTracker();
    private final SafeTimeTracker perditionTracker = new SafeTimeTracker();
    public final int[] powerSources = new int[6];
    public final IPowerReceptor receptor;
    private PerditionCalculator perdition;
    private final PowerReceiver receiver;
    private final Type type;
    /** Game time for the trackers in unit tests; null reads the receptor's level, as 4.2.2 did. */
    private final @Nullable LongSupplier clock;
    private final EnergyJournal energyJournal = new EnergyJournal();
    /** Sides that inserted Forge Energy in the open transaction, as bits by ordinal; marked as sources on commit. */
    private int pendingSources = 0;

    public PowerHandler(IPowerReceptor receptor, Type type) {
        this(receptor, type, null);
    }

    PowerHandler(IPowerReceptor receptor, Type type, @Nullable LongSupplier clock) {
        this.receptor = receptor;
        this.type = type;
        this.receiver = new PowerReceiver();
        this.perdition = DEFAULT_PERDITION;
        this.clock = clock;
    }

    public PowerReceiver getPowerReceiver() {
        return receiver;
    }

    public long getMinEnergyReceived() {
        return minEnergyReceived;
    }

    public long getMaxEnergyReceived() {
        return maxEnergyReceived;
    }

    public long getMaxEnergyStored() {
        return maxEnergyStored;
    }

    public long getActivationEnergy() {
        return activationEnergy;
    }

    public long getEnergyStored() {
        return energyStored;
    }

    /**
     * Setup your PowerHandler's settings. All values are FE (4.2.2 MJ × 10).
     *
     * @param minEnergyReceived This is the minimum about of power that will be
     * accepted by the PowerHandler. This should generally be greater than the
     * activationEnergy if you plan to use the doWork() callback. Anything
     * greater than 10 will prevent Redstone Engines from powering this Provider.
     * @param maxEnergyReceived The maximum amount of power accepted by the
     * PowerHandler. This should generally be less than 5000. Too low and larger
     * engines will overheat while trying to power the machine. Too high, and
     * the engines will never warm up. Greater values also place greater strain
     * on the power net.
     * @param activationEnergy If the stored energy is greater than this value,
     * the doWork() callback is called (once per tick).
     * @param maxStoredEnergy The maximum amount of power this PowerHandler can
     * store. Values tend to range between 1000 and 50000. With 10000 and 15000
     * being common.
     */
    public void configure(long minEnergyReceived, long maxEnergyReceived, long activationEnergy, long maxStoredEnergy) {
        if (minEnergyReceived > maxEnergyReceived) {
            maxEnergyReceived = minEnergyReceived;
        }
        this.minEnergyReceived = minEnergyReceived;
        this.maxEnergyReceived = maxEnergyReceived;
        this.maxEnergyStored = maxStoredEnergy;
        this.activationEnergy = activationEnergy;
    }

    /**
     * Allows you define perdition in terms of loss/ticks.
     *
     * This function is mostly for legacy implementations. See
     * PerditionCalculator for more complex perdition formulas.
     *
     * @param powerLoss FE lost every powerLossRegularity ticks (4.2.2 MJ × 10)
     * @param powerLossRegularity
     * @see PerditionCalculator
     */
    public void configurePowerPerdition(int powerLoss, int powerLossRegularity) {
        if (powerLoss == 0 || powerLossRegularity == 0) {
            perdition = new PerditionCalculator(0);
            return;
        }
        perdition = new PerditionCalculator((float) powerLoss / (float) powerLossRegularity);
    }

    /**
     * Allows you to define a new PerditionCalculator class to handler perdition
     * calculations.
     *
     * For example if you want exponentially increasing loss based on amount
     * stored.
     *
     * @param perdition
     */
    public void setPerdition(PerditionCalculator perdition) {
        if (perdition == null)
            perdition = DEFAULT_PERDITION;
        this.perdition = perdition;
    }

    public PerditionCalculator getPerdition() {
        if (perdition == null)
            return DEFAULT_PERDITION;
        return perdition;
    }

    /**
     * Ticks the power handler. You should call this if you can, but its not
     * required. TileBuildCraft calls it every tick for an IPowerReceptor.
     *
     * If you don't call it, the possibility exists for some weirdness with the
     * perdition algorithm and work callback as its possible they will not be
     * called on every tick they otherwise would be. You should be able to
     * design around this though if you are aware of the limitations.
     */
    public void update() {
        applyPerdition();
        applyWork();
        validateEnergy();
    }

    private boolean markTimeIfDelay(SafeTimeTracker tracker) {
        if (clock != null)
            return tracker.markTimeIfDelay(clock.getAsLong(), 1);
        return tracker.markTimeIfDelay(receptor.getWorld(), 1);
    }

    private void applyPerdition() {
        if (markTimeIfDelay(perditionTracker) && energyStored > 0) {
            long ticksPassed = perditionTracker.durationOfLastDelay();
            double current = energyStored - (double) perditionDebt / PERDITION_PRECISION;
            double newEnergy = getPerdition().applyPerdition(this, current, ticksPassed);
            if (!(newEnergy == 0 || newEnergy < current))
                newEnergy = DEFAULT_PERDITION.applyPerdition(this, current, ticksPassed);
            setExactEnergy(newEnergy);
            validateEnergy();
        }
    }

    /**
     * Stores a fractional amount: whole FE rounded up, the missing fraction as perditionDebt. Rounding to a
     * thousandth first absorbs the floating point error of repeated fractional losses.
     */
    private void setExactEnergy(double exact) {
        long thousandths = Math.max(0, Math.round(exact * PERDITION_PRECISION));
        energyStored = Math.ceilDiv(thousandths, PERDITION_PRECISION);
        perditionDebt = energyStored * PERDITION_PRECISION - thousandths;
    }

    private void applyWork() {
        if (energyStored >= activationEnergy) {
            if (markTimeIfDelay(doWorkTracker)) {
                receptor.doWork(this);
            }
        }
    }

    private void updateSources(@Nullable Direction source) {
        if (markTimeIfDelay(sourcesTracker)) {
            for (int i = 0; i < 6; ++i) {
                powerSources[i] -= (int) sourcesTracker.durationOfLastDelay();
                if (powerSources[i] < 0) {
                    powerSources[i] = 0;
                }
            }
        }

        if (source != null)
            powerSources[source.ordinal()] = 10;
    }

    /**
     * Extract energy from the PowerHandler. You must call this even if doWork()
     * triggers.
     *
     * @param min
     * @param max
     * @param doUse
     * @return amount used
     */
    public long useEnergy(long min, long max, boolean doUse) {
        applyPerdition();

        long result = 0;

        if (energyStored >= min) {
            if (energyStored <= max) {
                result = energyStored;
                if (doUse) {
                    energyStored = 0;
                }
            } else {
                result = max;
                if (doUse) {
                    energyStored -= max;
                }
            }
        }

        validateEnergy();

        return result;
    }

    public void readFromNBT(ValueInput data) {
        readFromNBT(data, "powerProvider");
    }

    public void readFromNBT(ValueInput data, String tag) {
        ValueInput nbt = data.childOrEmpty(tag);
        energyStored = Math.max(0, nbt.getLongOr("storedEnergy", 0));
        perditionDebt = 0;
    }

    public void writeToNBT(ValueOutput data) {
        writeToNBT(data, "powerProvider");
    }

    public void writeToNBT(ValueOutput data, String tag) {
        ValueOutput nbt = data.child(tag);
        nbt.putLong("storedEnergy", energyStored);
    }

    public final class PowerReceiver {

        private PowerReceiver() {
        }

        public long getMinEnergyReceived() {
            return minEnergyReceived;
        }

        public long getMaxEnergyReceived() {
            return maxEnergyReceived;
        }

        public long getMaxEnergyStored() {
            return maxEnergyStored;
        }

        public long getActivationEnergy() {
            return activationEnergy;
        }

        public long getEnergyStored() {
            return energyStored;
        }

        public Type getType() {
            return type;
        }

        public void update() {
            PowerHandler.this.update();
        }

        /**
         * The amount of power that this PowerHandler currently needs.
         *
         * @return
         */
        public long powerRequest() {
            update();
            return Math.min(maxEnergyReceived, maxEnergyStored - energyStored);
        }

        /**
         * Add power to the PowerReceiver from an external source.
         *
         * IPowerEmitters are responsible for calling this themselves.
         *
         * @param quantity
         * @param from
         * @return the amount of power used
         */
        public long receiveEnergy(Type source, final long quantity, @Nullable Direction from) {
            long used = quantity;
            if (source == Type.ENGINE) {
                if (used < minEnergyReceived) {
                    return 0;
                } else if (used > maxEnergyReceived) {
                    used = maxEnergyReceived;
                }
            }

            updateSources(from);

            used -= tax(used);

            used = addEnergy(used);

            applyWork();

            if (source == Type.ENGINE && type.eatsEngineExcess()) {
                used = Math.min(quantity, maxEnergyReceived);
            }

            return used;
        }

        /**
         * Forge Energy from {@link PowerEnergyHandler}: receiveEnergy for a Type.PIPE source, capped at
         * maxEnergyReceived per call as 4.2.2's power pipes capped themselves through powerRequest. Changes only the
         * stored energy, which the transaction can revert; the source side is marked and doWork runs once it commits.
         */
        long insertEnergy(long quantity, @Nullable Direction from, TransactionContext transaction) {
            long used = Math.min(quantity, maxEnergyReceived);
            used -= tax(used);
            used = Math.min(used, maxEnergyStored - energyStored);
            if (used <= 0)
                return 0;

            energyJournal.updateSnapshots(transaction);
            energyStored += used;
            if (from != null)
                pendingSources |= 1 << from.ordinal();
            return used;
        }
    }

    private long tax(long quantity) {
        return (long) (quantity * getPerdition().getTaxPercent());
    }

    /**
     *
     * @return the amount the power changed by
     */
    public long addEnergy(long quantity) {
        energyStored += quantity;

        if (energyStored > maxEnergyStored) {
            quantity -= energyStored - maxEnergyStored;
            energyStored = maxEnergyStored;
        } else if (energyStored < 0) {
            quantity -= energyStored;
            energyStored = 0;
        }

        applyPerdition();

        return quantity;
    }

    public void setEnergy(long quantity) {
        this.energyStored = quantity;
        validateEnergy();
    }

    public boolean isPowerSource(Direction from) {
        return powerSources[from.ordinal()] != 0;
    }

    private void validateEnergy() {
        if (energyStored < 0) {
            energyStored = 0;
        }
        if (energyStored > maxEnergyStored) {
            energyStored = maxEnergyStored;
        }
        if (energyStored == 0) {
            perditionDebt = 0;
        }
    }

    private record EnergySnapshot(long energyStored, long perditionDebt, int pendingSources) {
    }

    private final class EnergyJournal extends SnapshotJournal<EnergySnapshot> {

        @Override
        protected EnergySnapshot createSnapshot() {
            return new EnergySnapshot(energyStored, perditionDebt, pendingSources);
        }

        @Override
        protected void revertToSnapshot(EnergySnapshot snapshot) {
            energyStored = snapshot.energyStored();
            perditionDebt = snapshot.perditionDebt();
            pendingSources = snapshot.pendingSources();
        }

        @Override
        protected void onRootCommit(EnergySnapshot originalState) {
            int sources = pendingSources;
            pendingSources = 0;
            for (Direction side : Direction.values()) {
                if ((sources & 1 << side.ordinal()) != 0)
                    updateSources(side);
            }
            applyWork();
        }
    }
}
