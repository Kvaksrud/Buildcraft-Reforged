package buildcraftreforged.gametest;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.core.BuildCraftConfiguration;
import buildcraftreforged.core.CoreRegistry;
import buildcraftreforged.core.RotationHelper;
import buildcraftreforged.core.network.PacketData;
import buildcraftreforged.core.status.HeatStage;
import buildcraftreforged.core.status.MachineStatus;
import buildcraftreforged.energy.BlockEngine;
import buildcraftreforged.energy.EnergyRegistry;
import buildcraftreforged.energy.TileEngine;
import buildcraftreforged.energy.TileEngine.EnergyStage;
import buildcraftreforged.energy.TileEngineIron;
import buildcraftreforged.energy.TileEngineWood;
import buildcraftreforged.energy.gui.ContainerEngine;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * #44 and #40: the combustion engine (engine_iron) burning oil and fuel from its tanks at 4.2.2's rates × 10, cooling
 * with water and ice, its menu and GUI data; the overheat timer, the shut-down mode with its wrench restart, and the
 * engines.* config switch.
 */
final class CombustionEngineTests {
    private static final BlockPos ENGINE = new BlockPos(2, 2, 2);
    /** 4.2.2 water: 0.0023 °C per mB; 40 mB a tick at most. */
    private static final float WATER_COOLING = 0.0023F;

    private CombustionEngineTests() {
    }

