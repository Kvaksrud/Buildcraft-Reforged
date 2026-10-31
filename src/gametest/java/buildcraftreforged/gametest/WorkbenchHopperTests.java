package buildcraftreforged.gametest;

import buildcraftreforged.api.transport.IPipeTile.PipeType;
import buildcraftreforged.core.CoreRegistry;
import buildcraftreforged.core.status.MachineStatus;
import buildcraftreforged.core.status.StatusPanel;
import buildcraftreforged.core.status.StatusReason;
import buildcraftreforged.energy.BlockEngine;
import buildcraftreforged.energy.EnergyRegistry;
import buildcraftreforged.energy.TileEngineStone;
import buildcraftreforged.factory.FactoryRegistry;
import buildcraftreforged.factory.TileAutoWorkbench;
import buildcraftreforged.factory.TileHopper;
import buildcraftreforged.factory.gui.ContainerAutoWorkbench;
import buildcraftreforged.factory.gui.ContainerHopper;
import buildcraftreforged.transport.PipeMaterial;
import buildcraftreforged.transport.TransportRegistry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.ResourceStack;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * #69 auto workbench and #70 hopper (the "Chute"). The workbench crafts from its own grid, which other blocks top up
 * through the template slots, one craft every 256 ticks, keeping the last item of each slot as the template unless the
 * result slot was clicked; the hopper drops one item every two ticks into whatever is below it. In the padded area.
 */
final class WorkbenchHopperTests {
    static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();
    static final int MAX_TICKS = 1000;
    private static final int Y = 2;

    static {
        TESTS.put("auto_workbench_crafts_from_chest", WorkbenchHopperTests::craftsFromChest);
        TESTS.put("auto_workbench_rules_menu_and_save", WorkbenchHopperTests::rulesMenuAndSave);
        TESTS.put("hopper_feeds_pipe", WorkbenchHopperTests::feedsPipe);
        TESTS.put("hopper_feeds_machine", WorkbenchHopperTests::feedsMachine);
        TESTS.put("hopper_blocked_menu_and_recipe", WorkbenchHopperTests::blockedMenuAndRecipe);
    }

    private WorkbenchHopperTests() {
    }

