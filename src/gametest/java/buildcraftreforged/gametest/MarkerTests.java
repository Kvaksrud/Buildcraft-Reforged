package buildcraftreforged.gametest;

import buildcraftreforged.builders.BlockMarker;
import buildcraftreforged.builders.BlockPathMarker;
import buildcraftreforged.builders.BuildersRegistry;
import buildcraftreforged.builders.TileMarker;
import buildcraftreforged.builders.TilePathMarker;
import buildcraftreforged.core.BlockIndex;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * #29 land markers and #30 path markers: linking, box formation and save/reload. All land marker checks share one
 * test, and all path marker checks another, so markers of parallel tests never find each other.
 */
final class MarkerTests {
    private static final BlockPos ORIGIN = new BlockPos(1, 2, 1);
    private static final BlockPos X = new BlockPos(6, 2, 1);
    private static final BlockPos Z = new BlockPos(1, 2, 5);
    private static final BlockPos Y = new BlockPos(1, 6, 1);

    private MarkerTests() {
    }

    static void markers(GameTestHelper helper) {
        for (BlockPos pos : List.of(ORIGIN, X, Z, Y))
            placeMarker(helper, BuildersRegistry.markerBlock.get(), pos);

        // A marker searches 63 blocks each way (4.2.2 maxSize 64): 64 blocks apart does not link, 63 does.
        BlockPos far = new BlockPos(10, 2, 10);
        placeMarker(helper, BuildersRegistry.markerBlock.get(), far);
        placeMarker(helper, BuildersRegistry.markerBlock.get(), far.east(64));
        BlockPos near = new BlockPos(12, 2, 12);
        placeMarker(helper, BuildersRegistry.markerBlock.get(), near);
        placeMarker(helper, BuildersRegistry.markerBlock.get(), near.east(63));
        marker(helper, far).tryConnection();
        helper.assertFalse(marker(helper, far).origin.isSet(), "markers 64 blocks apart linked");
        marker(helper, near).tryConnection();
        helper.assertTrue(marker(helper, near).origin.isSet(), "markers 63 blocks apart did not link");

        // Linking: one click on the origin finds a marker on each axis and all four share one origin.
        TileMarker origin = marker(helper, ORIGIN);
        origin.tryConnection();
        assertBox(helper, origin);
        for (BlockPos pos : List.of(X, Z, Y))
            helper.assertTrue(marker(helper, pos).origin == origin.origin, "marker at " + pos + " not in the box");

        // Save: only the origin writes the box, with the 4.2.2 tag names.
        CompoundTag tag = origin.saveWithFullMetadata(helper.getLevel().registryAccess());
        for (String key : List.of("vectO", "vect0", "vect1", "vect2"))
            helper.assertTrue(tag.getCompound(key).isPresent(), "saved origin has no " + key + ": " + tag);
        helper.assertFalse(marker(helper, X).saveWithFullMetadata(helper.getLevel().registryAccess()).contains("vectO"),
                "a linked marker that is not the origin saved the box");

        // Breaking a marker of the box unlinks all of them, as in 4.2.2.
        helper.setBlock(X, Blocks.AIR);
        helper.assertFalse(marker(helper, ORIGIN).origin.isSet(), "origin still linked after a marker broke");
        helper.assertFalse(marker(helper, Z).origin.isSet(), "marker still linked after a marker broke");

        // Reload: fresh block entities, the saved tag loaded into the origin, and the box is back after its first tick.
        for (BlockPos pos : List.of(ORIGIN, Z, Y))
            helper.setBlock(pos, Blocks.AIR);
        for (BlockPos pos : List.of(ORIGIN, X, Z, Y))
            placeMarker(helper, BuildersRegistry.markerBlock.get(), pos);
        TileMarker reloaded = marker(helper, ORIGIN);
        helper.assertTrue(reloaded != origin, "the origin block entity was not replaced");
        reloaded.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag));

        helper.succeedWhen(() -> {
            assertBox(helper, reloaded);
            for (BlockPos pos : List.of(X, Z, Y))
                helper.assertTrue(marker(helper, pos).origin == reloaded.origin, "marker at " + pos + " not relinked");
        });
    }

    static void pathMarkers(GameTestHelper helper) {
        BlockPos a = new BlockPos(1, 2, 1);
        BlockPos b = new BlockPos(5, 2, 1);
        BlockPos c = new BlockPos(5, 2, 6);
        for (BlockPos pos : List.of(a, b, c))
            placeMarker(helper, BuildersRegistry.pathMarkerBlock.get(), pos);

        // A click starts the search (the ACTIVE texture); the next tick links the nearest free path marker.
        pathMarker(helper, a).tryConnection();
        helper.assertTrue(pathMarker(helper, a).tryingToConnect, "click did not start the search");
        helper.assertBlockProperty(a, BlockPathMarker.ACTIVE, true);

        helper.runAfterDelay(2, () -> {
            helper.assertTrue(pathMarker(helper, a).isLinkedTo(pathMarker(helper, b)), "a did not link to the nearest marker b");
            helper.assertTrue(pathMarker(helper, b).isLinkedTo(pathMarker(helper, a)), "b did not link back to a");
            helper.assertBlockProperty(a, BlockPathMarker.ACTIVE, false);

            pathMarker(helper, b).tryConnection();
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(pathMarker(helper, b).isFullyConnected(), "b did not link on to c");
                List<BlockIndex> path = pathMarker(helper, a).getPath();
                helper.assertValueEqual(path, List.of(index(helper, a), index(helper, b), index(helper, c)), "path");

                // Save and reload a: its link to b comes back after the first tick.
                CompoundTag tag = pathMarker(helper, a).saveWithFullMetadata(helper.getLevel().registryAccess());
                helper.assertTrue(tag.getInt("x0").isPresent(), "saved path marker has no link: " + tag);
                helper.setBlock(a, Blocks.AIR);
                helper.assertFalse(pathMarker(helper, b).isFullyConnected(),
                        "b kept the link to the broken marker");
                placeMarker(helper, BuildersRegistry.pathMarkerBlock.get(), a);
                TilePathMarker reloaded = pathMarker(helper, a);
                reloaded.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag));
                helper.succeedWhen(() -> {
                    helper.assertTrue(reloaded.isLinkedTo(pathMarker(helper, b)), "reloaded a did not relink to b");
                    helper.assertTrue(pathMarker(helper, b).isLinkedTo(reloaded), "b did not relink to reloaded a");
                });
            });
        });
    }

    private static void assertBox(GameTestHelper helper, TileMarker origin) {
        BlockPos min = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos max = helper.absolutePos(new BlockPos(6, 6, 5));
        helper.assertTrue(origin.origin.isSet(), "no box formed");
        helper.assertValueEqual(BlockPos.min(new BlockPos(origin.xMin(), origin.yMin(), origin.zMin()),
                new BlockPos(origin.xMax(), origin.yMax(), origin.zMax())), BlockPos.min(min, max), "box minimum");
        helper.assertValueEqual(BlockPos.max(new BlockPos(origin.xMin(), origin.yMin(), origin.zMin()),
                new BlockPos(origin.xMax(), origin.yMax(), origin.zMax())), BlockPos.max(min, max), "box maximum");
        helper.assertTrue(origin.getLasers() != null, "the origin has no laser box");
    }

    /** A marker standing on a stone block, as a player places it on the floor. */
    private static void placeMarker(GameTestHelper helper, Block block, BlockPos pos) {
        helper.setBlock(pos.below(), Blocks.STONE);
        helper.setBlock(pos, block.defaultBlockState().setValue(BlockMarker.FACING, Direction.UP));
    }

    private static TileMarker marker(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos, TileMarker.class);
    }

    private static TilePathMarker pathMarker(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos, TilePathMarker.class);
    }

    private static BlockIndex index(GameTestHelper helper, BlockPos pos) {
        BlockPos absolute = helper.absolutePos(pos);
        return new BlockIndex(absolute.getX(), absolute.getY(), absolute.getZ());
    }
}
