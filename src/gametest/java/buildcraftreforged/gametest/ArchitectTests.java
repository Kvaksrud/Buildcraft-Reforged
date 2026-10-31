package buildcraftreforged.gametest;

import buildcraftreforged.builders.BlockArchitect;
import buildcraftreforged.builders.BlockMarker;
import buildcraftreforged.builders.BptItemData;
import buildcraftreforged.builders.BuildCraftBuilders;
import buildcraftreforged.builders.BuildersRegistry;
import buildcraftreforged.builders.ItemBptBase;
import buildcraftreforged.builders.TileArchitect;
import buildcraftreforged.builders.TileMarker;
import buildcraftreforged.core.blueprints.BptBase;
import buildcraftreforged.core.blueprints.BptBlueprint;
import buildcraftreforged.core.blueprints.BptError;
import buildcraftreforged.core.blueprints.BptRootIndex;
import buildcraftreforged.core.blueprints.BptSlot;
import buildcraftreforged.core.blueprints.BptTemplate;
import buildcraftreforged.core.network.InvalidPacketException;
import buildcraftreforged.core.network.PacketData;
import buildcraftreforged.core.status.MachineStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * #77 architect table: four markers give a 3 x 3 x 3 area (x, z 0..2 and y 4..6) beside the table at (-1, 4, 0). A
 * small structure in it is scanned, over 4.2.2's 200 ticks, into the server's blueprint library and onto the item.
 */
final class ArchitectTests {
    static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();
    static final int MAX_TICKS = 700;

    private static final BlockPos ORIGIN = new BlockPos(0, 4, 0);
    private static final BlockPos ARCHITECT = new BlockPos(-1, 4, 0);

    /** Positions in the box (relative to its minimum, the origin marker). */
    private static final BlockPos STONE = new BlockPos(1, 0, 1);
    private static final BlockPos COBBLE = new BlockPos(2, 0, 1);
    private static final BlockPos STAIRS = new BlockPos(1, 1, 1);
    private static final BlockPos CHEST = new BlockPos(1, 0, 2);

    static {
        TESTS.put("architect_scan_blueprint", ArchitectTests::scanBlueprint);
        TESTS.put("architect_scan_template", ArchitectTests::scanTemplate);
        TESTS.put("architect_facing_turns_capture", ArchitectTests::facingTurnsCapture);
    }

    private ArchitectTests() {
    }

    /** The FillerTests marker layout: three markers on a stone floor at y = 4, the fourth at y = 6 on a stone. */
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

    /** Stone and cobblestone on the bottom layer, stairs facing north on the stone, a chest with five diamonds. */
    private static void structure(GameTestHelper helper) {
        helper.setBlock(ORIGIN.offset(STONE), Blocks.STONE);
        helper.setBlock(ORIGIN.offset(COBBLE), Blocks.COBBLESTONE);
        helper.setBlock(ORIGIN.offset(STAIRS), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
        helper.setBlock(ORIGIN.offset(CHEST), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH));
        helper.getBlockEntity(ORIGIN.offset(CHEST), ChestBlockEntity.class).setItem(0, new ItemStack(Items.DIAMOND, 5));
    }

    private static TileArchitect placeArchitect(GameTestHelper helper, Direction facing, @Nullable ServerPlayer placer) {
        helper.setBlock(ARCHITECT, BuildersRegistry.architectBlock.get().defaultBlockState().setValue(BlockArchitect.FACING, facing));
        TileArchitect tile = helper.getBlockEntity(ARCHITECT, TileArchitect.class);
        if (placer != null)
            tile.onBlockPlacedBy(placer, new ItemStack(BuildersRegistry.architectBlock.get()));
        return tile;
    }

    /** Types into the name field as GuiTemplate does: one PacketGuiReturn per character. */
    private static void type(GameTestHelper helper, TileArchitect tile, String text) {
        for (char c : text.toCharArray()) {
            RegistryFriendlyByteBuf data = PacketData.wrap(PacketData.write(helper.getLevel().registryAccess(),
                    buf -> buf.writeChar(c)), helper.getLevel().registryAccess());
            try {
                tile.readGuiData(data, null);
            } catch (InvalidPacketException e) {
                throw helper.assertionException(Component.literal("name character refused: " + e.getMessage()));
            } finally {
                data.release();
            }
        }
    }

    private static BptRootIndex index(GameTestHelper helper) {
        BptRootIndex index = BuildCraftBuilders.getBptRootIndex(helper.getLevel().getServer());
        helper.assertTrue(index != null, "no blueprint library");
        return index;
    }

    /** The output item, checked to be a used item of the same kind, with its library entry. */
    private static BptItemData output(GameTestHelper helper, TileArchitect tile, ItemStack input) {
        ItemStack out = tile.getItem(1);
        helper.assertTrue(out.is(input.getItem()), "output " + out + " for " + input);
        helper.assertTrue(tile.getItem(0).isEmpty(), "input not used up: " + tile.getItem(0));
        BptItemData data = ItemBptBase.getBptData(out);
        helper.assertTrue(data != null && data.id() > 0, "output carries no blueprint: " + out.getComponents());
        return data;
    }

