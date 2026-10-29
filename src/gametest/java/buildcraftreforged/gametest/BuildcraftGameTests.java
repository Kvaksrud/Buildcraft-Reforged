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
    private static final ResourceKey<Consumer<GameTestHelper>> TRIGGER_REGISTRIES = function("trigger_registries");
    private static final ResourceKey<Consumer<GameTestHelper>> TRIGGER_MACHINE = function("trigger_machine");
    private static final ResourceKey<Consumer<GameTestHelper>> TRIGGER_INVENTORY = function("trigger_inventory");
    private static final ResourceKey<Consumer<GameTestHelper>> TRIGGER_FLUID = function("trigger_fluid");

    private BuildcraftGameTests() {
    }

    @SubscribeEvent
    static void registerFunctions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, registry -> {
            registry.register(NO_SOCKETS, NoSocketsTest::noSockets);
            registry.register(WRENCH_ROTATES_BLOCK, WrenchTests::wrenchRotatesBlock);
            registry.register(WRENCH_TAG, WrenchTests::wrenchTag);
            registry.register(TRIGGER_REGISTRIES, TriggerTests::registries);
            registry.register(TRIGGER_MACHINE, TriggerTests::machine);
            registry.register(TRIGGER_INVENTORY, TriggerTests::inventory);
            registry.register(TRIGGER_FLUID, TriggerTests::fluid);
        });
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
        for (ResourceKey<Consumer<GameTestHelper>> trigger : List.of(TRIGGER_REGISTRIES, TRIGGER_MACHINE, TRIGGER_INVENTORY,
                TRIGGER_FLUID)) {
            event.registerTest(trigger.identifier(), new FunctionGameTestInstance(trigger,
                    new TestData<>(environment, EMPTY_STRUCTURE, 20, 0, true)));
        }
    }

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
