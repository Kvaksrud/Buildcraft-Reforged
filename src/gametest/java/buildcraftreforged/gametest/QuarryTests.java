package buildcraftreforged.gametest;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.builders.BlockMarker;
import buildcraftreforged.builders.BuildersRegistry;
import buildcraftreforged.builders.TileMarker;
import buildcraftreforged.core.Box;
import buildcraftreforged.core.status.MachineStatus;
import buildcraftreforged.core.status.StatusPanel;
import buildcraftreforged.core.status.StatusReason;
import buildcraftreforged.factory.BlockFrame;
import buildcraftreforged.factory.BlockQuarry;
import buildcraftreforged.factory.FactoryRegistry;
import buildcraftreforged.factory.TileQuarry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * #67: the quarry builds its frame, digs its area layer by layer as its owner into the inventory beside it, takes a
 * marker-defined area, stops with the red light while its output is blocked or its owner may not break a block, and
 * takes its frame with it when removed. They build in the padded area (see {@link OilFuelTests#PADDING}) on a bedrock
 * floor at y = 2 with one layer to dig at y = 3.
 */
@EventBusSubscriber(modid = BuildcraftReforged.MOD_ID)
final class QuarryTests {
    static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();
    static final int MAX_TICKS = 3000;

    /** Absolute positions the stand-in claim mod protects. */
    private static final Set<BlockPos> PROTECTED = ConcurrentHashMap.newKeySet();
    /** The UUID of the last player the stand-in refused. */
    private static volatile UUID lastRefused;

    /** The marker-defined area of the small tests: x and z 0..4, a 3 x 3 interior. */
    private static final BlockPos MARKER_ORIGIN = new BlockPos(0, 4, 0);
    /** The quarry of the small tests, west of the origin marker, and the chest south of it. */
    private static final BlockPos SMALL_QUARRY = new BlockPos(-1, 4, 0);
    private static final BlockPos SMALL_CHEST = new BlockPos(-1, 4, 1);

    static {
        TESTS.put("quarry_digs_9x9", QuarryTests::digs9x9);
        TESTS.put("quarry_marker_area", QuarryTests::markerArea);
        TESTS.put("quarry_blocked_output", QuarryTests::blockedOutput);
        TESTS.put("quarry_protected_block", QuarryTests::protectedBlock);
    }

    private QuarryTests() {
    }

    @SubscribeEvent
    static void onBreak(BreakBlockEvent event) {
        if (PROTECTED.contains(event.getPos())) {
            lastRefused = event.getPlayer().getUUID();
            event.setCanceled(true);
        }
    }

    private static ServerPlayer placer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.snapTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(0, 9, 0))));
        return player;
    }

    /** A quarry at pos with its front to facing, placed by placer (the owner), before its first tick. */
    private static TileQuarry placeQuarry(GameTestHelper helper, BlockPos pos, Direction facing, ServerPlayer placer) {
        helper.setBlock(pos, FactoryRegistry.quarryBlock.get().defaultBlockState().setValue(BlockQuarry.FACING, facing));
        TileQuarry tile = helper.getBlockEntity(pos, TileQuarry.class);
        tile.onBlockPlacedBy(placer, new ItemStack(FactoryRegistry.quarryBlock.get()));
        return tile;
    }

    /** One insert per tick, at most the quarry's receive cap (100 MJ while building, 500 MJ while digging). */
    private static void power(GameTestHelper helper, BlockPos quarry) {
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(quarry)) instanceof TileQuarry))
            return;
        EnergyHandler handler = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(quarry),
                Direction.UP);
        helper.assertTrue(handler != null, "the quarry has no energy capability");
        try (Transaction tx = Transaction.openRoot()) {
            handler.insert(Integer.MAX_VALUE, tx);
            tx.commit();
        }
    }

    /** Bedrock at y = 0 and dirt at y = 1 under x and z from min to max. */
    private static void floor(GameTestHelper helper, int min, int max) {
        for (int x = min; x <= max; x++) {
            for (int z = min; z <= max; z++) {
                helper.setBlock(new BlockPos(x, 2, z), Blocks.BEDROCK);
                helper.setBlock(new BlockPos(x, 3, z), Blocks.DIRT);
            }
        }
    }

    private static int count(GameTestHelper helper, BlockPos pos, Item item) {
        Container container = (Container) helper.getBlockEntity(pos, BlockEntity.class);
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.is(item))
                count += stack.getCount();
        }
        return count;
    }

    /** Relative positions of every edge of box (absolute), where 4.2.2's blueprint puts frames. */
    private static List<BlockPos> frameEdges(GameTestHelper helper, Box box) {
        List<BlockPos> edges = new ArrayList<>();
        for (int x = box.xMin; x <= box.xMax; x++) {
            for (int y = box.yMin; y <= box.yMax; y++) {
                for (int z = box.zMin; z <= box.zMax; z++) {
                    int onEdge = (x == box.xMin || x == box.xMax ? 1 : 0) + (y == box.yMin || y == box.yMax ? 1 : 0)
                            + (z == box.zMin || z == box.zMax ? 1 : 0);
                    if (onEdge >= 2)
                        edges.add(helper.relativePos(new BlockPos(x, y, z)));
                }
            }
        }
        return edges;
    }

    private static void assertFrame(GameTestHelper helper, TileQuarry tile, int expected) {
        List<BlockPos> edges = frameEdges(helper, tile.box);
        helper.assertValueEqual(edges.size(), expected, "frame positions");
        for (BlockPos pos : edges) {
            helper.assertBlockPresent(FactoryRegistry.frameBlock.get(), pos);
        }
    }

    private static void assertDug(GameTestHelper helper, int min, int max) {
        for (int x = min; x <= max; x++) {
            for (int z = min; z <= max; z++) {
                helper.assertBlockPresent(Blocks.AIR, new BlockPos(x, 3, z));
                helper.assertBlockPresent(Blocks.BEDROCK, new BlockPos(x, 2, z));
            }
        }
    }

    private static int thrown(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(10)).size();
    }

    /**
     * The default area (11 x 5 x 11 behind the quarry's front): the frame is built on all twelve edges, a block in its
     * way is cleared first and one inside the box is cleared without drops, as 4.2.2's robot did. Then the 9 x 9
     * interior is dug down to bedrock into the chest beside the quarry, including a chest in the floor and its
     * contents (BuildCraft#2340), and the quarry is Done and takes no more power.
     */
    static void digs9x9(GameTestHelper helper) {
        BlockPos quarry = new BlockPos(0, 4, 6);
        BlockPos output = new BlockPos(1, 4, 6);
        floor(helper, -4, 4);
        BlockPos buried = new BlockPos(2, 3, -3);
        helper.setBlock(buried, Blocks.CHEST);
        ((Container) helper.getBlockEntity(buried, BlockEntity.class)).setItem(0, new ItemStack(Items.DIAMOND, 5));
        helper.setBlock(new BlockPos(5, 4, 0), Blocks.STONE);
        helper.setBlock(new BlockPos(0, 6, 0), Blocks.STONE);
        helper.setBlock(output, Blocks.CHEST);

        // Front to the south: 4.2.2 puts the default box to the north, x - 5 .. x + 5 and z - 11 .. z - 1.
        TileQuarry tile = placeQuarry(helper, quarry, Direction.SOUTH, placer(helper));
        helper.onEachTick(() -> power(helper, quarry));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(tile.builderDone && tile.builder == null, "frame not built yet"))
                .thenExecute(() -> {
                    Box box = tile.box;
                    BlockPos min = helper.relativePos(new BlockPos(box.xMin, box.yMin, box.zMin));
                    BlockPos max = helper.relativePos(new BlockPos(box.xMax, box.yMax, box.zMax));
                    helper.assertValueEqual(min, new BlockPos(-5, 4, -5), "box min");
                    helper.assertValueEqual(max, new BlockPos(5, 8, 5), "box max");
                    assertFrame(helper, tile, 4 * 11 + 4 * 9 + 4 * 3);
                    helper.assertBlockPresent(Blocks.AIR, new BlockPos(0, 6, 0));
                    BlockState corner = helper.getBlockState(new BlockPos(-5, 4, -5));
                    helper.assertTrue(corner.getValue(BlockFrame.CONNECTED.get(Direction.EAST))
                            && corner.getValue(BlockFrame.CONNECTED.get(Direction.SOUTH))
                            && corner.getValue(BlockFrame.CONNECTED.get(Direction.UP))
                            && !corner.getValue(BlockFrame.CONNECTED.get(Direction.WEST))
                            && !corner.getValue(BlockFrame.CONNECTED.get(Direction.DOWN)), "corner frame connections " + corner);
                    helper.assertTrue(tile.box.getLasers() == null, "the stripes box is still up");
                    helper.assertTrue(tile.arm != null, "no mechanical arm");
                    helper.assertValueEqual(tile.getStatusState(), MachineStatus.WORKING, "State while digging");
                })
                .thenWaitUntil(() -> helper.assertValueEqual(tile.getStatusState(), MachineStatus.DONE, "State"))
                .thenExecute(() -> {
                    assertDug(helper, -4, 4);
                    helper.assertValueEqual(count(helper, output, Items.DIRT), 80, "dirt in the chest");
                    helper.assertValueEqual(count(helper, output, Items.CHEST), 1, "the dug chest");
                    helper.assertValueEqual(count(helper, output, Items.DIAMOND), 5, "the dug chest's contents");
                    helper.assertValueEqual(count(helper, output, Items.STONE) + count(helper, output, Items.COBBLESTONE), 0,
                            "the cleared blocks dropped");
                    helper.assertValueEqual(thrown(helper), 0, "items thrown out");
                    helper.assertFalse(tile.isActive(), "a finished quarry is active");
                    helper.assertFalse(tile.isBlocked(), "a finished quarry is blocked");
                    helper.assertValueEqual(tile.powerHandler.getPowerReceiver().powerRequest(), 0L, "power taken when done");
                    assertFrame(helper, tile, 4 * 11 + 4 * 9 + 4 * 3);
                })
                .thenSucceed();
    }

    /** Three linked markers next to the quarry; returns nothing, the quarry finds them on its first tick. */
    private static void markers(GameTestHelper helper) {
        for (BlockPos pos : List.of(MARKER_ORIGIN, MARKER_ORIGIN.east(4), MARKER_ORIGIN.south(4))) {
            helper.setBlock(pos.below(), Blocks.STONE);
            helper.setBlock(pos, BuildersRegistry.markerBlock.get().defaultBlockState().setValue(BlockMarker.FACING, Direction.UP));
        }
        helper.getBlockEntity(MARKER_ORIGIN, TileMarker.class).tryConnection();
        helper.assertTrue(helper.getBlockEntity(MARKER_ORIGIN, TileMarker.class).origin.isSet(), "markers not linked");
    }

    /**
     * Markers 5 x 5 next to the quarry give the box (raised to 5 high, as in 4.2.2) and are removed; the frame goes
     * on its edges and the 3 x 3 interior is dug into the chest. Removing the quarry removes its frame (plan 19.3).
     */
    static void markerArea(GameTestHelper helper) {
        floor(helper, 1, 3);
        markers(helper);
        helper.setBlock(SMALL_CHEST, Blocks.CHEST);
        TileQuarry tile = placeQuarry(helper, SMALL_QUARRY, Direction.WEST, placer(helper));
        helper.onEachTick(() -> power(helper, SMALL_QUARRY));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(tile.box.isInitialized(), "no box"))
                .thenExecute(() -> {
                    Box box = tile.box;
                    helper.assertValueEqual(helper.relativePos(new BlockPos(box.xMin, box.yMin, box.zMin)), MARKER_ORIGIN, "box min");
                    helper.assertValueEqual(helper.relativePos(new BlockPos(box.xMax, box.yMax, box.zMax)), new BlockPos(4, 8, 4),
                            "box max");
                    helper.assertBlockNotPresent(BuildersRegistry.markerBlock.get(), MARKER_ORIGIN);
                })
                .thenWaitUntil(() -> helper.assertValueEqual(tile.getStatusState(), MachineStatus.DONE, "State"))
                .thenExecute(() -> {
                    assertFrame(helper, tile, 4 * 5 + 4 * 3 + 4 * 3);
                    assertDug(helper, 1, 3);
                    helper.assertValueEqual(count(helper, SMALL_CHEST, Items.DIRT), 9, "dirt in the chest");

                    List<BlockPos> edges = frameEdges(helper, tile.box);
                    helper.setBlock(SMALL_QUARRY, Blocks.AIR);
                    for (BlockPos pos : edges) {
                        helper.assertBlockNotPresent(FactoryRegistry.frameBlock.get(), pos);
                    }
                })
                .thenSucceed();
    }

    /**
     * Nothing beside the quarry takes items: after the first block the quarry stops with the red light, State Blocked
     * and Reason "No destination", the block stays in its output and nothing is thrown out. A chest beside it
     * clears that, and the quarry finishes with all nine blocks in the chest.
     */
    static void blockedOutput(GameTestHelper helper) {
        floor(helper, 1, 3);
        markers(helper);
        TileQuarry tile = placeQuarry(helper, SMALL_QUARRY, Direction.WEST, placer(helper));
        helper.onEachTick(() -> power(helper, SMALL_QUARRY));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(tile.isBlocked(), "the quarry is not blocked"))
                .thenIdle(40)
                .thenExecute(() -> {
                    helper.assertValueEqual(tile.getStatusState(), MachineStatus.BLOCKED, "State line");
                    helper.assertValueEqual(tile.getStatusReason(), StatusReason.NO_DESTINATION, "Reason line");
                    helper.assertTrue(helper.getBlockState(SMALL_QUARRY).getValue(StatusPanel.BLOCKED), "no red light");
                    // The collect pass may also have picked up a marker's drop near the head.
                    int pendingDirt = 0;
                    for (ItemStack stack : tile.output.getPending())
                        if (stack.is(Items.DIRT))
                            pendingDirt += stack.getCount();
                    helper.assertValueEqual(pendingDirt, 1, "pending dirt");
                    int dirt = 0;
                    for (int x = 1; x <= 3; x++)
                        for (int z = 1; z <= 3; z++)
                            if (helper.getBlockState(new BlockPos(x, 3, z)).is(Blocks.DIRT))
                                dirt++;
                    helper.assertValueEqual(dirt, 8, "dirt left while blocked");
                    helper.setBlock(SMALL_CHEST, Blocks.CHEST);
                })
                .thenWaitUntil(() -> helper.assertValueEqual(tile.getStatusState(), MachineStatus.DONE, "State"))
                .thenExecute(() -> {
                    assertDug(helper, 1, 3);
                    helper.assertValueEqual(count(helper, SMALL_CHEST, Items.DIRT), 9, "dirt in the chest");
                    helper.assertFalse(helper.getBlockState(SMALL_QUARRY).getValue(StatusPanel.BLOCKED), "red light still on");
                })
                .thenSucceed();
    }

    /**
     * A claim refuses the owner at one block of the layer: the quarry stops there with the block in place, shows
     * blocked (State, Reason "protected block", red light) and the claim saw the owner's UUID; it does not skip the
     * block. When the claim lifts, it digs on and finishes.
     */
    static void protectedBlock(GameTestHelper helper) {
        floor(helper, 1, 3);
        markers(helper);
        helper.setBlock(SMALL_CHEST, Blocks.CHEST);
        ServerPlayer placer = placer(helper);
        TileQuarry tile = placeQuarry(helper, SMALL_QUARRY, Direction.WEST, placer);
        BlockPos claimed = new BlockPos(2, 3, 2);
        BlockPos claimedAbsolute = helper.absolutePos(claimed);
        PROTECTED.add(claimedAbsolute);
        lastRefused = null;
        helper.onEachTick(() -> power(helper, SMALL_QUARRY));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(tile.isBlocked(), "the quarry is not blocked"))
                .thenIdle(60)
                .thenExecute(() -> {
                    try {
                        helper.assertBlockPresent(Blocks.DIRT, claimed);
                        helper.assertValueEqual(tile.getStatusState(), MachineStatus.BLOCKED, "State line");
                        helper.assertValueEqual(tile.getStatusReason(), StatusReason.PROTECTED_BLOCK, "Reason line");
                        helper.assertTrue(helper.getBlockState(SMALL_QUARRY).getValue(StatusPanel.BLOCKED), "no red light");
                        helper.assertValueEqual(lastRefused, placer.getUUID(), "breaker seen by the claim");
                        int dirt = 0;
                        for (int x = 1; x <= 3; x++)
                            for (int z = 1; z <= 3; z++)
                                if (helper.getBlockState(new BlockPos(x, 3, z)).is(Blocks.DIRT))
                                    dirt++;
                        helper.assertTrue(dirt >= 1 && dirt < 9, "the quarry skipped or never reached the claim: " + dirt);
                        helper.assertValueEqual(count(helper, SMALL_CHEST, Items.DIRT), 9 - dirt, "dirt in the chest");
                    } finally {
                        PROTECTED.remove(claimedAbsolute);
                    }
                })
                .thenWaitUntil(() -> helper.assertValueEqual(tile.getStatusState(), MachineStatus.DONE, "State"))
                .thenExecute(() -> {
                    assertDug(helper, 1, 3);
                    helper.assertValueEqual(count(helper, SMALL_CHEST, Items.DIRT), 9, "dirt in the chest");
                    helper.assertFalse(tile.isBlocked(), "the quarry is still blocked");
                })
                .thenSucceed();
    }
}
