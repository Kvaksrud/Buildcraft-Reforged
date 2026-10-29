package buildcraftreforged.core.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MathUtilsTest {

    @Test
    void clampFloatKeepsValuesInRange() {
        assertEquals(0.5F, MathUtils.clamp(0.5F, 0F, 1F));
        assertEquals(0F, MathUtils.clamp(0F, 0F, 1F));
        assertEquals(1F, MathUtils.clamp(1F, 0F, 1F));
    }

    @Test
    void clampFloatCutsValuesOutOfRange() {
        assertEquals(0F, MathUtils.clamp(-3F, 0F, 1F));
        assertEquals(1F, MathUtils.clamp(7.5F, 0F, 1F));
    }

    @Test
    void clampDoubleKeepsValuesInRange() {
        assertEquals(12.25D, MathUtils.clamp(12.25D, -20D, 20D));
    }

    @Test
    void clampDoubleCutsValuesOutOfRange() {
        assertEquals(-20D, MathUtils.clamp(-20.5D, -20D, 20D));
        assertEquals(20D, MathUtils.clamp(1e9D, -20D, 20D));
    }

    @Test
    void clampChecksMinBeforeMax() {
        // With min > max the 4.2.2 expression returns min for values below it and max otherwise; keep that.
        assertEquals(5D, MathUtils.clamp(0D, 5D, 1D));
        assertEquals(1D, MathUtils.clamp(10D, 5D, 1D));
    }
}
