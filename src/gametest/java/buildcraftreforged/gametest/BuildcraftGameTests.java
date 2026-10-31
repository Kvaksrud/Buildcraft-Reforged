package buildcraftreforged.gametest;

import buildcraftreforged.BuildcraftReforged;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Registers the mod's GameTests. Run them with {@code ./gradlew runGameTestServer}; this source set is not in the jar.
 */
@EventBusSubscriber(modid = BuildcraftReforged.MOD_ID)
public final class BuildcraftGameTests {
    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");

    private static final ResourceKey<Consumer<GameTestHelper>> NO_SOCKETS = function("no_sockets");
    private static final ResourceKey<Consumer<GameTestHelper>> WRENCH_ROTATES_BLOCK = function("wrench_rotates_block");
    private static final ResourceKey<Consumer<GameTestHelper>> WRENCH_TAG = function("wrench_tag");
    private static final ResourceKey<Consumer<GameTestHelper>> TANK_FILL_AND_DRAIN = function("tank_fill_and_drain");
    private static final ResourceKey<Consumer<GameTestHelper>> TANK_BUCKET_AND_SAVE = function("tank_bucket_and_save");
    private static final ResourceKey<Consumer<GameTestHelper>> TRIGGER_REGISTRIES = function("trigger_registries");
    private static final ResourceKey<Consumer<GameTestHelper>> TRIGGER_MACHINE = function("trigger_machine");
    private static final ResourceKey<Consumer<GameTestHelper>> TRIGGER_INVENTORY = function("trigger_inventory");
    private static final ResourceKey<Consumer<GameTestHelper>> TRIGGER_FLUID = function("trigger_fluid");
    private static final ResourceKey<Consumer<GameTestHelper>> MARKERS = function("markers");
    private static final ResourceKey<Consumer<GameTestHelper>> PATH_MARKERS = function("path_markers");
    private static final ResourceKey<Consumer<GameTestHelper>> GUI_MENU_SLOTS = function("gui_menu_slots");
    private static final ResourceKey<Consumer<GameTestHelper>> GUI_SAMPLE_MENU = function("gui_sample_menu");
    private static final ResourceKey<Consumer<GameTestHelper>> PIPE_CHEST_AND_PIPE = function("pipe_chest_and_pipe");
    private static final ResourceKey<Consumer<GameTestHelper>> PIPE_KINDS_AND_ENDPOINTS = function("pipe_kinds_and_endpoints");
    private static final ResourceKey<Consumer<GameTestHelper>> PIPE_PLUGS_AND_MODULES = function("pipe_plugs_and_modules");
    private static final ResourceKey<Consumer<GameTestHelper>> PIPE_WATERLOGGING = function("pipe_waterlogging");
    private static final ResourceKey<Consumer<GameTestHelper>> TANK_STACKING = function("tank_stacking");
    private static final ResourceKey<Consumer<GameTestHelper>> TANK_TRANSFER = function("tank_transfer");
    private static final ResourceKey<Consumer<GameTestHelper>> TANK_BUCKETS = function("tank_buckets");

    private BuildcraftGameTests() {
    }