    private static void scan(GameTestHelper helper, TileArchitect tile, ItemStack clean) {
        tile.setItem(0, clean);
        helper.assertTrue(tile.isComputing(), "not computing with " + clean);
        helper.assertValueEqual(tile.getStatusState(), MachineStatus.WORKING, "State while scanning");
    }

    /**
     * A named blueprint of the structure: the item holds the library number, name and author; the file in the world's
     * library reads back equal, with the chest's diamonds; the markers are gone and their places captured as air. The
     * same structure scanned again reuses the library entry (4.2.2's lastBptId, which #75's equals makes work).
     */
    static void scanBlueprint(GameTestHelper helper) {
        markers(helper);
        structure(helper);
        ServerPlayer placer = helper.makeMockServerPlayerInLevel();
        TileArchitect tile = placeArchitect(helper, Direction.WEST, placer);
        AtomicInteger firstId = new AtomicInteger();

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(tile.getBox().isInitialized(), "no area"))
                .thenExecute(() -> {
                    helper.assertBlockNotPresent(BuildersRegistry.markerBlock.get(), ORIGIN);
                    helper.assertValueEqual(tile.getStatusState(), MachineStatus.IDLE, "State without an item");
                    // A backspace, and '/' which 4.2.2 ignored; names stop at 14 characters.
                    type(helper, tile, "Hutx\b 1/");
                    helper.assertValueEqual(tile.name, "Hut 1", "typed name");
                    scan(helper, tile, new ItemStack(BuildersRegistry.blueprintItem.get()));
                })
                .thenIdle(TileArchitect.COMPUTING_TIME - 10)
                .thenExecute(() -> helper.assertTrue(tile.getItem(1).isEmpty(), "done before 200 ticks"))
                .thenWaitUntil(() -> helper.assertFalse(tile.getItem(1).isEmpty(), "no output yet"))
                .thenExecute(() -> {
                    BptItemData data = output(helper, tile, new ItemStack(BuildersRegistry.blueprintItem.get()));
                    helper.assertValueEqual(data.name(), "Hut 1", "item name");
                    helper.assertValueEqual(data.author(), placer.getGameProfile().name(), "item author");
                    helper.assertValueEqual(tile.getStatusState(), MachineStatus.DONE, "State with the output");
                    firstId.set(data.id());

                    BptRootIndex index = index(helper);
                    Path file = index.getBluePrintFile(data.id());
                    helper.assertTrue(file != null && Files.exists(file), "no file for blueprint " + data.id());
                    helper.assertTrue(file.startsWith(index.getBaseDir()) && file.getFileName().toString().startsWith("Hut 1"),
                            "file " + file);
                    BptBase read;
                    try {
                        read = BptBase.readBluePrint(file, data.id(), helper.getLevel().registryAccess());
                    } catch (BptError e) {
                        throw helper.assertionException(Component.literal("blueprint does not read back: " + e.getMessage()));
                    }
                    helper.assertTrue(read instanceof BptBlueprint, "read back as " + read);
                    helper.assertTrue(read.equals(index.getBluePrint(data.id())), "file differs from the library entry");
                    helper.assertValueEqual(read.getName(), "Hut 1", "blueprint name");
                    helper.assertTrue(read.sizeX == 3 && read.sizeY == 3 && read.sizeZ == 3, "size");
                    helper.assertTrue(read.anchorX == -1 && read.anchorY == 0 && read.anchorZ == 0,
                            "anchor " + read.anchorX + ", " + read.anchorY + ", " + read.anchorZ);

                    helper.assertTrue(slot(helper, read, STONE).state.is(Blocks.STONE), "stone");
                    helper.assertTrue(slot(helper, read, COBBLE).state.is(Blocks.COBBLESTONE), "cobblestone");
                    helper.assertValueEqual(slot(helper, read, STAIRS).state,
                            Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH), "stairs");
                    BptSlot chest = slot(helper, read, CHEST);
                    helper.assertTrue(chest.state.is(Blocks.CHEST) && chest.cpt.getListOrEmpty("Items").size() == 1,
                            "chest and its diamonds: " + chest.cpt);
                    helper.assertTrue(slot(helper, read, BlockPos.ZERO).state.isAir(), "origin marker captured");
                    helper.assertTrue(slot(helper, read, new BlockPos(0, 2, 0)).state.isAir(), "top marker captured");

                    // Take the blueprint out and scan the unchanged structure again.
                    tile.removeItem(1, 1);
                    scan(helper, tile, new ItemStack(BuildersRegistry.blueprintItem.get()));
                })
                .thenWaitUntil(() -> helper.assertFalse(tile.getItem(1).isEmpty(), "no second output yet"))
                .thenExecute(() -> helper.assertValueEqual(
                        output(helper, tile, new ItemStack(BuildersRegistry.blueprintItem.get())).id(), firstId.get(),
                        "library entry of the second scan"))
                .thenSucceed();
    }

    /**
     * A template of the structure: filled where a block stands, empty elsewhere, unnamed ("#" and its number) and
     * without an author. With a redstone signal at the
     * table the mask is inverted (4.2.2's mask0/mask1 swap), which is a new library entry.
     */
    static void scanTemplate(GameTestHelper helper) {
        markers(helper);
        structure(helper);
        TileArchitect tile = placeArchitect(helper, Direction.WEST, null);
        AtomicInteger firstId = new AtomicInteger();

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(tile.getBox().isInitialized(), "no area"))
                .thenExecute(() -> scan(helper, tile, new ItemStack(BuildersRegistry.templateItem.get())))
                .thenWaitUntil(() -> helper.assertFalse(tile.getItem(1).isEmpty(), "no output yet"))
                .thenExecute(() -> {
                    BptItemData data = output(helper, tile, new ItemStack(BuildersRegistry.templateItem.get()));
                    // 4.2.2: an unnamed blueprint's getName() is "#" and its library number, which goes on the item.
                    helper.assertValueEqual(data.name(), "#" + data.id(), "item name");
                    helper.assertValueEqual(data.author(), "", "item author");
                    firstId.set(data.id());
                    BptTemplate template = template(helper, data.id());
                    assertMask(helper, template, false);

                    tile.removeItem(1, 1);
                    helper.setBlock(ARCHITECT.west(), Blocks.REDSTONE_BLOCK);
                    scan(helper, tile, new ItemStack(BuildersRegistry.templateItem.get()));
                })
                .thenWaitUntil(() -> helper.assertFalse(tile.getItem(1).isEmpty(), "no second output yet"))
                .thenExecute(() -> {
                    BptItemData data = output(helper, tile, new ItemStack(BuildersRegistry.templateItem.get()));
                    helper.assertTrue(data.id() != firstId.get(), "inverted template reused entry " + data.id());
                    assertMask(helper, template(helper, data.id()), true);
                })
                .thenSucceed();
    }

    /**
     * The table's facing turns the capture as 4.2.2 did (front south: one rotateLeft), and the wrench cycle is 4.2.2's
     * west, south, east, north.
     */
    static void facingTurnsCapture(GameTestHelper helper) {
        markers(helper);
        structure(helper);
        TileArchitect tile = placeArchitect(helper, Direction.WEST, null);
        BlockArchitect block = BuildersRegistry.architectBlock.get();
        BlockPos pos = helper.absolutePos(ARCHITECT);
        for (Direction next : List.of(Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST)) {
            block.rotateBlock(helper.getLevel(), pos, Direction.UP);
            helper.assertValueEqual(helper.getBlockState(ARCHITECT).getValue(BlockArchitect.FACING), next, "wrench turn");
        }
        block.rotateBlock(helper.getLevel(), pos, Direction.UP);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(tile.getBox().isInitialized(), "no area"))
                .thenExecute(() -> scan(helper, tile, new ItemStack(BuildersRegistry.blueprintItem.get())))
                .thenWaitUntil(() -> helper.assertFalse(tile.getItem(1).isEmpty(), "no output yet"))
                .thenExecute(() -> {
                    BptBase bpt = index(helper).getBluePrint(output(helper, tile,
                            new ItemStack(BuildersRegistry.blueprintItem.get())).id());
                    helper.assertTrue(bpt instanceof BptBlueprint, "blueprint " + bpt);
                    // rotateLeft: (x, y, z) to (sizeZ - 1 - z, y, x), block states turned clockwise.
                    helper.assertTrue(slot(helper, bpt, new BlockPos(1, 0, 2)).state.is(Blocks.COBBLESTONE), "cobblestone");
                    helper.assertTrue(slot(helper, bpt, new BlockPos(0, 0, 1)).state.is(Blocks.CHEST), "chest");
                    helper.assertValueEqual(slot(helper, bpt, new BlockPos(1, 1, 1)).state,
                            Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST), "stairs");
                })
                .thenSucceed();
    }

    private static BptSlot slot(GameTestHelper helper, BptBase bpt, BlockPos pos) {
        BptSlot slot = bpt.getSlot(pos.getX(), pos.getY(), pos.getZ());
        helper.assertTrue(slot != null, "no slot at " + pos);
        return slot;
    }

    private static BptTemplate template(GameTestHelper helper, int id) {
        BptBase bpt = index(helper).getBluePrint(id);
        helper.assertTrue(bpt instanceof BptTemplate, "template " + id + " is " + bpt);
        BptTemplate template = (BptTemplate) bpt;
        helper.assertTrue(template.sizeX == 3 && template.sizeY == 3 && template.sizeZ == 3, "template size");
        return template;
    }

    private static void assertMask(GameTestHelper helper, BptTemplate template, boolean inverted) {
        List<BlockPos> blocks = List.of(STONE, COBBLE, STAIRS, CHEST);
        for (int x = 0; x < 3; x++)
            for (int y = 0; y < 3; y++)
                for (int z = 0; z < 3; z++) {
                    boolean block = blocks.contains(new BlockPos(x, y, z));
                    helper.assertValueEqual(template.isFilled(x, y, z), block != inverted,
                            "mask at " + x + ", " + y + ", " + z + (inverted ? " (powered)" : ""));
                }
    }
}
