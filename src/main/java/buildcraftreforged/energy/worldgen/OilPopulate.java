/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: runs from the oil_well placed feature (OilWellFeature) instead of
 * PopulateChunkEvent, only in the biomes of #buildcraftreforged:has_oil_fields (vanilla deserts and oceans). The Desert
 * and Ocean Oil Field biomes become oil field regions inside those biomes (GenLayerBiomeReplacer), which take the oil
 * biome bonus and the 4.2.2 excessive-biome multiplier. Biome dictionary checks become biome tags, the biome id sets
 * become config lists, block ids become block states, and debug mode is a system property (docs/PLAN.md section 19.3).
 * Wells are capped at ground level, on the sea floor under water (section 19.3); the spring sits on the world's
 * bedrock floor instead of y = 0. Every read and write is clipped to the chunks a feature may touch, and the well
 * lies anywhere in its own chunk rather than 8 blocks over, so nothing reaches ungenerated chunks
 * (docs/BUGFIXES.md). Blocks placed with 4.2.2's update flag 3 get a fluid tick, as a block update would have given.
 */
package buildcraftreforged.energy.worldgen;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.core.BuildCraftConfiguration;
import buildcraftreforged.energy.BlockSpring;
import buildcraftreforged.energy.EnergyRegistry;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.common.Tags;

public class OilPopulate {

