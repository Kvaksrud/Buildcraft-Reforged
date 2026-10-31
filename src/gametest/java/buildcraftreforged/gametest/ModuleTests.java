package buildcraftreforged.gametest;

import buildcraftreforged.api.gates.IAction;
import buildcraftreforged.api.transport.IExtractionHandler;
import buildcraftreforged.api.transport.IPipeTile.PipeType;
import buildcraftreforged.api.transport.PipeManager;
import buildcraftreforged.core.CoreRegistry;
import buildcraftreforged.core.network.InvalidPacketException;
import buildcraftreforged.core.network.PacketData;
import buildcraftreforged.transport.IPipeGateTarget;
import buildcraftreforged.transport.PipeMaterial;
import buildcraftreforged.transport.PipeModuleType;
import buildcraftreforged.transport.PowerMode;
import buildcraftreforged.transport.TilePipe;
import buildcraftreforged.transport.TransportRegistry;
import buildcraftreforged.transport.module.ContainerFilteredExtractor;
import buildcraftreforged.transport.module.ModuleDirector;
import buildcraftreforged.transport.module.ModuleExtractor;
import buildcraftreforged.transport.module.ModuleFilteredExtractor;
import buildcraftreforged.transport.module.PipeModule;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * #55: the module framework and the Extractor, Filtered Extractor and Director in a real level (docs/TRANSPORT.md 8,
 * section 14). Fitting with the item, the wrench, removal with its settings, extraction from a chest, the exclusion
 * list, filters in both modes, the Director's facing (wrench, gate setter, redstone) and the Power Limiter. They build
 * in the padded area from y = 2, like {@link ItemRoutingTests}.
 */
final class ModuleTests {
    private static final int Y = 2;

    /** Test name → test, registered by {@link BuildcraftGameTests} in the padded area. */
    static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();
    static final int MAX_TICKS = 400;

    static {
        TESTS.put("module_extractor_fit_and_wrench", ModuleTests::extractorFitAndWrench);
        TESTS.put("module_extractor_exclusion_list", ModuleTests::extractorExclusionList);
        TESTS.put("module_remove_and_refit", ModuleTests::removeAndRefit);
        TESTS.put("module_filtered_extractor_round_robin", ModuleTests::filteredRoundRobin);
        TESTS.put("module_filtered_extractor_modes", ModuleTests::filteredModes);
        TESTS.put("module_filtered_extractor_menu", ModuleTests::filteredMenu);
        TESTS.put("module_director_direction", ModuleTests::directorDirection);
        TESTS.put("module_director_power_limiter", ModuleTests::powerLimiter);
    }

    /** Absolute positions the test exclusion handler refuses (docs/TRANSPORT.md 8.2, {@link PipeManager}). */
    private static final Set<BlockPos> EXCLUDED = ConcurrentHashMap.newKeySet();

    static {
        PipeManager.registerExtractionHandler(new IExtractionHandler() {
            @Override
            public boolean canExtractItems(Object extractor, Level world, BlockPos pos) {
                return !EXCLUDED.contains(pos);
            }

            @Override
            public boolean canExtractFluids(Object extractor, Level world, BlockPos pos) {
                return !EXCLUDED.contains(pos);
            }
        });
    }

    private ModuleTests() {
    }

    private static BlockPos at(int x, int z) {
        return new BlockPos(x, Y, z);
    }

