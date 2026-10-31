package buildcraftreforged.transport.module;

import buildcraftreforged.api.transport.IPipeTile.PipeType;
import buildcraftreforged.api.transport.PipeManager;
import buildcraftreforged.transport.PipeModuleType;
import buildcraftreforged.transport.TilePipe;
import buildcraftreforged.transport.graph.PipeNetwork;
import buildcraftreforged.transport.graph.PipeNode;
import buildcraftreforged.transport.route.FluidRouting;
import buildcraftreforged.transport.route.ItemRouting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

/**
 * The Extractor (docs/TRANSPORT.md 8.2; 4.2.2 {@code PipeItemsWood}, {@code PipeLogicWood}, {@code PipeFluidsWood}):
 * pulls from one adjacent block, its facing, at the pipe's tier rate, with no engine. A valid facing is a connected
 * side whose neighbour is not a pipe and is not refused by the {@link PipeManager} exclusion list. The facing moves to
 * the next valid side when it becomes invalid and on every wrench use.
 */
public class ModuleExtractor extends PipeModule {

    private @Nullable Direction facing;

    ModuleExtractor(PipeModuleType type) {
        super(type);
    }

    public @Nullable Direction getFacing() {
        return facing;
    }

    /** Sets the facing if it is valid for {@code pipe}; returns whether it was. */
    public boolean setFacing(TilePipe pipe, Direction side) {
        if (!isValidFacing(pipe, side))
            return false;
        facing = side;
        pipe.setChanged();
        return true;
    }

    public boolean isValidFacing(TilePipe pipe, Direction side) {
        Level level = pipe.getLevel();
        if (level == null || !pipe.isPipeConnected(side))
            return false;
        BlockPos neighbour = pipe.getBlockPos().relative(side);
        if (level.getBlockEntity(neighbour) instanceof TilePipe)
            return false;
        return pipe.kind == PipeType.FLUID ? PipeManager.canExtractFluids(this, level, neighbour)
                : PipeManager.canExtractItems(this, level, neighbour);
    }

    /** 4.2.2 {@code switchSource}: the next valid facing; none when no side is valid. */
    public void switchSource(TilePipe pipe) {
        Direction next = ModuleFacing.next(facing, side -> isValidFacing(pipe, side));
        if (next != facing) {
            facing = next;
            pipe.setChanged();
        }
    }

    /** 4.2.2 {@code switchSourceIfNeeded}. */
    public void switchSourceIfNeeded(TilePipe pipe) {
        if (facing == null || !isValidFacing(pipe, facing))
            switchSource(pipe);
    }

    @Override
    public void onInstalled(TilePipe pipe) {
        switchSourceIfNeeded(pipe);
    }

    @Override
    public void onNeighbourChange(TilePipe pipe) {
        switchSourceIfNeeded(pipe);
    }

    @Override
    public boolean onWrench(TilePipe pipe, Player player) {
        switchSource(pipe);
        return true;
    }

    @Override
    public void tickSource(ServerLevel level, PipeNetwork network, int node, TilePipe pipe) {
        if (facing == null)
            return;
        if (network.kind == PipeType.ITEM) {
            ResourceHandler<ItemResource> source = source(network, node, facing);
            if (source != null)
                extractItems(level, network, node, facing, source);
        }
        if (network.kind == PipeType.FLUID) {
            ResourceHandler<FluidResource> source = fluidSource(network, node, facing);
            if (source != null)
                FluidRouting.extract(level, network, node, facing, source);
        }
    }

    /** The fluid handler of the endpoint on {@code side} of the node, or null when that side is no endpoint. */
    static @Nullable ResourceHandler<FluidResource> fluidSource(PipeNetwork network, int node, Direction side) {
        int next = network.nodes[node].next(side);
        if (!PipeNode.isEndpoint(next))
            return null;
        ResourceHandler<FluidResource> handler = FluidRouting.handler(network.endpoints[PipeNode.endpointIndex(next)]);
        return handler != null && handler.size() > 0 ? handler : null;
    }

    /** One item attempt: {@link ItemRouting#extract}, the first routable slot (Q13). */
    protected void extractItems(ServerLevel level, PipeNetwork network, int node, Direction facing,
            ResourceHandler<ItemResource> source) {
        ItemRouting.extract(level, network, node, facing, source);
    }

    /** The item handler of the endpoint on {@code side} of the node, or null when that side is no endpoint. */
    static @Nullable ResourceHandler<ItemResource> source(PipeNetwork network, int node, Direction side) {
        int next = network.nodes[node].next(side);
        if (!PipeNode.isEndpoint(next))
            return null;
        ResourceHandler<ItemResource> handler = ItemRouting.handler(network.endpoints[PipeNode.endpointIndex(next)]);
        return handler != null && handler.size() > 0 ? handler : null;
    }

    @Override
    public void save(ValueOutput output) {
        output.putInt("facing", ModuleFacing.toIndex(facing));
    }

    @Override
    public void load(ValueInput input) {
        facing = ModuleFacing.fromIndex(input.getIntOr("facing", -1));
    }
}
