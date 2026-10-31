package buildcraftreforged.gametest;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.api.filler.FillerManager;
import buildcraftreforged.api.filler.IFillerPattern;
import buildcraftreforged.api.gates.ActionManager;
import buildcraftreforged.api.gates.IAction;
import buildcraftreforged.builders.BlockFiller;
import buildcraftreforged.builders.BlockMarker;
import buildcraftreforged.builders.BuildersRegistry;
import buildcraftreforged.builders.TileFiller;
import buildcraftreforged.builders.TileMarker;
import buildcraftreforged.builders.triggers.ActionFiller;
import buildcraftreforged.core.CoreRegistry;
import buildcraftreforged.core.network.InvalidPacketException;
import buildcraftreforged.core.network.PacketData;
import buildcraftreforged.core.status.MachineStatus;
import buildcraftreforged.core.status.StatusPanel;
import buildcraftreforged.core.status.StatusReason;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * #79: the filler takes a 3 x 3 x 3 area from four markers beside it (x, z 0..2 and y 4..6) and runs a 4.2.2 pattern
 * over it as its owner. Each pattern test checks exactly which positions hold a block when the filler is Done. A claim
 * refusing the owner one block (to break or to place) makes the filler pass over it and finish the rest.
 */
@EventBusSubscriber(modid = BuildcraftReforged.MOD_ID)
final class FillerTests {
    static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();
    static final int MAX_TICKS = 600;

    /** Absolute positions the stand-in claim mod protects, from breaking and from placing. */
    private static final Set<BlockPos> PROTECTED = ConcurrentHashMap.newKeySet();
    /** The UUID of the player the stand-in last refused, per protected position (the tests run at once). */
    private static final Map<BlockPos, UUID> REFUSED = new ConcurrentHashMap<>();

    private static final BlockPos ORIGIN = new BlockPos(0, 4, 0);
    /** West of the origin marker, level with the box's bottom: pyramids and stairs go up, stairs step east. */
    private static final BlockPos FILLER = new BlockPos(-1, 4, 0);

    static {
        TESTS.put("filler_fill", FillerTests::fill);
        TESTS.put("filler_box", helper -> shape(helper, "box", (x, y, z) -> !(x == 1 && y == 5 && z == 1)));
        TESTS.put("filler_pyramid", helper -> shape(helper, "pyramid", (x, y, z) -> y == 4 || (y == 5 && x == 1 && z == 1)));
        TESTS.put("filler_stairs", helper -> shape(helper, "stairs", (x, y, z) -> x >= y - 4));
        TESTS.put("filler_clear", FillerTests::clear);
        TESTS.put("filler_flatten", FillerTests::flatten);
        TESTS.put("filler_protected_break", FillerTests::protectedBreak);
        TESTS.put("filler_protected_place", FillerTests::protectedPlace);
        TESTS.put("filler_actions_gui_and_save", FillerTests::actionsGuiAndSave);
    }

    private FillerTests() {
    }

    @FunctionalInterface
    interface Shape {
        boolean filled(int x, int y, int z);
    }

