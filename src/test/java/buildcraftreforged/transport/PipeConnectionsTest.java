package buildcraftreforged.transport;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import buildcraftreforged.api.transport.IPipeConnection.ConnectOverride;
import buildcraftreforged.api.transport.IPipeTile.PipeType;
import com.mojang.math.OctahedralGroup;
import java.util.function.BooleanSupplier;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import org.jspecify.annotations.Nullable;

/** docs/TRANSPORT.md section 14, #48: the connection rule table, the mask bit order and the cached layouts. */
class PipeConnectionsTest {
    private static final Direction SIDE = Direction.EAST;
    private static final BooleanSupplier ENDPOINT = () -> true;
    private static final BooleanSupplier NOTHING = () -> false;

    private static PipeConnections.PipeEnd pipe(PipeType kind) {
        return new PipeConnections.PipeEnd(kind, (byte) 0, null);
    }

    private static PipeConnections.PipeEnd pipe(PipeType kind, @Nullable PipeModuleType module) {
        return new PipeConnections.PipeEnd(kind, (byte) 0, module);
    }

    private static PipeConnections.PipeEnd plugged(PipeType kind, Direction side) {
        return new PipeConnections.PipeEnd(kind, (byte) PipeConnections.bit(side), null);
    }

    private static boolean toPipe(PipeConnections.PipeEnd self, PipeConnections.PipeEnd other) {
        return PipeConnections.canConnect(self, SIDE, ConnectOverride.DEFAULT, other, NOTHING);
    }

    private static boolean toBlock(PipeConnections.PipeEnd self, BooleanSupplier endpoint) {
        return PipeConnections.canConnect(self, SIDE, ConnectOverride.DEFAULT, null, endpoint);
    }

    @Test
    void pipeToPipe() {
        assertTrue(toPipe(pipe(PipeType.ITEM), pipe(PipeType.ITEM)), "item <-> item");
        assertFalse(toPipe(pipe(PipeType.ITEM), pipe(PipeType.FLUID)), "item <-> fluid");
        assertFalse(toPipe(pipe(PipeType.FLUID), pipe(PipeType.POWER)), "fluid <-> power");
        assertFalse(toPipe(pipe(PipeType.STRUCTURE), pipe(PipeType.ITEM)), "structure <-> item");
        assertTrue(toPipe(pipe(PipeType.STRUCTURE), pipe(PipeType.STRUCTURE)), "structure <-> structure");
    }

    @Test
    void pipeToBlock() {
        assertTrue(toBlock(pipe(PipeType.ITEM), ENDPOINT), "item <-> chest");
        assertFalse(toBlock(pipe(PipeType.ITEM), NOTHING), "item <-> block without a handler");
        assertTrue(toBlock(pipe(PipeType.FLUID), ENDPOINT), "fluid <-> tank");
        assertTrue(toBlock(pipe(PipeType.POWER), ENDPOINT), "power <-> energy storage");
        assertFalse(toBlock(pipe(PipeType.STRUCTURE), ENDPOINT), "structure <-> anything");
    }

    @Test
    void plugsOnEitherSide() {
        assertFalse(toPipe(plugged(PipeType.ITEM, SIDE), pipe(PipeType.ITEM)), "plug on this side");
        assertFalse(toPipe(pipe(PipeType.ITEM), plugged(PipeType.ITEM, SIDE.getOpposite())), "plug on the far side");
        assertTrue(toPipe(pipe(PipeType.ITEM), plugged(PipeType.ITEM, SIDE)), "a plug on another side does not matter");
        assertFalse(toBlock(plugged(PipeType.ITEM, SIDE), ENDPOINT), "plugged side towards a chest");
        assertFalse(PipeConnections.canConnect(plugged(PipeType.ITEM, SIDE), SIDE, ConnectOverride.CONNECT, null, ENDPOINT),
                "a plug beats an override");
    }

    @Test
    void isolator() {
        PipeConnections.PipeEnd isolator = pipe(PipeType.ITEM, PipeModuleType.ISOLATOR);
        assertFalse(toBlock(isolator, ENDPOINT), "Isolator <-> chest");
        assertTrue(toPipe(isolator, pipe(PipeType.ITEM)), "Isolator <-> pipe");
        assertTrue(toPipe(isolator, isolator), "Isolator <-> Isolator");
    }