    private static TilePipe pipe(GameTestHelper helper, int x, int z, PipeType kind, PipeMaterial material) {
        helper.setBlock(at(x, z), TransportRegistry.getPipe(kind, material));
        return helper.getBlockEntity(at(x, z), TilePipe.class);
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

    /** Uses {@code stack} on the pipe at (x, z) as the block sees it, wrench first: {@code BlockPipe.useItemOn}. */
    private static InteractionResult useOnPipe(GameTestHelper helper, Player player, int x, int z, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos pos = helper.absolutePos(at(x, z));
        return helper.getLevel().getBlockState(pos).useItemOn(stack, helper.getLevel(), player, InteractionHand.MAIN_HAND,
                hit(helper, x, z));
    }

    private static InteractionResult wrench(GameTestHelper helper, Player player, int x, int z) {
        return useOnPipe(helper, player, x, z, new ItemStack(CoreRegistry.wrenchItem.get()));
    }

    /** Fits a module by using its item on the pipe ({@code ItemPipeModule.useOn}). */
    private static InteractionResult fit(GameTestHelper helper, Player player, int x, int z, ItemStack module) {
        player.setItemInHand(InteractionHand.MAIN_HAND, module);
        return module.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit(helper, x, z)));
    }

    private static IAction pipeDirection(Direction side) {
        return TransportRegistry.actionPipeDirection.get(side.ordinal()).get();
    }

    private static <T extends PipeModule> T module(GameTestHelper helper, TilePipe pipe, Class<T> type) {
        PipeModule module = pipe.getModule();
        if (!type.isInstance(module))
            throw helper.assertionException("expected a " + type.getSimpleName() + ", found " + module);
        return type.cast(module);
    }

    /**
     * Chest A, a wood pipe, chest B in a line. The Extractor item fits the module, which faces A (UP, NORTH, SOUTH, WEST
     * come first in the search from no facing) and moves A's items to B at the wood rate with no engine. A wrench turns
     * it to B, and the items go back.
     */
    static void extractorFitAndWrench(GameTestHelper helper) {
        Container a = chest(helper, 0, 0);
        TilePipe pipe = pipe(helper, 1, 0, PipeType.ITEM, PipeMaterial.WOOD);
        Container b = chest(helper, 2, 0);
        a.setItem(0, new ItemStack(Items.COBBLESTONE, 3));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        helper.runAfterDelay(2, () -> {
            ItemStack item = new ItemStack(TransportRegistry.extractorModule.get(), 2);
            helper.assertTrue(fit(helper, player, 1, 0, item).consumesAction(), "the Extractor item did not fit");
            helper.assertValueEqual(item.getCount(), 1, "module items left after fitting");
            ModuleExtractor extractor = module(helper, pipe, ModuleExtractor.class);
            helper.assertValueEqual(extractor.getFacing(), Direction.WEST, "Extractor facing");
            // One module per pipe: a second one does not fit.
            helper.assertFalse(fit(helper, player, 1, 0, item).consumesAction(), "a second module fitted");

            // Wood moves one item a second; three take about three seconds.
            helper.runAfterDelay(80, () -> {
                helper.assertValueEqual(count(b, Items.COBBLESTONE), 3, "cobblestone moved to B");
                helper.assertTrue(wrench(helper, player, 1, 0).consumesAction(), "the wrench did nothing");
                helper.assertValueEqual(extractor.getFacing(), Direction.EAST, "Extractor facing after the wrench");
                helper.succeedWhen(() -> {
                    helper.assertValueEqual(count(a, Items.COBBLESTONE), 3, "cobblestone back in A");
                    helper.assertEntityNotPresent(EntityTypes.ITEM);
                });
            });
        });
    }

    /** A block refused by the exclusion list is never a facing: the Extractor pulls from the other chest. */
    static void extractorExclusionList(GameTestHelper helper) {
        Container a = chest(helper, 0, 0);
        TilePipe pipe = pipe(helper, 1, 0, PipeType.ITEM, PipeMaterial.GOLD);
        Container b = chest(helper, 2, 0);
        a.setItem(0, new ItemStack(Items.DIRT, 5));
        b.setItem(0, new ItemStack(Items.SAND, 5));
        BlockPos excluded = helper.absolutePos(at(0, 0));
        EXCLUDED.add(excluded);
        pipe.setModuleType(PipeModuleType.EXTRACTOR);

        helper.runAfterDelay(40, () -> {
            try {
                ModuleExtractor extractor = module(helper, pipe, ModuleExtractor.class);
                helper.assertValueEqual(extractor.getFacing(), Direction.EAST, "facing with A excluded");
                helper.assertValueEqual(count(a, Items.SAND), 5, "sand moved from B to A");
                helper.assertValueEqual(count(a, Items.DIRT), 5, "dirt left in A");
                helper.assertFalse(extractor.isValidFacing(pipe, Direction.WEST), "an excluded block is a valid facing");
            } finally {
                EXCLUDED.remove(excluded);
            }
            helper.succeed();
        });
    }

    /**
     * A sneaking wrench takes the module off and hands back its item with the settings (Q18); fitted again, the settings
     * come back; the pipe saves them; breaking the pipe drops the module.
     */
    static void removeAndRefit(GameTestHelper helper) {
        TilePipe pipe = pipe(helper, 1, 0, PipeType.ITEM, PipeMaterial.STONE);
        chest(helper, 0, 0);
        pipe.setModuleType(PipeModuleType.FILTERED_EXTRACTOR);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        RegistryAccess registries = helper.getLevel().registryAccess();

        helper.runAfterDelay(3, () -> {
            ModuleFilteredExtractor module = module(helper, pipe, ModuleFilteredExtractor.class);
            module.filters.setItem(4, new ItemStack(Items.DIAMOND));
            module.setMode(ModuleFilteredExtractor.Mode.NONBLOCKING);
            module.cursor.set(4);

            // The pipe saves the module and its settings.
            CompoundTag saved = pipe.saveWithoutMetadata(registries);
            TilePipe copy = new TilePipe(pipe.getBlockPos(), pipe.getBlockState());
            copy.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, saved));
            ModuleFilteredExtractor loaded = module(helper, copy, ModuleFilteredExtractor.class);
            helper.assertTrue(loaded.filters.getItem(4).is(Items.DIAMOND), "filter not saved with the pipe");
            helper.assertValueEqual(loaded.getMode(), ModuleFilteredExtractor.Mode.NONBLOCKING, "mode saved");
            helper.assertValueEqual(loaded.cursor.get(), 4, "cursor saved");
            helper.assertValueEqual(loaded.getFacing(), Direction.WEST, "facing saved");

            player.setShiftKeyDown(true);
            helper.assertTrue(wrench(helper, player, 1, 0).consumesAction(), "the sneaking wrench did nothing");
            helper.assertTrue(pipe.getModule() == null, "the module is still on the pipe");
            ItemStack item = ItemStack.EMPTY;
            for (ItemStack stack : player.getInventory()) {
                if (stack.is(TransportRegistry.filteredExtractorModule.get()))
                    item = stack;
            }
            helper.assertFalse(item.isEmpty(), "no module item handed back");
            helper.assertTrue(item.has(TransportRegistry.moduleSettings.get()), "the module item has no settings");

            player.setShiftKeyDown(false);
            helper.assertTrue(fit(helper, player, 1, 0, item.copy()).consumesAction(), "the module did not fit again");
            ModuleFilteredExtractor refitted = module(helper, pipe, ModuleFilteredExtractor.class);
            helper.assertTrue(refitted.filters.getItem(4).is(Items.DIAMOND), "filter lost on removal");
            helper.assertValueEqual(refitted.getMode(), ModuleFilteredExtractor.Mode.NONBLOCKING, "mode lost on removal");

            // Not on a fluid pipe (Q8).
            pipe(helper, 3, 0, PipeType.FLUID, PipeMaterial.STONE);
            helper.assertFalse(fit(helper, player, 3, 0, item.copy()).consumesAction(), "Filtered Extractor fitted a fluid pipe");

            helper.destroyBlock(at(1, 0));
            AABB area = new AABB(helper.absolutePos(at(1, 0))).inflate(2);
            boolean dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).stream()
                    .anyMatch(entity -> entity.getItem().is(TransportRegistry.filteredExtractorModule.get())
                            && entity.getItem().has(TransportRegistry.moduleSettings.get()));
            helper.assertTrue(dropped, "breaking the pipe did not drop the module with its settings");
            helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).forEach(ItemEntity::discard);
            helper.succeed();
        });
    }

    private static void filters(ModuleFilteredExtractor module, Item... items) {
        for (int i = 0; i < items.length; i++) {
            module.filters.setItem(i, new ItemStack(items[i]));
        }
    }

    /**
     * Filters cobblestone and dirt; the source also has sand. Items come in round-robin order over the filters, one
     * attempt each, and the sand never moves.
     */
    static void filteredRoundRobin(GameTestHelper helper) {
        Container source = chest(helper, 0, 0);
        TilePipe pipe = pipe(helper, 1, 0, PipeType.ITEM, PipeMaterial.WOOD);
        Container target = chest(helper, 2, 0);
        source.setItem(0, new ItemStack(Items.SAND, 10));
        source.setItem(1, new ItemStack(Items.COBBLESTONE, 10));
        source.setItem(2, new ItemStack(Items.DIRT, 10));
        pipe.setModuleType(PipeModuleType.FILTERED_EXTRACTOR);
        filters(module(helper, pipe, ModuleFilteredExtractor.class), Items.COBBLESTONE, Items.DIRT);

        // Wood moves one item a second, so the counts can be read between two items.
        helper.succeedWhen(() -> {
            int cobblestone = count(target, Items.COBBLESTONE);
            int dirt = count(target, Items.DIRT);
            helper.assertValueEqual(cobblestone + dirt, 4, "items moved");
            helper.assertValueEqual(cobblestone, 2, "cobblestone moved");
            helper.assertValueEqual(dirt, 2, "dirt moved");
            helper.assertValueEqual(count(target, Items.SAND), 0, "sand moved");
        });
    }

    /**
     * Filters cobblestone, dirt and sand; the source has no dirt. Blocking moves one cobblestone and waits on the dirt;
     * switched to non-blocking it skips the dirt. With no filters at all nothing moves.
     */
    static void filteredModes(GameTestHelper helper) {
        Container source = chest(helper, 0, 0);
        TilePipe pipe = pipe(helper, 1, 0, PipeType.ITEM, PipeMaterial.WOOD);
        Container target = chest(helper, 2, 0);
        source.setItem(0, new ItemStack(Items.COBBLESTONE, 10));
        source.setItem(1, new ItemStack(Items.SAND, 10));
        pipe.setModuleType(PipeModuleType.FILTERED_EXTRACTOR);
        ModuleFilteredExtractor module = module(helper, pipe, ModuleFilteredExtractor.class);
        filters(module, Items.COBBLESTONE, Items.DIRT, Items.SAND);

        Container emptySource = chest(helper, 0, 2);
        TilePipe unfiltered = pipe(helper, 1, 2, PipeType.ITEM, PipeMaterial.WOOD);
        Container emptyTarget = chest(helper, 2, 2);
        emptySource.setItem(0, new ItemStack(Items.COBBLESTONE, 10));
        unfiltered.setModuleType(PipeModuleType.FILTERED_EXTRACTOR);

        helper.runAfterDelay(80, () -> {
            helper.assertValueEqual(count(target, Items.COBBLESTONE), 1, "cobblestone moved while blocking");
            helper.assertValueEqual(count(target, Items.SAND), 0, "sand moved while blocking on dirt");
            helper.assertValueEqual(count(emptyTarget, null), 0, "items moved with no filters");
            module.setMode(ModuleFilteredExtractor.Mode.NONBLOCKING);
            helper.succeedWhen(() -> {
                helper.assertTrue(count(target, Items.SAND) >= 2, "sand not moved in non-blocking mode");
                helper.assertTrue(count(target, Items.COBBLESTONE) >= 2, "cobblestone not moved in non-blocking mode");
                helper.assertValueEqual(count(emptyTarget, null), 0, "items moved with no filters");
            });
        });
    }

    /** The menu: nine phantom filter slots before the player inventory, and the mode comes back by GUI return. */
    static void filteredMenu(GameTestHelper helper) {
        TilePipe pipe = pipe(helper, 1, 0, PipeType.ITEM, PipeMaterial.WOOD);
        pipe.setModuleType(PipeModuleType.FILTERED_EXTRACTOR);
        ModuleFilteredExtractor module = module(helper, pipe, ModuleFilteredExtractor.class);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.snapTo(Vec3.atBottomCenterOf(helper.absolutePos(at(1, 0)).above()));
        RegistryAccess registries = helper.getLevel().registryAccess();

        // A mock player has no mod channel; open the menu on the server side only, as GuiTests.sampleMenu does.
        AbstractContainerMenu opened = module.getMenuProvider(pipe).createMenu(1, player.getInventory(), player);
        if (!(opened instanceof ContainerFilteredExtractor menu))
            throw helper.assertionException("the module made no Filtered Extractor menu");
        helper.assertValueEqual(menu.slots.size(), 9 + 36, "menu slots");
        menu.slots.get(3).set(new ItemStack(Items.IRON_INGOT, 5));
        helper.assertTrue(module.filters.getItem(3).is(Items.IRON_INGOT), "filter slot is not the module's");
        helper.assertValueEqual(module.filters.getItem(3).getCount(), 1, "filter stack size");
        helper.assertTrue(menu.stillValid(player), "menu not valid next to the pipe");

        try {
            menu.getGuiReturnHandler().readGuiData(PacketData.wrap(PacketData.write(registries,
                    buf -> buf.writeByte(ModuleFilteredExtractor.Mode.NONBLOCKING.ordinal())), registries), player);
        } catch (InvalidPacketException e) {
            throw helper.assertionException("valid mode refused: " + e.getMessage());
        }
        helper.assertValueEqual(module.getMode(), ModuleFilteredExtractor.Mode.NONBLOCKING, "mode after GUI return");
        boolean refused = false;
        try {
            menu.getGuiReturnHandler().readGuiData(PacketData.wrap(PacketData.write(registries,
                    buf -> buf.writeByte(7)), registries), player);
        } catch (InvalidPacketException e) {
            refused = true;
        }
        helper.assertTrue(refused, "out-of-range mode accepted");

        pipe.setModuleType(null);
        helper.assertFalse(menu.stillValid(player), "menu still valid after the module was removed");
        helper.succeed();
    }

    /**
     * Source chest, an Extractor pipe, a Director junction with chests north, south and east. The Director starts
     * facing north and everything goes north; a wrench turns it south; the Pipe Direction gate action refuses the
     * Extractor's side and turns it to another; a redstone change turns it on to east (Q11).
     */
    static void directorDirection(GameTestHelper helper) {
        Container source = chest(helper, 0, 2);
        TilePipe extractor = pipe(helper, 1, 2, PipeType.ITEM, PipeMaterial.IRON);
        TilePipe director = pipe(helper, 2, 2, PipeType.ITEM, PipeMaterial.IRON);
        Container north = chest(helper, 2, 1);
        Container south = chest(helper, 2, 3);
        Container east = chest(helper, 3, 2);
        source.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        extractor.setModuleType(PipeModuleType.EXTRACTOR);
        director.setModuleType(PipeModuleType.DIRECTOR);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        helper.runAfterDelay(30, () -> {
            ModuleDirector module = module(helper, director, ModuleDirector.class);
            helper.assertValueEqual(module.getFacing(), Direction.NORTH, "Director facing");
            helper.assertTrue(count(north, null) > 0, "nothing reached the north chest");
            helper.assertValueEqual(count(south, null) + count(east, null), 0, "items left by another side");

            helper.assertTrue(wrench(helper, player, 2, 2).consumesAction(), "the wrench did nothing");
            helper.assertValueEqual(module.getFacing(), Direction.SOUTH, "Director facing after the wrench");
            helper.assertFalse(module.setFacing(director, Direction.WEST), "the Director faced an Extractor pipe");
            // The gate actions: one Pipe Direction per connected side; the Extractor's side is refused.
            IPipeGateTarget target = director.gateHolder.target;
            helper.assertTrue(target != null, "the Director is not the pipe's gate target");
            LinkedList<IAction> actions = new LinkedList<>();
            target.addActions(actions);
            helper.assertValueEqual(actions.size(), 4, "Pipe Direction actions (one per connected side)");
            target.actionsActivated(Map.of(pipeDirection(Direction.WEST), true));
            helper.assertValueEqual(module.getFacing(), Direction.SOUTH, "facing after a refused Pipe Direction action");
            target.actionsActivated(Map.of(pipeDirection(Direction.NORTH), true));
            helper.assertValueEqual(module.getFacing(), Direction.NORTH, "facing after the Pipe Direction action");
            helper.assertTrue(wrench(helper, player, 2, 2).consumesAction(), "the wrench did nothing");
            helper.assertValueEqual(module.getFacing(), Direction.SOUTH, "facing after the second wrench");
            int northBefore = count(north, null);

            helper.runAfterDelay(25, () -> {
                helper.assertTrue(count(south, null) > 0, "nothing reached the south chest");
                helper.assertValueEqual(count(north, null), northBefore, "items still going north");
                helper.assertValueEqual(count(east, null), 0, "items went east");

                helper.setBlock(at(2, 2).above(), Blocks.REDSTONE_BLOCK);
                helper.succeedWhen(() -> {
                    helper.assertValueEqual(module.getFacing(), Direction.EAST, "Director facing after redstone");
                    helper.assertTrue(count(east, null) > 0, "nothing reached the east chest");
                    helper.assertEntityNotPresent(EntityTypes.ITEM);
                });
            });
        });
    }

    /** On a power pipe the Director is the Power Limiter (Q7): the wrench steps through 20 … 1 280 FE/t. */
    static void powerLimiter(GameTestHelper helper) {
        TilePipe pipe = pipe(helper, 1, 0, PipeType.POWER, PipeMaterial.GOLD);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        helper.runAfterDelay(2, () -> {
            helper.assertTrue(fit(helper, player, 1, 0, new ItemStack(TransportRegistry.directorModule.get()))
                    .consumesAction(), "the Director did not fit a power pipe");
            ModuleDirector module = module(helper, pipe, ModuleDirector.class);
            helper.assertValueEqual(module.getPowerLimit(), 20, "first limit");
            LinkedList<IAction> actions = new LinkedList<>();
            pipe.gateHolder.target.addActions(actions);
            helper.assertValueEqual(actions.size(), 7, "Power Limiter actions");
            pipe.gateHolder.target.actionsActivated(Map.of(TransportRegistry.actionPowerLimiter.get(PowerMode.M64.ordinal())
                    .get(), true));
            helper.assertValueEqual(module.getPowerLimit(), 640, "limit after the Power Limiter action");
            pipe.gateHolder.target.actionsActivated(Map.of(TransportRegistry.actionPowerLimiter.get(PowerMode.M2.ordinal())
                    .get(), true));
            for (int limit : new int[] {40, 80, 160, 320, 640, 1280, 20}) {
                helper.assertTrue(wrench(helper, player, 1, 0).consumesAction(), "the wrench did nothing");
                helper.assertValueEqual(module.getPowerLimit(), limit, "limit after the wrench");
            }
            for (Direction side : Direction.values()) {
                helper.assertTrue(pipe.allowsOutput(side), "the Power Limiter directs");
            }
            helper.assertFalse(fit(helper, player, 3, 0, new ItemStack(TransportRegistry.extractorModule.get()))
                    .consumesAction(), "an Extractor fitted no pipe");
            pipe(helper, 3, 0, PipeType.POWER, PipeMaterial.GOLD);
            helper.assertFalse(fit(helper, player, 3, 0, new ItemStack(TransportRegistry.extractorModule.get()))
                    .consumesAction(), "an Extractor fitted a power pipe");
            helper.succeed();
        });
    }
}