    @SubscribeEvent
    static void registerFunctions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, registry -> {
            registry.register(NO_SOCKETS, NoSocketsTest::noSockets);
            registry.register(WRENCH_ROTATES_BLOCK, WrenchTests::wrenchRotatesBlock);
            registry.register(WRENCH_TAG, WrenchTests::wrenchTag);
            registry.register(TANK_FILL_AND_DRAIN, FluidTankTests::fillAndDrain);
            registry.register(TANK_BUCKET_AND_SAVE, FluidTankTests::bucketAndSave);
            registry.register(TRIGGER_REGISTRIES, TriggerTests::registries);
            registry.register(TRIGGER_MACHINE, TriggerTests::machine);
            registry.register(TRIGGER_INVENTORY, TriggerTests::inventory);
            registry.register(TRIGGER_FLUID, TriggerTests::fluid);
            registry.register(MARKERS, MarkerTests::markers);
            registry.register(PATH_MARKERS, MarkerTests::pathMarkers);
            registry.register(GUI_MENU_SLOTS, GuiTests::menuSlots);
            registry.register(GUI_SAMPLE_MENU, GuiTests::sampleMenu);
            registry.register(PIPE_CHEST_AND_PIPE, PipeTests::chestAndPipe);
            registry.register(PIPE_KINDS_AND_ENDPOINTS, PipeTests::kindsAndEndpoints);
            registry.register(PIPE_PLUGS_AND_MODULES, PipeTests::plugsAndModules);
            registry.register(PIPE_WATERLOGGING, PipeTests::waterlogging);
            registry.register(TANK_STACKING, TankTests::stacking);
            registry.register(TANK_TRANSFER, TankTests::transfer);
            registry.register(TANK_BUCKETS, TankTests::buckets);
        });
        FluidTankTests.register(event);
    }

    @SubscribeEvent
    static void registerCapabilities(RegisterCapabilitiesEvent event) {
        FluidTankTests.registerCapabilities(event);
        PipeTests.registerTestCapabilities(event);
    }

    @SubscribeEvent
    static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(id("default"));
        event.registerTest(id("no_sockets"), new FunctionGameTestInstance(NO_SOCKETS,
                new TestData<>(environment, EMPTY_STRUCTURE, NoSocketsTest.TICKS + 200, 0, true)));
        event.registerTest(id("wrench_rotates_block"), new FunctionGameTestInstance(WRENCH_ROTATES_BLOCK,
                new TestData<>(environment, EMPTY_STRUCTURE, 20, 0, true)));
        event.registerTest(id("wrench_tag"), new FunctionGameTestInstance(WRENCH_TAG,
                new TestData<>(environment, EMPTY_STRUCTURE, 20, 0, true)));
        event.registerTest(id("tank_fill_and_drain"), new FunctionGameTestInstance(TANK_FILL_AND_DRAIN,
                new TestData<>(environment, EMPTY_STRUCTURE, 20, 0, true)));
        event.registerTest(id("tank_bucket_and_save"), new FunctionGameTestInstance(TANK_BUCKET_AND_SAVE,
                new TestData<>(environment, EMPTY_STRUCTURE, 20, 0, true)));
        for (ResourceKey<Consumer<GameTestHelper>> trigger : List.of(TRIGGER_REGISTRIES, TRIGGER_MACHINE, TRIGGER_INVENTORY,
                TRIGGER_FLUID)) {
            event.registerTest(trigger.identifier(), new FunctionGameTestInstance(trigger,
                    new TestData<>(environment, EMPTY_STRUCTURE, 20, 0, true)));
        }
        event.registerTest(id("markers"), new FunctionGameTestInstance(MARKERS,
                new TestData<>(environment, EMPTY_STRUCTURE, 40, 0, true)));
        event.registerTest(id("path_markers"), new FunctionGameTestInstance(PATH_MARKERS,
                new TestData<>(environment, EMPTY_STRUCTURE, 40, 0, true)));
        event.registerTest(id("gui_menu_slots"), new FunctionGameTestInstance(GUI_MENU_SLOTS,
                new TestData<>(environment, EMPTY_STRUCTURE, 20, 0, true)));
        event.registerTest(id("gui_sample_menu"), new FunctionGameTestInstance(GUI_SAMPLE_MENU,
                new TestData<>(environment, EMPTY_STRUCTURE, 100, 0, true)));
        event.registerTest(id("pipe_chest_and_pipe"), new FunctionGameTestInstance(PIPE_CHEST_AND_PIPE,
                new TestData<>(environment, EMPTY_STRUCTURE, 40, 0, true)));
        event.registerTest(id("pipe_kinds_and_endpoints"), new FunctionGameTestInstance(PIPE_KINDS_AND_ENDPOINTS,
                new TestData<>(environment, EMPTY_STRUCTURE, 40, 0, true)));
        event.registerTest(id("pipe_plugs_and_modules"), new FunctionGameTestInstance(PIPE_PLUGS_AND_MODULES,
                new TestData<>(environment, EMPTY_STRUCTURE, 60, 0, true)));
        event.registerTest(id("pipe_waterlogging"), new FunctionGameTestInstance(PIPE_WATERLOGGING,
                new TestData<>(environment, EMPTY_STRUCTURE, 40, 0, true)));
        for (ResourceKey<Consumer<GameTestHelper>> tank : List.of(TANK_STACKING, TANK_TRANSFER, TANK_BUCKETS)) {
            event.registerTest(tank.identifier(), new FunctionGameTestInstance(tank,
                    new TestData<>(environment, EMPTY_STRUCTURE, 60, 0, true)));
        }
    }

    /** Test-only capabilities (see {@link PipeTests#ENERGY_BLOCK}). */
    /** Test-only data: puts a vanilla item in {@code c:tools/wrench} to stand in for another mod's wrench. */
    @SubscribeEvent
    static void addTestData(AddPackFindersEvent event) {
        event.addPackFinders(id("gametest_data"), PackType.SERVER_DATA, Component.literal("Buildcraft Reforged GameTest data"),
                PackSource.BUILT_IN, true, Pack.Position.TOP);
    }

    private static ResourceKey<Consumer<GameTestHelper>> function(String name) {
        return ResourceKey.create(Registries.TEST_FUNCTION, id(name));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, path);
    }
}
