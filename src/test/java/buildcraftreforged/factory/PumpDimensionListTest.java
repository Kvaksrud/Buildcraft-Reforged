package buildcraftreforged.factory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import buildcraftreforged.core.DefaultProps;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.Test;

/** 4.2.2 PumpDimensionList, with dimension keys and fluid ids. Later entries win. */
class PumpDimensionListTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";

    @Test
    void defaultAllowsEverything() {
        PumpDimensionList list = new PumpDimensionList(DefaultProps.PUMP_DIMENSION_LIST);
        assertTrue(list.isFluidAllowed(Fluids.WATER, OVERWORLD));
        assertTrue(list.isFluidAllowed(Fluids.LAVA, NETHER));
    }

    @Test
    void laterEntriesWin() {
        PumpDimensionList list = new PumpDimensionList("+/*/*,-/minecraft:the_nether/minecraft:lava");
        assertFalse(list.isFluidAllowed(Fluids.LAVA, NETHER));
        assertTrue(list.isFluidAllowed(Fluids.LAVA, OVERWORLD));
        assertTrue(list.isFluidAllowed(Fluids.WATER, NETHER));

        list = new PumpDimensionList("-/minecraft:the_nether/minecraft:lava,+/*/*");
        assertTrue(list.isFluidAllowed(Fluids.LAVA, NETHER));
    }

    @Test
    void namesWithoutNamespaceAndNoMatch() {
        PumpDimensionList list = new PumpDimensionList("+/minecraft:overworld/Water");
        assertTrue(list.isFluidAllowed(Fluids.WATER, OVERWORLD));
        assertFalse(list.isFluidAllowed(Fluids.WATER, NETHER), "nothing matches: not allowed");
        assertFalse(list.isFluidAllowed(Fluids.LAVA, OVERWORLD));
    }

    @Test
    void malformedEntries() {
        assertThrows(RuntimeException.class, () -> new PumpDimensionList("*/*/*"));
        assertThrows(RuntimeException.class, () -> new PumpDimensionList("+/minecraft:lava"));
    }
}
