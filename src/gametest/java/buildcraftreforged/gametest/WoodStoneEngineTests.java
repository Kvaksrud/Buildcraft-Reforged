package buildcraftreforged.gametest;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.api.transport.IPipeConnection.ConnectOverride;
import buildcraftreforged.api.transport.IPipeTile.PipeType;
import buildcraftreforged.core.CoreRegistry;
import buildcraftreforged.core.status.MachineStatus;
import buildcraftreforged.energy.BlockEngine;
import buildcraftreforged.energy.EnergyRegistry;
import buildcraftreforged.energy.TileEngine;
import buildcraftreforged.energy.TileEngine.EnergyStage;
import buildcraftreforged.energy.TileEngineStone;
import buildcraftreforged.energy.TileEngineWood;
import buildcraftreforged.energy.gui.ContainerEngine;
import buildcraftreforged.transport.PipeMaterial;
import buildcraftreforged.transport.TransportRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;

/**
 * #37 and #38: the Redstone engine (engine_wood) and the Stirling engine (engine_stone) as registered blocks: output on a
 * redstone signal at 4.2.2's rate × 10, solid fuel burning at furnace burn times and the PID output × 10, the Stirling
 * engine's menu, and delivery from both through wood kinesis pipes.
 */
final class WoodStoneEngineTests {
    private static final BlockPos ENGINE = new BlockPos(2, 2, 2);

    private WoodStoneEngineTests() {
    }

    /**
     * Unpowered, the redstone engine makes nothing. Powered, it makes 10 FE every 16 ticks (4.2.2: 1 MJ) and pulses 10
     * FE at a time into a receptor; everything it makes is in the receptor or still in the engine. Started near its
     * working temperature, it delivers about 0.625 FE/t (4.2.2: 0.0625 MJ/t).
     */
    static void woodRedstoneOutput(GameTestHelper helper) {
        BlockPos receptorPos = ENGINE.east();
        helper.setBlock(receptorPos, EngineTests.receptorBlock);
        EngineTests.TestReceptor receptor = helper.getBlockEntity(receptorPos, EngineTests.TestReceptor.class);
        TileEngineWood engine = placeEngine(helper, EnergyRegistry.engineWood.block().get(), ENGINE, Direction.EAST,
                TileEngineWood.class);

        // A warm engine beside it, for the rate.
        BlockPos warmPos = ENGINE.south(3);
        helper.setBlock(warmPos.east(), EngineTests.receptorBlock);
        EngineTests.TestReceptor warmReceptor = helper.getBlockEntity(warmPos.east(), EngineTests.TestReceptor.class);
        TileEngineWood warm = placeEngine(helper, EnergyRegistry.engineWood.block().get(), warmPos, Direction.EAST,
                TileEngineWood.class);

        helper.assertValueEqual(engine.getMaxEnergy(), 1000F, "max energy");
        helper.assertValueEqual(engine.maxEnergyExtracted(), 10F, "pulse");
        helper.assertValueEqual(engine.maxEnergyReceived(), 500F, "intake");
        helper.assertValueEqual(engine.minEnergyReceived(), 0F, "minimum intake");
        helper.assertValueEqual(engine.getCurrentOutput(), 0.5F, "output");
        helper.assertValueEqual(engine.getTextureFile(), TileEngine.WOOD_TEXTURE, "texture");
        helper.assertValueEqual(engine.overridePipeConnection(PipeType.POWER, Direction.EAST), ConnectOverride.DEFAULT,
                "power pipe on the output side");
        helper.assertValueEqual(engine.overridePipeConnection(PipeType.ITEM, Direction.WEST), ConnectOverride.DISCONNECT,
                "item pipe on the back");
        helper.assertValueEqual(engine.overridePipeConnection(PipeType.FLUID, Direction.EAST), ConnectOverride.DISCONNECT,
                "fluid pipe on the output side");

        long[] start = new long[2];
        helper.runAfterDelay(40, () -> {
            helper.assertValueEqual(receptor.received, 0L, "an unpowered engine delivered");
            helper.assertValueEqual(engine.energy, 0F, "an unpowered engine made energy");
            helper.assertFalse(engine.isBurning(), "an unpowered engine burns");
            helper.assertValueEqual(engine.getStatusState(), MachineStatus.IDLE, "status when unpowered");
            helper.setBlock(ENGINE.below(), Blocks.REDSTONE_BLOCK);
            helper.setBlock(warmPos.below(), Blocks.REDSTONE_BLOCK);
            warm.energy = 800;
            start[0] = helper.getLevel().getGameTime();
            start[1] = warmReceptor.received;
        });
        helper.runAfterDelay(41, () -> {
            helper.assertTrue(engine.isBurning(), "a powered engine does not burn");
            helper.assertValueEqual(engine.getStatusState(), MachineStatus.WORKING, "status when powered");
        });
        helper.runAfterDelay(40 + 320, () -> {
            long now = helper.getLevel().getGameTime();
            long boundaries = now / 16 - start[0] / 16;
            float made = receptor.received + engine.energy;
            helper.assertTrue(receptor.received >= 10, "the receptor got " + receptor.received + " FE");
            helper.assertValueEqual(receptor.received % 10, 0L, "pulses of 10 FE");
            helper.assertTrue(Math.abs(made - 10 * boundaries) <= 20, "made " + made + " FE in " + boundaries
                    + " periods of 16 ticks, expected 10 FE each");
            helper.assertValueEqual(engine.getEnergyStage(), EnergyStage.BLUE, "stage of a cold engine");

            long delivered = warmReceptor.received - start[1];
            long ticks = now - start[0];
            helper.assertTrue(delivered >= 10 * ticks / 16 - 30 && delivered <= 10 * ticks / 16 + 30,
                    "a warm engine delivered " + delivered + " FE in " + ticks + " ticks, expected about " + 10 * ticks / 16);
            helper.succeed();
        });
    }

