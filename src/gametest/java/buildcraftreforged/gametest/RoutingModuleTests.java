package buildcraftreforged.gametest;

import buildcraftreforged.api.gates.IAction;
import buildcraftreforged.api.transport.IPipeTile.PipeType;
import buildcraftreforged.core.CoreRegistry;
import buildcraftreforged.core.network.InvalidPacketException;
import buildcraftreforged.core.network.PacketData;
import buildcraftreforged.core.utils.EnumColor;
import buildcraftreforged.factory.FactoryRegistry;
import buildcraftreforged.factory.TileTank;
import buildcraftreforged.transport.IPipeGateTarget;
import buildcraftreforged.transport.PipeConnections;
import buildcraftreforged.transport.PipeMaterial;
import buildcraftreforged.transport.PipeModuleType;
import buildcraftreforged.transport.TilePipe;
import buildcraftreforged.transport.TransportRegistry;
import buildcraftreforged.transport.module.ContainerFilteredColourTag;
import buildcraftreforged.transport.module.ContainerSorter;
import buildcraftreforged.transport.module.ModuleColourSorter;
import buildcraftreforged.transport.module.ModuleColourTag;
import buildcraftreforged.transport.module.ModuleFilteredColourTag;
import buildcraftreforged.transport.module.ModuleSorter;
import buildcraftreforged.transport.module.PipeModule;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * #56 and #57: the Sorter, Pickup, Void, Colour Tag, Colour Sorter, Filtered Colour Tag and Isolator modules in a
 * real level (docs/TRANSPORT.md 8.5-8.11, section 14): their items, settings, gate actions, menus and their effect on
 * the routing walk. They build in the padded area from y = 2, like {@link ModuleTests}.
 */
final class RoutingModuleTests {
    private static final int Y = 2;

    /** Test name → test, registered by {@link BuildcraftGameTests} in the padded area. */
    static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();
    static final int MAX_TICKS = 400;

    static {
        TESTS.put("module_items_fit_and_keep_settings", RoutingModuleTests::itemsFitAndKeepSettings);
        TESTS.put("module_sorter_separates_items", RoutingModuleTests::sorterSeparatesItems);
        TESTS.put("module_sorter_menu", RoutingModuleTests::sorterMenu);
        TESTS.put("module_pickup_range", RoutingModuleTests::pickupRange);
        TESTS.put("module_void_items_and_fluids", RoutingModuleTests::voidItemsAndFluids);
        TESTS.put("module_colour_tag_and_sorter", RoutingModuleTests::colourTagAndSorter);
        TESTS.put("module_filtered_colour_tag_presets", RoutingModuleTests::filteredColourTagPresets);
        TESTS.put("module_filtered_colour_tag_menu", RoutingModuleTests::filteredColourTagMenu);
        TESTS.put("module_isolator", RoutingModuleTests::isolator);
    }

    private RoutingModuleTests() {
    }

    private static BlockPos at(int x, int z) {
        return new BlockPos(x, Y, z);
    }

    private static TilePipe pipe(GameTestHelper helper, int x, int z, PipeType kind, PipeMaterial material) {
        helper.setBlock(at(x, z), TransportRegistry.getPipe(kind, material));
        return helper.getBlockEntity(at(x, z), TilePipe.class);
    }

    private static TilePipe itemPipe(GameTestHelper helper, int x, int z, PipeMaterial material,
            @Nullable PipeModuleType module) {
        TilePipe pipe = pipe(helper, x, z, PipeType.ITEM, material);
        if (module != null)
            pipe.setModuleType(module);
        return pipe;
    }

    private static Container chest(GameTestHelper helper, int x, int z) {
        helper.setBlock(at(x, z), Blocks.CHEST);
        return (Container) helper.getBlockEntity(at(x, z), BlockEntity.class);
    }

