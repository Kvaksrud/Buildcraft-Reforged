/*
 * Buildcraft Reforged, 2026: new file, no 4.2.2 original. GameTests for the engine gate triggers and the engine's
 * comparator output (#41).
 */
package buildcraftreforged.gametest;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.api.gates.ActionManager;
import buildcraftreforged.api.gates.ITrigger;
import buildcraftreforged.api.transport.IPipeTile.PipeType;
import buildcraftreforged.core.CoreRegistry;
import buildcraftreforged.energy.BlockEngine;
import buildcraftreforged.energy.EnergyRegistry;
import buildcraftreforged.energy.TileEngine;
import buildcraftreforged.energy.TileEngine.EnergyStage;
import buildcraftreforged.energy.TileEngineIron;
import buildcraftreforged.energy.TileEngineStone;
import buildcraftreforged.energy.TileEngineWood;
import buildcraftreforged.gametest.EngineTests.TestEngine;
import buildcraftreforged.transport.Gate;
import buildcraftreforged.transport.Gate.GateKind;
import buildcraftreforged.transport.PipeGateHolder;
import buildcraftreforged.transport.PipeMaterial;
import buildcraftreforged.transport.TilePipe;
import buildcraftreforged.transport.TransportRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * The four engine heat triggers (4.2.2 TriggerEngineHeat), the Engine Overheating trigger and the comparator output
 * (plan section 9), each read through a gate on a pipe next to the engine; the Stirling and combustion engines add
 * their inventory and fluid triggers as 4.2.2.
 */
final class EngineTriggerTests {
    static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();
    static final int MAX_TICKS = 200;

    private static final BlockPos ENGINE = new BlockPos(2, 2, 2);
    /** The gate pipe, east of the engine. */
    private static final BlockPos PIPE = ENGINE.east();
    /** The comparator, north of the engine and facing it (a diode reads the block its FACING points at). */
    private static final BlockPos COMPARATOR = ENGINE.north();

    static {
        TESTS.put("engine_heat_triggers", EngineTriggerTests::heatTriggers);
        TESTS.put("engine_overheating_trigger", EngineTriggerTests::overheatingTrigger);
        TESTS.put("engine_comparator_output", EngineTriggerTests::comparatorOutput);
        TESTS.put("engine_inventory_fluid_triggers", EngineTriggerTests::inventoryAndFluidTriggers);
    }

    private EngineTriggerTests() {
    }

    private static List<ITrigger> heatTriggers() {
        return List.of(EnergyRegistry.triggerBlueEngineHeat.get(), EnergyRegistry.triggerGreenEngineHeat.get(),
                EnergyRegistry.triggerYellowEngineHeat.get(), EnergyRegistry.triggerRedEngineHeat.get());
    }