    /**
     * A powered Stirling engine takes one coal (1 600 ticks, the furnace's) and makes 10 FE a tick while its store is
     * below the PID's target (4.2.2: 1 MJ). Fuel burn times are the furnace's, a lava bucket leaves its bucket, an empty
     * engine reports OUT_OF_FUEL, and the burn times are saved and go to the GUI.
     */
    static void stoneBurnsFuel(GameTestHelper helper) {
        TileEngineStone engine = placeEngine(helper, EnergyRegistry.engineStone.block().get(), ENGINE, Direction.UP,
                TileEngineStone.class);
        engine.setItem(0, new ItemStack(Items.COAL, 2));
        helper.assertValueEqual(engine.getMaxEnergy(), 10000F, "max energy");
        helper.assertValueEqual(engine.maxEnergyExtracted(), 1000F, "pulse");
        helper.assertValueEqual(engine.maxEnergyReceived(), 2000F, "intake");
        helper.assertValueEqual(engine.minEnergyReceived(), 20F, "minimum intake");
        helper.assertValueEqual(engine.getTextureFile(), TileEngine.STONE_TEXTURE, "texture");

        TileEngineStone lava = placeEngine(helper, EnergyRegistry.engineStone.block().get(), ENGINE.east(3), Direction.UP,
                TileEngineStone.class);
        lava.setItem(0, new ItemStack(Items.LAVA_BUCKET));
        TileEngineStone empty = placeEngine(helper, EnergyRegistry.engineStone.block().get(), ENGINE.south(3), Direction.UP,
                TileEngineStone.class);

        helper.runAfterDelay(20, () -> {
            helper.assertValueEqual(engine.getBurnTime(), 0, "an unpowered engine started burning");
            helper.assertValueEqual(engine.getItem(0).getCount(), 2, "an unpowered engine took fuel");
            helper.assertValueEqual(empty.getStatusState(), MachineStatus.IDLE, "status of an unpowered empty engine");
            helper.setBlock(ENGINE.below(), Blocks.REDSTONE_BLOCK);
            helper.setBlock(ENGINE.east(3).below(), Blocks.REDSTONE_BLOCK);
            helper.setBlock(ENGINE.south(3).below(), Blocks.REDSTONE_BLOCK);
        });
        helper.runAfterDelay(22, () -> {
            helper.assertValueEqual(engine.getTotalBurnTime(), 1600, "coal burn time");
            helper.assertValueEqual(engine.getItem(0).getCount(), 1, "coal left");
            helper.assertTrue(engine.isBurning(), "the engine is not burning");
            helper.assertValueEqual(engine.getStatusState(), MachineStatus.WORKING, "status while burning");
            helper.assertValueEqual(lava.getTotalBurnTime(), 20000, "lava bucket burn time");
            helper.assertValueEqual(lava.getItem(0).getItem(), Items.BUCKET, "what a lava bucket leaves");
            helper.assertValueEqual(empty.getStatusState(), MachineStatus.OUT_OF_FUEL, "status of a powered empty engine");
        });
        helper.runAfterDelay(120, () -> {
            int burnt = engine.getTotalBurnTime() - engine.getBurnTime();
            helper.assertTrue(burnt > 90 && burnt < 100, "burnt " + burnt + " ticks of coal in about 98");
            helper.assertValueEqual(engine.energy, 10F * burnt, "10 FE for every tick burnt");
            helper.assertValueEqual(engine.currentOutput, 10F, "output below the target");
            helper.assertValueEqual(engine.getScaledBurnTime(12), (int) ((float) engine.getBurnTime() / 1600 * 12),
                    "scaled burn time");

            // Saved under 4.2.2's tag names.
            CompoundTag tag = engine.saveWithFullMetadata(helper.getLevel().registryAccess());
            helper.assertValueEqual(tag.getIntOr("burnTime", -1), engine.getBurnTime(), "burnTime tag");
            helper.assertValueEqual(tag.getIntOr("totalBurnTime", -1), 1600, "totalBurnTime tag");
            TileEngineStone reloaded = new TileEngineStone(engine.getBlockPos(), engine.getBlockState());
            reloaded.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag));
            helper.assertValueEqual(reloaded.getBurnTime(), engine.getBurnTime(), "burnTime after load");
            helper.assertValueEqual(reloaded.getItem(0).getCount(), 1, "coal after load");

            // The GUI's progress bars: the engine's four, then burnTime and totalBurnTime (4.2.2 ids 15 and 16).
            TileEngineStone client = new TileEngineStone(engine.getBlockPos(), engine.getBlockState());
            helper.assertValueEqual(engine.guiData.getCount(), 6, "GUI values");
            for (int i = 0; i < engine.guiData.getCount(); i++) {
                client.guiData.set(i, (short) engine.guiData.get(i));
            }
            helper.assertValueEqual(client.getBurnTime(), engine.getBurnTime(), "GUI burn time");
            helper.assertValueEqual(client.getTotalBurnTime(), 1600, "GUI total burn time");
            helper.assertValueEqual(client.getScaledBurnTime(12), engine.getScaledBurnTime(12), "GUI flame");
            lava.guiData.set(5, (short) 20000);
            helper.assertValueEqual(lava.getTotalBurnTime(), 20000, "GUI total burn time of lava");

            // Other furnace fuels, at their 1.6.4 burn times.
            for (var fuel : java.util.Map.of(Items.BLAZE_ROD, 2400, Items.STICK, 100, Items.COAL_BLOCK, 16000).entrySet()) {
                helper.setBlock(ENGINE.east(6), Blocks.AIR);
                TileEngineStone other = placeEngine(helper, EnergyRegistry.engineStone.block().get(), ENGINE.east(6),
                        Direction.UP, TileEngineStone.class);
                other.setItem(0, new ItemStack(fuel.getKey()));
                helper.setBlock(ENGINE.east(6).below(), Blocks.REDSTONE_BLOCK);
                other.checkRedstonePower();
                other.burn();
                helper.assertValueEqual(other.getTotalBurnTime(), fuel.getValue(), "burn time of " + fuel.getKey());
            }
            helper.succeed();
        });
    }

    /**
     * 4.2.2's PID: the output aims at 37.5 % of the store (3 750 FE), between 10 / 3 and 10 FE a tick.
     */
    static void stoneOutputControl(GameTestHelper helper) {
        TileEngineStone engine = placeEngine(helper, EnergyRegistry.engineStone.block().get(), ENGINE, Direction.UP,
                TileEngineStone.class);
        engine.energy = 0;
        assertClose(helper, engine.getCurrentOutput(), 10F, "output when empty");
        engine.energy = 6000;
        // The integral was at its limit (+133.3) after the empty call and drops to -133.3: -2250 - 6.7 is below the minimum.
        assertClose(helper, engine.getCurrentOutput(), 10F / 3, "output above the target");
        TileEngineStone fresh = placeEngine(helper, EnergyRegistry.engineStone.block().get(), ENGINE.east(3), Direction.UP,
                TileEngineStone.class);
        fresh.energy = 3745;
        assertClose(helper, fresh.getCurrentOutput(), 5F + 0.05F * 5F, "output just below the target");
        helper.succeed();
    }

    /**
     * The Stirling engine's menu: the fuel slot at 4.2.2's (80, 41), the player inventory below, shift-click puts coal
     * into the fuel slot, the status widget is there and the GUI texture is in the jar at 256 × 256.
     */
    static void stoneMenu(GameTestHelper helper) {
        TileEngineStone engine = placeEngine(helper, EnergyRegistry.engineStone.block().get(), ENGINE, Direction.UP,
                TileEngineStone.class);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.snapTo(Vec3.atBottomCenterOf(helper.absolutePos(ENGINE.above())));
        // A mock player has no mod channel, so open the menu on the server side only (see GuiTests.sampleMenu).
        if (!(engine.createMenu(1, player.getInventory(), player) instanceof ContainerEngine menu)) {
            throw helper.assertionException("the Stirling engine made no ContainerEngine");
        }
        helper.assertValueEqual(menu.getType(), EnergyRegistry.engineStoneMenu.get(), "menu type");
        helper.assertValueEqual(menu.slots.size(), 1 + 27 + 9, "slots");
        helper.assertTrue(menu.slots.get(0).container == engine, "slot 0 is not the engine's");
        helper.assertValueEqual(menu.slots.get(0).x + "," + menu.slots.get(0).y, "80,41", "fuel slot position");
        helper.assertValueEqual(menu.slots.get(1).x + "," + menu.slots.get(1).y, "8,84", "first inventory slot");
        helper.assertValueEqual(menu.slots.get(28).x + "," + menu.slots.get(28).y, "8,142", "first hotbar slot");
        helper.assertTrue(menu.stillValid(player), "the menu is not valid for a player next to the engine");
        helper.assertTrue(menu.statusWidget != null, "no status widget");
        helper.assertValueEqual(engine.getDisplayName().getString(), EnergyRegistry.engineStone.block().get().getName().getString(),
                "title");

        player.getInventory().setItem(0, new ItemStack(Items.COAL, 5));
        menu.quickMoveStack(player, 28);
        helper.assertValueEqual(engine.getItem(0).getCount(), 5, "coal shift-clicked into the engine");

        byte[] png = resource(Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "textures/gui/steam_engine_gui.png"));
        helper.assertTrue(png != null && png.length > 24, "missing GUI texture");
        ByteBuffer header = ByteBuffer.wrap(png, 16, 8);
        helper.assertValueEqual(header.getInt() + "x" + header.getInt(), "256x256", "GUI texture size");

        // Both engines are registered with their recipes' ids and drop themselves.
        helper.assertValueEqual(BuiltInRegistries.BLOCK.getKey(EnergyRegistry.engineWood.block().get()).getPath(), "engine_wood",
                "redstone engine id");
        helper.assertValueEqual(BuiltInRegistries.BLOCK.getKey(EnergyRegistry.engineStone.block().get()).getPath(), "engine_stone",
                "Stirling engine id");
        assertCrafts(helper, List.of(Items.OAK_PLANKS, Items.OAK_PLANKS, Items.OAK_PLANKS, Items.AIR, Items.GLASS, Items.AIR,
                CoreRegistry.woodenGearItem.get(), Items.PISTON, CoreRegistry.woodenGearItem.get()),
                EnergyRegistry.engineWood.item().get());
        assertCrafts(helper, List.of(Items.COBBLESTONE, Items.COBBLESTONE, Items.COBBLESTONE, Items.AIR, Items.GLASS, Items.AIR,
                CoreRegistry.stoneGearItem.get(), Items.PISTON, CoreRegistry.stoneGearItem.get()),
                EnergyRegistry.engineStone.item().get());
        helper.succeed();
    }

    /**
     * Delivery through kinesis pipes: a redstone engine and a Stirling engine each push through three wood power pipes
     * into a machine. The redstone engine's pulses arrive as 10 FE; the pipes hold nothing.
     */
    static void deliveryThroughKinesisPipe(GameTestHelper helper) {
        int y = 2;
        BlockPos woodPos = new BlockPos(0, y, 0);
        TileEngineWood wood = placeEngine(helper, EnergyRegistry.engineWood.block().get(), woodPos, Direction.SOUTH,
                TileEngineWood.class);
        wood.energy = 800;
        helper.setBlock(woodPos.below(), Blocks.REDSTONE_BLOCK);
        for (int z = 1; z <= 3; z++) {
            helper.setBlock(new BlockPos(0, y, z), TransportRegistry.getPipe(PipeType.POWER, PipeMaterial.WOOD));
        }
        helper.setBlock(new BlockPos(0, y, 4), PowerRoutingTests.machineBlock);
        PowerRoutingTests.Machine woodMachine = helper.getBlockEntity(new BlockPos(0, y, 4), PowerRoutingTests.Machine.class);

        BlockPos stonePos = new BlockPos(3, y, 0);
        TileEngineStone stone = placeEngine(helper, EnergyRegistry.engineStone.block().get(), stonePos, Direction.SOUTH,
                TileEngineStone.class);
        stone.setItem(0, new ItemStack(Items.COAL));
        helper.setBlock(stonePos.below(), Blocks.REDSTONE_BLOCK);
        for (int z = 1; z <= 3; z++) {
            helper.setBlock(new BlockPos(3, y, z), TransportRegistry.getPipe(PipeType.POWER, PipeMaterial.WOOD));
        }
        helper.setBlock(new BlockPos(3, y, 4), PowerRoutingTests.machineBlock);
        PowerRoutingTests.Machine stoneMachine = helper.getBlockEntity(new BlockPos(3, y, 4), PowerRoutingTests.Machine.class);

        helper.onEachTick(() -> {
            for (int x : new int[] {0, 3}) {
                for (int z = 1; z <= 3; z++) {
                    var handler = helper.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.Energy.BLOCK,
                            helper.absolutePos(new BlockPos(x, y, z)), Direction.UP);
                    helper.assertTrue(handler != null, "power pipe without an energy handler");
                    helper.assertValueEqual(handler.getAmountAsLong(), 0L, "energy stored in a pipe");
                }
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(woodMachine.received >= 50, "the redstone engine's machine got " + woodMachine.received + " FE");
            helper.assertValueEqual(woodMachine.received % 10, 0L, "redstone engine pulses of 10 FE");
            helper.assertTrue(stoneMachine.received >= 500, "the Stirling engine's machine got " + stoneMachine.received + " FE");
            helper.assertTrue(wood.getMeter().energyOutPerSecond() > 0, "nothing on the redstone engine's meter");
            helper.assertTrue(stone.getMeter().energyOutPerSecond() > 0, "nothing on the Stirling engine's meter");
        });
    }

    private static <T extends TileEngine> T placeEngine(GameTestHelper helper, Block block, BlockPos pos, Direction facing,
            Class<T> type) {
        helper.setBlock(pos, block.defaultBlockState().setValue(BlockEngine.FACING, facing));
        return helper.getBlockEntity(pos, type);
    }

    private static void assertCrafts(GameTestHelper helper, List<Item> grid, Item result) {
        ServerLevel level = helper.getLevel();
        CraftingInput input = CraftingInput.of(3, 3, grid.stream().map(ItemStack::new).toList());
        RecipeHolder<CraftingRecipe> recipe = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level).orElse(null);
        helper.assertTrue(recipe != null, "no recipe for " + grid);
        ItemStack crafted = recipe.value().assemble(input);
        helper.assertTrue(crafted.is(result) && crafted.getCount() == 1, grid + " crafts " + crafted + ", expected " + result);
    }

    private static byte[] resource(Identifier location) {
        try (InputStream in = WoodStoneEngineTests.class.getResourceAsStream("/assets/" + location.getNamespace() + "/"
                + location.getPath())) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    private static void assertClose(GameTestHelper helper, float actual, float expected, String what) {
        helper.assertTrue(Math.abs(actual - expected) <= 1e-3F, what + ": " + actual + ", expected " + expected);
    }
}
