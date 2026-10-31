package buildcraftreforged.transport.graph;

import buildcraftreforged.api.transport.IPipeTile.PipeType;
import buildcraftreforged.transport.PipeModuleType;
import java.util.function.BooleanSupplier;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.transfer.resource.Resource;
import org.jspecify.annotations.Nullable;

/**
 * What {@link PipeNetworkManager} needs from a level, so the graph can be built and tested without Minecraft
 * (docs/TRANSPORT.md 3.4, section 14). {@link LevelPipeWorld} is the real one. Positions are {@code BlockPos.asLong}.
 */
public interface PipeWorld {

    /** The facts about one pipe the graph reads. {@code TilePipe} implements it. */
    interface Pipe {
        PipeType getPipeType();

        int getTier();

        /** Bit {@code d} set when connected on the side with {@code Direction.get3DDataValue()} {@code d}. */
        byte getConnectionMask();

        @Nullable PipeModuleType getModuleType();

        /**
         * Whether the pipe's module lets a routing walk leave by {@code side} (docs/TRANSPORT.md 4.2 step 4; the
         * Director allows only its facing). Read live by the walk, so a facing change needs no rebuild.
         */
        default boolean allowsOutput(Direction side) {
            return true;
        }

        /**
         * The module's candidate filter and order (docs/TRANSPORT.md 4.2 steps 5-6; the Sorter and the Colour Sorter):
         * reorders {@code sides[0 .. count)} and returns how many to try, or -1 for the uniform order. {@code colour}
         * is the walk colour on entry ({@code PipeWalk.NO_COLOUR} for none).
         */
        default int orderCandidates(int[] sides, int count, @Nullable Resource resource, int colour,
                RandomSource random) {
            return -1;
        }

        /** The walk colour after entering this pipe, from the one it was entered with (the Colour Tag, 8.8). */
        default int walkColour(int colour) {
            return colour;
        }
    }

    /** The loaded, live pipe at {@code pos}, or null. A removed or unloaded pipe is absent. */
    @Nullable Pipe getPipe(long pos);

    /**
     * Whether a connected side towards {@code pos} leads to an endpoint: the position is loaded and holds no pipe block
     * (a pipe block whose block entity is gone or removed is a dead end, not an endpoint).
     */
    boolean canBeEndpoint(long pos);

    /**
     * A capability cache for the {@code kind}'s capability of the block at {@code pos}, queried on {@code face} (the
     * face towards the pipe). {@code onInvalidate} runs when the capability there is invalidated, while
     * {@code isValid} holds. May return null where there is no capability to cache (structure pipes, unit tests).
     */
    @Nullable BlockCapabilityCache<?, Direction> createEndpointCache(PipeType kind, long pos, Direction face,
            BooleanSupplier isValid, Runnable onInvalidate);
}
