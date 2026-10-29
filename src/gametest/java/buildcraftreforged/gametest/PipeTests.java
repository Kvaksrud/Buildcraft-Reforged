package buildcraftreforged.gametest;

import buildcraftreforged.api.transport.IPipeTile.PipeType;
import buildcraftreforged.transport.BlockPipe;
import buildcraftreforged.transport.PipeConnections;
import buildcraftreforged.transport.PipeMaterial;
import buildcraftreforged.transport.PipeModuleType;
import buildcraftreforged.transport.TilePipe;
import buildcraftreforged.transport.TransportRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

/**
 * #48: pipe connections (docs/TRANSPORT.md 3.2, 3.3) and waterlogging, in a real level. Masks are recomputed on the
 * server tick after a change, so every check runs a few ticks after the blocks are set.
 */
final class PipeTests {
    /** Test-only energy endpoint: the GameTest source set gives lodestones an energy handler (no mod block has one yet). */
    static final Block ENERGY_BLOCK = Blocks.LODESTONE;

    private static final BlockPos WEST = new BlockPos(0, 1, 1);
    private static final BlockPos MIDDLE = new BlockPos(1, 1, 1);
    private static final BlockPos EAST = new BlockPos(2, 1, 1);
    private static final BlockPos ABOVE = new BlockPos(1, 2, 1);
    private static final BlockPos NORTH = new BlockPos(1, 1, 0);
    private static final BlockPos SOUTH = new BlockPos(1, 1, 2);
    private static final int SETTLE = 3;

    private PipeTests() {
    }