    /**
     * Oil and fuel go into the fuel tank, water into the coolant tank, nothing else in. Powered, the engine burns one mB
     * of oil every 5 ticks (5 000 ticks a bucket) and makes 30 FE a tick (4.2.2: 3 MJ), fuel 60 FE; unpowered it burns
     * nothing. A water bucket in the slot fills the coolant tank and leaves its bucket; ice (2 000 mB of water) goes in
     * only above 40 °C. An empty powered engine is OUT_OF_FUEL; the tanks are saved.
     */
    static void burnsFuel(GameTestHelper helper) {
        TileEngineIron engine = place(helper, ENGINE);
        helper.assertValueEqual(engine.getMaxEnergy(), 100000F, "max energy");
        helper.assertValueEqual(engine.maxEnergyExtracted(), 5000F, "pulse");
        helper.assertValueEqual(engine.maxEnergyReceived(), 20000F, "intake");
        helper.assertValueEqual(engine.explosionRange(), 4F, "explosion range");
        helper.assertValueEqual(engine.getTextureFile(), TileEngine.IRON_TEXTURE, "texture");

        helper.assertValueEqual(insert(helper, ENGINE, EnergyRegistry.fluidOil.get(), 1000), 1000, "oil inserted");
        helper.assertValueEqual(insert(helper, ENGINE, Fluids.LAVA, 1000), 0, "lava inserted");
        helper.assertValueEqual(insert(helper, ENGINE, Fluids.WATER, 500), 500, "water inserted");
        helper.assertValueEqual(engine.getFuel().getAmount(), 1000, "fuel tank");
        helper.assertTrue(engine.getFuel().getFluid() == EnergyRegistry.fluidOil.get(), "oil is not in the fuel tank");
        helper.assertValueEqual(engine.getCoolant().getAmount(), 500, "coolant tank");
        try (Transaction tx = Transaction.openRoot()) {
            helper.assertValueEqual(engine.insert(1, FluidResource.of(EnergyRegistry.fluidOil.get()), 100, tx), 0,
                    "oil into the coolant tank");
            helper.assertValueEqual(engine.insert(0, FluidResource.of(Fluids.WATER), 100, tx), 0, "water into the fuel tank");
        }
        helper.assertTrue(engine.canPlaceItem(0, new ItemStack(Items.WATER_BUCKET)), "a water bucket is refused");
        helper.assertTrue(engine.canPlaceItem(0, new ItemStack(Items.ICE)), "ice is refused");
        helper.assertFalse(engine.canPlaceItem(0, new ItemStack(Items.DIRT)), "dirt is accepted");
        engine.setItem(0, new ItemStack(Items.WATER_BUCKET));

        BlockPos icePos = ENGINE.east(3);
        TileEngineIron ice = place(helper, icePos);
        ice.setItem(0, new ItemStack(Items.ICE));
        BlockPos warmIcePos = ENGINE.east(6);
        TileEngineIron warmIce = place(helper, warmIcePos);
        warmIce.heat = 60;
        warmIce.setItem(0, new ItemStack(Items.ICE));

        BlockPos fuelPos = ENGINE.south(3);
        TileEngineIron fuel = place(helper, fuelPos);
        insert(helper, fuelPos, EnergyRegistry.fluidFuel.get(), 1000);
        BlockPos emptyPos = ENGINE.south(3).east(3);
        TileEngineIron empty = place(helper, emptyPos);

        long[] start = new long[1];
        helper.runAfterDelay(20, () -> {
            helper.assertValueEqual(engine.getFuel().getAmount(), 1000, "an unpowered engine burnt oil");
            helper.assertValueEqual(engine.energy, 0F, "an unpowered engine made energy");
            helper.assertFalse(engine.isBurning(), "an unpowered engine burns");
            helper.assertValueEqual(engine.getCoolant().getAmount(), 1500, "coolant after the water bucket");
            helper.assertTrue(engine.getItem(0).is(Items.BUCKET), "the water bucket left " + engine.getItem(0));
            helper.assertTrue(ice.getItem(0).is(Items.ICE), "a cold engine took ice");
            helper.assertTrue(warmIce.getItem(0).isEmpty(), "a warm engine did not take ice");
            helper.assertTrue(warmIce.getCoolant().getFluid() == Fluids.WATER && warmIce.getCoolant().getAmount() > 1000,
                    "ice gave " + warmIce.getCoolant().getAmount() + " mB of " + warmIce.getCoolant().getFluid());
            helper.assertValueEqual(empty.getStatusState(), MachineStatus.IDLE, "status of an unpowered empty engine");
            helper.setBlock(ENGINE.below(), Blocks.REDSTONE_BLOCK);
            helper.setBlock(fuelPos.below(), Blocks.REDSTONE_BLOCK);
            helper.setBlock(emptyPos.below(), Blocks.REDSTONE_BLOCK);
            start[0] = helper.getLevel().getGameTime();
        });
        helper.runAfterDelay(22, () -> {
            helper.assertTrue(engine.isBurning(), "a powered engine with oil does not burn");
            helper.assertValueEqual(engine.getStatusState(), MachineStatus.WORKING, "status when burning");
            helper.assertValueEqual(empty.getStatusState(), MachineStatus.OUT_OF_FUEL, "status of a powered empty engine");
        });
        helper.runAfterDelay(120, () -> {
            long ticks = helper.getLevel().getGameTime() - start[0];
            int burnt = 1000 - engine.getFuel().getAmount();
            helper.assertTrue(Math.abs(burnt - ticks / 5F) <= 2, "burnt " + burnt + " mB of oil in " + ticks
                    + " ticks, expected one every 5");
            helper.assertTrue(Math.abs(engine.energy - 30 * ticks) <= 60, "made " + engine.energy + " FE in " + ticks
                    + " ticks, expected 30 a tick");
            helper.assertValueEqual(engine.currentOutput, 30F, "output with oil");
            helper.assertValueEqual(engine.getCurrentOutput(), 30F, "current output with oil");
            helper.assertValueEqual(fuel.getCurrentOutput(), 60F, "current output with fuel");
            int fuelBurnt = 1000 - fuel.getFuel().getAmount();
            helper.assertTrue(fuelBurnt >= 1 && fuelBurnt <= 2 + ticks / 25, "burnt " + fuelBurnt + " mB of fuel in "
                    + ticks + " ticks, expected one every 25");
            float expectedHeat = TileEngine.MIN_HEAT + ticks * 3 * TileEngineIron.HEAT_PER_MJ * tempScalar(helper, ENGINE);
            helper.assertTrue(Math.abs(engine.heat - expectedHeat) <= 0.05F, "heat " + engine.heat + ", expected about "
                    + expectedHeat);
            helper.assertValueEqual(engine.getEnergyStage(), EnergyStage.BLUE, "stage");

            CompoundTag tag = engine.saveWithFullMetadata(helper.getLevel().registryAccess());
            TileEngineIron reloaded = new TileEngineIron(engine.getBlockPos(), engine.getBlockState());
            reloaded.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag));
            helper.assertValueEqual(reloaded.getFuel().getAmount(), engine.getFuel().getAmount(), "fuel after load");
            helper.assertValueEqual(reloaded.getCoolant().getAmount(), engine.getCoolant().getAmount(), "coolant after load");
            helper.assertValueEqual(reloaded.getBurnTime(), engine.getBurnTime(), "burn time after load");
            helper.succeed();
        });
    }

    /**
     * Unpowered, an engine cools 0.05 °C a tick, and water takes 40 mB a tick more at 0.0023 °C per mB (both divided by
     * the biome's temperature scalar). Powered above 100 °C, water brings it down to 100 °C; at 100 °C the water takes
     * just what the burning adds.
     */
    static void cooling(GameTestHelper helper) {
        TileEngineIron dry = place(helper, ENGINE);
        dry.heat = 100;
        BlockPos wetPos = ENGINE.east(3);
        TileEngineIron wet = place(helper, wetPos);
        wet.heat = 100;
        insert(helper, wetPos, Fluids.WATER, 5000);
        BlockPos hotPos = ENGINE.south(3);
        TileEngineIron hot = place(helper, hotPos);
        hot.heat = 150;
        insert(helper, hotPos, Fluids.WATER, 5000);
        insert(helper, hotPos, EnergyRegistry.fluidOil.get(), 1000);
        helper.setBlock(hotPos.below(), Blocks.REDSTONE_BLOCK);
        BlockPos idealPos = ENGINE.south(3).east(3);
        TileEngineIron ideal = place(helper, idealPos);
        ideal.heat = TileEngine.IDEAL_HEAT;
        insert(helper, idealPos, Fluids.WATER, 5000);
        insert(helper, idealPos, EnergyRegistry.fluidOil.get(), 1000);
        helper.setBlock(idealPos.below(), Blocks.REDSTONE_BLOCK);

        long start = helper.getLevel().getGameTime();
        helper.runAfterDelay(40, () -> {
            long ticks = helper.getLevel().getGameTime() - start;
            float scalar = tempScalar(helper, ENGINE);

            assertBetween(helper, 100 - dry.heat, 0.05F * (ticks - 2), 0.05F * (ticks + 1), "cooling without water");
            helper.assertTrue(dry.getCoolant().isEmpty(), "coolant appeared");

            int drained = 5000 - wet.getCoolant().getAmount();
            assertBetween(helper, drained, 40 * (ticks - 2), 40 * (ticks + 1), "water used while unpowered");
            float wetPerTick = 0.05F + 40 * WATER_COOLING / scalar;
            assertBetween(helper, 100 - wet.heat, wetPerTick * (ticks - 2), wetPerTick * (ticks + 1), "cooling with water");

            helper.assertTrue(hot.heat < 150 && hot.heat > TileEngine.IDEAL_HEAT, "a hot engine is at " + hot.heat + " °C");
            helper.assertTrue(5000 - hot.getCoolant().getAmount() >= 40 * (ticks - 2), "a hot engine used only "
                    + (5000 - hot.getCoolant().getAmount()) + " mB of water");

            assertBetween(helper, ideal.heat, TileEngine.IDEAL_HEAT, TileEngine.IDEAL_HEAT + 0.01F, "heat at 100 °C");
            int idealDrained = 5000 - ideal.getCoolant().getAmount();
            // 0.0069 °C a tick of heating over 0.0023 °C per mB of cooling, both scaled: 3 × scalar² mB a tick, rounded.
            float perTick = 3 * scalar * scalar;
            assertBetween(helper, idealDrained, Math.round(perTick) * (ticks - 2) - 2, (Math.round(perTick) + 1) * (ticks + 1),
                    "water used at 100 °C");
            helper.succeed();
        });
    }

    /**
     * In its red stage the engine starts the overheat timer (engines.overheatTimerSeconds) and keeps burning; the status
     * panel shows Overheating and the seconds left, and the client gets the timer. Water that brings it below red stops
     * the timer. When a timer runs out with engines.explode off the engine shuts down: no fuel burns, the energy bleeds
     * away, it cools as an unpowered engine, it is saved so, and the wrench restarts it.
     */
    static void overheat(GameTestHelper helper) {
        TileEngineIron engine = place(helper, ENGINE);
        insert(helper, ENGINE, EnergyRegistry.fluidOil.get(), 2000);
        engine.heat = 200;
        helper.setBlock(ENGINE.below(), Blocks.REDSTONE_BLOCK);

        BlockPos cooledPos = ENGINE.east(3);
        TileEngineIron cooled = place(helper, cooledPos);
        insert(helper, cooledPos, EnergyRegistry.fluidOil.get(), 2000);
        cooled.heat = 193;
        helper.setBlock(cooledPos.below(), Blocks.REDSTONE_BLOCK);

        int[] fuel = new int[1];
        float[] heat = new float[2];
        helper.runAfterDelay(5, () -> {
            int total = BuildCraftConfiguration.engineOverheatTimerSeconds.get() * 20;
            helper.assertValueEqual(engine.getEnergyStage(), EnergyStage.RED, "stage at 200 °C");
            helper.assertValueEqual(engine.overheatTimerTotal, total, "timer length");
            assertBetween(helper, engine.overheatTimer, total - 6, total - 2, "timer");
            helper.assertValueEqual(engine.getStatusState(), MachineStatus.OVERHEATING, "status while the timer runs");
            helper.assertValueEqual(engine.getStatusHeat().stage(), HeatStage.RED, "status heat stage");
            helper.assertValueEqual(engine.getStatusHeat().overheatSeconds(), (engine.overheatTimer + 19) / 20, "seconds left");
            helper.assertTrue(engine.isBurning(), "an engine on its overheat timer stopped burning");
            helper.assertValueEqual(cooled.getEnergyStage(), EnergyStage.RED, "stage at 193 °C");
            helper.assertTrue(cooled.overheatTimer > 0, "the timer did not start at 193 °C");

            TileEngineIron client = clientCopy(helper, engine);
            helper.assertValueEqual(client.overheatTimer, engine.overheatTimer, "synced timer");
            helper.assertValueEqual(client.overheatTimerTotal, total, "synced timer length");

            // Water cools the other one below red, which stops its timer.
            insert(helper, cooledPos, Fluids.WATER, 5000);
        });
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(cooled.heat < 192.5F, "water left the engine at " + cooled.heat + " °C");
            helper.assertValueEqual(cooled.getEnergyStage(), EnergyStage.YELLOW, "stage after cooling");
            helper.assertValueEqual(cooled.overheatTimer, -1, "the timer kept running below red");
            helper.assertValueEqual(cooled.getStatusHeat().overheatSeconds(), -1, "countdown below red");

            // The timer runs out with engines.explode off.
            boolean explode = BuildCraftConfiguration.engineExplode.get();
            try {
                BuildCraftConfiguration.engineExplode.set(false);
                engine.overheatTimer = 1;
                engine.updateEntity();
            } finally {
                BuildCraftConfiguration.engineExplode.set(explode);
            }
            helper.assertBlockPresent(EnergyRegistry.engineIron.block().get(), ENGINE);
            helper.assertValueEqual(engine.energyStage, EnergyStage.OVERHEAT, "stage after the timer");
            helper.assertValueEqual(engine.overheatTimer, -1, "timer after it ran out");
            helper.assertValueEqual(engine.getStatusState(), MachineStatus.OVERHEATING, "status when shut down");
            helper.assertValueEqual(engine.getStatusHeat().stage(), HeatStage.OVERHEAT, "status heat when shut down");
            engine.energy = 500;
            fuel[0] = engine.getFuel().getAmount();
            heat[0] = engine.heat;
        });
        helper.runAfterDelay(60, () -> {
            helper.assertValueEqual(engine.energyStage, EnergyStage.OVERHEAT, "a shut-down engine restarted by itself");
            helper.assertValueEqual(engine.getFuel().getAmount(), fuel[0], "a shut-down engine burnt fuel");
            helper.assertFalse(engine.isPumping, "a shut-down engine pumps");
            helper.assertValueEqual(engine.energy, 0F, "the energy of a shut-down engine");
            assertBetween(helper, heat[0] - engine.heat, 0.05F * 18, 0.05F * 21, "cooling while shut down");

            CompoundTag tag = engine.saveWithFullMetadata(helper.getLevel().registryAccess());
            TileEngineIron reloaded = new TileEngineIron(engine.getBlockPos(), engine.getBlockState());
            reloaded.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag));
            helper.assertValueEqual(reloaded.energyStage, EnergyStage.OVERHEAT, "shut down after load");

            // The wrench restarts it, facing as before.
            helper.assertTrue(RotationHelper.rotateBlock(helper.getLevel(), helper.absolutePos(ENGINE), Direction.UP),
                    "the wrench did nothing");
            helper.assertTrue(engine.energyStage != EnergyStage.OVERHEAT, "the wrench did not restart the engine");
            helper.assertValueEqual(engine.getOrientation(), Direction.UP, "the wrench turned a shut-down engine");
            heat[1] = engine.getFuel().getAmount();
        });
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(engine.getFuel().getAmount() < heat[1], "a restarted engine does not burn");
            helper.succeed();
        });
    }

    /**
     * engines.overheatTimerSeconds sets the timer, engines.explode chooses explosion or shut-down (defaults 300 and
     * true); the redstone engine runs no timer even full.
     */
    static void overheatConfig(GameTestHelper helper) {
        helper.assertValueEqual(BuildCraftConfiguration.engineExplode.getDefault(), true, "engines.explode default");
        helper.assertValueEqual(BuildCraftConfiguration.engineOverheatTimerSeconds.getDefault(), 300,
                "engines.overheatTimerSeconds default");

        EngineTests.TestEngine shutDown = EngineTests.placeEngine(helper, ENGINE, Direction.UP);
        EngineTests.TestEngine exploding = EngineTests.placeEngine(helper, ENGINE.east(3), Direction.UP);
        BlockPos woodPos = ENGINE.south(3);
        helper.setBlock(woodPos, Blocks.AIR);
        helper.setBlock(woodPos, EnergyRegistry.engineWood.block().get().defaultBlockState().setValue(BlockEngine.FACING, Direction.UP));
        TileEngineWood wood = helper.getBlockEntity(woodPos, TileEngineWood.class);
        wood.energy = wood.getMaxEnergy();
        helper.setBlock(woodPos.below(), Blocks.REDSTONE_BLOCK);

        helper.runAfterDelay(2, () -> {
            boolean explode = BuildCraftConfiguration.engineExplode.get();
            int seconds = BuildCraftConfiguration.engineOverheatTimerSeconds.get();
            try {
                BuildCraftConfiguration.engineOverheatTimerSeconds.set(7);
                shutDown.energy = 1000;
                shutDown.updateEntity();
                helper.assertValueEqual(shutDown.energyStage, EnergyStage.RED, "stage of a full engine");
                helper.assertValueEqual(shutDown.overheatTimer, 140, "timer with overheatTimerSeconds = 7");

                BuildCraftConfiguration.engineExplode.set(false);
                shutDown.overheatTimer = 1;
                shutDown.updateEntity();
                helper.assertValueEqual(shutDown.energyStage, EnergyStage.OVERHEAT, "explode = false: shut down");

                BuildCraftConfiguration.engineExplode.set(true);
                exploding.energy = 1000;
                exploding.updateEntity();
                exploding.overheatTimer = 1;
                exploding.updateEntity();
            } finally {
                BuildCraftConfiguration.engineExplode.set(explode);
                BuildCraftConfiguration.engineOverheatTimerSeconds.set(seconds);
            }
            helper.assertBlockPresent(EngineTests.engineBlock, ENGINE);
            helper.assertBlockNotPresent(EngineTests.engineBlock, ENGINE.east(3));
        });
        helper.runAfterDelay(20, () -> {
            helper.assertValueEqual(wood.getEnergyStage(), EnergyStage.RED, "stage of a full redstone engine");
            helper.assertValueEqual(wood.overheatTimer, -1, "the redstone engine ran an overheat timer");
            helper.assertValueEqual(wood.getStatusState(), MachineStatus.WORKING, "status of a full redstone engine");
            helper.succeed();
        });
    }

    /**
     * The combustion engine's menu: one slot at 52,41, the inventory, the status widget and 8 data slots that carry the
     * tanks to the client; the GUI texture; the recipe (iron, glass, iron gears, piston) and the registry id.
     */
    static void menu(GameTestHelper helper) {
        TileEngineIron engine = place(helper, ENGINE);
        insert(helper, ENGINE, EnergyRegistry.fluidFuel.get(), 3210);
        insert(helper, ENGINE, Fluids.WATER, 4321);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.snapTo(Vec3.atBottomCenterOf(helper.absolutePos(ENGINE.above())));
        if (!(engine.createMenu(1, player.getInventory(), player) instanceof ContainerEngine menu)) {
            throw helper.assertionException("the combustion engine made no ContainerEngine");
        }
        helper.assertValueEqual(menu.getType(), EnergyRegistry.engineIronMenu.get(), "menu type");
        helper.assertValueEqual(menu.slots.size(), 1 + 27 + 9, "slots");
        helper.assertValueEqual(menu.slots.get(0).x + "," + menu.slots.get(0).y, "52,41", "coolant slot position");
        helper.assertTrue(menu.stillValid(player), "the menu is not valid for a player next to the engine");
        helper.assertTrue(menu.statusWidget != null, "no status widget");
        helper.assertValueEqual(engine.guiData.getCount(), 8, "data slots");

        TileEngineIron client = new TileEngineIron(engine.getBlockPos(), engine.getBlockState());
        for (int i = 0; i < engine.guiData.getCount(); i++) {
            client.guiData.set(i, (short) engine.guiData.get(i));
        }
        helper.assertTrue(client.getFuel().getFluid() == EnergyRegistry.fluidFuel.get(), "fuel on the client");
        helper.assertValueEqual(client.getFuel().getAmount(), 3210, "fuel amount on the client");
        helper.assertTrue(client.getCoolant().getFluid() == Fluids.WATER, "coolant on the client");
        helper.assertValueEqual(client.getCoolant().getAmount(), 4321, "coolant amount on the client");
        helper.assertValueEqual(client.getScaledBurnTime(58), 18, "fuel gauge");
        helper.assertValueEqual(client.getScaledCoolant(58), 25, "coolant gauge");

        byte[] png = resource(Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "textures/gui/combustion_engine_gui.png"));
        helper.assertTrue(png != null && png.length > 24, "missing GUI texture");
        ByteBuffer header = ByteBuffer.wrap(png, 16, 8);
        helper.assertValueEqual(header.getInt() + "x" + header.getInt(), "256x256", "GUI texture size");

        helper.assertValueEqual(BuiltInRegistries.BLOCK.getKey(EnergyRegistry.engineIron.block().get()).getPath(), "engine_iron",
                "combustion engine id");
        ServerLevel level = helper.getLevel();
        List<Item> grid = List.of(Items.IRON_INGOT, Items.IRON_INGOT, Items.IRON_INGOT, Items.AIR, Items.GLASS, Items.AIR,
                CoreRegistry.ironGearItem.get(), Items.PISTON, CoreRegistry.ironGearItem.get());
        CraftingInput input = CraftingInput.of(3, 3, grid.stream().map(ItemStack::new).toList());
        RecipeHolder<CraftingRecipe> recipe = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level).orElse(null);
        helper.assertTrue(recipe != null, "no combustion engine recipe");
        helper.assertTrue(recipe.value().assemble(input).is(EnergyRegistry.engineIron.item().get()), "the recipe makes "
                + recipe.value().assemble(input));
        helper.succeed();
    }

    private static TileEngineIron place(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, Blocks.AIR);
        helper.setBlock(pos, EnergyRegistry.engineIron.block().get().defaultBlockState().setValue(BlockEngine.FACING, Direction.UP));
        return helper.getBlockEntity(pos, TileEngineIron.class);
    }

    private static int insert(GameTestHelper helper, BlockPos pos, Fluid fluid, int amount) {
        ResourceHandler<FluidResource> handler = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK,
                helper.absolutePos(pos), Direction.NORTH);
        helper.assertTrue(handler != null, "no fluid handler on the combustion engine");
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = handler.insert(FluidResource.of(fluid), amount, tx);
            tx.commit();
            return inserted;
        }
    }

    /** 4.2.2 getBiomeTempScalar. */
    private static float tempScalar(GameTestHelper helper, BlockPos pos) {
        float temperature = helper.getLevel().getBiome(helper.absolutePos(pos)).value().getBaseTemperature();
        return (temperature - 1.0F) * 0.5F + 1.0F;
    }

    private static TileEngineIron clientCopy(GameTestHelper helper, TileEngineIron engine) {
        TileEngineIron client = new TileEngineIron(engine.getBlockPos(), engine.getBlockState());
        byte[] bytes = PacketData.write(helper.getLevel().registryAccess(), engine::writePacketPayload);
        RegistryFriendlyByteBuf data = PacketData.wrap(bytes, helper.getLevel().registryAccess());
        try {
            client.readPacketPayload(data);
        } finally {
            data.release();
        }
        return client;
    }

    private static void assertBetween(GameTestHelper helper, float actual, float min, float max, String what) {
        helper.assertTrue(actual >= min - 1e-3F && actual <= max + 1e-3F, what + ": " + actual + ", expected " + min + " to " + max);
    }

    private static byte[] resource(Identifier id) {
        try (InputStream in = CombustionEngineTests.class.getResourceAsStream("/assets/" + id.getNamespace() + "/" + id.getPath())) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }
}
