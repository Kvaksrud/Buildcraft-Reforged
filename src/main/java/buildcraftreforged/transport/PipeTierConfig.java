package buildcraftreforged.transport;

import buildcraftreforged.core.BuildCraftConfiguration;
import com.electronwill.nightconfig.core.Config;
import java.util.EnumMap;
import java.util.Map;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The {@code transport.tiers} table of docs/TRANSPORT.md 2.2: three rates per material, keyed by the material id
 * ({@code transport.tiers.gold.itemsPerSecond}). A missing or invalid entry (below 1, not a number) is corrected to the
 * tier's default by the config spec, which logs one warning per corrected key. Values need no restart: routing reads
 * them through {@link #current(PipeMaterial)} when it needs them.
 */
public final class PipeTierConfig {

    private record Values(ModConfigSpec.ConfigValue<Integer> itemsPerSecond, ModConfigSpec.ConfigValue<Integer> fluidPerTick,
            ModConfigSpec.ConfigValue<Integer> powerPerTick) {
    }

    private final Map<PipeMaterial, Values> values = new EnumMap<>(PipeMaterial.class);

    /** Defines {@code tiers.<material>.*} at the builder's current path (the {@code transport} section). */
    public PipeTierConfig(ModConfigSpec.Builder builder) {
        builder.comment("Throughput caps per pipe material; one tier for every kind (items, fluids, power)").push("tiers");
        for (PipeMaterial material : PipeMaterial.values()) {
            PipeTierStats defaults = PipeTierStats.defaults(material);
            builder.comment("Tier " + material.tier).push(material.getSerializedName());
            values.put(material, new Values(
                    rate(builder, "itemsPerSecond", "Items per second through one item pipe", defaults.itemsPerSecond()),
                    rate(builder, "fluidPerTick", "Millibuckets per tick through one fluid pipe", defaults.fluidPerTick()),
                    rate(builder, "powerPerTick", "FE per tick through one power pipe", defaults.powerPerTick())));
            builder.pop();
        }
        builder.pop();
    }

    /**
     * A positive int. Not {@code defineInRange}, which clamps an out-of-range value to the nearest bound: a value below
     * 1 falls back to the tier's default (docs/TRANSPORT.md 2.2), as a missing or non-numeric one does.
     */
    private static ModConfigSpec.ConfigValue<Integer> rate(ModConfigSpec.Builder builder, String key, String comment,
            int defaultValue) {
        return builder.comment(comment + "\nRange: 1 ~ " + Integer.MAX_VALUE + ", default " + defaultValue)
                .define(key, defaultValue, value -> value instanceof Integer rate && rate >= 1);
    }

    /** The configured caps of a material; the config must be loaded. */
    public PipeTierStats get(PipeMaterial material) {
        Values entry = values.get(material);
        return new PipeTierStats(entry.itemsPerSecond().get(), entry.fluidPerTick().get(), entry.powerPerTick().get());
    }

    /** The caps of a material in {@code config}, read as {@link #get} reads the loaded file (for tests). */
    PipeTierStats read(Config config, PipeMaterial material) {
        Values entry = values.get(material);
        return new PipeTierStats(read(config, entry.itemsPerSecond()), read(config, entry.fluidPerTick()),
                read(config, entry.powerPerTick()));
    }

    private static int read(Config config, ModConfigSpec.ConfigValue<Integer> value) {
        return value.getRaw(config, value.getPath(), value::getDefault);
    }

    /** The caps in use: the common config once it is loaded, the defaults before. */
    public static PipeTierStats current(PipeMaterial material) {
        return BuildCraftConfiguration.COMMON_SPEC.isLoaded() ? BuildCraftConfiguration.pipeTiers.get(material)
                : PipeTierStats.defaults(material);
    }
}