    static void registerTestCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(Capabilities.Energy.BLOCK, (level, pos, state, tile, side) -> new SimpleEnergyHandler(1000),
                ENERGY_BLOCK);
    }

    private static BlockState pipe(PipeType kind, PipeMaterial material) {
        return TransportRegistry.getPipe(kind, material).defaultBlockState();
    }

    private static TilePipe tile(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos, TilePipe.class);
    }

    private static void assertMask(GameTestHelper helper, BlockPos pos, String what, Direction... sides) {
        int expected = 0;
        for (Direction side : sides) {
            expected |= PipeConnections.bit(side);
        }
        int actual = tile(helper, pos).getConnectionMask();
        helper.assertTrue(actual == expected, what + ": mask " + Integer.toBinaryString(actual) + ", expected "
                + Integer.toBinaryString(expected));
    }

    /** Section 14: a pipe between a chest and another pipe has bits for both; breaking the chest clears its bit. */
    static void chestAndPipe(GameTestHelper helper) {
        helper.setBlock(WEST, Blocks.CHEST);
        helper.setBlock(MIDDLE, pipe(PipeType.ITEM, PipeMaterial.STONE));
        helper.setBlock(EAST, pipe(PipeType.ITEM, PipeMaterial.GOLD));
        helper.setBlock(ABOVE, pipe(PipeType.FLUID, PipeMaterial.STONE));
        helper.setBlock(NORTH, Blocks.DIRT);

        helper.runAfterDelay(SETTLE, () -> {
            assertMask(helper, MIDDLE, "item pipe between chest and item pipe", Direction.WEST, Direction.EAST);
            assertMask(helper, EAST, "item pipe at the end of the line", Direction.WEST);
            assertMask(helper, ABOVE, "fluid pipe above an item pipe", new Direction[0]);
            helper.assertTrue(tile(helper, MIDDLE).isPipeConnected(Direction.EAST), "isPipeConnected disagrees with the mask");

            helper.setBlock(WEST, Blocks.AIR);
            helper.runAfterDelay(SETTLE, () -> {
                assertMask(helper, MIDDLE, "after breaking the chest", Direction.EAST);
                helper.setBlock(EAST, Blocks.AIR);
                helper.runAfterDelay(SETTLE, () -> {
                    assertMask(helper, MIDDLE, "after breaking the other pipe");
                    helper.succeed();
                });
            });
        });
    }

    /** Each kind connects to its own capability only: chest for items, cauldron for fluids, an energy handler for power. */
    static void kindsAndEndpoints(GameTestHelper helper) {
        // Fluid pipe: cauldron west, chest east, power pipe north, fluid pipe of another tier south.
        helper.setBlock(WEST, Blocks.WATER_CAULDRON.defaultBlockState());
        helper.setBlock(MIDDLE, pipe(PipeType.FLUID, PipeMaterial.WOOD));
        helper.setBlock(EAST, Blocks.CHEST);
        helper.setBlock(NORTH, pipe(PipeType.POWER, PipeMaterial.WOOD));
        helper.setBlock(SOUTH, pipe(PipeType.FLUID, PipeMaterial.GOLD));
        // Power pipe north: energy block further north, the fluid pipe south of it.
        helper.setBlock(new BlockPos(1, 1, -1), ENERGY_BLOCK);
        helper.setBlock(new BlockPos(0, 1, 0), Blocks.CHEST);

        helper.runAfterDelay(SETTLE, () -> {
            assertMask(helper, MIDDLE, "fluid pipe: cauldron and fluid pipe only", Direction.WEST, Direction.SOUTH);
            assertMask(helper, NORTH, "power pipe: energy handler only", Direction.NORTH);
            assertMask(helper, SOUTH, "fluid pipe of another tier", Direction.NORTH);
            helper.succeed();
        });
    }

    /** Plugs on either side break the connection; the Isolator refuses blocks; banned module pairs do not connect. */
    static void plugsAndModules(GameTestHelper helper) {
        helper.setBlock(WEST, Blocks.CHEST);
        helper.setBlock(MIDDLE, pipe(PipeType.ITEM, PipeMaterial.WOOD));
        helper.setBlock(EAST, pipe(PipeType.ITEM, PipeMaterial.WOOD));

        helper.runAfterDelay(SETTLE, () -> {
            assertMask(helper, MIDDLE, "plain", Direction.WEST, Direction.EAST);
            tile(helper, EAST).setPlugged(Direction.WEST, true);
            helper.runAfterDelay(SETTLE, () -> {
                assertMask(helper, MIDDLE, "neighbour plugged towards us", Direction.WEST);
                assertMask(helper, EAST, "plugged towards the middle pipe");
                tile(helper, EAST).setPlugged(Direction.WEST, false);
                tile(helper, MIDDLE).setModuleType(PipeModuleType.ISOLATOR);
                helper.runAfterDelay(SETTLE, () -> {
                    assertMask(helper, MIDDLE, "Isolator: pipe yes, chest no", Direction.EAST);
                    tile(helper, MIDDLE).setModuleType(PipeModuleType.EXTRACTOR);
                    tile(helper, EAST).setModuleType(PipeModuleType.EXTRACTOR);
                    helper.runAfterDelay(SETTLE, () -> {
                        assertMask(helper, MIDDLE, "Extractor next to Extractor", Direction.WEST);
                        assertMask(helper, EAST, "Extractor next to Extractor");
                        tile(helper, EAST).setModuleType(PipeModuleType.DIRECTOR);
                        helper.runAfterDelay(SETTLE, () -> {
                            assertMask(helper, MIDDLE, "Extractor next to Director", Direction.WEST, Direction.EAST);
                            helper.succeed();
                        });
                    });
                });
            });
        });
    }

    /** Section 14: a waterlogged pipe keeps its connections; bucketing water in and out changes nothing else. */
    static void waterlogging(GameTestHelper helper) {
        helper.setBlock(WEST, Blocks.CHEST);
        helper.setBlock(MIDDLE, pipe(PipeType.ITEM, PipeMaterial.STONE).setValue(BlockPipe.WATERLOGGED, true));
        helper.setBlock(EAST, pipe(PipeType.ITEM, PipeMaterial.STONE));

        helper.runAfterDelay(SETTLE, () -> {
            BlockPos middle = helper.absolutePos(MIDDLE);
            BlockPos east = helper.absolutePos(EAST);
            helper.assertTrue(helper.getLevel().getFluidState(middle).is(Fluids.WATER), "waterlogged pipe holds no water");
            assertMask(helper, MIDDLE, "waterlogged pipe", Direction.WEST, Direction.EAST);
            helper.assertTrue(helper.getBlockState(EAST).getBlock() instanceof BlockPipe, "water washed the dry pipe away");

            // Bucket the water out of the middle pipe, and into the east one.
            BlockPipe block = (BlockPipe) helper.getBlockState(MIDDLE).getBlock();
            ItemStack bucket = block.pickupBlock(null, helper.getLevel(), middle, helper.getBlockState(MIDDLE));
            helper.assertTrue(bucket.is(Items.WATER_BUCKET), "picking up the water gave " + bucket);
            helper.assertTrue(block.placeLiquid(helper.getLevel(), east, helper.getBlockState(EAST),
                    Fluids.WATER.getSource(false)), "the dry pipe refused water");

            helper.runAfterDelay(SETTLE, () -> {
                helper.assertBlockProperty(MIDDLE, BlockPipe.WATERLOGGED, false);
                helper.assertBlockProperty(EAST, BlockPipe.WATERLOGGED, true);
                assertMask(helper, MIDDLE, "after draining", Direction.WEST, Direction.EAST);
                assertMask(helper, EAST, "after filling", Direction.WEST);
                helper.succeed();
            });
        });
    }
}