    private static TileAutoWorkbench workbench(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, FactoryRegistry.autoWorkbenchBlock.get());
        return helper.getBlockEntity(pos, TileAutoWorkbench.class);
    }

    private static TileHopper hopper(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, FactoryRegistry.hopperBlock.get());
        return helper.getBlockEntity(pos, TileHopper.class);
    }

    private static Container chest(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, Blocks.CHEST);
        return (Container) helper.getBlockEntity(pos, BlockEntity.class);
    }

    private static int count(Container inv, Item item) {
        int count = 0;
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            if (inv.getItem(slot).is(item))
                count += inv.getItem(slot).getCount();
        }
        return count;
    }

    /** Planks in grid slots 0 and 3 (1 and 4 of the workbench's inventory): the sticks recipe. */
    private static void sticksTemplate(TileAutoWorkbench bench, int planksEach) {
        bench.craftMatrix.setItem(0, new ItemStack(Items.OAK_PLANKS, planksEach));
        bench.craftMatrix.setItem(3, new ItemStack(Items.OAK_PLANKS, planksEach));
    }

    private static int insert(ResourceHandler<ItemResource> handler, ItemStack stack) {
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = ResourceHandlerUtil.insertStacking(handler, ItemResource.of(stack), stack.getCount(), transaction);
            transaction.commit();
            return inserted;
        }
    }

    /**
     * A chest feeds the workbench through a vanilla hopper on top (4.2.2's workbench pulls nothing itself; pipes and
     * hoppers fill its template slots), and a vanilla hopper below takes the result out into a second chest. The planks
     * spread over both template slots, the workbench crafts sticks, and the last plank of each slot stays.
     */
    static void craftsFromChest(GameTestHelper helper) {
        Container input = chest(helper, new BlockPos(0, Y + 4, 0));
        helper.setBlock(new BlockPos(0, Y + 3, 0), Blocks.HOPPER);
        TileAutoWorkbench bench = workbench(helper, new BlockPos(0, Y + 2, 0));
        helper.setBlock(new BlockPos(0, Y + 1, 0), Blocks.HOPPER);
        Container output = chest(helper, new BlockPos(0, Y, 0));
        sticksTemplate(bench, 1);
        input.setItem(0, new ItemStack(Items.OAK_PLANKS, 4));

        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(input, Items.OAK_PLANKS), 0, "planks left in the input chest");
            helper.assertTrue(count(output, Items.STICK) >= 8, "sticks in the output chest: " + count(output, Items.STICK));
            // 6 planks made two crafts of 4 sticks; the template keeps one plank in each slot.
            helper.assertValueEqual(bench.craftMatrix.getItem(0).getCount() + "," + bench.craftMatrix.getItem(3).getCount(), "1,1",
                    "template planks");
            helper.assertValueEqual(bench.getStatusState(), MachineStatus.IDLE, "State line once only the template is left");
            helper.assertEntityNotPresent(EntityTypes.ITEM);
        });
    }

    /**
     * Template-only insertion and result-only extraction through the item capability; refusals (non-stackable items,
     * items with a crafting remainder); the menu's slots, its result preview and the "click to craft" that uses the
     * last items; save and load of the grid and the result.
     */
    static void rulesMenuAndSave(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, Y, 0);
        TileAutoWorkbench bench = workbench(helper, pos);
        ServerLevel level = helper.getLevel();
        ResourceHandler<ItemResource> top = level.getCapability(Capabilities.Item.BLOCK, helper.absolutePos(pos), Direction.UP);
        helper.assertTrue(top != null, "no item capability on the workbench");

        // Nothing goes into an empty grid: the grid is a template.
        helper.assertValueEqual(insert(top, new ItemStack(Items.OAK_PLANKS, 4)), 0, "planks into an empty grid");
        sticksTemplate(bench, 1);
        helper.assertValueEqual(insert(top, new ItemStack(Items.OAK_PLANKS, 4)), 4, "planks into the template");
        helper.assertValueEqual(insert(top, new ItemStack(Items.COBBLESTONE, 1)), 0, "cobblestone into a planks template");
        helper.assertFalse(bench.canPlaceItem(1, new ItemStack(Items.WATER_BUCKET)), "a water bucket goes into the grid");
        helper.assertFalse(bench.canPlaceItem(1, new ItemStack(Items.IRON_PICKAXE)), "a pickaxe goes into the grid");
        try (Transaction transaction = Transaction.openRoot()) {
            helper.assertTrue(ResourceHandlerUtil.extractFirst(top, resource -> true, 64, transaction) == null,
                    "something came out of the grid");
        }
        sticksTemplate(bench, 1);

        // A grid item with a crafting remainder stops the workbench (4.2.2 hasContainerItem).
        bench.craftMatrix.setItem(8, new ItemStack(Items.MILK_BUCKET));
        helper.assertTrue(bench.findRecipe() == null, "a recipe with a milk bucket in the grid");
        bench.craftMatrix.setItem(8, ItemStack.EMPTY);
        helper.assertTrue(bench.findRecipe() != null, "no sticks recipe");
        helper.assertTrue(bench.isLast(), "one plank each is not the last craft");

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.snapTo(Vec3.atBottomCenterOf(helper.absolutePos(pos.above())));
        // A mock player has no mod channel, so open the menu on the server side only (see GuiTests.sampleMenu).
        if (!(bench.createMenu(1, player.getInventory(), player) instanceof ContainerAutoWorkbench menu)) {
            throw helper.assertionException("the workbench made no ContainerAutoWorkbench");
        }
        helper.assertValueEqual(menu.getType(), FactoryRegistry.autoWorkbenchMenu.get(), "menu type");
        helper.assertValueEqual(menu.slots.size(), 2 + 9 + 27 + 9, "slots");
        helper.assertValueEqual(menu.slots.get(0).x + "," + menu.slots.get(0).y, "93,27", "preview slot position");
        helper.assertValueEqual(menu.slots.get(1).x + "," + menu.slots.get(1).y, "124,35", "result slot position");
        helper.assertValueEqual(menu.slots.get(2).x + "," + menu.slots.get(2).y, "30,17", "first grid slot position");
        helper.assertValueEqual(menu.slots.get(11).x + "," + menu.slots.get(11).y, "8,84", "first inventory slot");
        helper.assertValueEqual(menu.slots.get(38).x + "," + menu.slots.get(38).y, "8,142", "first hotbar slot");
        helper.assertTrue(menu.slots.get(1).container == bench, "the result slot is not the workbench's");
        helper.assertTrue(menu.craftResult.getItem(0).is(Items.STICK) && menu.craftResult.getItem(0).getCount() == 4,
                "preview shows " + menu.craftResult.getItem(0));
        helper.assertTrue(menu.stillValid(player), "the menu is not valid for a player next to the workbench");
        helper.assertTrue(menu.statusWidget != null, "no status widget");

        // Clicking the preview crafts with the last items (4.2.2 useLast).
        menu.clicked(0, 0, ContainerInput.PICKUP, player);
        helper.assertTrue(bench.useLast, "clicking the preview did not set useLast");
        menu.removed(player);

        helper.succeedWhen(() -> {
            helper.assertTrue(bench.getItem(TileAutoWorkbench.SLOT_RESULT).is(Items.STICK), "no sticks made");
            helper.assertValueEqual(bench.getItem(TileAutoWorkbench.SLOT_RESULT).getCount(), 4, "sticks made");
            helper.assertTrue(bench.craftMatrix.isEmpty(), "planks left in the grid");
            helper.assertFalse(bench.useLast, "useLast not cleared after the craft");
            helper.assertEntityNotPresent(EntityTypes.ITEM);

            // The result comes out through the capability; save and load keep grid and result.
            sticksTemplate(bench, 3);
            CompoundTag tag = bench.saveWithFullMetadata(level.registryAccess());
            TileAutoWorkbench copy = new TileAutoWorkbench(bench.getBlockPos(), bench.getBlockState());
            copy.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
            helper.assertValueEqual(copy.getItem(TileAutoWorkbench.SLOT_RESULT).getCount(), 4, "saved sticks");
            helper.assertValueEqual(copy.craftMatrix.getItem(3).getCount(), 3, "saved grid");
            try (Transaction transaction = Transaction.openRoot()) {
                ResourceStack<ItemResource> got = ResourceHandlerUtil.extractFirst(top, resource -> true, 64, transaction);
                helper.assertTrue(got != null && got.resource().is(Items.STICK) && got.amount() == 4,
                        "extracted " + got);
            }
            assertCrafts(helper, List.of(Items.AIR, CoreRegistry.woodenGearItem.get(), Items.AIR, CoreRegistry.woodenGearItem.get(),
                    Items.CRAFTING_TABLE, CoreRegistry.woodenGearItem.get(), Items.AIR, CoreRegistry.woodenGearItem.get(), Items.AIR),
                    FactoryRegistry.autoWorkbenchBlockItem.get());
            helper.assertValueEqual(BuiltInRegistries.BLOCK.getKey(FactoryRegistry.autoWorkbenchBlock.get()).getPath(),
                    "auto_workbench_block", "workbench id");
        });
    }

    /** The hopper drops its items into an item pipe below, which carries them to a chest. */
    static void feedsPipe(GameTestHelper helper) {
        TileHopper hopper = hopper(helper, new BlockPos(0, Y + 1, 1));
        helper.setBlock(new BlockPos(0, Y, 1), TransportRegistry.getPipe(PipeType.ITEM, PipeMaterial.GOLD));
        helper.setBlock(new BlockPos(0, Y, 2), TransportRegistry.getPipe(PipeType.ITEM, PipeMaterial.GOLD));
        Container chest = chest(helper, new BlockPos(0, Y, 3));
        hopper.setItem(0, new ItemStack(Items.DIAMOND, 5));
        hopper.setItem(3, new ItemStack(Items.COBBLESTONE, 3));

        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(chest, Items.DIAMOND), 5, "diamonds in the chest");
            helper.assertValueEqual(count(chest, Items.COBBLESTONE), 3, "cobblestone in the chest");
            helper.assertTrue(hopper.isEmpty(), "items left in the hopper");
            helper.assertValueEqual(hopper.getStatusState(), MachineStatus.IDLE, "State line once empty");
            helper.assertEntityNotPresent(EntityTypes.ITEM);
        });
    }

    /**
     * The hopper drops coal into a Stirling engine and planks into an auto workbench's template, one item every two
     * ticks; a workbench with no template takes nothing, so the hopper above it is Blocked.
     */
    static void feedsMachine(GameTestHelper helper) {
        BlockPos enginePos = new BlockPos(0, Y, 0);
        helper.setBlock(enginePos, EnergyRegistry.engineStone.block().get().defaultBlockState()
                .setValue(BlockEngine.FACING, Direction.NORTH));
        TileEngineStone engine = helper.getBlockEntity(enginePos, TileEngineStone.class);
        TileHopper engineHopper = hopper(helper, enginePos.above());
        engineHopper.setItem(1, new ItemStack(Items.COAL, 6));

        TileAutoWorkbench bench = workbench(helper, new BlockPos(2, Y, 0));
        TileHopper benchHopper = hopper(helper, new BlockPos(2, Y + 1, 0));
        sticksTemplate(bench, 1);
        benchHopper.setItem(0, new ItemStack(Items.OAK_PLANKS, 6));

        helper.runAfterDelay(5, () -> {
            // Two ticks per item: after 5 ticks two or three have moved, not all six.
            int moved = 6 - engineHopper.getItem(1).getCount();
            helper.assertTrue(moved >= 1 && moved <= 3, "coal moved in 5 ticks: " + moved);
            helper.succeedWhen(() -> {
                helper.assertValueEqual(engine.getItem(0).getCount(), 6, "coal in the engine");
                helper.assertTrue(engineHopper.isEmpty(), "coal left in the hopper");
                helper.assertTrue(benchHopper.isEmpty(), "planks left in the hopper");
                helper.assertValueEqual(count(bench, Items.OAK_PLANKS), 8, "planks in the workbench");
                helper.assertEntityNotPresent(EntityTypes.ITEM);
            });
        });
    }

    /**
     * Status panel: a hopper over a full chest is Blocked with "Output blocked" and the red light, one over nothing
     * "No destination"; the menu's slots; the recipe and registry name.
     */
    static void blockedMenuAndRecipe(GameTestHelper helper) {
        BlockPos fullPos = new BlockPos(0, Y + 1, 0);
        Container full = chest(helper, fullPos.below());
        for (int slot = 0; slot < full.getContainerSize(); slot++) {
            full.setItem(slot, new ItemStack(Items.STONE, 64));
        }
        TileHopper overFull = hopper(helper, fullPos);
        overFull.setItem(0, new ItemStack(Items.DIAMOND, 2));
        BlockPos airPos = new BlockPos(2, Y + 1, 0);
        TileHopper overAir = hopper(helper, airPos);
        overAir.setItem(2, new ItemStack(Items.DIAMOND, 2));

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.snapTo(Vec3.atBottomCenterOf(helper.absolutePos(fullPos.above())));
        if (!(overFull.createMenu(1, player.getInventory(), player) instanceof ContainerHopper menu)) {
            throw helper.assertionException("the hopper made no ContainerHopper");
        }
        helper.assertValueEqual(menu.getType(), FactoryRegistry.hopperMenu.get(), "menu type");
        helper.assertValueEqual(menu.slots.size(), 4 + 27 + 9, "slots");
        helper.assertValueEqual(menu.slots.get(3).x + "," + menu.slots.get(3).y, "80,36", "fourth hopper slot");
        helper.assertValueEqual(menu.slots.get(4).x + "," + menu.slots.get(4).y, "8,71", "first inventory slot");
        helper.assertValueEqual(menu.slots.get(31).x + "," + menu.slots.get(31).y, "8,129", "first hotbar slot");
        helper.assertTrue(menu.stillValid(player), "the menu is not valid for a player next to the hopper");
        player.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 5));
        menu.quickMoveStack(player, 31);
        helper.assertValueEqual(count(overFull, Items.IRON_INGOT), 5, "iron shift-clicked into the hopper");
        menu.removed(player);

        helper.runAfterDelay(6, () -> {
            helper.assertValueEqual(overFull.getStatusState(), MachineStatus.BLOCKED, "State over a full chest");
            helper.assertValueEqual(overFull.getStatusReason(), StatusReason.OUTPUT_FULL, "Reason over a full chest");
            helper.assertBlockProperty(fullPos, StatusPanel.BLOCKED, true);
            helper.assertValueEqual(overAir.getStatusState(), MachineStatus.BLOCKED, "State over nothing");
            helper.assertValueEqual(overAir.getStatusReason(), StatusReason.NO_DESTINATION, "Reason over nothing");

            // Free slots for the diamonds and the iron: the hopper empties and the light goes out.
            full.setItem(0, ItemStack.EMPTY);
            full.setItem(1, ItemStack.EMPTY);
            helper.succeedWhen(() -> {
                helper.assertTrue(overFull.isEmpty(), "items left in the hopper");
                helper.assertBlockProperty(fullPos, StatusPanel.BLOCKED, false);
                helper.assertValueEqual(overFull.getStatusReason(), StatusReason.NONE, "Reason once empty");
                assertCrafts(helper, List.of(Items.IRON_INGOT, Items.CHEST, Items.IRON_INGOT, Items.IRON_INGOT,
                        CoreRegistry.stoneGearItem.get(), Items.IRON_INGOT, Items.AIR, Items.IRON_INGOT, Items.AIR),
                        FactoryRegistry.hopperBlockItem.get());
                helper.assertValueEqual(BuiltInRegistries.BLOCK.getKey(FactoryRegistry.hopperBlock.get()).getPath(),
                        "block_hopper", "hopper id");
                helper.assertEntityNotPresent(EntityTypes.ITEM);
            });
        });
    }

    private static void assertCrafts(GameTestHelper helper, List<Item> grid, Item result) {
        ServerLevel level = helper.getLevel();
        CraftingInput input = CraftingInput.of(3, 3, grid.stream().map(ItemStack::new).toList());
        RecipeHolder<CraftingRecipe> recipe = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level).orElse(null);
        helper.assertTrue(recipe != null, "no recipe for " + grid);
        ItemStack crafted = recipe.value().assemble(input);
        helper.assertTrue(crafted.is(result) && crafted.getCount() == 1, grid + " crafts " + crafted + ", expected " + result);
    }
}
