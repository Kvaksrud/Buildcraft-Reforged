package buildcraftreforged.gametest;

import buildcraftreforged.api.transport.IPipeTile.PipeType;
import buildcraftreforged.transport.PipeMaterial;
import buildcraftreforged.transport.PipeModuleType;
import buildcraftreforged.transport.TilePipe;
import buildcraftreforged.transport.TransportRegistry;
import buildcraftreforged.transport.graph.PipeNetwork;
import buildcraftreforged.transport.graph.PipeNetworks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * #49: the network cache in a real level (docs/TRANSPORT.md 3.4). The graph logic itself is unit-tested on a stub
 * ({@code PipeNetworkManagerTest}); these check that placing, breaking, connection, module and endpoint changes reach
 * the cache. Masks settle on the server tick after a change, so checks run a few ticks later.
 */
final class PipeNetworkTests {
    private static final int SETTLE = 3;
    private static final int Y = 1;
    private static final int Z = 1;

    private PipeNetworkTests() {
    }

    private static BlockPos at(int x) {
        return new BlockPos(x, Y, Z);
    }

    private static BlockState itemPipe() {
        return TransportRegistry.getPipe(PipeType.ITEM, PipeMaterial.STONE).defaultBlockState();
    }

    private static @Nullable PipeNetwork network(GameTestHelper helper, BlockPos pos) {
        return PipeNetworks.get(helper.getLevel(), helper.absolutePos(pos));
    }

    private static PipeNetwork requireNetwork(GameTestHelper helper, BlockPos pos, String what) {
        PipeNetwork network = network(helper, pos);
        helper.assertTrue(network != null, what + ": no network at " + pos);
        return network;
    }

    private static void assertSize(GameTestHelper helper, PipeNetwork network, int pipes, int endpoints, String what) {
        helper.assertTrue(network.size() == pipes && network.endpoints.length == endpoints,
                what + ": expected " + pipes + " pipes and " + endpoints + " endpoints, got " + network);
    }

    /**
     * A chest and four item pipes in a row, a fluid pipe on top: one item network with one endpoint, cached for every
     * pipe; the fluid pipe is its own network. Chest contents keep the cache; a module change and breaking the chest
     * drop it.
     */
    static void buildAndEndpoints(GameTestHelper helper) {
        helper.setBlock(at(0), Blocks.CHEST);
        for (int x = 1; x <= 4; x++) {
            helper.setBlock(at(x), itemPipe());
        }
        helper.setBlock(new BlockPos(1, Y + 1, Z), TransportRegistry.getPipe(PipeType.FLUID, PipeMaterial.STONE));

        helper.runAfterDelay(SETTLE, () -> {
            PipeNetwork items = requireNetwork(helper, at(1), "item line");
            assertSize(helper, items, 4, 1, "item line");
            helper.assertTrue(items.nodes[0].pos == helper.absolutePos(at(1)).asLong(), "node 0 is the start pipe");
            helper.assertTrue(items.endpoints[0].pos() == helper.absolutePos(at(0)).asLong(), "endpoint is the chest");
            helper.assertTrue(items.endpoints[0].cache() != null && items.endpoints[0].cache().getCapability() != null,
                    "endpoint cache sees the chest's item handler");
            for (int x = 2; x <= 4; x++) {
                helper.assertTrue(network(helper, at(x)) == items, "pipe " + x + " is in the cached network");
            }
            PipeNetwork fluids = requireNetwork(helper, new BlockPos(1, Y + 1, Z), "fluid pipe");
            helper.assertTrue(fluids != items && fluids.kind == PipeType.FLUID, "fluid pipe joined the item network");
            assertSize(helper, fluids, 1, 0, "fluid pipe");

            helper.<ChestBlockEntity>getBlockEntity(at(0), ChestBlockEntity.class).setItem(0, new ItemStack(Items.COBBLESTONE, 10));
            helper.runAfterDelay(SETTLE, () -> {
                helper.assertTrue(items.isValid() && network(helper, at(3)) == items,
                        "changing chest contents rebuilt the network");

                helper.getBlockEntity(at(3), TilePipe.class).setModuleType(PipeModuleType.DIRECTOR);
                helper.assertTrue(!items.isValid(), "a module change kept the old network");
                PipeNetwork withModule = requireNetwork(helper, at(1), "after the module change");
                helper.assertTrue(withModule.node(helper.absolutePos(at(3)).asLong()).module == PipeModuleType.DIRECTOR,
                        "the rebuilt node has no module");

                helper.setBlock(at(0), Blocks.AIR);
                helper.assertTrue(!withModule.isValid(), "breaking the chest kept the old network");
                helper.runAfterDelay(SETTLE, () -> {
                    assertSize(helper, requireNetwork(helper, at(4), "after breaking the chest"), 4, 0,
                            "after breaking the chest");
                    helper.succeed();
                });
            });
        });
    }

