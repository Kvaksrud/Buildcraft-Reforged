package buildcraftreforged.gametest;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.transport.PipeMaterial;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
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
    private static final ResourceKey<Consumer<GameTestHelper>> FUELS = function("fuels");
    private static final ResourceKey<Consumer<GameTestHelper>> PIPE_NETWORK_BUILD = function("pipe_network_build");
    private static final ResourceKey<Consumer<GameTestHelper>> PIPE_NETWORK_MERGE_SPLIT = function("pipe_network_merge_split");
    private static final ResourceKey<Consumer<GameTestHelper>> PIPE_NETWORK_UNLOAD = function("pipe_network_unload");
    private static final ResourceKey<Consumer<GameTestHelper>> TIER_PIPES_CRAFTABLE = function("tier_pipes_craftable");
    private static final ResourceKey<Consumer<GameTestHelper>> TIER_PIPES_CAPS = function("tier_pipes_caps");
    private static final ResourceKey<Consumer<GameTestHelper>> STATUS_SYNC_WHILE_OPEN = function("status_sync_while_open");
    private static final ResourceKey<Consumer<GameTestHelper>> STATUS_PANEL_WITHOUT_GUI = function("status_panel_without_gui");
    private static final ResourceKey<Consumer<GameTestHelper>> LASER_FEEDS_TARGET = function("laser_feeds_target");
    private static final ResourceKey<Consumer<GameTestHelper>> LASER_TARGET_SEARCH = function("laser_target_search");
    private static final ResourceKey<Consumer<GameTestHelper>> TANK_STACKING = function("tank_stacking");
    private static final ResourceKey<Consumer<GameTestHelper>> TANK_TRANSFER = function("tank_transfer");
    private static final ResourceKey<Consumer<GameTestHelper>> TANK_BUCKETS = function("tank_buckets");
    private static final ResourceKey<Consumer<GameTestHelper>> OIL_FUEL_PLACEMENT_AND_BUCKETS = function("oil_fuel_placement_and_buckets");
    private static final ResourceKey<Consumer<GameTestHelper>> OIL_FUEL_FLOW = function("oil_fuel_flow");
    private static final ResourceKey<Consumer<GameTestHelper>> OIL_FUEL_LIQUIDS = function("oil_fuel_liquids");
    private static final ResourceKey<Consumer<GameTestHelper>> OIL_FUEL_NETHER_IGNITION = function("oil_fuel_nether_ignition");
    private static final ResourceKey<Consumer<GameTestHelper>> OIL_FUEL_NO_IGNITION_OUTSIDE_NETHER =
            function("oil_fuel_no_ignition_outside_nether");
    private static final ResourceKey<Consumer<GameTestHelper>> ENGINE_HEAT_AND_STAGES = function("engine_heat_and_stages");
    private static final ResourceKey<Consumer<GameTestHelper>> ENGINE_PUSHES_INTO_RECEPTOR = function("engine_pushes_into_receptor");
    private static final ResourceKey<Consumer<GameTestHelper>> ENGINE_PUSHES_INTO_FE_AND_ENGINES =
            function("engine_pushes_into_fe_and_engines");
    private static final ResourceKey<Consumer<GameTestHelper>> ENGINE_ORIENTATION = function("engine_orientation");
    private static final ResourceKey<Consumer<GameTestHelper>> ASSEMBLY_LASER_CRAFTING = function("assembly_laser_crafting");
    private static final ResourceKey<Consumer<GameTestHelper>> ASSEMBLY_QUEUE_AND_TAGS = function("assembly_queue_and_tags");
    private static final ResourceKey<Consumer<GameTestHelper>> ASSEMBLY_SELECTION_SAVE_AND_SYNC =
            function("assembly_selection_save_and_sync");
    private static final ResourceKey<Consumer<GameTestHelper>> OWNER_RECORDED_AND_SAVED = function("owner_recorded_and_saved");
    private static final ResourceKey<Consumer<GameTestHelper>> OWNER_BREAK = function("owner_break");
    private static final ResourceKey<Consumer<GameTestHelper>> OWNER_PLACE = function("owner_place");

    private static final ResourceKey<Consumer<GameTestHelper>> ITEM_ROUTING_CHEST_TO_CHEST = function("item_routing_chest_to_chest");
    private static final ResourceKey<Consumer<GameTestHelper>> ITEM_ROUTING_HOPPER_PUSH = function("item_routing_hopper_push");
    private static final ResourceKey<Consumer<GameTestHelper>> ITEM_ROUTING_BRANCHING = function("item_routing_branching");
    private static final ResourceKey<Consumer<GameTestHelper>> ITEM_ROUTING_BUDGETS = function("item_routing_budgets");

    private static final ResourceKey<Consumer<GameTestHelper>> POWER_ROUTING_ENGINE_TO_MACHINE =
            function("power_routing_engine_to_machine");
    private static final ResourceKey<Consumer<GameTestHelper>> POWER_ROUTING_SPLITTING = function("power_routing_splitting");
    private static final ResourceKey<Consumer<GameTestHelper>> POWER_ROUTING_LIMITER = function("power_routing_limiter");

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
            registry.register(FUELS, FuelTests::fuels);
            registry.register(PIPE_NETWORK_BUILD, PipeNetworkTests::buildAndEndpoints);
            registry.register(PIPE_NETWORK_MERGE_SPLIT, PipeNetworkTests::mergeAndSplit);
            registry.register(PIPE_NETWORK_UNLOAD, PipeNetworkTests::unloadAndReload);
            registry.register(TIER_PIPES_CRAFTABLE, TierPipeTests::craftable);
            registry.register(TIER_PIPES_CAPS, TierPipeTests::caps);
            registry.register(STATUS_SYNC_WHILE_OPEN, StatusTests::syncWhileOpen);
            registry.register(STATUS_PANEL_WITHOUT_GUI, StatusTests::panelWithoutGui);
            registry.register(LASER_FEEDS_TARGET, LaserTests::feedsTarget);
            registry.register(LASER_TARGET_SEARCH, LaserTests::targetSearch);
            registry.register(TANK_STACKING, TankTests::stacking);
            registry.register(TANK_TRANSFER, TankTests::transfer);
            registry.register(TANK_BUCKETS, TankTests::buckets);
            registry.register(OIL_FUEL_PLACEMENT_AND_BUCKETS, OilFuelTests::placementAndBuckets);
            registry.register(OIL_FUEL_FLOW, OilFuelTests::flow);
            registry.register(OIL_FUEL_LIQUIDS, OilFuelTests::liquids);
            registry.register(OIL_FUEL_NETHER_IGNITION, OilFuelTests::netherIgnition);
            registry.register(OIL_FUEL_NO_IGNITION_OUTSIDE_NETHER, OilFuelTests::noIgnitionOutsideNether);
            registry.register(ENGINE_HEAT_AND_STAGES, EngineTests::heatAndStages);
            registry.register(ENGINE_PUSHES_INTO_RECEPTOR, EngineTests::pushesIntoReceptor);
            registry.register(ENGINE_PUSHES_INTO_FE_AND_ENGINES, EngineTests::pushesIntoForgeEnergyAndEngines);
            registry.register(ENGINE_ORIENTATION, EngineTests::orientation);
            registry.register(ASSEMBLY_LASER_CRAFTING, AssemblyTableTests::laserCrafting);
            registry.register(ASSEMBLY_QUEUE_AND_TAGS, AssemblyTableTests::queueAndTags);
            registry.register(ASSEMBLY_SELECTION_SAVE_AND_SYNC, AssemblyTableTests::selectionSaveAndSync);
            registry.register(OWNER_RECORDED_AND_SAVED, OwnerTests::recordedAndSaved);
            registry.register(OWNER_BREAK, OwnerTests::breakAsOwner);
            registry.register(OWNER_PLACE, OwnerTests::placeAsOwner);
            registry.register(ITEM_ROUTING_CHEST_TO_CHEST, ItemRoutingTests::chestToChest);
            registry.register(ITEM_ROUTING_HOPPER_PUSH, ItemRoutingTests::hopperPush);
            registry.register(ITEM_ROUTING_BRANCHING, ItemRoutingTests::branching);
            registry.register(ITEM_ROUTING_BUDGETS, ItemRoutingTests::budgets);
            for (PipeMaterial material : PipeMaterial.values()) {
                registry.register(throughput(material), helper -> ItemRoutingTests.throughput(helper, material));
            }
            registry.register(POWER_ROUTING_ENGINE_TO_MACHINE, PowerRoutingTests::engineToMachine);
            registry.register(POWER_ROUTING_SPLITTING, PowerRoutingTests::splitting);
            registry.register(POWER_ROUTING_LIMITER, PowerRoutingTests::limiter);
            for (PipeMaterial material : PipeMaterial.values()) {
                registry.register(powerCaps(material), helper -> PowerRoutingTests.tierCaps(helper, material));
            }
        });
        FluidTankTests.register(event);
        LaserTests.register(event);
        EngineTests.register(event);
        PowerRoutingTests.register(event);
    }

    @SubscribeEvent
    static void registerCapabilities(RegisterCapabilitiesEvent event) {
        FluidTankTests.registerCapabilities(event);
        PipeTests.registerTestCapabilities(event);
        EngineTests.registerCapabilities(event);
        PowerRoutingTests.registerCapabilities(event);
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
        event.registerTest(id("fuels"), new FunctionGameTestInstance(FUELS,
                new TestData<>(environment, EMPTY_STRUCTURE, 20, 0, true)));
        for (ResourceKey<Consumer<GameTestHelper>> network : List.of(PIPE_NETWORK_BUILD, PIPE_NETWORK_MERGE_SPLIT,
                PIPE_NETWORK_UNLOAD)) {
            event.registerTest(network.identifier(), new FunctionGameTestInstance(network,
                    new TestData<>(environment, EMPTY_STRUCTURE, 60, 0, true)));
        }
        for (ResourceKey<Consumer<GameTestHelper>> tierPipes : List.of(TIER_PIPES_CRAFTABLE, TIER_PIPES_CAPS)) {
            event.registerTest(tierPipes.identifier(), new FunctionGameTestInstance(tierPipes,
                    new TestData<>(environment, EMPTY_STRUCTURE, 20, 0, true)));
        }
        event.registerTest(id("status_sync_while_open"), new FunctionGameTestInstance(STATUS_SYNC_WHILE_OPEN,
                new TestData<>(environment, EMPTY_STRUCTURE, 100, 0, true)));
        event.registerTest(id("status_panel_without_gui"), new FunctionGameTestInstance(STATUS_PANEL_WITHOUT_GUI,
                new TestData<>(environment, EMPTY_STRUCTURE, 40, 0, true)));
        event.registerTest(id("laser_feeds_target"), new FunctionGameTestInstance(LASER_FEEDS_TARGET,
                new TestData<>(environment, EMPTY_STRUCTURE, 60, 0, true)));
        event.registerTest(id("laser_target_search"), new FunctionGameTestInstance(LASER_TARGET_SEARCH,
                new TestData<>(environment, EMPTY_STRUCTURE, 160, 0, true)));
        for (ResourceKey<Consumer<GameTestHelper>> tank : List.of(TANK_STACKING, TANK_TRANSFER, TANK_BUCKETS)) {
            event.registerTest(tank.identifier(), new FunctionGameTestInstance(tank,
                    new TestData<>(environment, EMPTY_STRUCTURE, 60, 0, true)));
        }
        // Oil and fuel flow beyond the 1x1x1 structure; the padding clears that area and keeps other tests out of it.
        event.registerTest(OIL_FUEL_PLACEMENT_AND_BUCKETS.identifier(), new FunctionGameTestInstance(OIL_FUEL_PLACEMENT_AND_BUCKETS,
                padded(environment, Level.OVERWORLD, 20)));
        event.registerTest(OIL_FUEL_FLOW.identifier(), new FunctionGameTestInstance(OIL_FUEL_FLOW, padded(environment, Level.OVERWORLD, 140)));
        event.registerTest(OIL_FUEL_LIQUIDS.identifier(), new FunctionGameTestInstance(OIL_FUEL_LIQUIDS,
                padded(environment, Level.OVERWORLD, 60)));
        event.registerTest(OIL_FUEL_NETHER_IGNITION.identifier(), new FunctionGameTestInstance(OIL_FUEL_NETHER_IGNITION,
                padded(environment, Level.NETHER, 40)));
        event.registerTest(OIL_FUEL_NO_IGNITION_OUTSIDE_NETHER.identifier(), new FunctionGameTestInstance(
                OIL_FUEL_NO_IGNITION_OUTSIDE_NETHER, padded(environment, Level.OVERWORLD, 40)));
        event.registerTest(ENGINE_HEAT_AND_STAGES.identifier(), new FunctionGameTestInstance(ENGINE_HEAT_AND_STAGES,
                new TestData<>(environment, EMPTY_STRUCTURE, 40, 0, true)));
        for (ResourceKey<Consumer<GameTestHelper>> engine : List.of(ENGINE_PUSHES_INTO_RECEPTOR, ENGINE_PUSHES_INTO_FE_AND_ENGINES,
                ENGINE_ORIENTATION)) {
            event.registerTest(engine.identifier(), new FunctionGameTestInstance(engine,
                    new TestData<>(environment, EMPTY_STRUCTURE, 200, 0, true)));
        }
        for (ResourceKey<Consumer<GameTestHelper>> assembly : List.of(ASSEMBLY_LASER_CRAFTING, ASSEMBLY_QUEUE_AND_TAGS,
                ASSEMBLY_SELECTION_SAVE_AND_SYNC)) {
            event.registerTest(assembly.identifier(), new FunctionGameTestInstance(assembly,
                    new TestData<>(environment, EMPTY_STRUCTURE, 60, 0, true)));
        }
        for (ResourceKey<Consumer<GameTestHelper>> owner : List.of(OWNER_RECORDED_AND_SAVED, OWNER_BREAK, OWNER_PLACE)) {
            event.registerTest(owner.identifier(), new FunctionGameTestInstance(owner,
                    new TestData<>(environment, EMPTY_STRUCTURE, 40, 0, true)));
        }
        // Item routing builds lines longer than the 5-block spacing between tests, so it uses the padded area too.
        for (ResourceKey<Consumer<GameTestHelper>> routing : List.of(ITEM_ROUTING_CHEST_TO_CHEST, ITEM_ROUTING_HOPPER_PUSH,
                ITEM_ROUTING_BRANCHING, ITEM_ROUTING_BUDGETS)) {
            event.registerTest(routing.identifier(), new FunctionGameTestInstance(routing,
                    padded(environment, Level.OVERWORLD, 120)));
        }
        for (PipeMaterial material : PipeMaterial.values()) {
            event.registerTest(throughput(material).identifier(), new FunctionGameTestInstance(throughput(material),
                    padded(environment, Level.OVERWORLD, ItemRoutingTests.WINDOW_END + 20)));
        }
        for (ResourceKey<Consumer<GameTestHelper>> power : List.of(POWER_ROUTING_ENGINE_TO_MACHINE, POWER_ROUTING_SPLITTING,
                POWER_ROUTING_LIMITER)) {
            event.registerTest(power.identifier(), new FunctionGameTestInstance(power,
                    padded(environment, Level.OVERWORLD, PowerRoutingTests.WINDOW_END + 80)));
        }
        for (PipeMaterial material : PipeMaterial.values()) {
            event.registerTest(powerCaps(material).identifier(), new FunctionGameTestInstance(powerCaps(material),
                    padded(environment, Level.OVERWORLD, PowerRoutingTests.WINDOW_END + 20)));
        }
    }

    private static ResourceKey<Consumer<GameTestHelper>> powerCaps(PipeMaterial material) {
        return function("power_routing_caps_" + material.getSerializedName());
    }

    private static ResourceKey<Consumer<GameTestHelper>> throughput(PipeMaterial material) {
        return function("item_routing_throughput_" + material.getSerializedName());
    }

    /**
     * Test-only data: puts a vanilla item in {@code c:tools/wrench} to stand in for another mod's wrench, and adds lava
     * as a combustion engine fuel (see {@link FuelTests}).
     */
    @SubscribeEvent
    static void addTestData(AddPackFindersEvent event) {
        event.addPackFinders(id("gametest_data"), PackType.SERVER_DATA, Component.literal("Buildcraft Reforged GameTest data"),
                PackSource.BUILT_IN, true, Pack.Position.TOP);
    }

    private static TestData<Holder<TestEnvironmentDefinition<?>>> padded(Holder<TestEnvironmentDefinition<?>> environment,
            ResourceKey<Level> dimension, int maxTicks) {
        return new TestData<>(environment, dimension, EMPTY_STRUCTURE, maxTicks, 0, true, Rotation.NONE, false, 1, 1, false,
                OilFuelTests.PADDING);
    }

    private static ResourceKey<Consumer<GameTestHelper>> function(String name) {
        return ResourceKey.create(Registries.TEST_FUNCTION, id(name));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, path);
    }
}
