/*
 * Modified by Buildcraft Reforged, 2026: no longer a GenLayer. 4.2.2 replaced desert and ocean with the Desert Oil
 * Field and Ocean Oil Field biomes wherever this noise field passed its threshold; with no oil biomes (docs/PLAN.md
 * section 6) the same field now only marks the oil field regions inside vanilla deserts and oceans, which OilPopulate
 * treats as 4.2.2 treated the two oil biomes. GenLayerAddOilDesert and GenLayerAddOilOcean are the two constants
 * below. 4.2.2 sampled the field per biome-layer cell; here it is sampled at block coordinates, which is what the
 * 1:1 layer it also wrapped used.
 */
package buildcraftreforged.energy.worldgen;

import java.util.Random;

/**
 *
 * @author CovertJaguar <http://www.railcraft.info/>
 */
public enum GenLayerBiomeReplacer {

    /** 4.2.2 GenLayerAddOilDesert. */
    OIL_DESERT(0.001, 0.7),
    /** 4.2.2 GenLayerAddOilOcean. */
    OIL_OCEAN(0.0005, 0.9);

    public static final int OFFSET_RANGE = 500000;
    protected final double noiseScale;
    protected final double noiseThreshold;

    /**
     * @param noiseScale The scale of the noise field, smaller numbers zoom in the noise field.
     * @param noiseThreshold The strength the field must reach to replace the biome. Larger numbers result in smaller
     * patches.
     */
    GenLayerBiomeReplacer(double noiseScale, double noiseThreshold) {
        this.noiseScale = noiseScale;
        this.noiseThreshold = noiseThreshold;
    }

    /** True where 4.2.2 would have put this layer's oil biome, given that the biome there can be replaced. */
    public boolean isReplaced(long worldSeed, int x, int z) {
        Random rand = new Random(worldSeed);
        double xOffset = rand.nextInt(OFFSET_RANGE) - (OFFSET_RANGE / 2);
        double zOffset = rand.nextInt(OFFSET_RANGE) - (OFFSET_RANGE / 2);
        return SimplexNoise.noise((x + xOffset) * noiseScale, (z + zOffset) * noiseScale) > noiseThreshold;
    }
}
