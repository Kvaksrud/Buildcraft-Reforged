package buildcraftreforged.gametest;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.energy.EnergyRegistry;
import buildcraftreforged.energy.worldgen.GenLayerBiomeReplacer;
import buildcraftreforged.energy.worldgen.OilPopulate;
import buildcraftreforged.energy.worldgen.OilPopulate.GenType;
import buildcraftreforged.energy.worldgen.OilWellFeature;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * #45: OilPopulate's shapes built on a test floor (medium and large wells, a surface lake, a well under water and the
 * oil spring under a large well), the oil spring's flow, the noise regions, and the data that adds the oil_well feature
 * to deserts and oceans only. The shapes are clipped to the padded test area above y = 2 (see {@link OilFuelTests}):
 * ground level is y = 20 on a stone and sand slab, the deposit sphere sits at y = 3 (radius 16 at most, so it stays
 * below the ground).
 */
final class OilFieldTests {
    static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();
    static final int PADDING = 24;
    static final int MAX_TICKS = 40;

    /** Half the width of the slab and of the area the shapes may write. */
    private static final int REACH = 20;
    private static final int WELL_Y = 3;
    /** 4.2.2's ground level: one below the top block of the ground. */
    private static final int GROUND = 20;

    static {
        TESTS.put("oil_well_medium", helper -> well(helper, GenType.MEDIUM));
        TESTS.put("oil_well_large", helper -> well(helper, GenType.LARGE));
        TESTS.put("oil_lake", OilFieldTests::lake);
        TESTS.put("oil_well_ocean", OilFieldTests::ocean);
        TESTS.put("oil_well_spring", OilFieldTests::wellSpring);
        TESTS.put("oil_spring_flows", OilFieldTests::springFlows);
        TESTS.put("oil_field_regions", OilFieldTests::regions);
        TESTS.put("oil_biome_modifier", OilFieldTests::biomeModifier);
    }

    private OilFieldTests() {
    }

