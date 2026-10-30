package buildcraftreforged.transport;

import buildcraftreforged.api.transport.IPipeConnection.ConnectOverride;
import buildcraftreforged.api.transport.IPipeTile.PipeType;
import java.util.function.BooleanSupplier;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

/**
 * The connection rule of docs/TRANSPORT.md 3.2 and the bans of 3.3, kept free of the level so it can be tested on its
 * own. It mirrors 4.2.2 {@code TileGenericPipe.canPipeConnect} → {@code Pipe.canPipeConnect} →
 * {@code PipeTransport*.canPipeConnect}. {@link TilePipe} gathers the inputs from the world.
 *
 * <p>A connection mask has bit {@code d} set when the pipe is connected on the side with
 * {@link Direction#get3DDataValue()} {@code d}: the side order {@code DOWN, UP, NORTH, SOUTH, WEST, EAST}.
 */
public final class PipeConnections {

    private PipeConnections() {
    }

    /** What the rule needs to know about one pipe. */
    public record PipeEnd(PipeType kind, byte pluggedMask, @Nullable PipeModuleType module) {

        public boolean isPlugged(Direction side) {
            return hasSide(pluggedMask, side);
        }
    }

    /**
     * Whether {@code pipe} connects on {@code side} to the block next to it.
     *
     * @param override the neighbour's {@code IPipeConnection} answer for {@code (pipe.kind, side.opposite)}, or
     *        {@link ConnectOverride#DEFAULT} when it has none
     * @param neighbourPipe the neighbour if it is a pipe, else null
     * @param neighbourEndpoint whether the neighbour exposes the pipe kind's capability on {@code side.opposite} (an
     *        item handler with a slot, a fluid handler with a tank, an energy handler); asked only for non-pipes
     */
    public static boolean canConnect(PipeEnd pipe, Direction side, ConnectOverride override,
            @Nullable PipeEnd neighbourPipe, BooleanSupplier neighbourEndpoint) {
        // 1. A plugged side never connects.
        if (pipe.isPlugged(side))
            return false;

        // 2. The neighbour's override wins, except on an Isolator.
        if (override != ConnectOverride.DEFAULT && (pipe.module() == null || !pipe.module().ignoresConnectionOverrides()))
            return override == ConnectOverride.CONNECT;

        // 3. Pipe to pipe: no plug on the far side, same kind, not banned.
        if (neighbourPipe != null) {
            return !neighbourPipe.isPlugged(side.getOpposite())
                    && neighbourPipe.kind() == pipe.kind()
                    && !isBanned(pipe.module(), neighbourPipe.module());
        }

        // 4. Pipe to block: never for structure pipes and Isolators; otherwise the kind's capability decides.
        if (pipe.kind() == PipeType.STRUCTURE)
            return false;
        if (pipe.module() != null && !pipe.module().connectsToEndpoints())
            return false;
        return neighbourEndpoint.getAsBoolean();
    }

    /** docs/TRANSPORT.md 3.3: two pipes with the same banned module do not connect. Material bans are dropped. */
    public static boolean isBanned(@Nullable PipeModuleType a, @Nullable PipeModuleType b) {
        return a != null && a == b && a.bannedWithItself();
    }

    public static boolean hasSide(int mask, Direction side) {
        return (mask & bit(side)) != 0;
    }

    public static int bit(Direction side) {
        return 1 << side.get3DDataValue();
    }

    public static byte with(byte mask, Direction side, boolean set) {
        return (byte) (set ? mask | bit(side) : mask & ~bit(side));
    }
}