    @SubscribeEvent
    static void onBreak(BreakBlockEvent event) {
        if (PROTECTED.contains(event.getPos())) {
            REFUSED.put(event.getPos(), event.getPlayer().getUUID());
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (PROTECTED.contains(event.getPos())) {
            if (event.getEntity() != null)
                REFUSED.put(event.getPos(), event.getEntity().getUUID());
            event.setCanceled(true);
        }
    }

    private static ServerPlayer placer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.snapTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(-6, 9, -6))));
        return player;
    }

    /**
     * A stone floor under the box at y = 3 (the markers stand on it), the origin, x and z markers on it and the y
     * marker at y = 6 on a stone west of the box, linked from the origin.
     */
    private static void markers(GameTestHelper helper) {
        for (int x = 0; x <= 2; x++)
            for (int z = 0; z <= 2; z++)
                helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
        for (BlockPos pos : List.of(ORIGIN, ORIGIN.east(2), ORIGIN.south(2))) {
            helper.setBlock(pos, BuildersRegistry.markerBlock.get().defaultBlockState().setValue(BlockMarker.FACING, Direction.UP));
        }
        BlockPos top = ORIGIN.above(2);
        helper.setBlock(top.west(), Blocks.STONE);
        helper.setBlock(top, BuildersRegistry.markerBlock.get().defaultBlockState().setValue(BlockMarker.FACING, Direction.EAST));
        TileMarker origin = helper.getBlockEntity(ORIGIN, TileMarker.class);
        origin.tryConnection();
        helper.assertTrue(origin.origin.isSet(), "markers not linked");
    }

    /** A filler at FILLER, placed by placer, set to the pattern with the tag (4.2.2 "buildcraft:tag"). */
    private static TileFiller placeFiller(GameTestHelper helper, String tag, ServerPlayer placer) {
        helper.setBlock(FILLER, BuildersRegistry.fillerBlock.get());
        TileFiller tile = helper.getBlockEntity(FILLER, TileFiller.class);
        tile.onBlockPlacedBy(placer, new ItemStack(BuildersRegistry.fillerBlock.get()));
        IFillerPattern pattern = FillerManager.registry.getPattern("buildcraft:" + tag);
        helper.assertTrue(pattern != null, "no pattern " + tag);
        tile.setPattern(pattern);
        return tile;
    }

    private static void power(GameTestHelper helper) {
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(FILLER)) instanceof TileFiller))
            return;
        EnergyHandler handler = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(FILLER),
                Direction.UP);
        helper.assertTrue(handler != null, "the filler has no energy capability");
        try (Transaction tx = Transaction.openRoot()) {
            handler.insert(Integer.MAX_VALUE, tx);
            tx.commit();
        }
    }

    private static int count(TileFiller tile) {
        int count = 0;
        for (int slot = 0; slot < tile.getContainerSize(); slot++)
            count += tile.getItem(slot).getCount();
        return count;
    }

    /** block where shape is filled, air elsewhere. */
    private static void assertArea(GameTestHelper helper, Shape shape, Block block) {
        assertArea(helper, shape, block, null);
    }

    /** block where shape is filled, air elsewhere except at ignore. */
    private static void assertArea(GameTestHelper helper, Shape shape, Block block, @Nullable BlockPos ignore) {
        for (int x = 0; x <= 2; x++) {
            for (int y = 4; y <= 6; y++) {
                for (int z = 0; z <= 2; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (pos.equals(ignore))
                        continue;
                    if (shape.filled(x, y, z))
                        helper.assertBlockPresent(block, pos);
                    else
                        helper.assertBlockPresent(Blocks.AIR, pos);
                }
            }
        }
    }

    private static int filled(Shape shape) {
        int n = 0;
        for (int x = 0; x <= 2; x++)
            for (int y = 4; y <= 6; y++)
                for (int z = 0; z <= 2; z++)
                    if (shape.filled(x, y, z))
                        n++;
        return n;
    }

    private static void waitDone(GameTestHelper helper, TileFiller tile, Runnable then) {
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(tile.getBox().isInitialized(), "no area"))
                .thenExecute(() -> helper.assertBlockNotPresent(BuildersRegistry.markerBlock.get(), ORIGIN))
                .thenWaitUntil(() -> helper.assertValueEqual(tile.getStatusState(), MachineStatus.DONE, "State"))
                .thenExecute(then)
                .thenSucceed();
    }

    /**
     * Fill: with nothing to place the filler waits with the red light (State Blocked, Reason missing materials); then
     * every soft position of the area takes a cobblestone from the inventory, a dirt block already there stays, the
     * markers are gone and the filler is Done with its top light off.
     */
    static void fill(GameTestHelper helper) {
        markers(helper);
        helper.setBlock(new BlockPos(1, 5, 1), Blocks.DIRT);
        TileFiller tile = placeFiller(helper, "fill", placer(helper));
        helper.onEachTick(() -> power(helper));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(tile.isBlocked(), "the filler is not waiting for blocks"))
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertBlockNotPresent(BuildersRegistry.markerBlock.get(), ORIGIN);
                    helper.assertValueEqual(tile.getStatusState(), MachineStatus.BLOCKED, "State line");
                    helper.assertValueEqual(tile.getStatusReason(), StatusReason.MISSING_MATERIALS, "Reason line");
                    helper.assertTrue(helper.getBlockState(FILLER).getValue(StatusPanel.BLOCKED), "no red light");
                    helper.assertTrue(helper.getBlockState(FILLER).getValue(BlockFiller.ACTIVE), "top light off");
                    tile.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
                })
                .thenWaitUntil(() -> helper.assertValueEqual(tile.getStatusState(), MachineStatus.DONE, "State"))
                .thenIdle(2)
                .thenExecute(() -> {
                    assertArea(helper, (x, y, z) -> !(x == 1 && y == 5 && z == 1), Blocks.COBBLESTONE, new BlockPos(1, 5, 1));
                    helper.assertBlockPresent(Blocks.DIRT, new BlockPos(1, 5, 1));
                    helper.assertValueEqual(count(tile), 64 - 26, "cobblestone left");
                    helper.assertFalse(helper.getBlockState(FILLER).getValue(BlockFiller.ACTIVE), "top light still on");
                    helper.assertFalse(helper.getBlockState(FILLER).getValue(StatusPanel.BLOCKED), "red light still on");
                    helper.assertValueEqual(helper.getBlockState(FILLER).getValue(BlockFiller.PATTERN),
                            BlockFiller.PatternFace.FILL, "pattern face");
                })
                .thenSucceed();
    }

    /** A pattern over the empty area with cobblestone: exactly the positions of shape hold a block. */
    static void shape(GameTestHelper helper, String tag, Shape shape) {
        markers(helper);
        TileFiller tile = placeFiller(helper, tag, placer(helper));
        tile.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        helper.onEachTick(() -> power(helper));

        waitDone(helper, tile, () -> {
            assertArea(helper, shape, Blocks.COBBLESTONE);
            helper.assertValueEqual(count(tile), 64 - filled(shape), "cobblestone left");
        });
    }

    /**
     * Dirt in every position of the area that is not a marker; the markers leave with the first tick, so the area is
     * full but for those four. Returns how many dirt blocks were set.
     */
    private static int dirt(GameTestHelper helper) {
        List<BlockPos> markers = List.of(ORIGIN, ORIGIN.east(2), ORIGIN.south(2), ORIGIN.above(2));
        int n = 0;
        for (int x = 0; x <= 2; x++)
            for (int y = 4; y <= 6; y++)
                for (int z = 0; z <= 2; z++)
                    if (!markers.contains(new BlockPos(x, y, z))) {
                        helper.setBlock(new BlockPos(x, y, z), Blocks.DIRT);
                        n++;
                    }
        return n;
    }

    /** Clear: an area of dirt is broken (the dirt dropped as items) and the area is air. */
    static void clear(GameTestHelper helper) {
        markers(helper);
        int dirt = dirt(helper);
        TileFiller tile = placeFiller(helper, "clear", placer(helper));
        helper.onEachTick(() -> power(helper));

        waitDone(helper, tile, () -> {
            assertArea(helper, (x, y, z) -> false, Blocks.AIR);
            helper.assertItemEntityCountIs(Items.DIRT, new BlockPos(1, 5, 1), 4, dirt);
        });
    }

    /**
     * Flatten: the hole under the area (the floor at y = 3 is missing in the middle) is filled from the inventory, and
     * the dirt inside the area is removed.
     */
    static void flatten(GameTestHelper helper) {
        markers(helper);
        for (int x = 0; x <= 2; x++)
            for (int z = 0; z <= 2; z++)
                helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
        helper.setBlock(new BlockPos(1, 3, 1), Blocks.AIR);
        helper.setBlock(new BlockPos(2, 5, 2), Blocks.DIRT);
        TileFiller tile = placeFiller(helper, "flatten", placer(helper));
        tile.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        helper.onEachTick(() -> power(helper));

        waitDone(helper, tile, () -> {
            helper.assertBlockPresent(Blocks.COBBLESTONE, new BlockPos(1, 3, 1));
            assertArea(helper, (x, y, z) -> false, Blocks.AIR);
            helper.assertValueEqual(count(tile), 63, "cobblestone left");
        });
    }

    /**
     * Clear with a claim on one dirt block: the owner may not break it, so the filler passes over it, clears the rest
     * and is Done; the claim saw the owner's UUID.
     */
    static void protectedBreak(GameTestHelper helper) {
        markers(helper);
        dirt(helper);
        ServerPlayer placer = placer(helper);
        TileFiller tile = placeFiller(helper, "clear", placer);
        BlockPos claimed = new BlockPos(1, 4, 1);
        BlockPos claimedAbsolute = helper.absolutePos(claimed);
        PROTECTED.add(claimedAbsolute);
        helper.onEachTick(() -> power(helper));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertValueEqual(tile.getStatusState(), MachineStatus.DONE, "State"))
                .thenExecute(() -> {
                    try {
                        assertArea(helper, (x, y, z) -> x == 1 && y == 4 && z == 1, Blocks.DIRT);
                        helper.assertValueEqual(REFUSED.get(claimedAbsolute), placer.getUUID(), "breaker seen by the claim");
                    } finally {
                        PROTECTED.remove(claimedAbsolute);
                        REFUSED.remove(claimedAbsolute);
                    }
                })
                .thenSucceed();
    }

    /**
     * Fill with a claim on one empty position: the placement there is refused and undone, the filler passes over it,
     * fills the other 26 and is Done with the refused cobblestone still in its inventory.
     */
    static void protectedPlace(GameTestHelper helper) {
        markers(helper);
        ServerPlayer placer = placer(helper);
        TileFiller tile = placeFiller(helper, "fill", placer);
        tile.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        BlockPos claimed = new BlockPos(2, 5, 0);
        BlockPos claimedAbsolute = helper.absolutePos(claimed);
        PROTECTED.add(claimedAbsolute);
        helper.onEachTick(() -> power(helper));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertValueEqual(tile.getStatusState(), MachineStatus.DONE, "State"))
                .thenExecute(() -> {
                    try {
                        assertArea(helper, (x, y, z) -> !(x == 2 && y == 5 && z == 0), Blocks.COBBLESTONE);
                        helper.assertValueEqual(count(tile), 64 - 26, "cobblestone left");
                        helper.assertValueEqual(REFUSED.get(claimedAbsolute), placer.getUUID(), "placer seen by the claim");
                    } finally {
                        PROTECTED.remove(claimedAbsolute);
                        REFUSED.remove(claimedAbsolute);
                    }
                })
                .thenSucceed();
    }

    /**
     * The seven pattern actions are registered as filler_<tag> under their 4.2.2 unique tags and offered for a filler
     * only; a gate action switches the pattern, Off stops it. Without markers every pattern waits with "No work area"
     * through a save and reload (BuildCraft#2331 does not apply). The GUI's return packet takes a known pattern and
     * refuses an unknown one; the pattern face of the block follows the pattern.
     */
    static void actionsGuiAndSave(GameTestHelper helper) {
        List<String> tags = List.of("box", "clear", "fill", "flatten", "horizon", "pyramid", "stairs");
        for (String tag : tags) {
            IAction action = ActionManager.getAction(Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "filler_" + tag));
            helper.assertTrue(action instanceof ActionFiller, "no action filler_" + tag);
            helper.assertValueEqual(action.getUniqueTag(), "filler:buildcraft:" + tag, "unique tag");
            helper.assertTrue(ActionManager.actions.get("filler:buildcraft:" + tag) == action, "tag map " + tag);
            helper.assertValueEqual(action.getIcon(), Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "filler/" + tag),
                    "icon");
        }
        helper.assertValueEqual(FillerManager.registry.getActions().size(), 7, "pattern actions");

        helper.setBlock(FILLER, BuildersRegistry.fillerBlock.get());
        helper.setBlock(FILLER.east(2), Blocks.CHEST);
        TileFiller tile = helper.getBlockEntity(FILLER, TileFiller.class);
        tile.onBlockPlacedBy(placer(helper), new ItemStack(BuildersRegistry.fillerBlock.get()));
        tile.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        List<IAction> offered = ActionManager.getNeighborActions(BuildersRegistry.fillerBlock.get(), tile);
        helper.assertTrue(offered.containsAll(FillerManager.registry.getActions()), "pattern actions not offered");
        helper.assertTrue(offered.contains(CoreRegistry.actionLoop.get()), "Loop not offered");
        List<IAction> chest = ActionManager.getNeighborActions(Blocks.CHEST,
                helper.getBlockEntity(FILLER.east(2), BlockEntity.class));
        for (IAction action : FillerManager.registry.getActions())
            helper.assertFalse(chest.contains(action), "pattern action offered for a chest");

        helper.onEachTick(() -> power(helper));
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    for (IAction action : FillerManager.registry.getActions()) {
                        tile.actionActivated(action);
                        helper.assertTrue(tile.currentPattern == ((ActionFiller) action).pattern, "action did not set the pattern");
                        helper.assertValueEqual(tile.getStatusState(), MachineStatus.NO_WORK_AREA, "State without markers");

                        CompoundTag tag = tile.saveWithFullMetadata(helper.getLevel().registryAccess());
                        TileFiller copy = new TileFiller(tile.getBlockPos(), tile.getBlockState());
                        copy.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,
                                helper.getLevel().registryAccess(), tag));
                        helper.assertTrue(copy.currentPattern == tile.currentPattern, "pattern not saved");
                        helper.assertFalse(copy.getBox().isInitialized(), "an area came back from the save");
                        helper.assertValueEqual(count(copy), 64, "inventory not saved");
                    }
                    helper.assertValueEqual(count(tile), 64, "a filler without an area used blocks");

                    tile.actionActivated(CoreRegistry.actionOff.get());
                    helper.assertFalse(tile.isActive(), "Off left the filler active");
                    tile.actionActivated(CoreRegistry.actionOn.get());
                    helper.assertTrue(tile.isActive(), "On left the filler inactive");

                    tile.setPattern(FillerManager.registry.getPattern("buildcraft:pyramid"));
                    try {
                        setPatternThroughGui(helper, tile, "buildcraft:box");
                    } catch (InvalidPacketException e) {
                        throw new IllegalStateException("a known pattern was refused", e);
                    }
                    helper.assertTrue(tile.currentPattern == FillerManager.registry.getPattern("buildcraft:box"),
                            "GUI return did not set the pattern");
                    boolean refused = false;
                    try {
                        setPatternThroughGui(helper, tile, "buildcraft:nonsense");
                    } catch (InvalidPacketException e) {
                        refused = true;
                    }
                    helper.assertTrue(refused, "an unknown pattern was accepted");
                    helper.assertTrue(tile.currentPattern == FillerManager.registry.getPattern("buildcraft:box"),
                            "an unknown pattern changed the filler");
                    helper.assertValueEqual(FillerManager.registry.getNextPattern(tile.currentPattern).getUniqueTag(),
                            "buildcraft:clear", "next pattern");
                    helper.assertValueEqual(FillerManager.registry.getPreviousPattern(FillerManager.registry.getPattern("buildcraft:box"))
                            .getUniqueTag(), "buildcraft:stairs", "previous pattern wraps");
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertValueEqual(helper.getBlockState(FILLER).getValue(BlockFiller.PATTERN), BlockFiller.PatternFace.BOX,
                            "pattern face");
                    helper.assertFalse(helper.getBlockState(FILLER).getValue(StatusPanel.BLOCKED), "red light without an area");
                })
                .thenSucceed();
    }

    private static void setPatternThroughGui(GameTestHelper helper, TileFiller tile, String tag) throws InvalidPacketException {
        RegistryFriendlyByteBuf data = PacketData.wrap(PacketData.write(helper.getLevel().registryAccess(),
                buf -> buf.writeUtf(tag)), helper.getLevel().registryAccess());
        try {
            tile.readGuiData(data, null);
        } finally {
            data.release();
        }
    }
}
