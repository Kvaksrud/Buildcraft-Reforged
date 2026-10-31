package buildcraftreforged.gametest;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.builders.BlockBuilder;
import buildcraftreforged.builders.BuildCraftBuilders;
import buildcraftreforged.builders.BuildersRegistry;
import buildcraftreforged.builders.TileBuilder;
import buildcraftreforged.builders.gui.ContainerBuilder;
import buildcraftreforged.core.Box;
import buildcraftreforged.core.blueprints.BptBase;
import buildcraftreforged.core.blueprints.BptBlueprint;
import buildcraftreforged.core.blueprints.BptContext;
import buildcraftreforged.core.blueprints.BptRootIndex;
import buildcraftreforged.core.blueprints.BptTemplate;
import buildcraftreforged.core.status.MachineStatus;
import buildcraftreforged.core.status.StatusPanel;
import buildcraftreforged.core.status.StatusReason;
import java.util.Collection;
import java.util.LinkedHashMap;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * #78 builder. A small house (a stone floor, a door, stairs and a chest with diamonds) is captured as the architect
 * would, with the architect's place west of it, stored in the server's library and built by a powered builder: beside
 * itself when it faces west (no turn), behind itself and turned when it faces north. Templates, missing materials and
 * a stand-in claim mod are covered too. The tests run in a survival batch (BuildcraftGameTests): in a creative world the
 * builder takes no materials, as in 4.2.2.
 */
@EventBusSubscriber(modid = BuildcraftReforged.MOD_ID)
final class BuilderTests {
    static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();
    static final int MAX_TICKS = 1200;

    /** Absolute positions the stand-in claim mod protects, from breaking and from placing. */
    private static final Set<BlockPos> PROTECTED = ConcurrentHashMap.newKeySet();
    /** The UUID of the player the stand-in last refused, per protected position. */
    private static final Map<BlockPos, UUID> REFUSED = new ConcurrentHashMap<>();

    private static final BlockPos BUILDER = new BlockPos(1, 2, 1);
    /** The captured house: a 3 x 3 x 3 box, with the architect's place one block west of its minimum. */
    private static final BlockPos SOURCE = new BlockPos(2, 2, 8);

    static {
        TESTS.put("builder_blueprint_with_materials", BuilderTests::blueprintWithMaterials);
        TESTS.put("builder_blueprint_turned", BuilderTests::blueprintTurned);
        TESTS.put("builder_template_with_materials", BuilderTests::templateWithMaterials);
        TESTS.put("builder_missing_materials", BuilderTests::missingMaterials);
        TESTS.put("builder_protected_block", BuilderTests::protectedBlock);
    }

