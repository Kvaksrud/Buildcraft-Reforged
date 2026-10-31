package buildcraftreforged.gametest;

import buildcraftreforged.core.Box;
import buildcraftreforged.core.blueprints.BlueprintPaths;
import buildcraftreforged.core.blueprints.BptBase;
import buildcraftreforged.core.blueprints.BptBlueprint;
import buildcraftreforged.core.blueprints.BptContext;
import buildcraftreforged.core.blueprints.BptError;
import buildcraftreforged.core.blueprints.BptRootIndex;
import buildcraftreforged.core.blueprints.BptSlot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.LevelResource;

/**
 * #75 blueprint format: capture a small structure from the world, save it to a library, read it back, and place the
 * read copy turned once (clockwise) beside the original.
 */
final class BlueprintTests {
    static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();
    static final int MAX_TICKS = 20;

    /** The captured box, relative to the test origin: 3 wide (x), 2 high, 2 deep (z). */
    private static final BlockPos MIN = new BlockPos(1, 2, 1);
    private static final BlockPos MAX = new BlockPos(3, 3, 2);
    /** Where the turned copy goes. */
    private static final BlockPos COPY = new BlockPos(6, 2, 1);

    static {
        TESTS.put("blueprint_capture_and_reread", BlueprintTests::captureAndReread);
    }

    private BlueprintTests() {
    }

    static void captureAndReread(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        // Stairs facing north, a chest holding five diamonds, stone, and cobblestone on the back row's top.
        helper.setBlock(MIN, Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
        helper.setBlock(MIN.east(), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH));
        helper.setBlock(MIN.east(2), Blocks.STONE);
        helper.setBlock(MIN.above().south(), Blocks.COBBLESTONE);
        ChestBlockEntity chest = helper.getBlockEntity(MIN.east(), ChestBlockEntity.class);
        chest.setItem(4, new ItemStack(Items.DIAMOND, 5));

        // Capture, as the architect does: one readFromWorld per position of the box.
        BlockPos min = helper.absolutePos(MIN);
        BlockPos max = helper.absolutePos(MAX);
        Box box = new Box();
        box.initialize(Math.min(min.getX(), max.getX()), min.getY(), Math.min(min.getZ(), max.getZ()),
                Math.max(min.getX(), max.getX()), max.getY(), Math.max(min.getZ(), max.getZ()));
        BptBlueprint bpt = new BptBlueprint(box.sizeX(), box.sizeY(), box.sizeZ());
        BptContext context = new BptContext(level, bpt, box);
        for (int x = box.xMin; x <= box.xMax; ++x)
            for (int y = box.yMin; y <= box.yMax; ++y)
                for (int z = box.zMin; z <= box.zMax; ++z)
                    bpt.readFromWorld(context, null, x, y, z);
        bpt.setName("Test house");
        bpt.author = "GameTest";

        helper.assertTrue(bpt.sizeX == 3 && bpt.sizeY == 2 && bpt.sizeZ == 2, "box size " + box);
        BptSlot chestSlot = slotAt(helper, bpt, box, MIN.east());
        helper.assertTrue(chestSlot.cpt.getListOrEmpty("Items").size() == 1, "chest contents not captured: " + chestSlot.cpt);
        helper.assertTrue(chestSlot.storedRequirements.size() == 1 && chestSlot.storedRequirements.getFirst().is(Items.CHEST),
                "chest requirements " + chestSlot.storedRequirements);
        helper.assertTrue(slotAt(helper, bpt, box, MIN.above()).state.isAir(), "air captured as air");

        // Save into a library and read it back from disk.
        BptBase loaded;
        try {
            Path library = Files.createTempDirectory("bcr-blueprints");
            BptRootIndex index = new BptRootIndex(library, BlueprintPaths.ROOT_INDEX, level.registryAccess());
            index.loadIndex();
            int number = index.storeBluePrint(bpt);
            helper.assertTrue(Files.exists(library.resolve("Test house.bpt")), "blueprint file not written");

            loaded = BptBase.readBluePrint(library.resolve("Test house.bpt"), number, level.registryAccess());

            Files.delete(library.resolve("Test house.bpt"));
            Files.delete(library.resolve(BlueprintPaths.ROOT_INDEX));
            Files.delete(library);
        } catch (IOException | BptError e) {
            throw helper.assertionException(Component.literal("could not save and read the blueprint: " + e));
        }
        helper.assertTrue(loaded instanceof BptBlueprint, "read back as " + loaded);
        helper.assertTrue(bpt.equals(loaded), "read-back blueprint differs");
        helper.assertTrue("Test house".equals(loaded.getName()) && "GameTest".equals(loaded.author), "name or author lost");
        helper.assertTrue(slotAt(helper, loaded, box, MIN.east()).cpt.equals(chestSlot.cpt), "chest data differs");

        // The server's library lives under the world, not the working directory (plan section 19.2).
        Path serverLibrary = BlueprintPaths.serverLibrary(level.getServer());
        Path world = level.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        helper.assertTrue(serverLibrary.equals(world.resolve("buildcraft-reforged").resolve("blueprints")),
                "server library at " + serverLibrary);

        // Turn the read copy once and place it at COPY: (x, y, z) in the box goes to (sizeZ - 1 - z, y, x).
        Box copyBox = new Box();
        copyBox.initialize(0, 0, 0, loaded.sizeX - 1, loaded.sizeY - 1, loaded.sizeZ - 1);
        loaded.rotateLeft(new BptContext(level, (BptBlueprint) loaded, copyBox));
        BlockPos copy = helper.absolutePos(COPY);
        BptContext buildContext = new BptContext(level, (BptBlueprint) loaded, copyBox);
        for (int x = 0; x < loaded.sizeX; ++x)
            for (int y = 0; y < loaded.sizeY; ++y)
                for (int z = 0; z < loaded.sizeZ; ++z) {
                    BptSlot slot = loaded.getSlot(x, y, z);
                    if (slot == null)
                        continue;
                    // As the builder does: the slot is moved to its world position, then built.
                    slot.x = copy.getX() + x;
                    slot.y = copy.getY() + y;
                    slot.z = copy.getZ() + z;
                    slot.buildBlock(buildContext);
                }

        // Stairs (0, 0, 0) -> (1, 0, 0) facing east; chest (1, 0, 0) -> (1, 0, 1); stone (2, 0, 0) -> (1, 0, 2);
        // cobblestone (0, 1, 1) -> (0, 1, 0).
        helper.assertBlockState(COPY.east(), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
        helper.assertBlockState(COPY.east().south(), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST));
        helper.assertBlockPresent(Blocks.STONE, COPY.east().south(2));
        helper.assertBlockPresent(Blocks.COBBLESTONE, COPY.above());
        ChestBlockEntity built = helper.getBlockEntity(COPY.east().south(), ChestBlockEntity.class);
        helper.assertTrue(ItemStack.matches(built.getItem(4), new ItemStack(Items.DIAMOND, 5)),
                "built chest holds " + built.getItem(4));

        helper.succeed();
    }

    private static BptSlot slotAt(GameTestHelper helper, BptBase bpt, Box box, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        BptSlot slot = bpt.getSlot(pos.getX() - box.xMin, pos.getY() - box.yMin, pos.getZ() - box.zMin);
        helper.assertTrue(slot != null, "no slot at " + relative);
        return slot;
    }
}
