package buildcraftreforged.gametest;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.api.power.IPowerReceptor;
import buildcraftreforged.api.power.PowerEnergyHandler;
import buildcraftreforged.api.power.PowerHandler;
import buildcraftreforged.api.transport.IPipeConnection.ConnectOverride;
import buildcraftreforged.api.transport.IPipeTile.PipeType;
import buildcraftreforged.core.RotationHelper;
import buildcraftreforged.core.network.PacketData;
import buildcraftreforged.core.status.HeatStage;
import buildcraftreforged.core.status.MachineStatus;
import buildcraftreforged.energy.BlockEngine;
import buildcraftreforged.energy.TileEngine;
import buildcraftreforged.energy.TileEngine.EnergyStage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * #36 engine base: heat and stages, piston speeds, the push-on-pulse delivery into an IPowerReceptor, into another
 * engine and into a plain Forge Energy block, orientation, save, sync and the status lines. The engine is a test-only
 * {@link TestEngine} (gametest_engine) with the redstone engine's pulse of 10 FE, 1 000 FE storage and 500 FE intake.
 */
final class EngineTests {
    static final Identifier ENGINE_ID = id("gametest_engine");
    static final Identifier RECEPTOR_ID = id("gametest_engine_receptor");
    static final Identifier FE_RECEIVER_ID = id("gametest_fe_receiver");
    static Block engineBlock;
    static Block receptorBlock;
    static Block feReceiverBlock;
    static BlockEntityType<TestEngine> engineTile;
    static BlockEntityType<TestReceptor> receptorTile;
    static BlockEntityType<FeReceiver> feReceiverTile;

    private static final BlockPos ENGINE = new BlockPos(2, 2, 2);

    private EngineTests() {
    }

    static void register(RegisterEvent event) {
        event.register(Registries.BLOCK, registry -> {
            engineBlock = new BlockEngine(BlockEngine.engineProperties(properties(ENGINE_ID)), TestEngine::new);
            receptorBlock = new TestBlock(properties(RECEPTOR_ID), TestReceptor::new);
            feReceiverBlock = new TestBlock(properties(FE_RECEIVER_ID), FeReceiver::new);
            registry.register(ENGINE_ID, engineBlock);
            registry.register(RECEPTOR_ID, receptorBlock);
            registry.register(FE_RECEIVER_ID, feReceiverBlock);
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, registry -> {
            engineTile = new BlockEntityType<>(TestEngine::new, engineBlock);
            receptorTile = new BlockEntityType<>(TestReceptor::new, receptorBlock);
            feReceiverTile = new BlockEntityType<>(FeReceiver::new, feReceiverBlock);
            registry.register(ENGINE_ID, engineTile);
            registry.register(RECEPTOR_ID, receptorTile);
            registry.register(FE_RECEIVER_ID, feReceiverTile);
        });
    }

    static void registerCapabilities(RegisterCapabilitiesEvent event) {
        PowerEnergyHandler.registerEnergyHandler(event, engineTile);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, feReceiverTile, (tile, side) -> tile.handler);
    }

