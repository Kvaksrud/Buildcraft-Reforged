package buildcraftreforged.transport;

import buildcraftreforged.api.transport.IPipeTile.PipeType;
import net.minecraft.core.Direction;

/**
 * The Cobblestone Structure Pipe (docs/TRANSPORT.md 11, 4.2.2 {@code PipeStructureCobblestone} with
 * {@code PipeTransportStructure}): connects only to other structure pipes, carries nothing and never joins a network.
 * It holds gates, pipe wire, facades and plugs like any pipe, and carries wire across any wired neighbour
 * ({@code PipeGateHolder.isWireConnectedTo}). It sits outside the tier ladder, so it has no material.
 */
public class BlockPipeStructure extends BlockPipe {

    public BlockPipeStructure(Properties properties) {
        super(PipeType.STRUCTURE, null, properties);
    }

    /** 4.2.2 {@code PipeTransportStructure.canPipeConnect}: never a block, only another structure pipe (rule 4 of 3.2). */
    @Override
    protected boolean isEndpoint(TilePipe pipe, Direction side) {
        return false;
    }
}
