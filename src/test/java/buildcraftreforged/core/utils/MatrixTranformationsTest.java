package buildcraftreforged.core.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class MatrixTranformationsTest {

    /** {{xMin, xMax}, {yMin, yMax}, {zMin, zMax}} of a pipe connector stub pointing DOWN, the zero state. */
    private static float[][] downStub() {
        return new float[][] { { 0.25F, 0.75F }, { 0.0F, 0.25F }, { 0.25F, 0.75F } };
    }

    @Test
    void mirrorYFlipsTheYRangeAroundTheCentre() {
        float[][] box = downStub();
        MatrixTranformations.mirrorY(box);
        assertArrayEquals(new float[][] { { 0.25F, 0.75F }, { 0.75F, 1.0F }, { 0.25F, 0.75F } }, box);
    }

    @Test
    void rotateShiftsTheAxesXToYToZToX() {
        float[][] box = { { 1F, 2F }, { 3F, 4F }, { 5F, 6F } };
        MatrixTranformations.rotate(box);
        assertArrayEquals(new float[][] { { 5F, 6F }, { 1F, 2F }, { 3F, 4F } }, box);
    }

    @Test
    void threeRotationsAreTheIdentity() {
        float[][] box = { { 1F, 2F }, { 3F, 4F }, { 5F, 6F } };
        for (int i = 0; i < 3; i++)
            MatrixTranformations.rotate(box);
        assertArrayEquals(new float[][] { { 1F, 2F }, { 3F, 4F }, { 5F, 6F } }, box);
    }

    @Test
    void transformMovesTheStubToEachFace() {
        assertArrayEquals(new float[][] { { 0.25F, 0.75F }, { 0.0F, 0.25F }, { 0.25F, 0.75F } }, transformed(Direction.DOWN));
        assertArrayEquals(new float[][] { { 0.25F, 0.75F }, { 0.75F, 1.0F }, { 0.25F, 0.75F } }, transformed(Direction.UP));
        assertArrayEquals(new float[][] { { 0.25F, 0.75F }, { 0.25F, 0.75F }, { 0.0F, 0.25F } }, transformed(Direction.NORTH));
        assertArrayEquals(new float[][] { { 0.25F, 0.75F }, { 0.25F, 0.75F }, { 0.75F, 1.0F } }, transformed(Direction.SOUTH));
        assertArrayEquals(new float[][] { { 0.0F, 0.25F }, { 0.25F, 0.75F }, { 0.25F, 0.75F } }, transformed(Direction.WEST));
        assertArrayEquals(new float[][] { { 0.75F, 1.0F }, { 0.25F, 0.75F }, { 0.25F, 0.75F } }, transformed(Direction.EAST));
    }

    @Test
    void deepCloneCopiesBothDimensions() {
        float[][] source = downStub();
        float[][] clone = MatrixTranformations.deepClone(source);

        assertNotSame(source, clone);
        for (int i = 0; i < source.length; i++)
            assertNotSame(source[i], clone[i]);
        assertArrayEquals(source, clone);

        clone[1][0] = 9F;
        assertArrayEquals(downStub(), source);
    }

    private static float[][] transformed(Direction direction) {
        float[][] box = downStub();
        MatrixTranformations.transform(box, direction);
        return box;
    }
}