    /**
     * Heat follows the stored energy (20 °C empty, 250 °C full), the stage its quarters; a full engine stays RED (the
     * overheat timer, #40, decides OVERHEAT), and OVERHEAT sticks; server piston
     * speed is 0.16 × heat level (at least 0.01), the client's comes from the synced stage. An unpowered engine loses
     * 20 FE a tick (4.2.2: 1 MJ in updateEntity and 1 MJ in engineUpdate).
     */
    static void heatAndStages(GameTestHelper helper) {
        TestEngine engine = placeEngine(helper, ENGINE, Direction.UP);

        assertStage(helper, engine, 0, EnergyStage.BLUE, 20F);
        assertClose(helper, engine.getPistonSpeed(), 0.01F, "piston speed when cold");
        assertStage(helper, engine, 240, EnergyStage.BLUE, 75.2F);
        assertStage(helper, engine, 260, EnergyStage.GREEN, 79.8F);
        assertStage(helper, engine, 490, EnergyStage.GREEN, 132.7F);
        assertStage(helper, engine, 510, EnergyStage.YELLOW, 137.3F);
        assertClose(helper, engine.getPistonSpeed(), 0.16F * 0.51F, "piston speed at 51 %");
        assertStage(helper, engine, 740, EnergyStage.YELLOW, 190.2F);
        assertStage(helper, engine, 760, EnergyStage.RED, 194.8F);
        assertClose(helper, engine.getIdealHeatLevel(), 1.948F, "ideal heat level");
        helper.assertValueEqual(engine.getStatusHeat().stage(), HeatStage.RED, "status heat stage");

        // The client takes the stage and pumping flag from the payload, and the piston speed from the stage.
        for (EnergyStage stage : new EnergyStage[] {EnergyStage.BLUE, EnergyStage.GREEN, EnergyStage.YELLOW, EnergyStage.RED}) {
            engine.energyStage = stage;
            TestEngine client = clientCopy(helper, engine);
            helper.assertValueEqual(client.energyStage, stage, "synced stage");
            float expected = switch (stage) {
                case BLUE -> 0.02F;
                case GREEN -> 0.04F;
                case YELLOW -> 0.08F;
                default -> 0.16F;
            };
            assertClose(helper, client.getPistonSpeed(), expected, "client piston speed for " + stage);
        }

        assertStage(helper, engine, 1000, EnergyStage.RED, 250F);
        engine.energyStage = EnergyStage.OVERHEAT;
        helper.assertValueEqual(engine.getStatusState(), MachineStatus.OVERHEATING, "status state when overheated");
        assertStage(helper, engine, 100, EnergyStage.OVERHEAT, 43F);

        // Saved under 4.2.2's tag names; the orientation is the block state.
        engine.energy = 512.5F;
        engine.progress = 0.25F;
        engine.heat = 130F;
        CompoundTag tag = engine.saveWithFullMetadata(helper.getLevel().registryAccess());
        helper.assertValueEqual(tag.getFloatOr("energyF", -1), 512.5F, "energyF tag");
        helper.assertValueEqual(tag.getFloatOr("heat", -1), 130F, "heat tag");
        helper.assertValueEqual(tag.getFloatOr("progress", -1), 0.25F, "progress tag");
        TestEngine reloaded = new TestEngine(engine.getBlockPos(), engine.getBlockState());
        reloaded.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag));
        helper.assertValueEqual(reloaded.energy, 512.5F, "energy after load");
        helper.assertValueEqual(reloaded.heat, 130F, "heat after load");

        // The engine GUI's progress bars: energy in two 16-bit halves, output in tenths, heat in hundredths.
        engine.energy = 70000.4F;
        engine.currentOutput = 3.7F;
        engine.heat = 211.25F;
        TestEngine guiClient = new TestEngine(engine.getBlockPos(), engine.getBlockState());
        for (int i = 0; i < engine.guiData.getCount(); i++) {
            guiClient.guiData.set(i, (short) engine.guiData.get(i));
        }
        helper.assertValueEqual(guiClient.energy, 70000F, "GUI energy");
        assertClose(helper, guiClient.currentOutput, 3.7F, "GUI output");
        assertClose(helper, guiClient.heat, 211.25F, "GUI heat");

        // Unpowered, the stored energy drains by 20 FE a tick.
        TestEngine cooling = placeEngine(helper, ENGINE.east(3), Direction.UP);
        cooling.energy = 600;
        helper.runAfterDelay(5, () -> {
            float lost = 600 - cooling.energy;
            helper.assertTrue(lost > 0 && lost % 20 == 0, "an unpowered engine lost " + lost + " FE, not 20 a tick");
            helper.assertFalse(cooling.isPumping, "an unpowered engine pumps");
            helper.assertValueEqual(cooling.getStatusState(), MachineStatus.IDLE, "status state when unpowered");
            helper.succeed();
        });
    }

    /**
     * A redstone-powered engine facing an IPowerReceptor pulses 10 FE at a time into it, at the heat-dependent piston
     * speed, and keeps what it did not hand over; the status panel shows the output.
     */
    static void pushesIntoReceptor(GameTestHelper helper) {
        BlockPos receptorPos = ENGINE.east();
        helper.setBlock(receptorPos, receptorBlock);
        TestReceptor receptor = helper.getBlockEntity(receptorPos, TestReceptor.class);
        TestEngine engine = placeEngine(helper, ENGINE, Direction.EAST);
        engine.energy = 500;
        helper.setBlock(ENGINE.below(), Blocks.REDSTONE_BLOCK);

        helper.runAfterDelay(1, () -> {
            // The output side refuses Forge Energy, the others take it (into the PowerHandler configured on the first
            // tick, then 95 % of it into the engine).
            helper.assertValueEqual(insert(helper, ENGINE, Direction.EAST, 100), 0, "FE accepted on the output side");
            helper.assertValueEqual(insert(helper, ENGINE, Direction.WEST, 100), 100, "FE accepted on the back");
            assertClose(helper, engine.energy, 595, "energy after 100 FE in");
            helper.assertTrue(engine.isRedstonePowered, "the engine did not see the redstone block");
            helper.assertTrue(engine.isPumping, "a powered engine with energy facing a receptor is not pumping");
            helper.assertValueEqual(engine.getStatusState(), MachineStatus.WORKING, "status state while powered");
            helper.succeedWhen(() -> {
                helper.assertTrue(receptor.received >= 30, "the receptor got " + receptor.received + " FE");
                helper.assertValueEqual(receptor.received % 10, 0L, "pulses of 10 FE");
                // 500 stored + 95 from the 100 FE inserted, minus what was pushed.
                assertClose(helper, engine.energy, 595 - receptor.received, "energy left in the engine");
                helper.assertTrue(engine.getStatusPower().producing(), "the status panel shows no production");
                helper.assertTrue(engine.getMeter().energyOutPerSecond() > 0, "nothing on the meter");
            });
        });
    }

    /**
     * #39 renderer: what RenderEngine reads reaches the client. A running engine's payload carries the stage (trunk
     * colour) and the pumping flag (which starts the client's piston), the facing is the block state, and every texture
     * the renderer, the item model and the block models name is a 64 x 32 (or 16 x 16 particle) file in the jar.
     */
    static void renderState(GameTestHelper helper) {
        BlockPos receptorPos = ENGINE.east();
        helper.setBlock(receptorPos, receptorBlock);
        TestEngine engine = placeEngine(helper, ENGINE, Direction.EAST);
        engine.energy = 600;
        helper.setBlock(ENGINE.below(), Blocks.REDSTONE_BLOCK);

        helper.runAfterDelay(1, () -> {
            helper.assertTrue(engine.isPumping, "a powered engine facing a receptor is not pumping");
            TestEngine client = clientCopy(helper, engine);
            helper.assertValueEqual(client.energyStage, EnergyStage.YELLOW, "synced stage at 60 %");
            helper.assertTrue(client.isPumping, "the client does not see the engine pumping");
            helper.assertValueEqual(client.getOrientation(), Direction.EAST, "facing the client renders");
            assertClose(helper, client.getPistonSpeed(), 0.08F, "client piston speed");
            helper.assertValueEqual(client.getTextureFile(), TileEngine.WOOD_TEXTURE, "base texture");

            // Overheat draws the red trunk and stops the client's piston.
            engine.energyStage = EnergyStage.OVERHEAT;
            TestEngine hot = clientCopy(helper, engine);
            helper.assertValueEqual(hot.energyStage, EnergyStage.OVERHEAT, "synced overheat");
            assertClose(helper, hot.getPistonSpeed(), 0, "client piston speed when overheated");

            for (Identifier texture : List.of(TileEngine.WOOD_TEXTURE, TileEngine.STONE_TEXTURE, TileEngine.IRON_TEXTURE,
                    id("textures/block/chamber.png"), id("textures/block/trunk_blue.png"), id("textures/block/trunk_green.png"),
                    id("textures/block/trunk_yellow.png"), id("textures/block/trunk_red.png"))) {
                assertPng(helper, texture, 64, 32);
            }
            for (String engineName : List.of("wood", "stone", "iron")) {
                assertPng(helper, id("textures/block/engine_" + engineName + "_bottom.png"), 16, 16);
                helper.assertTrue(resource(id("models/block/engine_" + engineName + ".json")) != null,
                        "no block model for engine_" + engineName);
            }
            helper.succeed();
        });
    }

    private static void assertPng(GameTestHelper helper, Identifier texture, int width, int height) {
        byte[] png = resource(texture);
        helper.assertTrue(png != null && png.length > 24, "missing texture " + texture);
        ByteBuffer header = ByteBuffer.wrap(png, 16, 8);
        helper.assertValueEqual(header.getInt() + "x" + header.getInt(), width + "x" + height, "size of " + texture);
    }

    private static byte @Nullable [] resource(Identifier location) {
        try (InputStream in = EngineTests.class.getResourceAsStream("/assets/" + location.getNamespace() + "/" + location.getPath())) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * A neighbour that only has a Forge Energy handler gets the pulse through it, and the engine keeps what it refused.
     * Engines chain (each is a receptor with 4.2.2's 95 % intake) but two engines facing each other do not pass power.
     */
    static void pushesIntoForgeEnergyAndEngines(GameTestHelper helper) {
        // Forge Energy neighbour with room for 25 FE: pulses of 10, 10 and 5.
        BlockPos fePos = ENGINE.east();
        helper.setBlock(fePos, feReceiverBlock);
        FeReceiver fe = helper.getBlockEntity(fePos, FeReceiver.class);
        TestEngine engine = placeEngine(helper, ENGINE, Direction.EAST);
        engine.energy = 500;
        helper.setBlock(ENGINE.below(), Blocks.REDSTONE_BLOCK);

        // Engine chain: first faces second, second faces nowhere useful.
        BlockPos first = ENGINE.south(3);
        BlockPos second = first.east();
        TestEngine firstEngine = placeEngine(helper, first, Direction.EAST);
        TestEngine secondEngine = placeEngine(helper, second, Direction.EAST);
        firstEngine.energy = 500;
        helper.setBlock(first.below(), Blocks.REDSTONE_BLOCK);
        helper.setBlock(second.below(), Blocks.REDSTONE_BLOCK);

        // Two engines facing each other.
        BlockPos left = ENGINE.south(6);
        BlockPos right = left.east();
        TestEngine leftEngine = placeEngine(helper, left, Direction.EAST);
        TestEngine rightEngine = placeEngine(helper, right, Direction.WEST);
        leftEngine.energy = 500;
        rightEngine.energy = 500;
        helper.setBlock(left.below(), Blocks.REDSTONE_BLOCK);
        helper.setBlock(right.below(), Blocks.REDSTONE_BLOCK);

        helper.assertFalse(leftEngine.isPoweredTile(rightEngine, Direction.EAST), "an engine powers the engine facing it");
        helper.assertTrue(firstEngine.isPoweredTile(secondEngine, Direction.EAST), "an engine does not power the next one");

        helper.succeedWhen(() -> {
            helper.assertValueEqual(fe.handler.getAmountAsLong(), 25L, "FE handler fill");
            assertClose(helper, engine.energy, 475, "energy left after the FE handler filled");

            helper.assertTrue(secondEngine.energy >= 9.5F * 3, "the second engine got " + secondEngine.energy + " FE");
            float pulses = secondEngine.energy / 9.5F;
            assertClose(helper, pulses, Math.round(pulses), "the second engine's energy is not 95 % of 10 FE pulses");

            helper.assertFalse(leftEngine.isPumping || rightEngine.isPumping, "engines facing each other pump");
            assertClose(helper, leftEngine.energy, 500, "left engine energy");
            assertClose(helper, rightEngine.energy, 500, "right engine energy");
        });
    }

    /**
     * Placement turns the engine to a powered neighbour; switchOrientation cycles through powered neighbours; only the back is
     * a solid face; item and fluid pipes do not connect to the output side; a full engine explodes when its overheat
     * timer runs out (#40, shortened to one tick here).
     */
    static void orientation(GameTestHelper helper) {
        BlockPos east = ENGINE.east();
        BlockPos north = ENGINE.north();
        helper.setBlock(east, receptorBlock);
        TestEngine engine = placeEngine(helper, ENGINE, Direction.UP);
        BlockState state = helper.getBlockState(ENGINE);
        Level level = helper.getLevel();
        BlockPos abs = helper.absolutePos(ENGINE);
        state.getBlock().setPlacedBy(level, abs, state, null, ItemStack.EMPTY);
        helper.assertValueEqual(engine.getOrientation(), Direction.EAST, "orientation after placement");
        helper.assertValueEqual(helper.getBlockState(ENGINE).getValue(BlockEngine.FACING), Direction.EAST, "FACING");
        helper.assertTrue(helper.getBlockEntity(ENGINE, TestEngine.class) == engine, "the block entity was replaced");

        // The engine sees a new neighbour once its TileBuffer refreshes, at most 20 ticks later (4.2.2).
        helper.setBlock(north, receptorBlock);
        helper.runAfterDelay(21, () -> {
            // The wrench steps through all six faces (EngineWrenchTests); 4.2.2's jump to the next powered face is
            // switchOrientation, which placement still uses.
            helper.assertTrue(engine.switchOrientation(false), "no other powered face");
            helper.assertValueEqual(engine.getOrientation(), Direction.NORTH, "orientation after switching (east → north)");
            helper.assertTrue(RotationHelper.rotateBlock(level, abs, Direction.UP), "the wrench did not rotate the engine");
            helper.assertValueEqual(engine.getOrientation(), Direction.SOUTH, "orientation after the wrench (north → south)");
            engine.setOrientation(Direction.EAST);

            BlockState facingEast = helper.getBlockState(ENGINE);
            helper.assertTrue(facingEast.isFaceSturdy(level, abs, Direction.WEST), "the back is not solid");
            for (Direction side : new Direction[] {Direction.EAST, Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH}) {
                helper.assertFalse(facingEast.isFaceSturdy(level, abs, side), side + " face is solid");
            }
            helper.assertFalse(facingEast.isRedstoneConductor(level, abs), "an engine conducts redstone");

            helper.assertValueEqual(engine.overridePipeConnection(PipeType.ITEM, Direction.EAST), ConnectOverride.DISCONNECT,
                    "item pipe on the output side");
            helper.assertValueEqual(engine.overridePipeConnection(PipeType.POWER, Direction.EAST), ConnectOverride.DEFAULT,
                    "power pipe on the output side");
            helper.assertValueEqual(engine.overridePipeConnection(PipeType.FLUID, Direction.WEST), ConnectOverride.DEFAULT,
                    "fluid pipe on the back");

            // A full engine is red; when its overheat timer runs out it blows up (engines.explode).
            BlockPos hotPos = ENGINE.east(6);
            TestEngine hot = placeEngine(helper, hotPos, Direction.UP);
            hot.overheatTicks = 1;
            hot.explode = true;
            hot.energy = hot.getMaxEnergy();
            hot.output = 1;
            helper.setBlock(hotPos.below(), Blocks.REDSTONE_BLOCK);
            helper.succeedWhen(() -> helper.assertBlockNotPresent(engineBlock, hotPos));
        });
    }

    private static void assertStage(GameTestHelper helper, TestEngine engine, float energy, EnergyStage stage, float heat) {
        engine.energy = energy;
        engine.updateHeatLevel();
        assertClose(helper, engine.getHeat(), heat, "heat at " + energy + " FE");
        assertClose(helper, engine.getHeatLevel(), energy / 1000F, "heat level at " + energy + " FE");
        helper.assertValueEqual(engine.getEnergyStage(), stage, "stage at " + energy + " FE");
        if (stage != EnergyStage.OVERHEAT)
            assertClose(helper, engine.getPistonSpeed(), Math.max(0.16F * energy / 1000F, 0.01F), "piston speed at " + energy);
    }

    private static TestEngine clientCopy(GameTestHelper helper, TestEngine engine) {
        TestEngine client = new TestEngine(engine.getBlockPos(), engine.getBlockState());
        byte[] bytes = PacketData.write(helper.getLevel().registryAccess(), engine::writePacketPayload);
        RegistryFriendlyByteBuf data = PacketData.wrap(bytes, helper.getLevel().registryAccess());
        try {
            client.readPacketPayload(data);
        } finally {
            data.release();
        }
        return client;
    }

    static TestEngine placeEngine(GameTestHelper helper, BlockPos pos, Direction facing) {
        // A fresh block entity: an engine left at this spot by an earlier test (shut down, say) would otherwise stay.
        helper.setBlock(pos, Blocks.AIR);
        helper.setBlock(pos, engineBlock.defaultBlockState().setValue(BlockEngine.FACING, facing));
        return helper.getBlockEntity(pos, TestEngine.class);
    }

    private static int insert(GameTestHelper helper, BlockPos pos, Direction side, int amount) {
        EnergyHandler handler = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(pos), side);
        helper.assertTrue(handler != null, "no energy handler on " + side);
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = handler.insert(amount, tx);
            tx.commit();
            return inserted;
        }
    }

    private static void assertClose(GameTestHelper helper, float actual, float expected, String what) {
        helper.assertTrue(Math.abs(actual - expected) <= 1e-3F, what + ": " + actual + ", expected " + expected);
    }

    private static BlockBehaviour.Properties properties(Identifier id) {
        return BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, id));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, path);
    }

    /** A redstone engine stand-in: 10 FE pulses, 1 000 FE storage, 500 FE intake; burns output FE a tick while powered. */
    static final class TestEngine extends TileEngine {
        float output = 0;
        /** The overheat timer's length in ticks; -1 for engines.overheatTimerSeconds. */
        int overheatTicks = -1;
        /** Explode when the timer runs out; null for engines.explode. */
        @Nullable Boolean explode = null;

        TestEngine(BlockPos pos, BlockState state) {
            super(engineTile, pos, state);
        }

        @Override
        protected void burn() {
            if (isRedstonePowered && output > 0) {
                currentOutput = output;
                addEnergy(output);
            }
        }

        @Override
        public Identifier getTextureFile() {
            return WOOD_TEXTURE;
        }

        @Override
        public boolean isBurning() {
            return isRedstonePowered && output > 0;
        }

        @Override
        public int getScaledBurnTime(int scale) {
            return 0;
        }

        @Override
        public float getMaxEnergy() {
            return 1000;
        }

        @Override
        public float minEnergyReceived() {
            return 0;
        }

        @Override
        public float maxEnergyReceived() {
            return 500;
        }

        @Override
        public float maxEnergyExtracted() {
            return 10;
        }

        /** 0: the overheat still removes the engine, but the blast cannot reach the tests next to this one. */
        @Override
        public float explosionRange() {
            return 0;
        }

        @Override
        public float getCurrentOutput() {
            return output;
        }

        @Override
        protected int getOverheatTimerTicks() {
            return overheatTicks >= 0 ? overheatTicks : super.getOverheatTimerTicks();
        }

        @Override
        protected boolean explodesWhenOverheated() {
            return explode != null ? explode : super.explodesWhenOverheated();
        }
    }

    /** A machine that takes engine pulses and counts them. */
    static final class TestReceptor extends BlockEntity implements IPowerReceptor {
        final PowerHandler powerHandler = new PowerHandler(this, PowerHandler.Type.MACHINE);
        long received = 0;

        TestReceptor(BlockPos pos, BlockState state) {
            super(receptorTile, pos, state);
            powerHandler.configure(0, 1000, 1, 1000);
            // Ticked every tick, so a pulse loses at most 0.1 FE, which the whole-FE store rounds away.
            powerHandler.setPerdition(new PowerHandler.PerditionCalculator(PowerHandler.PerditionCalculator.MIN_POWERLOSS));
        }

        @Override
        public PowerHandler.PowerReceiver getPowerReceiver(@Nullable Direction side) {
            return powerHandler.getPowerReceiver();
        }

        @Override
        public void doWork(PowerHandler workProvider) {
            received += workProvider.useEnergy(0, 1000, true);
        }

        @Override
        public @Nullable Level getWorld() {
            return level;
        }
    }

    /** Another mod's block: only a Forge Energy handler, with room for 25 FE. */
    static final class FeReceiver extends BlockEntity {
        final SimpleEnergyHandler handler = new SimpleEnergyHandler(25, 1000, 0);

        FeReceiver(BlockPos pos, BlockState state) {
            super(feReceiverTile, pos, state);
        }
    }

    private static final class TestBlock extends Block implements EntityBlock {
        private final BlockEntityType.BlockEntitySupplier<? extends BlockEntity> factory;

        TestBlock(Properties properties, BlockEntityType.BlockEntitySupplier<? extends BlockEntity> factory) {
            super(properties);
            this.factory = factory;
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return factory.create(pos, state);
        }

        /** The receptor's PowerHandler is updated every tick, as TileBuildCraft does for a machine. */
        @Override
        public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                BlockEntityType<T> type) {
            return level.isClientSide() ? null : (tickLevel, pos, tickState, tile) -> {
                if (tile instanceof TestReceptor receptor)
                    receptor.powerHandler.update();
            };
        }
    }
}
