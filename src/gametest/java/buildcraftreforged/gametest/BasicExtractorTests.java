package buildcraftreforged.gametest;

import buildcraftreforged.api.transport.IPipeTile.PipeType;
import buildcraftreforged.core.RotationHelper;
import buildcraftreforged.energy.BlockEngine;
import buildcraftreforged.energy.EnergyRegistry;
import buildcraftreforged.energy.TileEngineWood;
import buildcraftreforged.transport.BlockBasicExtractor;
import buildcraftreforged.transport.PipeConnections;
import buildcraftreforged.transport.PipeMaterial;
import buildcraftreforged.transport.TileBasicExtractor;
import buildcraftreforged.transport.TilePipe;
import buildcraftreforged.transport.TransportRegistry;
import buildcraftreforged.transport.flow.FlowRecord;
import buildcraftreforged.transport.graph.PipeNetwork;
import buildcraftreforged.transport.graph.PipeNetworks;
import buildcraftreforged.transport.route.ItemRouting;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * #58: the Basic Extractor (docs/TRANSPORT.md 10). A redstone engine on it moves one item from a chest into a pipe line
 * per pulse; without an engine nothing moves; the pulse arithmetic (10 FE an item, 641 FE at most, the line's budget
 * caps it, only the FE used is taken); placement, the pipe-face-only connection, energy on the side faces only, the
 * wrench turning it to the next inventory, and the recipe. In the padded area (see {@link OilFuelTests#PADDING}).
 */
final class BasicExtractorTests {
    static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();
    static final int MAX_TICKS = 400;

    static {
        TESTS.put("basic_extractor_engine_pulses", BasicExtractorTests::enginePulses);
        TESTS.put("basic_extractor_pulse_rules", BasicExtractorTests::pulseRules);
        TESTS.put("basic_extractor_no_engine", BasicExtractorTests::noEngine);
    }

    private static final int Y = 2;
    private static final BlockPos SOURCE = new BlockPos(1, Y, 2);
    private static final BlockPos EXTRACTOR = SOURCE.east();
    private static final int PIPES = 3;
    private static final BlockPos DESTINATION = EXTRACTOR.east(PIPES + 1);

    private BasicExtractorTests() {
    }

    /** Chest of cobblestone, Basic Extractor (inventory face west), three pipes of {@code material}, empty chest. */
    private static void line(GameTestHelper helper, PipeMaterial material) {
        helper.setBlock(SOURCE, Blocks.CHEST);
        source(helper).setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        helper.setBlock(EXTRACTOR, TransportRegistry.basicExtractor.get().defaultBlockState()
                .setValue(BlockBasicExtractor.FACING, Direction.WEST));
        for (int i = 1; i <= PIPES; i++)
            helper.setBlock(EXTRACTOR.east(i), TransportRegistry.getPipe(PipeType.ITEM, material));
        helper.setBlock(DESTINATION, Blocks.CHEST);
    }

    private static Container source(GameTestHelper helper) {
        return (Container) helper.getBlockEntity(SOURCE, BlockEntity.class);
    }

    private static Container destination(GameTestHelper helper) {
        return (Container) helper.getBlockEntity(DESTINATION, BlockEntity.class);
    }

    private static TileBasicExtractor extractor(GameTestHelper helper) {
        return helper.getBlockEntity(EXTRACTOR, TileBasicExtractor.class);
    }

    private static int count(Container container) {
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++)
            count += container.getItem(slot).getCount();
        return count;
    }

    private static EnergyHandler energy(GameTestHelper helper, Direction side) {
        return helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(EXTRACTOR), side);
    }

    /** One pulse of {@code fe} into the top face, committed or not; returns the FE taken. */
    private static int pulse(GameTestHelper helper, int fe, boolean commit) {
        EnergyHandler handler = energy(helper, Direction.UP);
        helper.assertTrue(handler != null, "no energy handler on the top face");
        try (Transaction transaction = Transaction.openRoot()) {
            int taken = handler.insert(fe, transaction);
            if (commit)
                transaction.commit();
            return taken;
        }
    }

    /**
     * A redstone engine on top, facing down, powered: each pulse (progress passing 0.5) moves one cobblestone through
     * three iron pipes into the far chest, never more than one a tick; the extractor's flow record shows cobblestone.
     */
    static void enginePulses(GameTestHelper helper) {
        line(helper, PipeMaterial.IRON);
        BlockPos enginePos = EXTRACTOR.above();
        helper.setBlock(enginePos, EnergyRegistry.engineWood.block().get().defaultBlockState()
                .setValue(BlockEngine.FACING, Direction.DOWN));
        TileEngineWood engine = helper.getBlockEntity(enginePos, TileEngineWood.class);
        engine.energy = 800;
        helper.setBlock(enginePos.above(), Blocks.REDSTONE_BLOCK);

        int[] pulses = {0};
        float[] lastProgress = {0};
        int[] lastCount = {0};
        helper.onEachTick(() -> {
            float progress = engine.progress;
            if (lastProgress[0] <= 0.5F && progress > 0.5F)
                pulses[0]++;
            lastProgress[0] = progress;
            int now = count(destination(helper));
            helper.assertTrue(now - lastCount[0] <= 1, "more than one item in a tick: " + lastCount[0] + " -> " + now);
            lastCount[0] = now;
        });

        helper.runAfterDelay(300, () -> {
            int moved = count(destination(helper));
            helper.assertTrue(pulses[0] >= 3, "only " + pulses[0] + " engine pulses");
            helper.assertTrue(moved >= pulses[0] - 1 && moved <= pulses[0],
                    moved + " items moved for " + pulses[0] + " pulses");
            helper.assertValueEqual(count(source(helper)), 64 - moved, "items left in the source chest");
            FlowRecord record = extractor(helper).getSentRecord();
            helper.assertTrue(record.isFlowing() && record.content() != null
                    && record.content().value() == Items.COBBLESTONE, "extractor flow record " + record);
            helper.succeed();
        });
    }

    /**
     * Without engines: placement, connections and energy faces; 10 FE an item, 641 FE at most; the path budget caps a
     * pulse (a full gold line takes 16 of 64 items, then none until it refills) and only the FE used is taken; an aborted pulse moves nothing; the wrench turns it to the next inventory;
     * planks, glass, planks in a column craft one.
     */
    static void pulseRules(GameTestHelper helper) {
        helper.assertValueEqual(TileBasicExtractor.itemsForPulse(10), 1, "10 FE");
        helper.assertValueEqual(TileBasicExtractor.itemsForPulse(9), 0, "9 FE");
        helper.assertValueEqual(TileBasicExtractor.itemsForPulse(641), 64, "641 FE");
        helper.assertValueEqual(TileBasicExtractor.itemsForPulse(1000), 64, "1000 FE");

        line(helper, PipeMaterial.GOLD);
        BlockPos sidePipe = EXTRACTOR.north();
        helper.setBlock(sidePipe, TransportRegistry.getPipe(PipeType.ITEM, PipeMaterial.GOLD));

        helper.runAfterDelay(3, () -> {
            ServerLevel level = helper.getLevel();
            TilePipe first = helper.getBlockEntity(EXTRACTOR.east(), TilePipe.class);
            helper.assertTrue(PipeConnections.hasSide(first.getConnectionMask(), Direction.WEST),
                    "the pipe on the pipe face does not connect");
            helper.assertFalse(PipeConnections.hasSide(helper.getBlockEntity(sidePipe, TilePipe.class).getConnectionMask(),
                    Direction.SOUTH), "a pipe on a side face connects");
            for (Direction side : List.of(Direction.WEST, Direction.EAST))
                helper.assertTrue(energy(helper, side) == null, "energy handler on the " + side + " face");
            for (Direction side : List.of(Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH))
                helper.assertTrue(energy(helper, side) != null, "no energy handler on the " + side + " face");

            // Make sure every budget of the line is counting; 40 ticks later they are all full.
            PipeNetwork network = PipeNetworks.get(level, helper.absolutePos(EXTRACTOR.east()));
            helper.assertTrue(network != null && network.size() == PIPES, "line network " + network);
            for (int node = 0; node < network.size(); node++)
                ItemRouting.ROUTER.available(network.nodes[node], level.getGameTime(), ItemRouting.settings());

            helper.runAfterDelay(40, () -> {
                int node = network.indexOf(helper.absolutePos(EXTRACTOR.east()).asLong());
                long budget = ItemRouting.ROUTER.available(network.nodes[node], level.getGameTime(),
                        ItemRouting.settings()) / ItemRouting.ITEM_COST;
                helper.assertTrue(budget > 0 && budget < 64, "a full gold budget of " + budget + " items");

                helper.assertValueEqual(pulse(helper, 1000, false), (int) budget * 10, "FE an aborted pulse took");
                helper.assertValueEqual(count(destination(helper)), 0, "an aborted pulse moved items");

                helper.assertValueEqual(pulse(helper, 1000, true), (int) budget * 10, "FE a capped pulse took");
                helper.assertValueEqual(count(destination(helper)), (int) budget, "items a capped pulse moved");
                helper.assertValueEqual(pulse(helper, 30, true), 0, "FE taken with the budget spent");

                helper.runAfterDelay(10, () -> {
                    helper.assertValueEqual(pulse(helper, 30, true), 30, "FE of a 3-item pulse");
                    helper.assertValueEqual(count(destination(helper)), (int) budget + 3, "items after 3 more");
                    helper.assertValueEqual(count(source(helper)), 64 - (int) budget - 3, "items left");

                    // The wrench turns the plate to the next face with an inventory.
                    helper.setBlock(EXTRACTOR.below(), Blocks.CHEST);
                    BlockPos absolute = helper.absolutePos(EXTRACTOR);
                    helper.assertTrue(RotationHelper.rotateBlock(level, absolute, Direction.UP), "the wrench did not turn it");
                    helper.assertValueEqual(level.getBlockState(absolute).getValue(BlockBasicExtractor.FACING),
                            Direction.DOWN, "facing after the wrench");
                    helper.assertTrue(RotationHelper.rotateBlock(level, absolute, Direction.UP), "the wrench did not turn back");
                    helper.assertValueEqual(level.getBlockState(absolute).getValue(BlockBasicExtractor.FACING),
                            Direction.WEST, "facing after the second wrench");

                    Item planks = Items.OAK_PLANKS;
                    CraftingInput input = CraftingInput.of(1, 3, List.of(new ItemStack(planks), new ItemStack(Items.GLASS),
                            new ItemStack(planks)));
                    RecipeHolder<CraftingRecipe> recipe = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input,
                            level).orElse(null);
                    helper.assertTrue(recipe != null, "no Basic Extractor recipe");
                    ItemStack crafted = recipe.value().assemble(input);
                    helper.assertTrue(crafted.is(TransportRegistry.basicExtractorItem.get()) && crafted.getCount() == 1,
                            "planks, glass, planks in a column craft " + crafted);
                    helper.succeed();
                });
            });
        });
    }

    /** No engine and no pulse: nothing moves in 200 ticks. */
    static void noEngine(GameTestHelper helper) {
        line(helper, PipeMaterial.WOOD);
        helper.runAfterDelay(200, () -> {
            helper.assertValueEqual(count(destination(helper)), 0, "items moved without an engine");
            helper.assertValueEqual(count(source(helper)), 64, "items left");
            helper.assertFalse(extractor(helper).getSentRecord().isFlowing(), "flow without an engine");
            helper.succeed();
        });
    }
}
