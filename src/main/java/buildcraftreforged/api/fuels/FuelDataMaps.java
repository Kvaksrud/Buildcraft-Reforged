/*
 * New in Buildcraft Reforged, 2026 (#43): no 4.2.2 counterpart. Data maps that hold the combustion engine fuels and
 * coolants 4.2.2 registered in code in buildcraft/BuildCraftEnergy.java, so data packs can add, change and remove
 * them.
 */
package buildcraftreforged.api.fuels;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/**
 * The data-driven half of {@link IronEngineFuel} and {@link IronEngineCoolant}. Files live at
 * {@code data/buildcraftreforged/data_maps/fluid/combustion_fuels.json},
 * {@code data/buildcraftreforged/data_maps/fluid/combustion_coolants.json} and
 * {@code data/buildcraftreforged/data_maps/item/solid_coolants.json}. The lookups in {@link IronEngineFuel} and
 * {@link IronEngineCoolant} read these first and fall back to the code-registered maps.
 */
public final class FuelDataMaps {

    /** A combustion engine fuel. {@code power_per_cycle} is FE per engine cycle (4.2.2: MJ; 1 MJ = 10 FE). */
    public record FuelData(float powerPerCycle, int totalBurningTime) {
        public static final Codec<FuelData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ExtraCodecs.POSITIVE_FLOAT.fieldOf("power_per_cycle").forGetter(FuelData::powerPerCycle),
                ExtraCodecs.POSITIVE_INT.fieldOf("total_burning_time").forGetter(FuelData::totalBurningTime))
                .apply(instance, FuelData::new));
    }

    /** A combustion engine coolant fluid: heat removed per mB, whatever the engine's heat. */
    public record CoolantData(float degreesCoolingPerMB) {
        public static final Codec<CoolantData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ExtraCodecs.POSITIVE_FLOAT.fieldOf("degrees_cooling_per_mb").forGetter(CoolantData::degreesCoolingPerMB))
                .apply(instance, CoolantData::new));
    }

    /**
     * A solid coolant such as ice: the item stands for {@code amount} mB of {@code fluid}. Not a {@code FluidStack},
     * which cannot be decoded while data maps load (fluid components are bound later).
     */
    public record SolidCoolantData(Fluid fluid, int amount) {
        public static final Codec<SolidCoolantData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BuiltInRegistries.FLUID.byNameCodec().fieldOf("fluid").forGetter(SolidCoolantData::fluid),
                ExtraCodecs.POSITIVE_INT.fieldOf("amount").forGetter(SolidCoolantData::amount))
                .apply(instance, SolidCoolantData::new));

        public FluidStack toFluidStack() {
            return new FluidStack(fluid, amount);
        }
    }

    public static final DataMapType<Fluid, FuelData> COMBUSTION_FUELS = DataMapType
            .builder(id("combustion_fuels"), Registries.FLUID, FuelData.CODEC)
            .synced(FuelData.CODEC, false)
            .build();

    public static final DataMapType<Fluid, CoolantData> COMBUSTION_COOLANTS = DataMapType
            .builder(id("combustion_coolants"), Registries.FLUID, CoolantData.CODEC)
            .synced(CoolantData.CODEC, false)
            .build();

    /** Solid coolants. As in 4.2.2, the fluid must itself be a coolant or the item does nothing. */
    public static final DataMapType<Item, SolidCoolantData> SOLID_COOLANTS = DataMapType
            .builder(id("solid_coolants"), Registries.ITEM, SolidCoolantData.CODEC)
            .synced(SolidCoolantData.CODEC, false)
            .build();

    private FuelDataMaps() {
    }

    public static void register(RegisterDataMapTypesEvent event) {
        event.register(COMBUSTION_FUELS);
        event.register(COMBUSTION_COOLANTS);
        event.register(SOLID_COOLANTS);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("buildcraftreforged", path);
    }
}
