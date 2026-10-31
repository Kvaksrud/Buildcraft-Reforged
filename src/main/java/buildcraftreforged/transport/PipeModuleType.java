package buildcraftreforged.transport;

import buildcraftreforged.api.transport.IPipeTile.PipeType;
import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/**
 * The module kinds of docs/TRANSPORT.md 8.1, with what the connection rule (3.2, 3.3), the graph and fitting need to
 * know about them. The module objects (settings, hooks, GUIs) are {@link buildcraftreforged.transport.module.PipeModule}.
 */
public enum PipeModuleType implements StringRepresentable {

    EXTRACTOR,
    FILTERED_EXTRACTOR,
    DIRECTOR,
    SORTER,
    PICKUP,
    VOID,
    COLOUR_TAG,
    COLOUR_SORTER,
    FILTERED_COLOUR_TAG,
    ISOLATOR;

    public static final Codec<PipeModuleType> CODEC = StringRepresentable.fromEnum(PipeModuleType::values);

    /**
     * 4.2.2 {@code IPipeConnectionForced.ignoreConnectionOverrides}, which only the sandstone pipes returned true for.
     */
    public boolean ignoresConnectionOverrides() {
        return this == ISOLATOR;
    }

    /** False for the Isolator, which connects only to pipes (4.2.2 {@code PipeItemsSandstone.canPipeConnect}). */
    public boolean connectsToEndpoints() {
        return this != ISOLATOR;
    }

    /**
     * Two pipes with the same module of this type never connect (docs/TRANSPORT.md 3.3, Q16). 4.2.2
     * {@code PipeConnectionBans} banned wood↔wood, obsidian↔obsidian and emzuli↔emzuli; the material bans are gone.
     */
    public boolean bannedWithItself() {
        return this == EXTRACTOR || this == FILTERED_EXTRACTOR || this == PICKUP || this == FILTERED_COLOUR_TAG;
    }

    /**
     * Whether a pipe with this module starts transfers on its own, ticked by the network (docs/TRANSPORT.md 6.1): the
     * Extractor, the Filtered Extractor, the Pickup and the Filtered Colour Tag (an extractor, 8.10).
     */
    public boolean isSource() {
        return this == EXTRACTOR || this == FILTERED_EXTRACTOR || this == PICKUP || this == FILTERED_COLOUR_TAG;
    }

    /** Which pipe kinds take this module (docs/TRANSPORT.md 8.1, Q8). */
    public boolean accepts(PipeType kind) {
        return switch (this) {
        case EXTRACTOR, VOID, ISOLATOR -> kind == PipeType.ITEM || kind == PipeType.FLUID;
        case DIRECTOR -> kind == PipeType.ITEM || kind == PipeType.FLUID || kind == PipeType.POWER;
        default -> kind == PipeType.ITEM;
        };
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ENGLISH);
    }
}