    private static int count(Container container, @Nullable Item item) {
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (item == null || stack.is(item))
                count += stack.getCount();
        }
        return count;
    }

    private static BlockHitResult hit(GameTestHelper helper, int x, int z) {
        BlockPos pos = helper.absolutePos(at(x, z));
        return new BlockHitResult(Vec3.atCenterOf(pos).relative(Direction.UP, 0.5), Direction.UP, pos, false);
    }

    /** Uses {@code stack} on the pipe at (x, z) as the block sees it: {@code BlockPipe.useItemOn}. */
    private static InteractionResult useOnPipe(GameTestHelper helper, Player player, int x, int z, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos pos = helper.absolutePos(at(x, z));
        return helper.getLevel().getBlockState(pos).useItemOn(stack, helper.getLevel(), player, InteractionHand.MAIN_HAND,
                hit(helper, x, z));
    }

    /** Fits a module by using its item on the pipe ({@code ItemPipeModule.useOn}). */
    private static InteractionResult fit(GameTestHelper helper, Player player, int x, int z, ItemStack module) {
        player.setItemInHand(InteractionHand.MAIN_HAND, module);
        return module.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit(helper, x, z)));
    }

    private static <T extends PipeModule> T module(GameTestHelper helper, TilePipe pipe, Class<T> type) {
        PipeModule module = pipe.getModule();
        if (!type.isInstance(module))
            throw helper.assertionException("expected a " + type.getSimpleName() + ", found " + module);
        return type.cast(module);
    }

    private static IAction pipeColour(EnumColor colour) {
        return TransportRegistry.actionPipeColor.get(colour.ordinal()).get();
    }

    private static List<IAction> actions(IPipeGateTarget target) {
        LinkedList<IAction> list = new LinkedList<>();
        target.addActions(list);
        return list;
    }

    /** An item entity of {@code count} {@code item} floating still at the centre of block (x, y, z), relative. */
    private static ItemEntity floatingItem(GameTestHelper helper, Item item, int count, int x, int y, int z) {
        return floatingItem(helper, item, count, x, y, z, 0.5);
    }

    /** The same, {@code height} above the bottom of the block. */
    private static ItemEntity floatingItem(GameTestHelper helper, Item item, int count, int x, int y, int z,
            double height) {
        Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(x, y, z))).add(0, height, 0);
        ItemEntity entity = new ItemEntity(helper.getLevel(), pos.x, pos.y, pos.z, new ItemStack(item, count));
        entity.setNoGravity(true);
        entity.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }

    private static void discardItems(GameTestHelper helper) {
        helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(2)).forEach(ItemEntity::discard);
    }

    /**
     * Every new module item fits an item pipe; the Void and the Isolator also fit a fluid pipe, the others do not;
     * none fits a power pipe (Q8). Settings travel with the item: a Colour Tag's colour, a Sorter's filters, a
     * Colour Sorter's colour and a Filtered Colour Tag's filters and paint.
     */
    static void itemsFitAndKeepSettings(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        RegistryAccess registries = helper.getLevel().registryAccess();
        Map<PipeModuleType, Item> items = new LinkedHashMap<>();
        items.put(PipeModuleType.SORTER, TransportRegistry.sorterModule.get());
        items.put(PipeModuleType.PICKUP, TransportRegistry.pickupModule.get());
        items.put(PipeModuleType.VOID, TransportRegistry.voidModule.get());
        items.put(PipeModuleType.COLOUR_TAG, TransportRegistry.colourTagModule.get());
        items.put(PipeModuleType.COLOUR_SORTER, TransportRegistry.colourSorterModule.get());
        items.put(PipeModuleType.FILTERED_COLOUR_TAG, TransportRegistry.filteredColourTagModule.get());
        items.put(PipeModuleType.ISOLATOR, TransportRegistry.isolatorModule.get());
        int x = 0;
        for (Map.Entry<PipeModuleType, Item> entry : items.entrySet()) {
            PipeModuleType type = entry.getKey();
            helper.assertTrue(TransportRegistry.getModuleItem(type) == entry.getValue(), "module item of " + type);
            TilePipe item = pipe(helper, x, 0, PipeType.ITEM, PipeMaterial.STONE);
            pipe(helper, x, 2, PipeType.FLUID, PipeMaterial.STONE);
            pipe(helper, x, 4, PipeType.POWER, PipeMaterial.STONE);
            helper.assertTrue(fit(helper, player, x, 0, new ItemStack(entry.getValue())).consumesAction(),
                    type + " did not fit an item pipe");
            helper.assertValueEqual(item.getModuleType(), type, "module fitted");
            boolean fluid = type == PipeModuleType.VOID || type == PipeModuleType.ISOLATOR;
            helper.assertValueEqual(fit(helper, player, x, 2, new ItemStack(entry.getValue())).consumesAction(), fluid,
                    type + " fits a fluid pipe");
            helper.assertFalse(fit(helper, player, x, 4, new ItemStack(entry.getValue())).consumesAction(),
                    type + " fitted a power pipe");
            x += 2;
        }

        PipeModule tag = PipeModule.create(PipeModuleType.COLOUR_TAG);
        ((ModuleColourTag) tag).setColour(itemPipe(helper, 0, 6, PipeMaterial.STONE, null), EnumColor.LIME);
        ModuleColourTag tagCopy = (ModuleColourTag) PipeModule.fromItem(PipeModuleType.COLOUR_TAG, tag.toItem(registries),
                registries);
        helper.assertValueEqual(tagCopy.getColour(), EnumColor.LIME, "Colour Tag colour on the item");

        ModuleSorter sorter = (ModuleSorter) PipeModule.create(PipeModuleType.SORTER);
        sorter.filters.setItem(30, new ItemStack(Items.DIRT));
        ModuleSorter sorterCopy = (ModuleSorter) PipeModule.fromItem(PipeModuleType.SORTER, sorter.toItem(registries),
                registries);
        helper.assertTrue(sorterCopy.filters.getItem(30).is(Items.DIRT), "Sorter filter on the item");

        ModuleColourSorter colourSorter = (ModuleColourSorter) PipeModule.create(PipeModuleType.COLOUR_SORTER);
        colourSorter.setColour(itemPipe(helper, 2, 6, PipeMaterial.STONE, null), EnumColor.CYAN);
        ModuleColourSorter colourSorterCopy = (ModuleColourSorter) PipeModule.fromItem(PipeModuleType.COLOUR_SORTER,
                colourSorter.toItem(registries), registries);
        helper.assertValueEqual(colourSorterCopy.getColour(), EnumColor.CYAN, "Colour Sorter colour on the item");

        ModuleFilteredColourTag filtered = (ModuleFilteredColourTag) PipeModule.create(PipeModuleType.FILTERED_COLOUR_TAG);
        filtered.filters.setItem(2, new ItemStack(Items.SAND));
        filtered.slotColors[2] = (byte) (EnumColor.ORANGE.ordinal() + 1);
        ModuleFilteredColourTag filteredCopy = (ModuleFilteredColourTag) PipeModule.fromItem(
                PipeModuleType.FILTERED_COLOUR_TAG, filtered.toItem(registries), registries);
        helper.assertTrue(filteredCopy.filters.getItem(2).is(Items.SAND), "Filtered Colour Tag filter on the item");
        helper.assertValueEqual(filteredCopy.walkColour(2), EnumColor.ORANGE.ordinal(), "paint on the item");
        helper.assertValueEqual(filteredCopy.walkColour(0), -1, "no paint");
        helper.succeed();
    }

    /**
     * A source chest of cobblestone, dirt and sand, an Extractor, and a Sorter junction with chests north, south and
     * east. The north row holds cobblestone (twice), the south row dirt, the east row nothing: cobblestone goes only
     * north, dirt only south, and the sand, matching no row, only east (the side without filters).
     */
    static void sorterSeparatesItems(GameTestHelper helper) {
        Container source = chest(helper, 0, 1);
        itemPipe(helper, 1, 1, PipeMaterial.IRON, PipeModuleType.EXTRACTOR);
        TilePipe sorter = itemPipe(helper, 2, 1, PipeMaterial.IRON, PipeModuleType.SORTER);
        Container north = chest(helper, 2, 0);
        Container south = chest(helper, 2, 2);
        Container east = chest(helper, 3, 1);
        source.setItem(0, new ItemStack(Items.COBBLESTONE, 6));
        source.setItem(1, new ItemStack(Items.DIRT, 6));
        source.setItem(2, new ItemStack(Items.SAND, 6));
        ModuleSorter module = module(helper, sorter, ModuleSorter.class);
        int northRow = Direction.NORTH.get3DDataValue() * ModuleSorter.ROW;
        int southRow = Direction.SOUTH.get3DDataValue() * ModuleSorter.ROW;
        module.filters.setItem(northRow, new ItemStack(Items.COBBLESTONE));
        module.filters.setItem(northRow + 4, new ItemStack(Items.COBBLESTONE));
        module.filters.setItem(southRow + 8, new ItemStack(Items.DIRT));

        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(source, null), 0, "items left in the source");
            helper.assertValueEqual(count(north, Items.COBBLESTONE), 6, "cobblestone north");
            helper.assertValueEqual(count(south, Items.DIRT), 6, "dirt south");
            helper.assertValueEqual(count(east, Items.SAND), 6, "sand east");
            helper.assertValueEqual(count(north, null) + count(south, null) + count(east, null), 18, "items in all");
            helper.assertEntityNotPresent(EntityTypes.ITEM);
        });
    }

    /** The Sorter's menu: 54 phantom filter slots in six rows before the player inventory; the pipe saves them. */
    static void sorterMenu(GameTestHelper helper) {
        TilePipe pipe = itemPipe(helper, 1, 0, PipeMaterial.WOOD, PipeModuleType.SORTER);
        ModuleSorter module = module(helper, pipe, ModuleSorter.class);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.snapTo(Vec3.atBottomCenterOf(helper.absolutePos(at(1, 0)).above()));

        AbstractContainerMenu opened = module.getMenuProvider(pipe).createMenu(1, player.getInventory(), player);
        if (!(opened instanceof ContainerSorter menu))
            throw helper.assertionException("the module made no Sorter menu");
        helper.assertValueEqual(menu.slots.size(), ModuleSorter.SLOTS + 36, "menu slots");
        menu.slots.get(ModuleSorter.ROW * 5 + 2).set(new ItemStack(Items.IRON_INGOT, 7));
        helper.assertTrue(module.filters.getItem(ModuleSorter.ROW * 5 + 2).is(Items.IRON_INGOT),
                "filter slot is not the module's");
        helper.assertValueEqual(module.filters.getItem(ModuleSorter.ROW * 5 + 2).getCount(), 1, "filter stack size");
        helper.assertTrue(menu.stillValid(player), "menu not valid next to the pipe");
        pipe.setModuleType(null);
        helper.assertFalse(menu.stillValid(player), "menu still valid after the module was removed");
        helper.succeed();
    }

    /**
     * Pickup pipes, each with a chest on its only connection (west) and its open side east. A wood one (range 1)
     * takes an item in its own block and one a block out, not one two blocks out; an ender one (range 4) takes one
     * four blocks out, not five. A Pickup with two connections takes nothing, nor does one whose chest is full.
     */
    static void pickupRange(GameTestHelper helper) {
        Container woodChest = chest(helper, 0, 0);
        itemPipe(helper, 1, 0, PipeMaterial.WOOD, PipeModuleType.PICKUP);
        Container enderChest = chest(helper, 0, 4);
        itemPipe(helper, 1, 4, PipeMaterial.ENDER, PipeModuleType.PICKUP);
        chest(helper, 0, 8);
        itemPipe(helper, 1, 8, PipeMaterial.ENDER, PipeModuleType.PICKUP);
        chest(helper, 2, 8);
        Container full = chest(helper, 0, 12);
        itemPipe(helper, 1, 12, PipeMaterial.ENDER, PipeModuleType.PICKUP);
        for (int slot = 0; slot < full.getContainerSize(); slot++) {
            full.setItem(slot, new ItemStack(Items.STONE, 64));
        }

        helper.runAfterDelay(5, () -> {
            // Resting on the pipe's core (12/16 high), still in its block: 4.2.2's touch.
            floatingItem(helper, Items.GRAVEL, 1, 1, Y, 0, 0.76);
            floatingItem(helper, Items.DIRT, 1, 2, Y, 0);
            ItemEntity tooFar = floatingItem(helper, Items.SAND, 1, 3, Y, 0);
            floatingItem(helper, Items.OAK_LOG, 3, 5, Y, 4);
            ItemEntity beyond = floatingItem(helper, Items.BIRCH_LOG, 1, 6, Y, 4);
            ItemEntity twoConnections = floatingItem(helper, Items.CLAY_BALL, 1, 1, Y, 8);
            ItemEntity nowhere = floatingItem(helper, Items.FLINT, 1, 2, Y, 12);

            helper.runAfterDelay(60, () -> {
                helper.assertValueEqual(count(woodChest, Items.GRAVEL), 1, "the item in the wood Pickup's block");
                helper.assertValueEqual(count(woodChest, Items.DIRT), 1, "the item a block out");
                helper.assertTrue(tooFar.isAlive() && count(woodChest, Items.SAND) == 0, "wood reached two blocks");
                helper.assertValueEqual(count(enderChest, Items.OAK_LOG), 3, "the stack four blocks out");
                helper.assertTrue(beyond.isAlive() && count(enderChest, Items.BIRCH_LOG) == 0,
                        "ender reached five blocks");
                helper.assertTrue(twoConnections.isAlive() && twoConnections.getItem().getCount() == 1,
                        "a Pickup with two connections took an item");
                helper.assertTrue(nowhere.isAlive() && nowhere.getItem().getCount() == 1,
                        "an item with nowhere to go was taken");
                discardItems(helper);
                helper.succeed();
            });
        });
    }

    /**
     * Items: a stone Extractor feeds a junction with a chest and a Void pipe; each takes a share and the Void's share is
     * gone. Fluids: an Extractor drains a tank into a Void fluid pipe, which deletes the water.
     */
    static void voidItemsAndFluids(GameTestHelper helper) {
        Container source = chest(helper, 0, 1);
        itemPipe(helper, 1, 1, PipeMaterial.STONE, PipeModuleType.EXTRACTOR);
        itemPipe(helper, 2, 1, PipeMaterial.STONE, null);
        Container kept = chest(helper, 2, 0);
        itemPipe(helper, 3, 1, PipeMaterial.STONE, PipeModuleType.VOID);
        source.setItem(0, new ItemStack(Items.COBBLESTONE, 16));

        helper.setBlock(at(0, 4), FactoryRegistry.tankBlock.get());
        TileTank tank = helper.getBlockEntity(at(0, 4), TileTank.class);
        try (Transaction transaction = Transaction.openRoot()) {
            helper.assertValueEqual(tank.insert(FluidResource.of(Fluids.WATER), 4000,
                    transaction), 4000, "water filled");
            transaction.commit();
        }
        pipe(helper, 1, 4, PipeType.FLUID, PipeMaterial.WOOD).setModuleType(PipeModuleType.EXTRACTOR);
        pipe(helper, 2, 4, PipeType.FLUID, PipeMaterial.WOOD).setModuleType(PipeModuleType.VOID);

        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(source, null), 0, "cobblestone left in the source");
            int chest = count(kept, Items.COBBLESTONE);
            helper.assertTrue(chest > 0 && chest < 16, "the junction split 16 items " + chest + " to the chest");
            long water = 0;
            for (int index = 0; index < tank.size(); index++) {
                water += tank.getAmountAsLong(index);
            }
            helper.assertTrue(water <= 2000, "the Void fluid pipe deleted too little water: " + water + " mB left");
            helper.assertEntityNotPresent(EntityTypes.ITEM);
        });
    }

    /**
     * Source chest, an Extractor, a Colour Tag and a Colour Sorter junction with chests north, south and east. The
     * wrench turns the tag from black to red; the Pipe Color action makes the sorter red and its output is set east:
     * every item goes east. A blue dye on the tag makes it blue: no item goes east any more. A redstone block does not
     * turn the sorter, the wrench does. The tag offers the 16 Pipe Color actions, the sorter those and a Pipe Direction
     * per connected side.
     */
    static void colourTagAndSorter(GameTestHelper helper) {
        Container source = chest(helper, 0, 1);
        itemPipe(helper, 1, 1, PipeMaterial.IRON, PipeModuleType.EXTRACTOR);
        TilePipe tagPipe = itemPipe(helper, 2, 1, PipeMaterial.IRON, PipeModuleType.COLOUR_TAG);
        TilePipe sorterPipe = itemPipe(helper, 3, 1, PipeMaterial.IRON, PipeModuleType.COLOUR_SORTER);
        Container north = chest(helper, 3, 0);
        Container south = chest(helper, 3, 2);
        Container east = chest(helper, 4, 1);
        source.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ModuleColourTag tag = module(helper, tagPipe, ModuleColourTag.class);
        ModuleColourSorter sorter = module(helper, sorterPipe, ModuleColourSorter.class);

        helper.runAfterDelay(3, () -> {
            helper.assertValueEqual(tag.getColour(), EnumColor.BLACK, "a new Colour Tag");
            helper.assertTrue(useOnPipe(helper, player, 2, 1, new ItemStack(CoreRegistry.wrenchItem.get()))
                    .consumesAction(), "the wrench did nothing");
            helper.assertValueEqual(tag.getColour(), EnumColor.RED, "Colour Tag after the wrench");
            helper.assertValueEqual(actions(tagPipe.gateHolder.target).size(), 16, "Colour Tag actions");

            IPipeGateTarget target = sorterPipe.gateHolder.target;
            helper.assertValueEqual(actions(target).size(), 16 + 4, "Colour Sorter actions");
            target.actionsActivated(Map.of(pipeColour(EnumColor.RED), true));
            helper.assertValueEqual(sorter.getColour(), EnumColor.RED, "Colour Sorter after Pipe Color");
            target.actionsActivated(
                    Map.of(TransportRegistry.actionPipeDirection.get(Direction.EAST.ordinal()).get(), true));
            helper.assertValueEqual(sorter.getFacing(), Direction.EAST, "Colour Sorter output after Pipe Direction");

            helper.runAfterDelay(40, () -> {
                helper.assertTrue(count(east, null) > 0, "no red item went east");
                helper.assertValueEqual(count(north, null) + count(south, null), 0, "red items went elsewhere");

                helper.assertTrue(useOnPipe(helper, player, 2, 1, new ItemStack(Items.DYE.blue())).consumesAction(),
                        "the dye did nothing");
                helper.assertValueEqual(tag.getColour(), EnumColor.BLUE, "Colour Tag after a blue dye");
                helper.setBlock(at(3, 1).above(), Blocks.REDSTONE_BLOCK);
                int eastBefore = count(east, null);

                helper.runAfterDelay(40, () -> {
                    helper.assertValueEqual(sorter.getFacing(), Direction.EAST, "redstone turned the Colour Sorter");
                    helper.assertValueEqual(count(east, null), eastBefore, "blue items went east");
                    helper.assertTrue(count(north, null) + count(south, null) > 0, "blue items went nowhere");
                    helper.assertTrue(useOnPipe(helper, player, 3, 1, new ItemStack(CoreRegistry.wrenchItem.get()))
                            .consumesAction(), "the wrench did nothing");
                    helper.assertValueEqual(sorter.getFacing(), Direction.NORTH, "Colour Sorter output after the wrench");
                    helper.assertEntityNotPresent(EntityTypes.ITEM);
                    helper.succeed();
                });
            });
        });
    }

    /**
     * A source of cobblestone and dirt, a Filtered Colour Tag (filter 0 cobblestone painted red, filter 1 dirt painted
     * blue) and a red Colour Sorter whose output faces east, with a chest east and one north. Without a gate preset
     * nothing moves. With the red and blue presets active the two filters alternate: red cobblestone goes east only,
     * blue dirt north only.
     */
    static void filteredColourTagPresets(GameTestHelper helper) {
        Container source = chest(helper, 0, 1);
        // Wood moves one item per transfer, so the two filters alternate item by item.
        TilePipe tagPipe = itemPipe(helper, 1, 1, PipeMaterial.WOOD, PipeModuleType.FILTERED_COLOUR_TAG);
        TilePipe sorterPipe = itemPipe(helper, 2, 1, PipeMaterial.WOOD, PipeModuleType.COLOUR_SORTER);
        Container north = chest(helper, 2, 0);
        Container east = chest(helper, 3, 1);
        source.setItem(0, new ItemStack(Items.COBBLESTONE, 32));
        source.setItem(1, new ItemStack(Items.DIRT, 32));
        ModuleFilteredColourTag tag = module(helper, tagPipe, ModuleFilteredColourTag.class);
        tag.filters.setItem(0, new ItemStack(Items.COBBLESTONE));
        tag.slotColors[0] = (byte) (EnumColor.RED.ordinal() + 1);
        tag.filters.setItem(1, new ItemStack(Items.DIRT));
        tag.slotColors[1] = (byte) (EnumColor.BLUE.ordinal() + 1);

        helper.runAfterDelay(3, () -> {
            ModuleColourSorter sorter = module(helper, sorterPipe, ModuleColourSorter.class);
            sorter.setColour(sorterPipe, EnumColor.RED);
            helper.assertTrue(sorter.setFacing(sorterPipe, Direction.EAST), "the Colour Sorter refused east");
            IPipeGateTarget target = tagPipe.gateHolder.target;
            helper.assertValueEqual(actions(target).size(), 4, "Extraction Preset actions");

            helper.runAfterDelay(40, () -> {
                helper.assertValueEqual(count(north, null) + count(east, null), 0, "items moved with no preset");
                target.actionsActivated(Map.of(TransportRegistry.actionExtractionPresetRed.get(), true,
                        TransportRegistry.actionExtractionPresetBlue.get(), true,
                        TransportRegistry.actionExtractionPresetGreen.get(), false));
                helper.assertTrue(tag.isActive(0) && tag.isActive(1) && !tag.isActive(2), "active presets");

                helper.succeedWhen(() -> {
                    int cobblestone = count(east, Items.COBBLESTONE);
                    int dirt = count(north, Items.DIRT);
                    helper.assertTrue(cobblestone >= 3 && dirt >= 3, "moved " + cobblestone + " and " + dirt);
                    helper.assertTrue(Math.abs(cobblestone - dirt) <= 1, "filters did not alternate: " + cobblestone
                            + " cobblestone, " + dirt + " dirt");
                    helper.assertValueEqual(count(east, Items.DIRT) + count(north, Items.COBBLESTONE), 0,
                            "an item went the wrong way");
                    helper.assertEntityNotPresent(EntityTypes.ITEM);
                });
            });
        });
    }

    /** The Filtered Colour Tag's menu: four phantom filters, paint as data slots, paint coming back by GUI return. */
    static void filteredColourTagMenu(GameTestHelper helper) {
        TilePipe pipe = itemPipe(helper, 1, 0, PipeMaterial.WOOD, PipeModuleType.FILTERED_COLOUR_TAG);
        ModuleFilteredColourTag module = module(helper, pipe, ModuleFilteredColourTag.class);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.snapTo(Vec3.atBottomCenterOf(helper.absolutePos(at(1, 0)).above()));
        RegistryAccess registries = helper.getLevel().registryAccess();

        AbstractContainerMenu opened = module.getMenuProvider(pipe).createMenu(1, player.getInventory(), player);
        if (!(opened instanceof ContainerFilteredColourTag menu))
            throw helper.assertionException("the module made no Filtered Colour Tag menu");
        helper.assertValueEqual(menu.slots.size(), 4 + 36, "menu slots");
        menu.slots.get(3).set(new ItemStack(Items.IRON_INGOT, 5));
        helper.assertTrue(module.filters.getItem(3).is(Items.IRON_INGOT), "filter slot is not the module's");
        helper.assertValueEqual(module.filters.getItem(3).getCount(), 1, "filter stack size");
        helper.assertValueEqual(menu.getWidgets().size(), 4, "paint buttons");

        try {
            menu.getGuiReturnHandler().readGuiData(PacketData.wrap(PacketData.write(registries, buf -> {
                buf.writeByte(2);
                buf.writeByte(EnumColor.YELLOW.ordinal() + 1);
            }), registries), player);
        } catch (InvalidPacketException e) {
            throw helper.assertionException("valid paint refused: " + e.getMessage());
        }
        helper.assertValueEqual(menu.paint(2), EnumColor.YELLOW, "paint after GUI return");
        for (int[] bad : new int[][] {{4, 1}, {0, 17}}) {
            boolean refused = false;
            try {
                menu.getGuiReturnHandler().readGuiData(PacketData.wrap(PacketData.write(registries, buf -> {
                    buf.writeByte(bad[0]);
                    buf.writeByte(bad[1]);
                }), registries), player);
            } catch (InvalidPacketException e) {
                refused = true;
            }
            helper.assertTrue(refused, "out-of-range paint accepted: slot " + bad[0] + ", colour " + bad[1]);
        }
        helper.assertTrue(menu.stillValid(player), "menu not valid next to the pipe");
        pipe.setModuleType(null);
        helper.assertFalse(menu.stillValid(player), "menu still valid after the module was removed");
        helper.succeed();
    }

    /**
     * Source chest, an Extractor, an Isolator pipe with a chest beside it, a plain pipe and a target chest. The Isolator
     * does not connect to its chest and passes everything on to the target. A fluid Isolator does not connect to a tank.
     */
    static void isolator(GameTestHelper helper) {
        Container source = chest(helper, 0, 0);
        itemPipe(helper, 1, 0, PipeMaterial.STONE, PipeModuleType.EXTRACTOR);
        TilePipe isolator = itemPipe(helper, 2, 0, PipeMaterial.STONE, PipeModuleType.ISOLATOR);
        Container beside = chest(helper, 2, 1);
        itemPipe(helper, 3, 0, PipeMaterial.STONE, null);
        Container target = chest(helper, 4, 0);
        source.setItem(0, new ItemStack(Items.COBBLESTONE, 6));

        helper.setBlock(at(0, 4), FactoryRegistry.tankBlock.get());
        TilePipe fluidIsolator = pipe(helper, 1, 4, PipeType.FLUID, PipeMaterial.STONE);
        fluidIsolator.setModuleType(PipeModuleType.ISOLATOR);
        pipe(helper, 2, 4, PipeType.FLUID, PipeMaterial.STONE);

        helper.succeedWhen(() -> {
            helper.assertFalse(PipeConnections.hasSide(isolator.getConnectionMask(), Direction.SOUTH),
                    "the Isolator connected to a chest");
            helper.assertTrue(PipeConnections.hasSide(isolator.getConnectionMask(), Direction.EAST),
                    "the Isolator did not connect to the next pipe");
            helper.assertFalse(PipeConnections.hasSide(fluidIsolator.getConnectionMask(), Direction.WEST),
                    "the fluid Isolator connected to a tank");
            helper.assertTrue(PipeConnections.hasSide(fluidIsolator.getConnectionMask(), Direction.EAST),
                    "the fluid Isolator did not connect to the next pipe");
            helper.assertValueEqual(count(target, Items.COBBLESTONE), 6, "cobblestone through the Isolator");
            helper.assertValueEqual(count(beside, null), 0, "items in the chest beside the Isolator");
            helper.assertEntityNotPresent(EntityTypes.ITEM);
        });
    }
}