    /** The biomes the oil_well biome modifier adds the feature to; a data pack can change it. */
    public static final TagKey<Biome> OIL_FIELD_BIOMES = TagKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "has_oil_fields"));
    /** 4.2.2 BuildCraftCore.debugMode, a dev-only system property now (docs/PLAN.md section 19.3). */
    public static final boolean DEBUG_MODE = Boolean.getBoolean("buildcraftreforged.debug");
    private static final byte LARGE_WELL_HEIGHT = 16;
    private static final byte MEDIUM_WELL_HEIGHT = 6;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    public enum GenType {

        LARGE, MEDIUM, LAKE, NONE
    };

    /** The blocks this run may read and write; outside it reads see air and writes are dropped. */
    private final BoundingBox bounds;

    public OilPopulate(BoundingBox bounds) {
        this.bounds = bounds;
    }

    /** What a feature placed in the chunk at origin may touch: that chunk and the eight around it, full height. */
    public static BoundingBox featureBounds(WorldGenLevel world, BlockPos origin) {
        ChunkPos chunk = ChunkPos.containing(origin);
        return new BoundingBox(chunk.getMinBlockX() - 16, world.getMinY(), chunk.getMinBlockZ() - 16,
                chunk.getMaxBlockX() + 16, world.getMaxY(), chunk.getMaxBlockZ() + 16);
    }

    public void generateOil(WorldGenLevel world, RandomSource rand, int chunkX, int chunkZ) {
        if (!BuildCraftConfiguration.modifyWorld.get()) {
            return;
        }

        // shift to world coordinates
        int x = chunkX * 16 + rand.nextInt(16);
        int z = chunkZ * 16 + rand.nextInt(16);

        Holder<Biome> biome = world.getBiome(new BlockPos(x, world.getSeaLevel(), z));

        // Only in the biomes the feature was added to, and not in excluded ones
        if (!biome.is(OIL_FIELD_BIOMES) || isListed(biome, BuildCraftConfiguration.excludedBiomes.get())) {
            return;
        }

        boolean excessive = isOilField(world, biome, x, z);
        boolean oilBiome = excessive
                || isListed(biome, BuildCraftConfiguration.surfaceDepositBiomes.get())
                || biome.is(Tags.Biomes.IS_DESERT);

        double bonus = oilBiome ? 3.0 : 1.0;
        bonus *= BuildCraftConfiguration.oilWellScalar.get();
        if (excessive) {
            bonus *= BuildCraftConfiguration.oilFieldMultiplier.get();
        } else if (DEBUG_MODE) {
            bonus *= 20.0;
        }
        GenType type = GenType.NONE;
        if (rand.nextDouble() <= 0.0004 * bonus) {// 0.04%
            type = GenType.LARGE;
        } else if (rand.nextDouble() <= 0.001 * bonus) {// 0.1%
            type = GenType.MEDIUM;
        } else if (oilBiome && rand.nextDouble() <= 0.02 * bonus) {// 2%
            type = GenType.LAKE;
        }

        if (type == GenType.NONE) {
            return;
        }

        // Find ground level
        int groundLevel = getTopBlock(world, x, z);
        if (groundLevel < world.getMinY() + 5) {
            return;
        }

        double deviation = surfaceDeviation(world, x, groundLevel, z, 8);
        if (deviation > 0.45) {
            return;
        }

        // Generate a Well
        if (type == GenType.LARGE || type == GenType.MEDIUM) {
            generateWell(world, rand, type, x, groundLevel, z, 20 + rand.nextInt(10));
        } else if (type == GenType.LAKE) {
            // Generate a surface oil lake
            int lakeX = x;
            int lakeZ = z;
            int lakeY = groundLevel;

            if (isSurfaceBlock(getBlockState(world, lakeX, lakeY, lakeZ))) {
                generateSurfaceDeposit(world, rand, lakeX, lakeY, lakeZ, 5 + rand.nextInt(10));
            }
        }
    }

    /**
     * 4.2.2 GenLayerAddOilDesert and GenLayerAddOilOcean: inside the noise field a desert was the Desert Oil Field and
     * an ocean the Ocean Oil Field.
     */
    public static boolean isOilField(WorldGenLevel world, Holder<Biome> biome, int x, int z) {
        if (BuildCraftConfiguration.biomeOilDesert.get() && biome.is(Tags.Biomes.IS_DESERT)
                && GenLayerBiomeReplacer.OIL_DESERT.isReplaced(world.getSeed(), x, z)) {
            return true;
        }
        return BuildCraftConfiguration.biomeOilOcean.get() && biome.is(Tags.Biomes.IS_OCEAN)
                && GenLayerBiomeReplacer.OIL_OCEAN.isReplaced(world.getSeed(), x, z);
    }

    private static boolean isListed(Holder<Biome> biome, List<? extends String> biomeIds) {
        return !biomeIds.isEmpty() && biome.unwrapKey().map(key -> biomeIds.contains(key.identifier().toString())).orElse(false);
    }

    /**
     * The large and medium wells: an oil sphere at wellY, a surface deposit around the spout and the spout, which
     * stops at ground level (docs/PLAN.md section 19.3).
     */
    public void generateWell(WorldGenLevel world, RandomSource rand, GenType type, int wellX, int groundLevel, int wellZ, int wellY) {
        int wellHeight = MEDIUM_WELL_HEIGHT;
        if (type == GenType.LARGE) {
            wellHeight = LARGE_WELL_HEIGHT;
        }
        int maxHeight = groundLevel + wellHeight;
        if (maxHeight >= world.getMaxY() - 1) {
            return;
        }
        int spoutTop = getSpoutTop(world, wellX, groundLevel, wellZ);

        // Generate a spherical cave deposit
        int radius;
        if (type == GenType.LARGE) {
            radius = 8 + rand.nextInt(9);
        } else {
            radius = 4 + rand.nextInt(4);
        }

        int radiusSq = radius * radius;

        for (int poolX = -radius; poolX <= radius; poolX++) {
            for (int poolY = -radius; poolY <= radius; poolY++) {
                for (int poolZ = -radius; poolZ <= radius; poolZ++) {
                    int distance = poolX * poolX + poolY * poolY + poolZ * poolZ;

                    if (distance <= radiusSq) {
                        setOil(world, poolX + wellX, poolY + wellY, poolZ + wellZ, distance == radiusSq);
                    }
                }
            }
        }

        // Generate Lake around Spout
        int lakeRadius;
        if (type == GenType.LARGE) {
            lakeRadius = 25 + rand.nextInt(20);
        } else {
            lakeRadius = 5 + rand.nextInt(10);
        }
        generateSurfaceDeposit(world, rand, wellX, groundLevel, wellZ, lakeRadius);

        // Generate Spout
        boolean spawnOilSprings = BuildCraftConfiguration.spawnOilSprings.get();
        int baseY;
        if (type == GenType.LARGE && spawnOilSprings && (DEBUG_MODE || rand.nextDouble() <= 0.25)) {
            baseY = world.getMinY();
        } else {
            baseY = wellY;
        }

        if (getBlockState(world, wellX, baseY, wellZ).is(Blocks.BEDROCK)) {
            if (spawnOilSprings) {
                BlockSpring spring = EnergyRegistry.springOil.get();
                setBlock(world, wellX, baseY, wellZ, spring.defaultBlockState(), true);
                if (bounds.isInside(wellX, baseY, wellZ)) {
                    world.scheduleTick(new BlockPos(wellX, baseY, wellZ), spring, spring.spring.tickRate);
                }
            }
        }
        // 4.2.2 went on to maxHeight, wellHeight blocks above the ground
        for (int y = baseY + 1; y <= spoutTop; ++y) {
            setOil(world, wellX, y, wellZ, true);
        }

        if (type == GenType.LARGE) {
            for (int y = wellY; y <= Math.min(maxHeight - wellHeight / 2, spoutTop); ++y) {
                setOil(world, wellX + 1, y, wellZ, true);
                setOil(world, wellX - 1, y, wellZ, true);
                setOil(world, wellX, y, wellZ + 1, true);
                setOil(world, wellX, y, wellZ - 1, true);
            }
        }
    }

    /**
     * Where the spout stops: ground level, or under water (where 4.2.2's ground level is the water surface) the top
     * block of the sea floor, so no oil stands in the water.
     */
    private int getSpoutTop(WorldGenLevel world, int x, int groundLevel, int z) {
        int y = groundLevel;
        while (y > world.getMinY() && getBlockState(world, x, y, z).getBlock() instanceof LiquidBlock) {
            --y;
        }
        return y;
    }

    public void generateSurfaceDeposit(WorldGenLevel world, RandomSource rand, int x, int y, int z, int radius) {
        int depth = rand.nextDouble() < 0.5 ? 1 : 2;

        // Center
        setOilColumnForLake(world, x, y, z, depth, false);

        // Generate tendrils, from the center outward
        for (int w = 1; w <= radius; ++w) {
            float proba = (float) (radius - w + 4) / (float) (radius + 4);

            setOilWithProba(world, rand, proba, x, y, z + w, depth);
            setOilWithProba(world, rand, proba, x, y, z - w, depth);
            setOilWithProba(world, rand, proba, x + w, y, z, depth);
            setOilWithProba(world, rand, proba, x - w, y, z, depth);

            for (int i = 1; i <= w; ++i) {
                setOilWithProba(world, rand, proba, x + i, y, z + w, depth);
                setOilWithProba(world, rand, proba, x + i, y, z - w, depth);
                setOilWithProba(world, rand, proba, x + w, y, z + i, depth);
                setOilWithProba(world, rand, proba, x - w, y, z + i, depth);

                setOilWithProba(world, rand, proba, x - i, y, z + w, depth);
                setOilWithProba(world, rand, proba, x - i, y, z - w, depth);
                setOilWithProba(world, rand, proba, x + w, y, z - i, depth);
                setOilWithProba(world, rand, proba, x - w, y, z - i, depth);
            }
        }

        // Fill in holes
        for (int dx = x - radius; dx <= x + radius; ++dx) {
            for (int dz = z - radius; dz <= z + radius; ++dz) {
                if (isOil(world, dx, y, dz)) {
                    continue;
                }
                if (isOilSurrounded(world, dx, y, dz)) {
                    setOilColumnForLake(world, dx, y, dz, depth, false);
                }
            }
        }
    }

    private boolean isReplaceableFluid(WorldGenLevel world, int x, int y, int z) {
        BlockState state = getBlockState(world, x, y, z);
        return state.getBlock() instanceof LiquidBlock && !state.getFluidState().is(FluidTags.LAVA);
    }

    private boolean isOil(WorldGenLevel world, int x, int y, int z) {
        return getBlockState(world, x, y, z).is(EnergyRegistry.blockOil.get());
    }

    /** 4.2.2 compared with the biome's top block; 26.x biomes have surface rules instead, so sand and dirt count. */
    private static boolean isSurfaceBlock(BlockState state) {
        return state.is(BlockTags.SAND) || state.is(BlockTags.DIRT);
    }

    private boolean isReplaceableForLake(WorldGenLevel world, int x, int y, int z) {
        BlockState state = getBlockState(world, x, y, z);
        if (state.isAir()) {
            return true;
        }
        // biome.fillerBlock and biome.topBlock
        if (isSurfaceBlock(state)) {
            return true;
        }
        if (!state.isSolid()) {
            return true;
        }
        // isGenMineableReplaceable(stone)
        if (state.is(BlockTags.BASE_STONE_OVERWORLD)) {
            return true;
        }
        if (state.is(BlockTags.FLOWERS)) {
            return true;
        }
        if (!state.isSolidRender()) {
            return true;
        }
        return false;
    }

    private boolean isOilAdjacent(WorldGenLevel world, int x, int y, int z) {
        return isOil(world, x + 1, y, z)
                || isOil(world, x - 1, y, z)
                || isOil(world, x, y, z + 1)
                || isOil(world, x, y, z - 1);
    }

    private boolean isOilSurrounded(WorldGenLevel world, int x, int y, int z) {
        return isOil(world, x + 1, y, z)
                && isOil(world, x - 1, y, z)
                && isOil(world, x, y, z + 1)
                && isOil(world, x, y, z - 1);
    }

    private void setOilWithProba(WorldGenLevel world, RandomSource rand, float proba, int x, int y, int z, int depth) {
        if (rand.nextFloat() <= proba && !getBlockState(world, x, y - depth - 1, z).isAir()) {
            if (isOilAdjacent(world, x, y, z)) {
                setOilColumnForLake(world, x, y, z, depth, true);
            }
        }
    }

    private void setOilColumnForLake(WorldGenLevel world, int x, int y, int z, int depth, boolean update) {
        if (isReplaceableForLake(world, x, y + 1, z)) {
            if (!getBlockState(world, x, y + 2, z).isAir()) {
                return;
            }
            if (isReplaceableFluid(world, x, y, z) || isSolidOnTop(world, x, y - 1, z)) {
                setOil(world, x, y, z, update);
            } else {
                return;
            }
            if (!getBlockState(world, x, y + 1, z).isAir()) {
                setBlock(world, x, y + 1, z, AIR, update);
            }

            for (int d = 1; d <= depth - 1; d++) {
                if (isReplaceableFluid(world, x, y - d, z) || !isSolidOnTop(world, x, y - d - 1, z)) {
                    return;
                }
                setOil(world, x, y - d, z, false);
            }
        }
    }

    private boolean isSolidOnTop(WorldGenLevel world, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        return bounds.isInside(pos) && world.getBlockState(pos).isFaceSturdy(world, pos, Direction.UP);
    }

    private int getTopBlock(WorldGenLevel world, int x, int z) {
        if (!bounds.isInside(x, bounds.minY(), z)) {
            return world.getMinY() - 1;
        }
        // The worldgen heightmap while generating, the live one afterwards (the GameTests place wells in a live level)
        Heightmap.Types heightmap = world instanceof WorldGenRegion ? Heightmap.Types.WORLD_SURFACE_WG : Heightmap.Types.WORLD_SURFACE;
        int y = Math.min(world.getHeight(heightmap, x, z) + 1, bounds.maxY());

        for (; y > world.getMinY(); --y) {
            BlockState state = getBlockState(world, x, y, z);
            if (state.isAir()) {
                continue;
            }
            if (state.getBlock() instanceof LiquidBlock) {
                return y;
            }
            if (!state.isSolid()) {
                continue;
            }
            if (state.is(BlockTags.FLOWERS)) {
                continue;
            }
            return y - 1;
        }

        return world.getMinY() - 1;
    }

    private double surfaceDeviation(WorldGenLevel world, int x, int y, int z, int radius) {
        int diameter = radius * 2;
        double centralTendancy = y;
        double deviation = 0;
        for (int i = 0; i < diameter; i++) {
            for (int k = 0; k < diameter; k++) {
                deviation += getTopBlock(world, x - radius + i, z - radius + k) - centralTendancy;
            }
        }
        return Math.abs(deviation / centralTendancy);
    }

    private BlockState getBlockState(WorldGenLevel world, int x, int y, int z) {
        return bounds.isInside(x, y, z) ? world.getBlockState(new BlockPos(x, y, z)) : AIR;
    }

    /** A source of oil; update stands for 4.2.2's flag 3, which let it flow. */
    private void setOil(WorldGenLevel world, int x, int y, int z, boolean update) {
        setBlock(world, x, y, z, EnergyRegistry.blockOil.get().defaultBlockState(), update);
    }

    private void setBlock(WorldGenLevel world, int x, int y, int z, BlockState state, boolean update) {
        if (!bounds.isInside(x, y, z)) {
            return;
        }
        BlockPos pos = new BlockPos(x, y, z);
        world.setBlock(pos, state, update ? 3 : 2);
        FluidState fluid = state.getFluidState();
        if (update && !fluid.isEmpty()) {
            world.scheduleTick(pos, fluid.getType(), fluid.getType().getTickDelay(world));
        }
    }
}
