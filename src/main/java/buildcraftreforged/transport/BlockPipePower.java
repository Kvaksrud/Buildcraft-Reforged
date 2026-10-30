package buildcraftreforged.transport;

import buildcraftreforged.api.transport.IPipeTile.PipeType;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EmptyEnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;

/**
 * Power pipe of one tier. Connects to any energy handler, receivers and sources alike: every power pipe accepts pushes
 * (docs/TRANSPORT.md Q6), where 4.2.2 {@code PipeTransportPower.canPipeConnect} took emitters on the wooden pipe only.
 * NeoForge's {@link EnergyHandler} has no {@code canReceive}/{@code canExtract}; the empty handler stands for "neither".
 */
public class BlockPipePower extends BlockPipe {

    public BlockPipePower(PipeMaterial material, Properties properties) {
        super(PipeType.POWER, material, properties);
    }

    @Override
    protected boolean isEndpoint(TilePipe pipe, Direction side) {
        EnergyHandler handler = pipe.getEndpointCapability(Capabilities.Energy.BLOCK, side);
        return handler != null && !(handler instanceof EmptyEnergyHandler);
    }
}
