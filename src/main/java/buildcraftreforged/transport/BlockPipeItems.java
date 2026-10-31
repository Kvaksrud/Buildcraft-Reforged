package buildcraftreforged.transport;

import buildcraftreforged.api.transport.IPipeTile.PipeType;
import buildcraftreforged.core.IMachine;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Item pipe of one tier. Connects to item handlers with at least one slot and to machines that manage solids (4.2.2
 * {@code PipeTransportItems.canPipeConnect}).
 */
public class BlockPipeItems extends BlockPipe {

    public BlockPipeItems(PipeMaterial material, Properties properties) {
        super(PipeType.ITEM, material, properties);
    }

    @Override
    protected boolean isEndpoint(TilePipe pipe, Direction side) {
        ResourceHandler<ItemResource> handler = pipe.getEndpointCapability(Capabilities.Item.BLOCK, side);
        if (handler != null && handler.size() > 0)
            return true;
        // 4.2.2 also connected to an IMachine that managed solids (mining well, quarry): machines without an inventory
        // that push their output into the pipe.
        return pipe.getLevel() != null && pipe.getLevel().isLoaded(pipe.getBlockPos().relative(side))
                && pipe.getLevel().getBlockEntity(pipe.getBlockPos().relative(side)) instanceof IMachine machine
                && machine.manageSolids();
    }
}