    private static Gate gateBeside(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, TransportRegistry.getPipe(PipeType.ITEM, PipeMaterial.STONE).defaultBlockState());
        PipeGateHolder holder = helper.getBlockEntity(pos, TilePipe.class).gateHolder;
        holder.addOrStripGate(null, new ItemStack(TransportRegistry.getGateItem(GateKind.AND_3, false)), Vec3.ZERO);
        helper.assertTrue(holder.gate != null, "no gate fitted at " + pos);
        return holder.gate;
    }

    private static <T extends TileEngine> T placeEngine(GameTestHelper helper, Block block, BlockPos pos, Class<T> type) {
        helper.setBlock(pos, Blocks.AIR);
        helper.setBlock(pos, block.defaultBlockState().setValue(BlockEngine.FACING, Direction.UP));
        return helper.getBlockEntity(pos, type);
    }

    private static TestEngine placeTestEngine(GameTestHelper helper, int overheatTicks) {
        TestEngine engine = EngineTests.placeEngine(helper, ENGINE, Direction.UP);
        engine.overheatTicks = overheatTicks;
        engine.explode = false;
        return engine;
    }

    /** What a gate's GUI offers for the engine: ActionManager's neighbour triggers, cut to the ones this test knows. */
    private static List<ITrigger> offered(GameTestHelper helper, TileEngine engine, List<ITrigger> known) {
        Block block = helper.getLevel().getBlockState(engine.getBlockPos()).getBlock();
        return ActionManager.getNeighborTriggers(block, engine).stream().filter(known::contains).toList();
    }

    private static List<ITrigger> allKnown() {
        List<ITrigger> known = new ArrayList<>(heatTriggers());
        known.add(EnergyRegistry.triggerEngineOverheating.get());
        known.addAll(List.of(CoreRegistry.triggerEmptyInventory.get(), CoreRegistry.triggerContainsInventory.get(),
                CoreRegistry.triggerSpaceInventory.get(), CoreRegistry.triggerFullInventory.get(),
                CoreRegistry.triggerEmptyFluid.get(), CoreRegistry.triggerContainsFluid.get(),
                CoreRegistry.triggerSpaceFluid.get(), CoreRegistry.triggerFullFluid.get()));
        return known;
    }

    private static void placeComparator(GameTestHelper helper) {
        helper.setBlock(COMPARATOR.below(), Blocks.STONE);
        helper.setBlock(COMPARATOR, Blocks.COMPARATOR.defaultBlockState().setValue(ComparatorBlock.FACING, Direction.SOUTH));
    }

    private static int comparator(GameTestHelper helper) {
        return helper.getBlockEntity(COMPARATOR, ComparatorBlockEntity.class).getOutputSignal();
    }

    private static byte @Nullable [] resource(String path) {
        try (InputStream in = BuildcraftReforged.class.getResourceAsStream("/assets/" + BuildcraftReforged.MOD_ID + "/" + path)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Each heat trigger is true in its own stage only (4.2.2: getEnergyStage() == stage; the stage thresholds are
     * fractions of the heat range, so FE × 10 leaves them as they were); a gate offers the four and Engine
     * Overheating; tags, names and sprites.
     */
    static void heatTriggers(GameTestHelper helper) {
        TestEngine engine = placeTestEngine(helper, 10000);
        Gate gate = gateBeside(helper, PIPE);

        List<ITrigger> expectedOffer = new ArrayList<>(heatTriggers());
        expectedOffer.add(EnergyRegistry.triggerEngineOverheating.get());
        helper.assertValueEqual(offered(helper, engine, allKnown()), expectedOffer, "triggers offered next to an engine");

        float[] energies = {100, 300, 600, 800};
        EnergyStage[] stages = {EnergyStage.BLUE, EnergyStage.GREEN, EnergyStage.YELLOW, EnergyStage.RED};
        for (int i = 0; i < energies.length; i++) {
            engine.energy = energies[i];
            engine.updateHeatLevel();
            for (int t = 0; t < 4; t++) {
                ITrigger trigger = heatTriggers().get(t);
                helper.assertValueEqual(gate.isNearbyTriggerActive(trigger, null), t == i,
                        trigger.getUniqueTag() + " in stage " + stages[i]);
            }
            helper.assertValueEqual(engine.getEnergyStage(), stages[i], "stage at " + energies[i] + " FE");
            // Nothing has ticked: no timer yet, even in red.
            helper.assertFalse(gate.isNearbyTriggerActive(EnergyRegistry.triggerEngineOverheating.get(), null),
                    "Engine Overheating before the timer runs, at " + energies[i] + " FE");
        }

        String[] colours = {"blue", "green", "yellow", "red"};
        for (int t = 0; t < 4; t++) {
            ITrigger trigger = heatTriggers().get(t);
            helper.assertValueEqual(trigger.getUniqueTag(), "buildcraft.engine.stage." + colours[t], "4.2.2 tag");
            Identifier icon = trigger.getIcon();
            helper.assertValueEqual(icon, Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID,
                    "triggers/trigger_engineheat_" + colours[t]), "sprite of " + colours[t]);
            helper.assertTrue(resource("textures/gui/sprites/" + icon.getPath() + ".png") != null, "missing sprite " + icon);
            helper.assertFalse(trigger.hasParameter(), "heat triggers take no parameter");
        }
        ITrigger overheating = EnergyRegistry.triggerEngineOverheating.get();
        helper.assertValueEqual(overheating.getUniqueTag(), "buildcraft.engine.overheating", "Engine Overheating tag");
        helper.assertTrue(resource("textures/gui/sprites/" + overheating.getIcon().getPath() + ".png") != null,
                "missing sprite " + overheating.getIcon());
        helper.assertValueEqual(ActionManager.triggers.get("buildcraft.engine.stage.red"), EnergyRegistry.triggerRedEngineHeat.get(),
                "red heat trigger by its 4.2.2 tag");

        byte[] lang = resource("lang/en_us.json");
        helper.assertTrue(lang != null, "no en_us.json");
        String text = new String(lang, StandardCharsets.UTF_8);
        for (String key : new String[] {"gate.engine.blue", "gate.engine.green", "gate.engine.yellow", "gate.engine.red",
                "gate.engine.overheating"}) {
            helper.assertTrue(text.contains("\"" + key + "\""), "no translation for " + key);
        }
        helper.succeed();
    }

    /**
     * Engine Overheating is true while the timer runs and while the engine waits shut down for the wrench; the red
     * trigger is true while the timer runs and false once shut down; the wrench restart clears both.
     */
    static void overheatingTrigger(GameTestHelper helper) {
        TestEngine engine = placeTestEngine(helper, 40);
        Gate gate = gateBeside(helper, PIPE);
        ITrigger overheating = EnergyRegistry.triggerEngineOverheating.get();
        ITrigger red = EnergyRegistry.triggerRedEngineHeat.get();
        engine.energy = 1000;
        engine.output = 50;
        helper.setBlock(ENGINE.below(), Blocks.REDSTONE_BLOCK);
        helper.assertFalse(gate.isNearbyTriggerActive(overheating, null), "Engine Overheating before any tick");

        helper.runAfterDelay(5, () -> {
            helper.assertTrue(engine.overheatTimer > 0, "the overheat timer did not start");
            helper.assertTrue(gate.isNearbyTriggerActive(overheating, null), "Engine Overheating while the timer runs");
            helper.assertTrue(gate.isNearbyTriggerActive(red, null), "Engine Red while the timer runs");
        });
        helper.runAfterDelay(60, () -> {
            helper.assertValueEqual(engine.energyStage, EnergyStage.OVERHEAT, "stage after the timer ran out");
            helper.assertTrue(gate.isNearbyTriggerActive(overheating, null), "Engine Overheating while shut down");
            for (ITrigger trigger : heatTriggers())
                helper.assertFalse(gate.isNearbyTriggerActive(trigger, null), trigger.getUniqueTag() + " while shut down");

            helper.setBlock(ENGINE.below(), Blocks.AIR);
            engine.output = 0;
            engine.energy = 0;
            engine.updateHeatLevel();
            helper.assertTrue(engine.restartAfterOverheat(), "the restart failed");
            helper.assertFalse(gate.isNearbyTriggerActive(overheating, null), "Engine Overheating after the restart");
            helper.assertTrue(gate.isNearbyTriggerActive(EnergyRegistry.triggerBlueEngineHeat.get(), null),
                    "Engine Blue after the restart");
            helper.succeed();
        });
    }

    /**
     * A vanilla comparator facing the engine reads its heat: 0 cold, 7 at half heat, 11 late in yellow, 15 from the
     * moment the overheat timer starts and while shut down. An engine that never overheats tops out at 14.
     */
    static void comparatorOutput(GameTestHelper helper) {
        TestEngine engine = placeTestEngine(helper, 60);
        placeComparator(helper);
        // Powered with no output: the stored energy, and so the heat, stays where the test puts it.
        helper.setBlock(ENGINE.below(), Blocks.REDSTONE_BLOCK);
        helper.assertTrue(engine.getBlockState().hasAnalogOutputSignal(), "the engine has no comparator output");

        int[][] steps = {{0, 0}, {500, 7}, {740, 11}, {1000, 15}};
        for (int i = 0; i < steps.length; i++) {
            int energy = steps[i][0];
            int expected = steps[i][1];
            helper.runAfterDelay(10L * i + 1, () -> engine.energy = energy);
            helper.runAfterDelay(10L * i + 8, () -> {
                helper.assertValueEqual(engine.calculateComparatorInputOverride(), expected, "level at " + energy + " FE");
                helper.assertValueEqual(comparator(helper), expected, "comparator at " + energy + " FE");
            });
        }
        // The 60-tick timer started at about tick 32 and ran out at about tick 92: shut down, still 15.
        helper.runAfterDelay(110, () -> {
            helper.assertValueEqual(engine.energyStage, EnergyStage.OVERHEAT, "stage after the timer ran out");
            helper.assertValueEqual(comparator(helper), 15, "comparator while shut down");
            helper.setBlock(ENGINE.below(), Blocks.AIR);
            engine.energy = 0;
            engine.updateHeatLevel();
            engine.restartAfterOverheat();
        });
        helper.runAfterDelay(120, () -> {
            helper.assertValueEqual(comparator(helper), 0, "comparator after the restart");

            TileEngineWood wood = placeEngine(helper, EnergyRegistry.engineWood.block().get(), ENGINE.east(4), TileEngineWood.class);
            wood.energy = wood.getMaxEnergy();
            wood.updateHeatLevel();
            helper.assertValueEqual(wood.getEnergyStage(), EnergyStage.RED, "full redstone engine stage");
            helper.assertValueEqual(wood.calculateComparatorInputOverride(), 14, "full redstone engine level");
            helper.succeed();
        });
    }

    /**
     * 4.2.2 getTriggers: the Stirling engine adds the four inventory triggers, the combustion engine the four fluid
     * triggers, both after the heat triggers and Engine Overheating; the redstone engine offers the heat triggers only.
     */
    static void inventoryAndFluidTriggers(GameTestHelper helper) {
        List<ITrigger> heatAndAlarm = new ArrayList<>(heatTriggers());
        heatAndAlarm.add(EnergyRegistry.triggerEngineOverheating.get());

        TileEngineWood wood = placeEngine(helper, EnergyRegistry.engineWood.block().get(), new BlockPos(1, 2, 1), TileEngineWood.class);
        helper.assertValueEqual(offered(helper, wood, allKnown()), heatTriggers(), "redstone engine triggers");

        BlockPos stonePos = new BlockPos(1, 2, 4);
        TileEngineStone stone = placeEngine(helper, EnergyRegistry.engineStone.block().get(), stonePos, TileEngineStone.class);
        Gate stoneGate = gateBeside(helper, stonePos.east());
        List<ITrigger> stoneOffer = new ArrayList<>(heatAndAlarm);
        stoneOffer.addAll(List.of(CoreRegistry.triggerEmptyInventory.get(), CoreRegistry.triggerContainsInventory.get(),
                CoreRegistry.triggerSpaceInventory.get(), CoreRegistry.triggerFullInventory.get()));
        helper.assertValueEqual(offered(helper, stone, allKnown()), stoneOffer, "Stirling engine triggers");
        helper.assertTrue(stoneGate.isNearbyTriggerActive(CoreRegistry.triggerEmptyInventory.get(), null), "Inventory Empty");
        helper.assertFalse(stoneGate.isNearbyTriggerActive(CoreRegistry.triggerContainsInventory.get(), null),
                "Inventory Contains while empty");
        stone.setItem(0, new ItemStack(Items.COAL, 64));
        helper.assertFalse(stoneGate.isNearbyTriggerActive(CoreRegistry.triggerEmptyInventory.get(), null),
                "Inventory Empty with coal");
        helper.assertTrue(stoneGate.isNearbyTriggerActive(CoreRegistry.triggerContainsInventory.get(), null),
                "Inventory Contains with coal");
        helper.assertTrue(stoneGate.isNearbyTriggerActive(CoreRegistry.triggerFullInventory.get(), null),
                "Inventory Full with 64 coal");
        helper.assertTrue(stoneGate.isNearbyTriggerActive(EnergyRegistry.triggerBlueEngineHeat.get(), null),
                "Engine Blue on a cold Stirling engine");

        BlockPos ironPos = new BlockPos(1, 2, 7);
        TileEngineIron iron = placeEngine(helper, EnergyRegistry.engineIron.block().get(), ironPos, TileEngineIron.class);
        Gate ironGate = gateBeside(helper, ironPos.east());
        List<ITrigger> ironOffer = new ArrayList<>(heatAndAlarm);
        ironOffer.addAll(List.of(CoreRegistry.triggerEmptyFluid.get(), CoreRegistry.triggerContainsFluid.get(),
                CoreRegistry.triggerSpaceFluid.get(), CoreRegistry.triggerFullFluid.get()));
        helper.assertValueEqual(offered(helper, iron, allKnown()), ironOffer, "combustion engine triggers");
        helper.assertTrue(ironGate.isNearbyTriggerActive(CoreRegistry.triggerEmptyFluid.get(), null), "Tank Empty");
        ResourceHandler<FluidResource> tanks = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK,
                helper.absolutePos(ironPos), Direction.EAST);
        helper.assertTrue(tanks != null, "no fluid handler on the combustion engine");
        try (Transaction tx = Transaction.openRoot()) {
            tanks.insert(FluidResource.of(Fluids.WATER), 1000, tx);
            tx.commit();
        }
        helper.assertFalse(ironGate.isNearbyTriggerActive(CoreRegistry.triggerEmptyFluid.get(), null), "Tank Empty with water");
        helper.assertTrue(ironGate.isNearbyTriggerActive(CoreRegistry.triggerContainsFluid.get(), null),
                "Tank Contains with water");
        helper.succeed();
    }
}
