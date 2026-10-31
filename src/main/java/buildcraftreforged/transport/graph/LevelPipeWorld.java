package buildcraftreforged.transport.graph;

import buildcraftreforged.api.transport.IPipeTile.PipeType;
import buildcraftreforged.transport.BlockPipe;
import buildcraftreforged.transport.TilePipe;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.jspecify.annotations.Nullable;

/** {@link PipeWorld} over a server level. Never loads chunks: an unloaded position holds no pipe and no endpoint. */
final class LevelPipeWorld implements PipeWorld {
    private final ServerLevel level;

    LevelPipeWorld(ServerLevel level) {
        this.level = level;
    }

    @Override
    public @Nullable Pipe getPipe(long pos) {
        BlockPos blockPos = BlockPos.of(pos);
        if (!level.isLoaded(blockPos))
            return null;
        // CHECK: a build never creates block entities (IMMEDIATE would replace a removed one and run its onLoad).
        return level.getChunkAt(blockPos).getBlockEntity(blockPos, LevelChunk.EntityCreationType.CHECK)
                instanceof TilePipe pipe && !pipe.isRemoved() ? pipe : null;
    }

    @Override
    public boolean canBeEndpoint(long pos) {
        BlockPos blockPos = BlockPos.of(pos);
        return level.isLoaded(blockPos) && !(level.getBlockState(blockPos).getBlock() instanceof BlockPipe);
    }

    @Override
    public @Nullable BlockCapabilityCache<?, Direction> createEndpointCache(PipeType kind, long pos, Direction face,
            BooleanSupplier isValid, Runnable onInvalidate) {
        BlockCapability<?, Direction> capability = switch (kind) {
        case ITEM -> Capabilities.Item.BLOCK;
        case FLUID -> Capabilities.Fluid.BLOCK;
        case POWER -> Capabilities.Energy.BLOCK;
        case STRUCTURE -> null;
        };
        if (capability == null)
            return null;
        BlockCapabilityCache<?, Direction> cache = BlockCapabilityCache.create(capability, level, BlockPos.of(pos), face,
                isValid, onInvalidate);
        // NeoForge calls the listener only for a cache that was read since its last invalidation: read it once.
        cache.getCapability();
        return cache;
    }
}
