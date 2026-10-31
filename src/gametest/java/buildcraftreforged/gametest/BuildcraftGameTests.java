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
    /** The system property the {@code gameTestServer} run sets (build.gradle.kts). */
    public static final String GAMETEST_PROPERTY = "buildcraftreforged.gametest";
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
    private static final ResourceKey<Consumer<GameTestHelper>> ENGINE_RENDER_STATE = function("engine_render_state");
    private static final ResourceKey<Consumer<GameTestHelper>> ASSEMBLY_LASER_CRAFTING = function("assembly_laser_crafting");
    private static final ResourceKey<Consumer<GameTestHelper>> ASSEMBLY_QUEUE_AND_TAGS = function("assembly_queue_and_tags");
    private static final ResourceKey<Consumer<GameTestHelper>> ASSEMBLY_SELECTION_SAVE_AND_SYNC =
            function("assembly_selection_save_and_sync");
    private static final ResourceKey<Consumer<GameTestHelper>> OWNER_RECORDED_AND_SAVED = function("owner_recorded_and_saved");
    private static final ResourceKey<Consumer<GameTestHelper>> OWNER_BREAK = function("owner_break");
    private static final ResourceKey<Consumer<GameTestHelper>> OWNER_PLACE = function("owner_place");
    private static final ResourceKey<Consumer<GameTestHelper>> REFINERY_OIL_TO_FUEL = function("refinery_oil_to_fuel");
    private static final ResourceKey<Consumer<GameTestHelper>> REFINERY_FILTERS_AND_BLOCKED = function("refinery_filters_and_blocked");
    private static final ResourceKey<Consumer<GameTestHelper>> REFINERY_RECIPES = function("refinery_recipes");

    private static final ResourceKey<Consumer<GameTestHelper>> ITEM_ROUTING_CHEST_TO_CHEST = function("item_routing_chest_to_chest");
    private static final ResourceKey<Consumer<GameTestHelper>> ITEM_ROUTING_HOPPER_PUSH = function("item_routing_hopper_push");
    private static final ResourceKey<Consumer<GameTestHelper>> ITEM_ROUTING_BRANCHING = function("item_routing_branching");
    private static final ResourceKey<Consumer<GameTestHelper>> ITEM_ROUTING_BUDGETS = function("item_routing_budgets");

    private static final ResourceKey<Consumer<GameTestHelper>> GATE_ITEMS_AND_FITTING = function("gate_items_and_fitting");
    private static final ResourceKey<Consumer<GameTestHelper>> GATE_LOGIC_KINDS = function("gate_logic_kinds");
    private static final ResourceKey<Consumer<GameTestHelper>> GATE_WIRE_SIGNALS = function("gate_wire_signals");
    private static final ResourceKey<Consumer<GameTestHelper>> GATE_PIPE_CONTENTS = function("gate_pipe_contents");
    private static final ResourceKey<Consumer<GameTestHelper>> GATE_MODULE_ACTIONS_AND_PULSER = function("gate_module_actions_and_pulser");
    private static final ResourceKey<Consumer<GameTestHelper>> GATE_MENU = function("gate_menu");
    private static final ResourceKey<Consumer<GameTestHelper>> POWER_ROUTING_ENGINE_TO_MACHINE =
            function("power_routing_engine_to_machine");
    private static final ResourceKey<Consumer<GameTestHelper>> POWER_ROUTING_SPLITTING = function("power_routing_splitting");
    private static final ResourceKey<Consumer<GameTestHelper>> POWER_ROUTING_LIMITER = function("power_routing_limiter");
    private static final ResourceKey<Consumer<GameTestHelper>> FLUID_ROUTING_TANK_TO_TANK = function("fluid_routing_tank_to_tank");
    private static final ResourceKey<Consumer<GameTestHelper>> FLUID_ROUTING_SPLITTING = function("fluid_routing_splitting");
    private static final ResourceKey<Consumer<GameTestHelper>> FLUID_ROUTING_PUSH_AND_VOID = function("fluid_routing_push_and_void");
    private static final ResourceKey<Consumer<GameTestHelper>> FLUID_ROUTING_MIXING_REFUSAL =
            function("fluid_routing_mixing_refusal");
    private static final ResourceKey<Consumer<GameTestHelper>> ENGINE_WOOD_REDSTONE_OUTPUT = function("engine_wood_redstone_output");
    private static final ResourceKey<Consumer<GameTestHelper>> ENGINE_STONE_BURNS_FUEL = function("engine_stone_burns_fuel");
    private static final ResourceKey<Consumer<GameTestHelper>> ENGINE_STONE_OUTPUT_CONTROL = function("engine_stone_output_control");
    private static final ResourceKey<Consumer<GameTestHelper>> ENGINE_STONE_MENU = function("engine_stone_menu");
    private static final ResourceKey<Consumer<GameTestHelper>> ENGINE_DELIVERY_THROUGH_KINESIS_PIPE =
            function("engine_delivery_through_kinesis_pipe");
    private static final ResourceKey<Consumer<GameTestHelper>> ENGINE_IRON_BURNS_FUEL = function("engine_iron_burns_fuel");
    private static final ResourceKey<Consumer<GameTestHelper>> ENGINE_IRON_COOLING = function("engine_iron_cooling");
    private static final ResourceKey<Consumer<GameTestHelper>> ENGINE_IRON_OVERHEAT = function("engine_iron_overheat");
    private static final ResourceKey<Consumer<GameTestHelper>> ENGINE_OVERHEAT_CONFIG = function("engine_overheat_config");
    private static final ResourceKey<Consumer<GameTestHelper>> ENGINE_IRON_MENU = function("engine_iron_menu");
    private static final ResourceKey<Consumer<GameTestHelper>> PUMP_SMALL_POOL = function("pump_small_pool");
    private static final ResourceKey<Consumer<GameTestHelper>> PUMP_OIL = function("pump_oil");
    private static final ResourceKey<Consumer<GameTestHelper>> FLOOD_GATE_FILLS_HOLE = function("flood_gate_fills_hole");

    private BuildcraftGameTests() {
    }

    /**
     * True in the GameTest run only: {@code runGameTestServer} sets {@value #GAMETEST_PROPERTY}. The test-only blocks,
     * capabilities, test data and the tests themselves are registered there and nowhere else, so the client and server
     * dev runs carry none of them (they have no models). The sample blocks for manual checks ({@code sample_gui},
     * {@code sample_status}) have models and stay in every dev run. Decided 2026-10-31 (#130).
     */
    public static boolean isGameTestRun() {
        return Boolean.getBoolean(GAMETEST_PROPERTY);
    }

    @SubscribeEvent
    static void registerFunctions(RegisterEvent event) {
        if (!isGameTestRun())
            return;
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
            registry.register(GATE_ITEMS_AND_FITTING, GateTests::itemsAndFitting);
            registry.register(GATE_LOGIC_KINDS, GateTests::logicKinds);
            registry.register(GATE_WIRE_SIGNALS, GateTests::wireSignals);
            registry.register(GATE_PIPE_CONTENTS, GateTests::pipeContents);
            registry.register(GATE_MODULE_ACTIONS_AND_PULSER, GateTests::moduleActionsAndPulser);
            registry.register(GATE_MENU, GateTests::menu);
            registry.register(ENGINE_HEAT_AND_STAGES, EngineTests::heatAndStages);
            registry.register(ENGINE_PUSHES_INTO_RECEPTOR, EngineTests::pushesIntoReceptor);
            registry.register(ENGINE_PUSHES_INTO_FE_AND_ENGINES, EngineTests::pushesIntoForgeEnergyAndEngines);
            registry.register(ENGINE_ORIENTATION, EngineTests::orientation);
            registry.register(ENGINE_RENDER_STATE, EngineTests::renderState);
            registry.register(ASSEMBLY_LASER_CRAFTING, AssemblyTableTests::laserCrafting);
            registry.register(ASSEMBLY_QUEUE_AND_TAGS, AssemblyTableTests::queueAndTags);
            registry.register(ASSEMBLY_SELECTION_SAVE_AND_SYNC, AssemblyTableTests::selectionSaveAndSync);
            registry.register(OWNER_RECORDED_AND_SAVED, OwnerTests::recordedAndSaved);
            registry.register(OWNER_BREAK, OwnerTests::breakAsOwner);
            registry.register(OWNER_PLACE, OwnerTests::placeAsOwner);
            registry.register(REFINERY_OIL_TO_FUEL, RefineryTests::oilToFuel);
            registry.register(REFINERY_FILTERS_AND_BLOCKED, RefineryTests::filtersAndBlocked);
            registry.register(REFINERY_RECIPES, RefineryTests::recipes);
            registry.register(ITEM_ROUTING_CHEST_TO_CHEST, ItemRoutingTests::chestToChest);
            registry.register(ITEM_ROUTING_HOPPER_PUSH, ItemRoutingTests::hopperPush);
            registry.register(ITEM_ROUTING_BRANCHING, ItemRoutingTests::branching);
            registry.register(ITEM_ROUTING_BUDGETS, ItemRoutingTests::budgets);
            for (PipeMaterial material : PipeMaterial.values()) {
                registry.register(throughput(material), helper -> ItemRoutingTests.throughput(helper, material));
            }
            ModuleTests.TESTS.forEach((name, test) -> registry.register(function(name), test::accept));
            RoutingModuleTests.TESTS.forEach((name, test) -> registry.register(function(name), test::accept));
            BlockedOutputTests.TESTS.forEach((name, test) -> registry.register(function(name), test::accept));
            MiningWellTests.TESTS.forEach((name, test) -> registry.register(function(name), test::accept));
            EngineWrenchTests.TESTS.forEach((name, test) -> registry.register(function(name), test::accept));
            SeamTests.TESTS.forEach((name, test) -> registry.register(function(name), test::accept));
            FlowRecordTests.TESTS.forEach((name, test) -> registry.register(function(name), test::accept));
            QuarryTests.TESTS.forEach((name, test) -> registry.register(function(name), test::accept));
            registry.register(POWER_ROUTING_ENGINE_TO_MACHINE, PowerRoutingTests::engineToMachine);
            registry.register(POWER_ROUTING_SPLITTING, PowerRoutingTests::splitting);
            registry.register(POWER_ROUTING_LIMITER, PowerRoutingTests::limiter);
            registry.register(PUMP_SMALL_POOL, PumpTests::smallPool);
            registry.register(PUMP_OIL, PumpTests::oil);
            registry.register(FLOOD_GATE_FILLS_HOLE, PumpTests::floodGate);
            for (PipeMaterial material : PipeMaterial.values()) {
                registry.register(powerCaps(material), helper -> PowerRoutingTests.tierCaps(helper, material));
            }
            registry.register(FLUID_ROUTING_TANK_TO_TANK, FluidRoutingTests::tankToTank);
            registry.register(FLUID_ROUTING_SPLITTING, FluidRoutingTests::splitting);
            registry.register(FLUID_ROUTING_PUSH_AND_VOID, FluidRoutingTests::pushAndVoid);
            registry.register(FLUID_ROUTING_MIXING_REFUSAL, FluidRoutingTests::mixingRefusal);
            for (PipeMaterial material : PipeMaterial.values()) {
                registry.register(fluidCaps(material), helper -> FluidRoutingTests.tierCaps(helper, material));
            }
            registry.register(ENGINE_WOOD_REDSTONE_OUTPUT, WoodStoneEngineTests::woodRedstoneOutput);
            registry.register(ENGINE_STONE_BURNS_FUEL, WoodStoneEngineTests::stoneBurnsFuel);
            registry.register(ENGINE_STONE_OUTPUT_CONTROL, WoodStoneEngineTests::stoneOutputControl);
            registry.register(ENGINE_STONE_MENU, WoodStoneEngineTests::stoneMenu);
            registry.register(ENGINE_DELIVERY_THROUGH_KINESIS_PIPE, WoodStoneEngineTests::deliveryThroughKinesisPipe);
            registry.register(ENGINE_IRON_BURNS_FUEL, CombustionEngineTests::burnsFuel);
            registry.register(ENGINE_IRON_COOLING, CombustionEngineTests::cooling);
            registry.register(ENGINE_IRON_OVERHEAT, CombustionEngineTests::overheat);
            registry.register(ENGINE_OVERHEAT_CONFIG, CombustionEngineTests::overheatConfig);
            registry.register(ENGINE_IRON_MENU, CombustionEngineTests::menu);
        });
        FluidTankTests.register(event);
        LaserTests.register(event);
        EngineTests.register(event);
        PowerRoutingTests.register(event);
        FluidRoutingTests.register(event);
    }

    @SubscribeEvent
    static void registerCapabilities(RegisterCapabilitiesEvent event) {
        if (!isGameTestRun())
            return;
        FluidTankTests.registerCapabilities(event);
        PipeTests.registerTestCapabilities(event);
        EngineTests.registerCapabilities(event);
        PowerRoutingTests.registerCapabilities(event);
        FluidRoutingTests.registerCapabilities(event);
    }

    @SubscribeEvent
    static void registerTests(RegisterGameTestsEvent event) {
        if (!isGameTestRun())
            return;
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
        event.registerTest(id("gate_items_and_fitting"), new FunctionGameTestInstance(GATE_ITEMS_AND_FITTING,
                new TestData<>(environment, EMPTY_STRUCTURE, 40, 0, true)));
        event.registerTest(id("gate_logic_kinds"), new FunctionGameTestInstance(GATE_LOGIC_KINDS,
                new TestData<>(environment, EMPTY_STRUCTURE, 60, 0, true)));
        event.registerTest(id("gate_wire_signals"), new FunctionGameTestInstance(GATE_WIRE_SIGNALS,
                new TestData<>(environment, EMPTY_STRUCTURE, 100, 0, true)));
        event.registerTest(id("gate_pipe_contents"), new FunctionGameTestInstance(GATE_PIPE_CONTENTS,
                new TestData<>(environment, EMPTY_STRUCTURE, 20, 0, true)));
        event.registerTest(id("gate_module_actions_and_pulser"), new FunctionGameTestInstance(GATE_MODULE_ACTIONS_AND_PULSER,
                new TestData<>(environment, EMPTY_STRUCTURE, 20, 0, true)));
        event.registerTest(id("gate_menu"), new FunctionGameTestInstance(GATE_MENU,
                new TestData<>(environment, EMPTY_STRUCTURE, 40, 0, true)));
        event.registerTest(ENGINE_HEAT_AND_STAGES.identifier(), new FunctionGameTestInstance(ENGINE_HEAT_AND_STAGES,
                new TestData<>(environment, EMPTY_STRUCTURE, 40, 0, true)));
        for (ResourceKey<Consumer<GameTestHelper>> engine : List.of(ENGINE_PUSHES_INTO_RECEPTOR, ENGINE_PUSHES_INTO_FE_AND_ENGINES,
                ENGINE_ORIENTATION, ENGINE_RENDER_STATE)) {
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
        for (ResourceKey<Consumer<GameTestHelper>> refinery : List.of(REFINERY_OIL_TO_FUEL, REFINERY_FILTERS_AND_BLOCKED,
                REFINERY_RECIPES)) {
            event.registerTest(refinery.identifier(), new FunctionGameTestInstance(refinery,
                    new TestData<>(environment, EMPTY_STRUCTURE, 100, 0, true)));
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
        // #55 modules: lines of pipes and chests, in the padded area like item routing.
        for (String name : ModuleTests.TESTS.keySet()) {
            event.registerTest(id(name), new FunctionGameTestInstance(function(name),
                    padded(environment, Level.OVERWORLD, ModuleTests.MAX_TICKS)));
        }
        // #56 and #57 modules: lines of pipes, chests and item entities, in the padded area like item routing.
        for (String name : RoutingModuleTests.TESTS.keySet()) {
            event.registerTest(id(name), new FunctionGameTestInstance(function(name),
                    padded(environment, Level.OVERWORLD, RoutingModuleTests.MAX_TICKS)));
        }
        // #63 blocked output: a machine, pipes and chests, in the padded area like item routing.
        for (String name : BlockedOutputTests.TESTS.keySet()) {
            event.registerTest(id(name), new FunctionGameTestInstance(function(name),
                    padded(environment, Level.OVERWORLD, BlockedOutputTests.MAX_TICKS)));
        }
        // #66 mining well: digs columns down to a bedrock floor, in the padded area.
        for (String name : MiningWellTests.TESTS.keySet()) {
            event.registerTest(id(name), new FunctionGameTestInstance(function(name),
                    padded(environment, Level.OVERWORLD, MiningWellTests.MAX_TICKS)));
        }
        // Engine wrench, engine Reason line and quarry power faces, in the padded area.
        for (String name : EngineWrenchTests.TESTS.keySet()) {
            event.registerTest(id(name), new FunctionGameTestInstance(function(name),
                    padded(environment, Level.OVERWORLD, EngineWrenchTests.MAX_TICKS)));
        }
        // #130 seams: machines with pipes, chests and engines, in the padded area.
        for (String name : SeamTests.TESTS.keySet()) {
            event.registerTest(id(name), new FunctionGameTestInstance(function(name),
                    padded(environment, Level.OVERWORLD, SeamTests.MAX_TICKS)));
        }
        // #59 flow records: running lines watched for 300 ticks, in the padded area like item routing.
        for (String name : FlowRecordTests.TESTS.keySet()) {
            event.registerTest(id(name), new FunctionGameTestInstance(function(name),
                    padded(environment, Level.OVERWORLD, FlowRecordTests.MAX_TICKS)));
        }
        // #67 quarry: frames up to 11 x 5 x 11 on a bedrock floor, in the padded area.
        for (String name : QuarryTests.TESTS.keySet()) {
            event.registerTest(id(name), new FunctionGameTestInstance(function(name),
                    padded(environment, Level.OVERWORLD, QuarryTests.MAX_TICKS)));
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
        for (ResourceKey<Consumer<GameTestHelper>> fluid : List.of(FLUID_ROUTING_TANK_TO_TANK, FLUID_ROUTING_SPLITTING,
                FLUID_ROUTING_PUSH_AND_VOID, FLUID_ROUTING_MIXING_REFUSAL)) {
            event.registerTest(fluid.identifier(), new FunctionGameTestInstance(fluid,
                    padded(environment, Level.OVERWORLD, FluidRoutingTests.MAX_TICKS)));
        }
        for (PipeMaterial material : PipeMaterial.values()) {
            event.registerTest(fluidCaps(material).identifier(), new FunctionGameTestInstance(fluidCaps(material),
                    padded(environment, Level.OVERWORLD, FluidRoutingTests.WINDOW_END + 20)));
        }
        // #37 and #38: engines with redstone blocks and neighbours a few blocks apart, in the padded area.
        for (ResourceKey<Consumer<GameTestHelper>> engine : List.of(ENGINE_WOOD_REDSTONE_OUTPUT, ENGINE_STONE_BURNS_FUEL,
                ENGINE_STONE_OUTPUT_CONTROL, ENGINE_STONE_MENU, ENGINE_DELIVERY_THROUGH_KINESIS_PIPE, ENGINE_IRON_BURNS_FUEL,
                ENGINE_IRON_COOLING, ENGINE_IRON_OVERHEAT, ENGINE_OVERHEAT_CONFIG, ENGINE_IRON_MENU)) {
            event.registerTest(engine.identifier(), new FunctionGameTestInstance(engine, padded(environment, Level.OVERWORLD, 400)));
        }
        // #65 pump and flood gate: pools and holes built in the padded area.
        event.registerTest(PUMP_SMALL_POOL.identifier(), new FunctionGameTestInstance(PUMP_SMALL_POOL,
                padded(environment, Level.OVERWORLD, PumpTests.POOL_TICKS)));
        event.registerTest(PUMP_OIL.identifier(), new FunctionGameTestInstance(PUMP_OIL,
                padded(environment, Level.OVERWORLD, PumpTests.OIL_TICKS)));
        event.registerTest(FLOOD_GATE_FILLS_HOLE.identifier(), new FunctionGameTestInstance(FLOOD_GATE_FILLS_HOLE,
                padded(environment, Level.OVERWORLD, PumpTests.FLOOD_TICKS)));
    }

    private static ResourceKey<Consumer<GameTestHelper>> powerCaps(PipeMaterial material) {
        return function("power_routing_caps_" + material.getSerializedName());
    }

    private static ResourceKey<Consumer<GameTestHelper>> fluidCaps(PipeMaterial material) {
        return function("fluid_routing_caps_" + material.getSerializedName());
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
        if (!isGameTestRun())
            return;
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
