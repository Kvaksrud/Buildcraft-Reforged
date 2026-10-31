/*
 * Ported from buildcraft/transport/EnergyPulser.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: the pulse is Forge Energy (4.2.2 MJ x 10) and goes to the receptor with no
 * particular side (4.2.2 sent it from WEST); ValueInput/ValueOutput in place of NBT.
 */
package buildcraftreforged.transport;

import buildcraftreforged.api.power.IPowerReceptor;
import buildcraftreforged.api.power.PowerHandler;
import buildcraftreforged.api.power.PowerHandler.Type;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The autarchic gate's pulser. Every 10 ticks while active it gives its receptor {@code min(2^(count-1), 64) * 1.01}
 * MJ, now {@link #pulseEnergy} FE, where count is the number of gate slots holding the pulser action. Pipe modules need
 * no power (docs/TRANSPORT.md 12), so on a pipe the receptor is null and the pulser only lights the gate; the Basic
 * Extractor (#58) is the receptor it was kept for.
 */
public class EnergyPulser {

    private final @Nullable IPowerReceptor powerReceptor;

    private boolean isActive;
    private boolean singlePulse;
    private boolean hasPulsed;
    private int pulseCount;
    private int tick;

    public EnergyPulser(@Nullable IPowerReceptor receptor) {
        powerReceptor = receptor;
    }

    public void update() {
        if (!isActive && hasPulsed)
            hasPulsed = false;

        if (powerReceptor == null || !isActive || tick++ % 10 != 0)
            return;

        if (!singlePulse || !hasPulsed) {
            PowerHandler.PowerReceiver receiver = powerReceptor.getPowerReceiver(null);
            if (receiver != null)
                receiver.receiveEnergy(Type.GATE, pulseEnergy(pulseCount), null);
            hasPulsed = true;
        }
    }

    /** The FE one pulse carries: 4.2.2's {@code Math.min(1 << (pulseCount - 1), 64) * 1.01f} MJ, x 10. */
    public static long pulseEnergy(int pulseCount) {
        return (long) (Math.min(1 << (pulseCount - 1), 64) * 1.01f * 10);
    }

    public void enableSinglePulse(int count) {
        singlePulse = true;
        isActive = true;
        pulseCount = count;
    }

    public void enablePulse(int count) {
        isActive = true;
        singlePulse = false;
        pulseCount = count;
    }

    public void disablePulse() {
        if (!isActive) {
            hasPulsed = false;
        }
        isActive = false;
        pulseCount = 0;
    }

    public boolean isActive() {
        return isActive;
    }

    public void writeToNBT(ValueOutput nbttagcompound) {
        nbttagcompound.putBoolean("SinglePulse", singlePulse);
        nbttagcompound.putBoolean("IsActive", isActive);
        nbttagcompound.putBoolean("hasPulsed", hasPulsed);
        nbttagcompound.putInt("pulseCount", pulseCount);
        nbttagcompound.putInt("tick", tick);
    }

    public void readFromNBT(ValueInput nbttagcompound) {
        isActive = nbttagcompound.getBooleanOr("IsActive", false);
        singlePulse = nbttagcompound.getBooleanOr("SinglePulse", false);
        hasPulsed = nbttagcompound.getBooleanOr("hasPulsed", false);
        pulseCount = nbttagcompound.getIntOr("pulseCount", 0);
        tick = nbttagcompound.getIntOr("tick", 0);
    }
}