    /** Two lines merge when a pipe joins them, and split again when it is broken. */
    static void mergeAndSplit(GameTestHelper helper) {
        for (int x : new int[] {0, 1, 3, 4}) {
            helper.setBlock(at(x), itemPipe());
        }
        helper.runAfterDelay(SETTLE, () -> {
            PipeNetwork left = requireNetwork(helper, at(0), "left line");
            PipeNetwork right = requireNetwork(helper, at(4), "right line");
            helper.assertTrue(left != right, "the two lines share a network");
            assertSize(helper, left, 2, 0, "left line");

            helper.setBlock(at(2), itemPipe());
            helper.runAfterDelay(SETTLE, () -> {
                helper.assertTrue(!left.isValid() && !right.isValid(), "merging kept an old network");
                PipeNetwork merged = requireNetwork(helper, at(0), "merged");
                assertSize(helper, merged, 5, 0, "merged");
                helper.assertTrue(network(helper, at(4)) == merged, "the merged line has two networks");

                helper.setBlock(at(2), Blocks.AIR);
                helper.assertTrue(!merged.isValid(), "breaking the middle pipe kept the merged network");
                helper.assertTrue(network(helper, at(2)) == null, "a network at the broken pipe");
                helper.runAfterDelay(SETTLE, () -> {
                    PipeNetwork a = requireNetwork(helper, at(1), "split left");
                    PipeNetwork b = requireNetwork(helper, at(3), "split right");
                    helper.assertTrue(a != b, "split halves share a network");
                    assertSize(helper, a, 2, 0, "split left");
                    assertSize(helper, b, 2, 0, "split right");
                    helper.succeed();
                });
            });
        });
    }

    /**
     * Chunk unload and reload, as the pipe sees them: {@code setRemoved}, then {@code clearRemoved} + {@code onLoad},
     * on the middle pipe of a line. Each invalidates the network. How a network looks across an unloaded chunk (a dead
     * end, not an endpoint) is unit-tested: here the level would replace a removed block entity as soon as it is looked
     * up, so nothing reads the level between the two calls.
     */
    static void unloadAndReload(GameTestHelper helper) {
        for (int x = 0; x < 3; x++) {
            helper.setBlock(at(x), itemPipe());
        }
        helper.runAfterDelay(SETTLE, () -> {
            PipeNetwork whole = requireNetwork(helper, at(0), "line");
            assertSize(helper, whole, 3, 0, "line");

            TilePipe middle = helper.getBlockEntity(at(1), TilePipe.class);
            middle.setRemoved();
            helper.assertTrue(!whole.isValid(), "unloading a pipe kept the network");

            middle.clearRemoved();
            PipeNetwork between = requireNetwork(helper, at(0), "before onLoad");
            middle.onLoad();
            helper.assertTrue(!between.isValid(), "loading a pipe kept the network");
            PipeNetwork reloaded = requireNetwork(helper, at(2), "reloaded");
            helper.assertTrue(reloaded != whole && reloaded != between, "no rebuild after reloading");
            assertSize(helper, reloaded, 3, 0, "after reloading");
            helper.succeed();
        });
    }
}
