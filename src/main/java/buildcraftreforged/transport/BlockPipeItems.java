package buildcraftreforged.transport;

import buildcraftreforged.api.transport.IPipeTile.PipeType;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

/** Item pipe of one tier. Connects to item handlers with at least one slot (4.2.2 {@code PipeTransportItems.canPipeConnect}). */
public class BlockPipeItems extends BlockPipe {

    public BlockPipeItems(PipeMaterial material, Properties properties) {
        super(PipeType.ITEM, material, properties);
    }

    @Override
    protected boolean isEndpoint(TilePipe pipe, Direction side) {
        ResourceHandler<ItemResource> handler = pipe.getEndpointCapability(Capabilities.Item.BLOCK, side);
        return handler != null && handler.size() > 0;
    }
}
