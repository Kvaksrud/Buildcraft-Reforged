package buildcraftreforged.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import buildcraftreforged.api.transport.IPipeTile.PipeType;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.ConfigSpec.CorrectionAction;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.Test;

/** #53: the tier ladder, the {@code transport.tiers} config table, and the 30 registered pipes with their resources. */
class PipeTiersTest {

    /** docs/TRANSPORT.md 2.1, row by row. */
    private static final int[][] TABLE = {
            { 1, 20, 80 }, { 2, 40, 160 }, { 4, 80, 320 }, { 8, 160, 640 }, { 16, 320, 1280 }, { 32, 640, 2560 },
            { 64, 1280, 5120 }, { 128, 2560, 10240 }, { 256, 5120, 20480 }, { 512, 10240, 40960 } };

    private record Correction(CorrectionAction action, String path) {
    }

    private static PipeTierStats row(PipeMaterial material) {
        int[] row = TABLE[material.tier - 1];
        return new PipeTierStats(row[0], row[1], row[2]);
    }

    /** A config holding the tier table after the spec's correction, which the config tracker runs on every load. */
    private record Loaded(PipeTierConfig tiers, CommentedConfig config) {
        PipeTierStats get(PipeMaterial material) {
            return tiers.read(config, material);
        }
    }

    private static Loaded load(CommentedConfig config, List<Correction> corrections) {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("transport");
        PipeTierConfig tiers = new PipeTierConfig(builder);
        builder.pop();
        ModConfigSpec spec = builder.build();
        spec.correct(config, (action, path, incorrect, corrected) -> corrections.add(
                new Correction(action, String.join(".", path))));
        return new Loaded(tiers, config);
    }

    private static void set(CommentedConfig config, PipeMaterial material, PipeTierStats stats) {
        String prefix = "transport.tiers." + material.getSerializedName() + ".";
        config.set(prefix + "itemsPerSecond", stats.itemsPerSecond());
        config.set(prefix + "fluidPerTick", stats.fluidPerTick());
        config.set(prefix + "powerPerTick", stats.powerPerTick());
    }

    @Test
    void defaultsAreTheDecidedLadder() {
        for (PipeMaterial material : PipeMaterial.values()) {
            assertEquals(row(material), PipeTierStats.defaults(material), material.getSerializedName());
        }
    }

    @Test
    void allTiersAreRead() {
        CommentedConfig config = CommentedConfig.inMemory();
        for (PipeMaterial material : PipeMaterial.values()) {
            set(config, material, new PipeTierStats(material.tier, material.tier + 100, material.tier + 1000));
        }
        List<Correction> corrections = new ArrayList<>();
        Loaded tiers = load(config, corrections);

        assertTrue(corrections.stream().noneMatch(c -> c.action() == CorrectionAction.REPLACE), corrections.toString());
        for (PipeMaterial material : PipeMaterial.values()) {
            assertEquals(new PipeTierStats(material.tier, material.tier + 100, material.tier + 1000), tiers.get(material));
        }
    }

    @Test
    void missingTierFallsBackToItsDefaults() {
        CommentedConfig config = CommentedConfig.inMemory();
        for (PipeMaterial material : PipeMaterial.values()) {
            if (material != PipeMaterial.DIAMOND)
                set(config, material, new PipeTierStats(3, 3, 3));
        }
        List<Correction> corrections = new ArrayList<>();
        Loaded tiers = load(config, corrections);

        assertEquals(PipeTierStats.defaults(PipeMaterial.DIAMOND), tiers.get(PipeMaterial.DIAMOND));
        assertEquals(new PipeTierStats(3, 3, 3), tiers.get(PipeMaterial.GOLD));
        assertTrue(corrections.stream().anyMatch(c -> c.path().equals("transport.tiers.diamond")), corrections.toString());
    }

    @Test
    void invalidValueFallsBackToTheDefaultWithOneCorrection() {
        CommentedConfig config = CommentedConfig.inMemory();
        for (PipeMaterial material : PipeMaterial.values()) {
            set(config, material, PipeTierStats.defaults(material));
        }
        config.set("transport.tiers.gold.fluidPerTick", 0);
        config.set("transport.tiers.iron.powerPerTick", "fast");
        List<Correction> corrections = new ArrayList<>();
        Loaded tiers = load(config, corrections);

        assertEquals(PipeTierStats.defaults(PipeMaterial.GOLD), tiers.get(PipeMaterial.GOLD));
        assertEquals(PipeTierStats.defaults(PipeMaterial.IRON), tiers.get(PipeMaterial.IRON));
        assertEquals(List.of(new Correction(CorrectionAction.REPLACE, "transport.tiers.gold.fluidPerTick"),
                new Correction(CorrectionAction.REPLACE, "transport.tiers.iron.powerPerTick")),
                corrections.stream().filter(c -> c.action() == CorrectionAction.REPLACE).sorted(
                        (a, b) -> a.path().compareTo(b.path())).toList());
    }

    @Test
    void thirtyPipesAreRegistered() {
        assertEquals(30, TransportRegistry.PIPE_DEFINITIONS.size());
        Set<PipeType> kinds = EnumSet.noneOf(PipeType.class);
        for (PipeDefinition definition : TransportRegistry.PIPE_DEFINITIONS) {
            Identifier id = Identifier.fromNamespaceAndPath("buildcraftreforged", definition.registryName());
            assertTrue(BuiltInRegistries.BLOCK.containsKey(id), id + " block");
            assertTrue(BuiltInRegistries.ITEM.containsKey(id), id + " item");
            BlockPipe block = TransportRegistry.getPipe(definition.kind(), definition.material());
            assertEquals(definition.kind(), block.kind);
            assertEquals(definition.material().tier, block.getTier());
            assertEquals(row(definition.material()), block.getTierStats());
            kinds.add(definition.kind());
        }
        assertEquals(EnumSet.of(PipeType.ITEM, PipeType.FLUID, PipeType.POWER), kinds);
    }

    /** TRANSPORT.md 14, "datagen check": every pipe has its blockstate, item model, texture, loot table and recipe. */
    @Test
    void everyPipeHasItsResources() {
        for (PipeDefinition definition : TransportRegistry.PIPE_DEFINITIONS) {
            String name = definition.registryName();
            for (String path : List.of("assets/buildcraftreforged/blockstates/" + name + ".json",
                    "assets/buildcraftreforged/items/" + name + ".json",
                    "assets/buildcraftreforged/models/item/" + name + ".json",
                    "assets/buildcraftreforged/textures/block/" + name + ".png",
                    "data/buildcraftreforged/loot_table/blocks/" + name + ".json",
                    "data/buildcraftreforged/recipe/" + name + ".json")) {
                assertNotNull(PipeTiersTest.class.getClassLoader().getResource(path), path);
            }
            assertTrue(definition.material().tier >= 1 && definition.material().tier <= 10);
        }
    }
}
