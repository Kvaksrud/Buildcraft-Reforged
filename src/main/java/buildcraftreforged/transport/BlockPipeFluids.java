package buildcraftreforged.transport;

import buildcraftreforged.api.transport.IPipeTile.PipeType;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.ResourceHandler;

/** Fluid pipe of one tier. Connects to fluid handlers with at least one tank (4.2.2 {@code PipeTransportFluids.canPipeConnect}). */
public class BlockPipeFluids extends BlockPipe {

    public BlockPipeFluids(PipeMaterial material, Properties properties) {
        super(PipeType.FLUID, material, properties);
    }

    @Override
    protected boolean isEndpoint(TilePipe pipe, Direction side) {
        ResourceHandler<FluidResource> handler = pipe.getEndpointCapability(Capabilities.Fluid.BLOCK, side);
        return handler != null && handler.size() > 0;
    }
}