    /** The sphere at WELL_Y, the spout up to ground level and no higher, the lake there, the cross on large wells. */
    private static void well(GameTestHelper helper, GenType type) {
        buildGround(helper);
        OilPopulate populate = new OilPopulate(area(helper));
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        populate.generateWell(helper.getLevel(), RandomSource.create(45), type, origin.getX(), origin.getY() + GROUND,
                origin.getZ(), origin.getY() + WELL_Y);

        // The sphere: radius 4 to 7 for medium wells, 8 to 16 for large ones
        int innerRadius = type == GenType.LARGE ? 7 : 3;
        assertOil(helper, new BlockPos(0, WELL_Y, 0), "sphere centre");
        assertOil(helper, new BlockPos(innerRadius, WELL_Y, 0), "sphere");
        assertOil(helper, new BlockPos(0, WELL_Y, -innerRadius), "sphere");
        // The spout, from the sphere to ground level, and the lake around it
        for (int y = WELL_Y + 1; y <= GROUND; y++) {
            assertOil(helper, new BlockPos(0, y, 0), "spout");
        }
        helper.assertBlockNotPresent(Blocks.SAND, new BlockPos(0, GROUND + 1, 0));
        // The cross around a large spout, which ends at ground level too (4.2.2: 8 blocks above it)
        if (type == GenType.LARGE) {
            for (int y = WELL_Y; y <= GROUND; y++) {
                for (BlockPos arm : List.of(new BlockPos(1, y, 0), new BlockPos(-1, y, 0), new BlockPos(0, y, 1), new BlockPos(0, y, -1))) {
                    assertOil(helper, arm, "cross");
                }
            }
        } else {
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(1, GROUND - 3, 0));
        }
        assertNothingAboveGround(helper);
        helper.succeed();
    }

    /** A lake one block below the surface: the centre, its tendrils, the top block over them removed. */
    private static void lake(GameTestHelper helper) {
        buildGround(helper);
        OilPopulate populate = new OilPopulate(area(helper));
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        populate.generateSurfaceDeposit(helper.getLevel(), RandomSource.create(45), origin.getX(), origin.getY() + GROUND,
                origin.getZ(), 8);

        assertOil(helper, new BlockPos(0, GROUND, 0), "lake centre");
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(0, GROUND + 1, 0));
        int surface = 0;
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                if (isOil(helper, new BlockPos(x, GROUND, z))) {
                    surface++;
                    helper.assertBlockNotPresent(Blocks.SAND, new BlockPos(x, GROUND + 1, z));
                }
            }
        }
        helper.assertTrue(surface >= 5, "the lake has only " + surface + " blocks");
        assertNothingAboveGround(helper);
        helper.succeed();
    }

    /**
     * Under water 4.2.2's ground level is the water surface: the spout stops on the sea floor and the deposit floats
     * on the surface. Sea floor at y = 15, water from 16 to 19.
     */
    private static void ocean(GameTestHelper helper) {
        int floor = 15;
        int surface = 19;
        for (int x = -REACH; x <= REACH; x++) {
            for (int z = -REACH; z <= REACH; z++) {
                for (int y = floor - 3; y < floor; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
                helper.setBlock(new BlockPos(x, floor, z), Blocks.SAND);
                boolean wall = Math.abs(x) == REACH || Math.abs(z) == REACH;
                for (int y = floor + 1; y <= surface; y++) {
                    helper.setBlock(new BlockPos(x, y, z), wall ? Blocks.GLASS : Blocks.WATER);
                }
            }
        }
        OilPopulate populate = new OilPopulate(area(helper));
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        populate.generateWell(helper.getLevel(), RandomSource.create(45), GenType.MEDIUM, origin.getX(), origin.getY() + surface,
                origin.getZ(), origin.getY() + WELL_Y);

        for (int y = WELL_Y + 1; y <= floor; y++) {
            assertOil(helper, new BlockPos(0, y, 0), "spout");
        }
        for (int y = floor + 1; y < surface; y++) {
            helper.assertBlockPresent(Blocks.WATER, new BlockPos(0, y, 0));
        }
        assertOil(helper, new BlockPos(0, surface, 0), "slick on the surface");
        helper.succeed();
    }

    /**
     * A quarter of large wells start at an oil spring on the bedrock floor of the world, with the spout from there to
     * ground level. Only the spout column is written; seeds are tried until one rolls the spring.
     */
    private static void wellSpring(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos spout = helper.absolutePos(new BlockPos(3, 0, 3));
        BlockPos bedrock = new BlockPos(spout.getX(), level.getMinY(), spout.getZ());
        BlockPos ground = helper.absolutePos(new BlockPos(3, GROUND, 3));
        OilPopulate populate = new OilPopulate(new BoundingBox(spout.getX(), level.getMinY(), spout.getZ(), spout.getX(),
                ground.getY() + 4, spout.getZ()));
        level.setBlock(bedrock, Blocks.BEDROCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        for (int y = GROUND - 2; y <= GROUND + 1; y++) {
            helper.setBlock(new BlockPos(3, y, 3), y == GROUND + 1 ? Blocks.SAND : Blocks.STONE);
        }
        for (int seed = 0; seed < 64 && !level.getBlockState(bedrock).is(EnergyRegistry.springOil.get()); seed++) {
            populate.generateWell(level, RandomSource.create(seed), GenType.LARGE, spout.getX(), ground.getY(), spout.getZ(),
                    helper.absolutePos(new BlockPos(3, WELL_Y, 3)).getY());
        }
        helper.assertTrue(level.getBlockState(bedrock).is(EnergyRegistry.springOil.get()), "no spring in 64 large wells");
        for (int y = level.getMinY() + 1; y <= ground.getY(); y++) {
            BlockPos pos = new BlockPos(spout.getX(), y, spout.getZ());
            helper.assertTrue(level.getBlockState(pos).is(EnergyRegistry.blockOil.get()), "no spout at " + pos);
        }
        helper.succeed();
    }

    /** 4.2.2 BlockSpring.assertSpring: one tick in eight puts an oil source on the free block above. */
    private static void springFlows(GameTestHelper helper) {
        BlockPos spring = new BlockPos(0, 3, 0);
        helper.setBlock(spring, EnergyRegistry.springOil.get());
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(spring);
        helper.assertTrue(level.getBlockTicks().hasScheduledTick(abs, EnergyRegistry.springOil.get()), "placing it schedules no tick");
        BlockState state = level.getBlockState(abs);
        RandomSource random = RandomSource.create(45);
        for (int i = 0; i < 200 && level.getBlockState(abs.above()).isAir(); i++) {
            state.tick(level, abs, random);
        }
        assertOil(helper, spring.above(), "above the spring");
        helper.assertValueEqual(state.getDestroySpeed(level, abs), 50.0F, "hardness (obsidian)");
        helper.assertValueEqual(EnergyRegistry.springOilItem.get().getDescriptionId(), "tile.spring.oil", "translation key");
        helper.succeed();
    }

    /** The 4.2.2 oil biome noise marks scattered regions: some, but far from all, of a large area. */
    private static void regions(GameTestHelper helper) {
        long seed = helper.getLevel().getSeed();
        for (GenLayerBiomeReplacer layer : GenLayerBiomeReplacer.values()) {
            int inside = 0;
            int samples = 0;
            for (int x = -32768; x < 32768; x += 256) {
                for (int z = -32768; z < 32768; z += 256) {
                    samples++;
                    if (layer.isReplaced(seed, x, z)) {
                        inside++;
                    }
                }
            }
            helper.assertTrue(inside > 0 && inside < samples / 4, layer + " covers " + inside + " of " + samples + " samples");
        }
        helper.succeed();
    }

    /** The configured and placed features exist, and the biome modifier gives them to deserts and oceans only. */
    private static void biomeModifier(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        Holder<Feature> feature = registries.lookupOrThrow(Registries.FEATURE).getOrThrow(key(Registries.FEATURE, "oil_well"));
        helper.assertTrue(feature.value() instanceof OilWellFeature, "configured feature " + feature.value());
        Holder<PlacedFeature> placed = registries.lookupOrThrow(Registries.PLACED_FEATURE).getOrThrow(key(Registries.PLACED_FEATURE, "oil_well"));
        BiomeModifier modifier = registries.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .getOrThrow(key(NeoForgeRegistries.Keys.BIOME_MODIFIERS, "oil_well")).value();
        helper.assertTrue(modifier instanceof BiomeModifiers.AddFeaturesBiomeModifier add && add.features().contains(placed)
                && add.step() == GenerationStep.Decoration.LAKES, "biome modifier " + modifier);

        HolderLookup.RegistryLookup<Biome> biomes = registries.lookupOrThrow(Registries.BIOME);
        for (ResourceKey<Biome> biome : List.of(Biomes.DESERT, Biomes.OCEAN, Biomes.DEEP_OCEAN, Biomes.WARM_OCEAN,
                Biomes.LUKEWARM_OCEAN, Biomes.COLD_OCEAN, Biomes.FROZEN_OCEAN, Biomes.DEEP_FROZEN_OCEAN)) {
            Holder<Biome> holder = biomes.getOrThrow(biome);
            helper.assertTrue(holder.is(OilPopulate.OIL_FIELD_BIOMES), biome + " is not in has_oil_fields");
            helper.assertTrue(hasOilWells(holder, placed), biome + " has no oil wells");
        }
        for (ResourceKey<Biome> biome : List.of(Biomes.PLAINS, Biomes.TAIGA, Biomes.BADLANDS, Biomes.BEACH, Biomes.RIVER,
                Biomes.SAVANNA, Biomes.NETHER_WASTES, Biomes.THE_END)) {
            Holder<Biome> holder = biomes.getOrThrow(biome);
            helper.assertFalse(holder.is(OilPopulate.OIL_FIELD_BIOMES), biome + " is in has_oil_fields");
            helper.assertFalse(hasOilWells(holder, placed), biome + " has oil wells");
        }
        helper.succeed();
    }

    private static boolean hasOilWells(Holder<Biome> biome, Holder<PlacedFeature> placed) {
        return biome.value().getGenerationSettings().features().get(GenerationStep.Decoration.LAKES.ordinal()).contains(placed);
    }

    private static <T> ResourceKey<T> key(ResourceKey<? extends net.minecraft.core.Registry<T>> registry, String path) {
        return ResourceKey.create(registry, Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, path));
    }

    /** Stone from GROUND - 3 to GROUND - 1, sand at GROUND and above it: the top block is at GROUND + 1. */
    private static void buildGround(GameTestHelper helper) {
        for (int x = -REACH; x <= REACH; x++) {
            for (int z = -REACH; z <= REACH; z++) {
                for (int y = GROUND - 3; y <= GROUND + 1; y++) {
                    helper.setBlock(new BlockPos(x, y, z), y >= GROUND ? Blocks.SAND : Blocks.STONE);
                }
            }
        }
    }

    /** The area the shapes may write: the padded test area above the structure's barriers. */
    private static BoundingBox area(GameTestHelper helper) {
        BlockPos min = helper.absolutePos(new BlockPos(-REACH, 2, -REACH));
        BlockPos max = helper.absolutePos(new BlockPos(REACH, PADDING, REACH));
        return BoundingBox.fromCorners(min, max);
    }

    /** No oil block of any kind above ground level: the 4.2.2 geyser is capped (docs/PLAN.md section 19.3). */
    private static void assertNothingAboveGround(GameTestHelper helper) {
        for (int x = -REACH; x <= REACH; x++) {
            for (int z = -REACH; z <= REACH; z++) {
                for (int y = GROUND + 1; y <= PADDING; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    helper.assertFalse(isOil(helper, pos), "oil above ground level at " + pos);
                }
            }
        }
    }

    private static boolean isOil(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockState(pos).is(EnergyRegistry.blockOil.get());
    }

    private static void assertOil(GameTestHelper helper, BlockPos pos, String what) {
        helper.assertTrue(isOil(helper, pos) && helper.getBlockState(pos).getFluidState().isSource(),
                "no oil source for the " + what + " at " + pos + ": " + helper.getBlockState(pos));
    }
}