    private BuilderTests() {
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

    @FunctionalInterface
    interface Expected {
        BlockState at(int x, int y, int z);
    }

    /** A stone floor; on it a door facing north at (1, 0), stairs facing north at (0, 1), a chest with diamonds at (2, 2). */
    private static void house(GameTestHelper helper, BlockPos min) {
        for (int x = 0; x < 3; x++)
            for (int z = 0; z < 3; z++)
                helper.setBlock(min.offset(x, 0, z), Blocks.STONE);
        BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        helper.setBlock(min.offset(1, 1, 0), door);
        helper.setBlock(min.offset(1, 2, 0), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        helper.setBlock(min.offset(0, 1, 1), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
        helper.setBlock(min.offset(2, 1, 2), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH));
        helper.getBlockEntity(min.offset(2, 1, 2), ChestBlockEntity.class).setItem(0, new ItemStack(Items.DIAMOND, 3));
    }

    /**
     * Captures the box of size at min as TileArchitect does for a table facing west (no turn), with the table's place
     * one block west of min as the anchor, stores it in the server's library and returns the item.
     */
    private static ItemStack capture(GameTestHelper helper, BlockPos min, int sizeX, int sizeY, int sizeZ) {
        BlockPos absMin = helper.absolutePos(min);
        BlockPos absMax = helper.absolutePos(min.offset(sizeX - 1, sizeY - 1, sizeZ - 1));
        Box box = new Box();
        box.initialize(Math.min(absMin.getX(), absMax.getX()), absMin.getY(), Math.min(absMin.getZ(), absMax.getZ()),
                Math.max(absMin.getX(), absMax.getX()), absMax.getY(), Math.max(absMin.getZ(), absMax.getZ()));
        BptBlueprint bpt = new BptBlueprint(box.sizeX(), box.sizeY(), box.sizeZ());
        BptContext context = new BptContext(helper.getLevel(), bpt, box);
        for (int x = box.xMin; x <= box.xMax; ++x)
            for (int y = box.yMin; y <= box.yMax; ++y)
                for (int z = box.zMin; z <= box.zMax; ++z)
                    bpt.readFromWorld(context, null, x, y, z);
        BlockPos architect = helper.absolutePos(min.west());
        bpt.anchorX = architect.getX() - box.xMin;
        bpt.anchorY = architect.getY() - box.yMin;
        bpt.anchorZ = architect.getZ() - box.zMin;
        return store(helper, bpt, BuildersRegistry.blueprintItem.get());
    }

    private static ItemStack store(GameTestHelper helper, BptBase bpt, Item item) {
        BptRootIndex index = BuildCraftBuilders.getBptRootIndex(helper.getLevel().getServer());
        helper.assertTrue(index != null, "no blueprint library");
        int id = index.storeBluePrint(bpt);
        helper.assertTrue(id > 0, "not stored");
        return BuildCraftBuilders.getBptItemStack(item, id, bpt.getName(), bpt.author);
    }

    private static ServerPlayer placer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.snapTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(-6, 9, -6))));
        return player;
    }

    private static TileBuilder placeBuilder(GameTestHelper helper, Direction facing, ServerPlayer placer) {
        helper.setBlock(BUILDER, BuildersRegistry.builderBlock.get().defaultBlockState().setValue(BlockBuilder.FACING, facing));
        TileBuilder tile = helper.getBlockEntity(BUILDER, TileBuilder.class);
        tile.onBlockPlacedBy(placer, new ItemStack(BuildersRegistry.builderBlock.get()));
        return tile;
    }

    private static void power(GameTestHelper helper) {
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(BUILDER)) instanceof TileBuilder))
            return;
        EnergyHandler handler = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(BUILDER),
                Direction.UP);
        helper.assertTrue(handler != null, "the builder has no energy capability");
        try (Transaction tx = Transaction.openRoot()) {
            handler.insert(Integer.MAX_VALUE, tx);
            tx.commit();
        }
    }

    private static void assertSurvival(GameTestHelper helper) {
        helper.assertValueEqual(helper.getLevel().getServer().getWorldData().getGameType(), GameType.SURVIVAL,
                "the builder batch's game type");
    }

    /** Checks every position of a 3 x 3 x 3 box: min plus (x, y, z) holds expected.at(x, y, z), by block and facing. */
    private static void assertBox(GameTestHelper helper, BlockPos min, Expected expected) {
        for (int x = 0; x < 3; x++)
            for (int y = 0; y < 3; y++)
                for (int z = 0; z < 3; z++) {
                    BlockState want = expected.at(x, y, z);
                    BlockState got = helper.getBlockState(min.offset(x, y, z));
                    boolean same = got.getBlock() == want.getBlock();
                    if (same && want.hasProperty(StairBlock.FACING))
                        same = got.getValue(StairBlock.FACING) == want.getValue(StairBlock.FACING);
                    if (same && want.hasProperty(DoorBlock.HALF))
                        same = got.getValue(DoorBlock.HALF) == want.getValue(DoorBlock.HALF);
                    helper.assertTrue(same, "at " + min.offset(x, y, z) + " (" + x + ", " + y + ", " + z + "): " + got
                            + ", wanted " + want);
                }
    }

    /** The house of {@link #house} with its north turned to facing. */
    private static BlockState houseBlock(int x, int y, int z, Direction facing) {
        if (y == 0)
            return Blocks.STONE.defaultBlockState();
        if (x == 1 && z == 0)
            return Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing)
                    .setValue(DoorBlock.HALF, y == 1 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
        if (y == 1 && x == 0 && z == 1)
            return Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing);
        if (y == 1 && x == 2 && z == 2)
            return Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing);
        return Blocks.AIR.defaultBlockState();
    }

    private static int count(Container inv, Item item) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++)
            if (inv.getItem(i).is(item))
                n += inv.getItem(i).getCount();
        return n;
    }

    /**
     * A builder facing west builds the house beside itself as captured: the clear pass leaves the air alone, the stone
     * goes first, then the door from its lower half (both halves), the stairs and the chest with its three diamonds.
     * The stone comes from a chest next to the builder, the rest from the builder's own slots; the needed list starts
     * with the stone. The builder ends Done, its box gone, and the menu has 4.2.2's slots.
     */
    static void blueprintWithMaterials(GameTestHelper helper) {
        assertSurvival(helper);
        house(helper, SOURCE);
        ItemStack blueprint = capture(helper, SOURCE, 3, 3, 3);
        ServerPlayer placer = placer(helper);
        TileBuilder tile = placeBuilder(helper, Direction.WEST, placer);
        helper.setBlock(BUILDER.west(), Blocks.CHEST);
        ChestBlockEntity stock = helper.getBlockEntity(BUILDER.west(), ChestBlockEntity.class);
        stock.setItem(3, new ItemStack(Items.STONE, 11));
        tile.setItem(1, new ItemStack(Items.OAK_DOOR));
        tile.setItem(2, new ItemStack(Items.OAK_STAIRS));
        tile.setItem(3, new ItemStack(Items.CHEST));
        tile.setItem(4, new ItemStack(Items.DIAMOND, 3));
        tile.setItem(0, blueprint);

        ContainerBuilder menu = new ContainerBuilder(1, placer.getInventory(), tile);
        helper.assertValueEqual(menu.slots.size(), 1 + 27 + 36, "menu slots");
        helper.assertValueEqual(menu.slots.get(0).x, 80, "blueprint slot x");
        helper.assertValueEqual(menu.slots.get(0).y, 27, "blueprint slot y");
        helper.assertTrue(tile.isBuildingBlueprint(), "not building a blueprint");

        helper.onEachTick(() -> power(helper));
        BlockPos min = BUILDER.east();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(tile.getBluePrintBuilder() != null, "no blueprint builder"))
                .thenExecute(() -> {
                    Collection<ItemStack> needs = tile.getNeededItems();
                    helper.assertTrue(needs != null && !needs.isEmpty() && needs.iterator().next().is(Items.STONE),
                            "needed " + needs);
                    helper.assertTrue(tile.getBox().isInitialized(), "no box");
                    helper.assertValueEqual(tile.getBox().xMin, helper.absolutePos(min).getX(), "box xMin");
                })
                .thenWaitUntil(() -> helper.assertValueEqual(tile.getStatusState(), MachineStatus.DONE, "State"))
                .thenExecute(() -> {
                    assertBox(helper, min, (x, y, z) -> houseBlock(x, y, z, Direction.NORTH));
                    ChestBlockEntity chest = helper.getBlockEntity(min.offset(2, 1, 2), ChestBlockEntity.class);
                    helper.assertValueEqual(count(chest, Items.DIAMOND), 3, "diamonds in the built chest");
                    helper.assertValueEqual(count(stock, Items.STONE), 2, "stone left in the chest beside the builder");
                    for (int i = 1; i < tile.getContainerSize(); i++)
                        helper.assertTrue(tile.getItem(i).isEmpty(), "left in builder slot " + i + ": " + tile.getItem(i));
                    helper.assertTrue(tile.getItem(0) == blueprint || tile.getItem(0).is(BuildersRegistry.blueprintItem.get()),
                            "the blueprint went");
                })
                .thenWaitUntil(() -> helper.assertTrue(!tile.getBox().isInitialized(), "the box is still up"))
                .thenSucceed();
    }

    /**
     * Facing north the builder builds behind itself (south), the house turned once clockwise: a point east of the
     * architect's place lands south of the builder, and the door, stairs and chest face east.
     */
    static void blueprintTurned(GameTestHelper helper) {
        assertSurvival(helper);
        house(helper, SOURCE);
        ItemStack blueprint = capture(helper, SOURCE, 3, 3, 3);
        TileBuilder tile = placeBuilder(helper, Direction.NORTH, placer(helper));
        tile.setItem(1, new ItemStack(Items.STONE, 9));
        tile.setItem(2, new ItemStack(Items.OAK_DOOR));
        tile.setItem(3, new ItemStack(Items.OAK_STAIRS));
        tile.setItem(4, new ItemStack(Items.CHEST));
        tile.setItem(5, new ItemStack(Items.DIAMOND, 3));
        tile.setItem(0, blueprint);

        helper.onEachTick(() -> power(helper));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertValueEqual(tile.getStatusState(), MachineStatus.DONE, "State"))
                .thenExecute(() -> {
                    // Captured offset from the architect (dx, dy, dz) = (x + 1, y, z) lands at (-dz, dy, dx) from the
                    // builder.
                    for (int x = 0; x < 3; x++)
                        for (int y = 0; y < 3; y++)
                            for (int z = 0; z < 3; z++) {
                                BlockState want = houseBlock(x, y, z, Direction.EAST);
                                BlockPos at = BUILDER.offset(-z, y, x + 1);
                                BlockState got = helper.getBlockState(at);
                                boolean same = got.getBlock() == want.getBlock();
                                if (same && want.hasProperty(StairBlock.FACING))
                                    same = got.getValue(StairBlock.FACING) == want.getValue(StairBlock.FACING);
                                if (same && want.hasProperty(DoorBlock.HALF))
                                    same = got.getValue(DoorBlock.HALF) == want.getValue(DoorBlock.HALF);
                                helper.assertTrue(same, "at " + at + " for (" + x + ", " + y + ", " + z + "): " + got
                                        + ", wanted " + want);
                            }
                    ChestBlockEntity chest = helper.getBlockEntity(BUILDER.offset(-2, 1, 3), ChestBlockEntity.class);
                    helper.assertValueEqual(count(chest, Items.DIAMOND), 3, "diamonds in the built chest");
                    for (int i = 1; i < tile.getContainerSize(); i++)
                        helper.assertTrue(tile.getItem(i).isEmpty(), "left in builder slot " + i + ": " + tile.getItem(i));
                })
                .thenSucceed();
    }

    /**
     * A template of a ring (a 3 x 3 floor without its centre, nothing above): the dirt in the centre and above it is
     * cleared, a bedrock block above the ring is passed over (BuildCraft#1789), the ring is filled with cobblestone
     * from the builder, which keeps the two it did not need.
     */
    static void templateWithMaterials(GameTestHelper helper) {
        assertSurvival(helper);
        BptTemplate template = new BptTemplate(3, 2, 3);
        for (int x = 0; x < 3; x++)
            for (int z = 0; z < 3; z++) {
                template.setMask(x, 0, z, x == 1 && z == 1 ? 0 : 1);
                template.setMask(x, 1, z, 0);
            }
        template.anchorX = -1;
        ItemStack item = store(helper, template, BuildersRegistry.templateItem.get());

        BlockPos min = BUILDER.east();
        helper.setBlock(min.offset(1, 0, 1), Blocks.DIRT);
        helper.setBlock(min.offset(1, 1, 1), Blocks.DIRT);
        helper.setBlock(min.offset(0, 1, 0), Blocks.BEDROCK);
        TileBuilder tile = placeBuilder(helper, Direction.WEST, placer(helper));
        tile.setItem(1, new ItemStack(Items.COBBLESTONE, 10));
        tile.setItem(0, item);
        helper.assertTrue(!tile.isBuildingBlueprint(), "a template counts as a blueprint");

        helper.onEachTick(() -> power(helper));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertValueEqual(tile.getStatusState(), MachineStatus.DONE, "State"))
                .thenExecute(() -> {
                    for (int x = 0; x < 3; x++)
                        for (int z = 0; z < 3; z++) {
                            Block want = x == 1 && z == 1 ? Blocks.AIR : Blocks.COBBLESTONE;
                            helper.assertValueEqual(helper.getBlockState(min.offset(x, 0, z)).getBlock(), want,
                                    "floor at " + x + ", " + z);
                            helper.assertValueEqual(helper.getBlockState(min.offset(x, 1, z)).getBlock(),
                                    x == 0 && z == 0 ? Blocks.BEDROCK : Blocks.AIR, "above the floor at " + x + ", " + z);
                        }
                    helper.assertValueEqual(count(tile, Items.COBBLESTONE), 2, "cobblestone left");
                })
                .thenSucceed();
    }

    /**
     * A 3 x 3 stone floor with four stone: four are built, then the builder waits, Blocked with "missing materials" and
     * its red light on. Five more stone and it finishes.
     */
    static void missingMaterials(GameTestHelper helper) {
        assertSurvival(helper);
        for (int x = 0; x < 3; x++)
            for (int z = 0; z < 3; z++)
                helper.setBlock(SOURCE.offset(x, 0, z), Blocks.STONE);
        ItemStack blueprint = capture(helper, SOURCE, 3, 1, 3);
        TileBuilder tile = placeBuilder(helper, Direction.WEST, placer(helper));
        tile.setItem(1, new ItemStack(Items.STONE, 4));
        tile.setItem(0, blueprint);
        BlockPos min = BUILDER.east();

        helper.onEachTick(() -> power(helper));
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertValueEqual(tile.getStatusState(), MachineStatus.BLOCKED, "State");
                    helper.assertTrue(tile.getRobot() != null && tile.getRobot().done(), "the robot is still building");
                })
                .thenExecute(() -> {
                    helper.assertValueEqual(tile.getStatusReason(), StatusReason.MISSING_MATERIALS, "Reason");
                    int built = 0;
                    for (int x = 0; x < 3; x++)
                        for (int z = 0; z < 3; z++)
                            if (helper.getBlockState(min.offset(x, 0, z)).is(Blocks.STONE))
                                built++;
                    helper.assertValueEqual(built, 4, "stone built");
                })
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockState(BUILDER).getValue(StatusPanel.BLOCKED),
                        "red light off"))
                .thenExecute(() -> tile.setItem(5, new ItemStack(Items.STONE, 5)))
                .thenWaitUntil(() -> helper.assertValueEqual(tile.getStatusState(), MachineStatus.DONE, "State"))
                .thenExecute(() -> {
                    for (int x = 0; x < 3; x++)
                        for (int z = 0; z < 3; z++)
                            helper.assertValueEqual(helper.getBlockState(min.offset(x, 0, z)).getBlock(), Blocks.STONE,
                                    "floor at " + x + ", " + z);
                    helper.assertValueEqual(tile.getStatusReason(), StatusReason.NONE, "Reason when done");
                })
                .thenWaitUntil(() -> helper.assertTrue(!helper.getBlockState(BUILDER).getValue(StatusPanel.BLOCKED),
                        "red light on"))
                .thenSucceed();
    }

    /**
     * A 3 x 3 stone floor where a claim protects a dirt block in the middle (no breaking) and an empty corner (no
     * placing): the builder clears and builds around both, gives the two refused stone back and ends Done; the claim
     * saw the owner's UUID at both.
     */
    static void protectedBlock(GameTestHelper helper) {
        assertSurvival(helper);
        for (int x = 0; x < 3; x++)
            for (int z = 0; z < 3; z++)
                helper.setBlock(SOURCE.offset(x, 0, z), Blocks.STONE);
        ItemStack blueprint = capture(helper, SOURCE, 3, 1, 3);
        BlockPos min = BUILDER.east();
        helper.setBlock(min.offset(1, 0, 1), Blocks.DIRT);
        BlockPos dirt = helper.absolutePos(min.offset(1, 0, 1));
        BlockPos corner = helper.absolutePos(min);
        PROTECTED.add(dirt);
        PROTECTED.add(corner);
        ServerPlayer placer = placer(helper);
        TileBuilder tile = placeBuilder(helper, Direction.WEST, placer);
        tile.setItem(1, new ItemStack(Items.STONE, 9));
        tile.setItem(0, blueprint);

        helper.onEachTick(() -> power(helper));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertValueEqual(tile.getStatusState(), MachineStatus.DONE, "State"))
                .thenExecute(() -> {
                    try {
                        for (int x = 0; x < 3; x++)
                            for (int z = 0; z < 3; z++) {
                                Block want = x == 1 && z == 1 ? Blocks.DIRT : x == 0 && z == 0 ? Blocks.AIR : Blocks.STONE;
                                helper.assertValueEqual(helper.getBlockState(min.offset(x, 0, z)).getBlock(), want,
                                        "floor at " + x + ", " + z);
                            }
                        helper.assertValueEqual(count(tile, Items.STONE), 2, "stone given back");
                        helper.assertValueEqual(REFUSED.get(dirt), placer.getUUID(), "breaker seen by the claim");
                        helper.assertValueEqual(REFUSED.get(corner), placer.getUUID(), "placer seen by the claim");
                    } finally {
                        PROTECTED.remove(dirt);
                        PROTECTED.remove(corner);
                        REFUSED.remove(dirt);
                        REFUSED.remove(corner);
                    }
                })
                .thenSucceed();
    }
}