    @Test
    void overrides() {
        PipeConnections.PipeEnd item = pipe(PipeType.ITEM);
        assertFalse(PipeConnections.canConnect(item, SIDE, ConnectOverride.DISCONNECT, null, ENDPOINT), "DISCONNECT wins");
        assertTrue(PipeConnections.canConnect(item, SIDE, ConnectOverride.CONNECT, null, NOTHING), "CONNECT wins");
        PipeConnections.PipeEnd isolator = pipe(PipeType.ITEM, PipeModuleType.ISOLATOR);
        assertFalse(PipeConnections.canConnect(isolator, SIDE, ConnectOverride.CONNECT, null, ENDPOINT),
                "an Isolator ignores CONNECT and never connects to blocks");
    }

    @Test
    void moduleBans() {
        for (PipeModuleType module : PipeModuleType.values()) {
            boolean banned = module == PipeModuleType.EXTRACTOR || module == PipeModuleType.FILTERED_EXTRACTOR
                    || module == PipeModuleType.PICKUP || module == PipeModuleType.FILTERED_COLOUR_TAG;
            assertEquals(!banned, toPipe(pipe(PipeType.ITEM, module), pipe(PipeType.ITEM, module)), module + " <-> " + module);
            assertTrue(toPipe(pipe(PipeType.ITEM, module), pipe(PipeType.ITEM)), module + " <-> plain pipe");
        }
        assertTrue(toPipe(pipe(PipeType.ITEM, PipeModuleType.EXTRACTOR), pipe(PipeType.ITEM, PipeModuleType.PICKUP)),
                "different banned modules connect");
    }

    @Test
    void maskBitOrderIsSideOrder() {
        Direction[] sideOrder = { Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST };
        for (int d = 0; d < 6; d++) {
            assertEquals(1 << d, PipeConnections.bit(sideOrder[d]), sideOrder[d].toString());
        }
        byte mask = PipeConnections.with((byte) 0, Direction.UP, true);
        mask = PipeConnections.with(mask, Direction.EAST, true);
        assertEquals(0b100010, mask);
        assertTrue(PipeConnections.hasSide(mask, Direction.EAST));
        assertFalse(PipeConnections.hasSide(mask, Direction.DOWN));
        assertEquals(0b100000, PipeConnections.with(mask, Direction.UP, false));
    }

    @Test
    void everyMaskHasALayoutAndAShape() {
        for (int mask = 0; mask < 64; mask++) {
            int[] parts = PipeModelLayout.parts(mask);
            assertEquals(6, parts.length);
            for (Direction side : Direction.values()) {
                boolean connected = PipeConnections.hasSide(mask, side);
                assertEquals(connected ? PipeModelLayout.arm(side) : PipeModelLayout.centreFace(side),
                        parts[side.get3DDataValue()], "mask " + mask + " side " + side);
                // The shape reaches the block edge exactly on the connected sides.
                AABB bounds = PipeShapes.get(mask).bounds();
                double reach = switch (side) {
                case DOWN -> 0.25 - bounds.minY;
                case UP -> bounds.maxY - 0.75;
                case NORTH -> 0.25 - bounds.minZ;
                case SOUTH -> bounds.maxZ - 0.75;
                case WEST -> 0.25 - bounds.minX;
                case EAST -> bounds.maxX - 0.75;
                };
                assertEquals(connected ? 0.25 : 0, reach, 1e-9, "shape of mask " + mask + " side " + side);
            }
        }
        assertArrayEquals(new int[] { 0, 1, 2, 3, 4, 5 }, PipeModelLayout.parts(0));
        assertArrayEquals(new int[] { 6, 7, 8, 9, 10, 11 }, PipeModelLayout.parts(63));
    }

    @Test
    void templateRotations() {
        for (Direction side : Direction.values()) {
            OctahedralGroup rotation = PipeModelLayout.rotationTowards(side);
            assertEquals(side, rotation.rotate(Direction.NORTH), side.toString());
            assertEquals(1F, rotation.transformation().determinant(), 1e-6, side + " is a rotation, not a mirror");
        }
    }

    @Test
    void names() {
        PipeDefinition gold = new PipeDefinition(PipeType.ITEM, PipeMaterial.GOLD);
        assertEquals("PipeItemsGold", gold.unlocalizedName());
        assertEquals("pipe_items_gold", gold.registryName());
        assertEquals("pipe_fluids_wood", new PipeDefinition(PipeType.FLUID, PipeMaterial.WOOD).registryName());
        assertEquals("pipe_power_netherite", new PipeDefinition(PipeType.POWER, PipeMaterial.NETHERITE).registryName());
        assertThrows(IllegalArgumentException.class, () -> new PipeDefinition(PipeType.STRUCTURE, PipeMaterial.STONE));
        for (PipeMaterial material : PipeMaterial.values()) {
            assertEquals(material.ordinal() + 1, material.tier, material.toString());
        }
    }
}
