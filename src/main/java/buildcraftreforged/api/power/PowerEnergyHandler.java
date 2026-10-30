/*
 * Buildcraft Reforged, 2026: new file, no 4.2.2 original. The Forge Energy side of 4.2.2's PowerHandler.PowerReceiver
 * (plan section 7).
 */
package buildcraftreforged.api.power;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * One side of an {@link IPowerReceptor} as a Forge Energy handler ({@code Capabilities.Energy.BLOCK}). NeoForge 26.x
 * replaced {@code IEnergyStorage} with the transaction-based {@link EnergyHandler}; this is the adapter plan section 7
 * calls "IEnergyStorage".
 *
 * <ul>
 * <li>Amount and capacity are the side's PowerReceiver stored and max stored energy, in FE.</li>
 * <li>{@link #insert} accepts FE on a side whose receiver is not null, unless the receptor is an {@link IPowerEmitter}
 * that emits from that side (an engine's output side). It works as 4.2.2's receiveEnergy from a power pipe: no
 * minimum, at most maxEnergyReceived per call, the perdition tax taken, limited by free space. The null side never
 * accepts.</li>
 * <li>{@link #extract} always returns 0: 4.2.2 machines never gave power back, and engines push on their pulse.</li>
 * </ul>
 *
 * The side's receiver is looked up on every call, so a rotated engine answers for its new output side. A tile whose
 * receivers appear or disappear invalidates its capabilities ({@code level.invalidateCapabilities(pos)}).
 */
public final class PowerEnergyHandler implements EnergyHandler {

    private final IPowerReceptor receptor;
    private final @Nullable Direction side;

    public PowerEnergyHandler(IPowerReceptor receptor, @Nullable Direction side) {
        this.receptor = receptor;
        this.side = side;
    }

    /**
     * Registers {@code Capabilities.Energy.BLOCK} for every side of the type's tiles; call from the module's
     * RegisterCapabilitiesEvent listener. A side whose getPowerReceiver returns null has no capability, as 4.2.2
     * never received power there.
     */
    public static <T extends BlockEntity & IPowerReceptor> void registerEnergyHandler(RegisterCapabilitiesEvent event,
            BlockEntityType<T> type) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK, type,
                (tile, side) -> tile.getPowerReceiver(side) == null ? null : new PowerEnergyHandler(tile, side));
    }

    private PowerHandler.@Nullable PowerReceiver receiver() {
        return receptor.getPowerReceiver(side);
    }

    /** Whether FE may be inserted on this side; plan section 7's canReceive(). */
    public boolean canReceive() {
        if (side == null || receiver() == null)
            return false;
        return !(receptor instanceof IPowerEmitter emitter && emitter.canEmitPowerFrom(side));
    }

    @Override
    public long getAmountAsLong() {
        PowerHandler.PowerReceiver receiver = receiver();
        return receiver == null ? 0 : receiver.getEnergyStored();
    }

    @Override
    public long getCapacityAsLong() {
        PowerHandler.PowerReceiver receiver = receiver();
        return receiver == null ? 0 : receiver.getMaxEnergyStored();
    }

    @Override
    public int insert(int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonNegative(amount);
        if (amount == 0 || !canReceive())
            return 0;
        return (int) receiver().insertEnergy(amount, side, transaction);
    }

    @Override
    public int extract(int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonNegative(amount);
        return 0;
    }
}
