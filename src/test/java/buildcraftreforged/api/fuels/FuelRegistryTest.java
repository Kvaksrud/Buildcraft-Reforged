package buildcraftreforged.api.fuels;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import buildcraftreforged.api.fuels.FuelDataMaps.CoolantData;
import buildcraftreforged.api.fuels.FuelDataMaps.FuelData;
import buildcraftreforged.api.fuels.FuelDataMaps.SolidCoolantData;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * #43: the shipped data maps hold the 4.2.2 fuel and coolant values (power × 10 for FE), and the 4.2.2 code
 * registration still works. No data packs load in unit tests, so the data maps are empty here and the lookups fall
 * back to the code-registered maps; the GameTest {@code fuels} reads the loaded data maps.
 */
class FuelRegistryTest {

    @BeforeAll
    static void bindFluidComponents() {
        BuiltInRegistries.FLUID.listElements()
                .filter(holder -> !holder.areComponentsBound())
                .forEach(holder -> holder.bindComponents(DataComponentMap.EMPTY));
        bindItemComponents();
    }

    /** No registries load in unit tests, so the one item made into a stack here needs components (see ItemComponents). */
    @SuppressWarnings("deprecation") // builtInRegistryHolder: there is no other way to reach the item's holder here
    private static void bindItemComponents() {
        Holder.Reference<Item> holder = Items.SNOW_BLOCK.builtInRegistryHolder();
        if (!holder.areComponentsBound()) {
            holder.bindComponents(DataComponentMap.builder().addAll(DataComponents.COMMON_ITEM_COMPONENTS).build());
        }
    }

    @AfterEach
    void clearCodeRegistrations() {
        IronEngineFuel.fuels.clear();
        IronEngineCoolant.liquidCoolants.clear();
        IronEngineCoolant.solidCoolants.clear();
    }

    @Test
    void shippedFuelsAre422ValuesInFe() {
        JsonObject values = values("fluid/combustion_fuels.json");
        // BuildCraftEnergy: addFuel("oil", 3, 5000) and addFuel("fuel", 6, 25000); 1 MJ = 10 FE.
        assertEquals(new FuelData(30, 5000), decode(FuelData.CODEC, values.getAsJsonObject("buildcraftreforged:oil")));
        assertEquals(new FuelData(60, 25000), decode(FuelData.CODEC, values.getAsJsonObject("buildcraftreforged:fuel")));
        // Lava was commented out in 4.2.2 and stays out.
        assertEquals(2, values.size());
        // Oil and fuel come from #42; until they are registered the entries are skipped, not errors.
        for (String fluid : values.keySet()) {
            JsonObject condition = values.getAsJsonObject(fluid).getAsJsonArray("neoforge:conditions").get(0).getAsJsonObject();
            assertEquals("neoforge:registered", condition.get("type").getAsString());
            assertEquals("minecraft:fluid", condition.get("registry").getAsString());
            assertEquals(fluid, condition.get("value").getAsString());
        }
    }

    @Test
    void shippedCoolantsAreWaterAndIce() {
        JsonObject coolants = values("fluid/combustion_coolants.json");
        assertEquals(1, coolants.size());
        assertEquals(new CoolantData(0.0023F), CoolantData.CODEC.parse(JsonOps.INSTANCE, coolants.get("minecraft:water")).getOrThrow());

        JsonObject solids = values("item/solid_coolants.json");
        assertEquals(1, solids.size());
        SolidCoolantData ice = SolidCoolantData.CODEC.parse(JsonOps.INSTANCE, solids.get("minecraft:ice")).getOrThrow();
        // Two buckets of water per ice block, as FluidContainerRegistry.BUCKET_VOLUME * 2.
        assertEquals(new SolidCoolantData(Fluids.WATER, 2000), ice);
        assertTrue(FluidStack.matches(new FluidStack(Fluids.WATER, 2000), ice.toFluidStack()));
    }

    @Test
    void codeRegisteredFuels() {
        assertNull(IronEngineFuel.getFuelForFluid(null));
        assertNull(IronEngineFuel.getFuelForFluid(Fluids.LAVA));

        IronEngineFuel.addFuel(Fluids.LAVA, 10, 20000);
        IronEngineFuel.Fuel lava = IronEngineFuel.getFuelForFluid(Fluids.LAVA);
        assertNotNull(lava);
        assertSame(Fluids.LAVA, lava.liquid);
        assertEquals(10F, lava.powerPerCycle);
        assertEquals(20000, lava.totalBurningTime);

        // By registry id, looked up when added.
        IronEngineFuel.addFuel("minecraft:water", 1, 2);
        assertSame(Fluids.WATER, IronEngineFuel.getFuelForFluid(Fluids.WATER).liquid);
        assertNull(IronEngineFuel.getFuelForFluid(Fluids.FLOWING_WATER));
    }

    @Test
    void codeRegisteredCoolants() {
        assertFalse(IronEngineCoolant.isCoolant(Fluids.WATER));
        assertNull(IronEngineCoolant.getCoolant(FluidStack.EMPTY));
        assertNull(IronEngineCoolant.getFluidCoolant(ItemStack.EMPTY));

        // A solid coolant whose fluid is not a coolant does nothing.
        IronEngineCoolant.addCoolant(new ItemStack(Items.SNOW_BLOCK), new FluidStack(Fluids.WATER, 1000));
        assertEquals(1000, IronEngineCoolant.getFluidCoolant(new ItemStack(Items.SNOW_BLOCK)).getAmount());
        assertNull(IronEngineCoolant.getCoolant(new ItemStack(Items.SNOW_BLOCK)));

        IronEngineCoolant.addCoolant(Fluids.WATER, 0.0023F);
        assertTrue(IronEngineCoolant.isCoolant(Fluids.WATER));
        assertEquals(0.0023F, IronEngineCoolant.getCoolant(new FluidStack(Fluids.WATER, 1)).getDegreesCoolingPerMB(500));
        assertEquals(0.0023F, IronEngineCoolant.getCoolant(new ItemStack(Items.SNOW_BLOCK)).getDegreesCoolingPerMB(0));
        assertNull(IronEngineCoolant.getCoolant(new FluidStack(Fluids.LAVA, 1)));
    }

    private static <T> T decode(Codec<T> codec, JsonObject entry) {
        return codec.parse(JsonOps.INSTANCE, entry.get("value")).getOrThrow();
    }

    private static JsonObject values(String file) {
        String path = "/data/buildcraftreforged/data_maps/" + file;
        try (InputStream in = FuelRegistryTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path);
            JsonElement json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            return json.getAsJsonObject().getAsJsonObject("values");
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }
}
