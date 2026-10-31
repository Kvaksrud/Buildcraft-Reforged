package buildcraftreforged.energy.worldgen;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * The oil wells, spouts and lakes of 4.2.2's OilPopulate as a feature type ({@code buildcraftreforged:oil_well}), run
 * once per chunk by the {@code buildcraftreforged:oil_well} placed feature in place of 4.2.2's PopulateChunkEvent.Pre
 * listener. It takes no configuration: OilPopulate reads the 4.2.2 options from BuildCraftConfiguration.
 */
public record OilWellFeature() implements Feature {
    public static final OilWellFeature INSTANCE = new OilWellFeature();
    public static final MapCodec<OilWellFeature> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public MapCodec<OilWellFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        ChunkPos chunk = ChunkPos.containing(origin);
        new OilPopulate(OilPopulate.featureBounds(level, origin)).generateOil(level, random, chunk.x(), chunk.z());
        return true;
    }
}
