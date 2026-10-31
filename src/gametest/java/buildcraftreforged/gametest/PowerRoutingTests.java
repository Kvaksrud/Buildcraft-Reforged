package buildcraftreforged.gametest;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.api.power.IPowerReceptor;
import buildcraftreforged.api.power.PowerEnergyHandler;
import buildcraftreforged.api.power.PowerHandler;
import buildcraftreforged.api.transport.IPipeTile.PipeType;
import buildcraftreforged.energy.BlockEngine;
import buildcraftreforged.transport.PipeFlow;
import buildcraftreforged.transport.PipeMaterial;
import buildcraftreforged.transport.TilePipe;
import buildcraftreforged.transport.TransportRegistry;
import buildcraftreforged.transport.route.PowerRouting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * #52: power routing in a real level (docs/TRANSPORT.md 7.4, section 14). The phases are unit-tested
 * ({@code PowerRoutingTest}); these check engines, machines, other mods' energy
 * handlers and the tier caps with pipe block entities. They build in the padded area from y = 2 (see
 * {@link OilFuelTests#PADDING}).
 */
final class PowerRoutingTests {
    private static final int Y = 2;

    static final Identifier SOURCE_ID = id("gametest_fe_source");
    static final Identifier SINK_ID = id("gametest_fe_sink");
    static final Identifier MACHINE_ID = id("gametest_power_machine");
    static Block sourceBlock;
    static Block sinkBlock;
    static Block machineBlock;
    static BlockEntityType<Source> sourceTile;
    static BlockEntityType<Sink> sinkTile;
    static BlockEntityType<Machine> machineTile;

    /** First and last tick of the measured window of {@link #tierCaps}: after the first second's burst. */
    static final int WINDOW_START = 20;
    static final int WINDOW_END = 120;

    private PowerRoutingTests() {
    }

    static void register(RegisterEvent event) {
        event.register(Registries.BLOCK, registry -> {
            sourceBlock = new TestBlock(properties(SOURCE_ID), Source::new);
            sinkBlock = new TestBlock(properties(SINK_ID), Sink::new);
            machineBlock = new TestBlock(properties(MACHINE_ID), Machine::new);
            registry.register(SOURCE_ID, sourceBlock);
            registry.register(SINK_ID, sinkBlock);
            registry.register(MACHINE_ID, machineBlock);
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, registry -> {
            sourceTile = new BlockEntityType<>(Source::new, sourceBlock);
            sinkTile = new BlockEntityType<>(Sink::new, sinkBlock);
            machineTile = new BlockEntityType<>(Machine::new, machineBlock);
            registry.register(SOURCE_ID, sourceTile);
            registry.register(SINK_ID, sinkTile);
            registry.register(MACHINE_ID, machineTile);
        });
    }

    static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK, sinkTile, (tile, side) -> tile.handler);
        PowerEnergyHandler.registerEnergyHandler(event, machineTile);
    }

    private static BlockPos at(int x, int z) {
        return new BlockPos(x, Y, z);
    }

    private static void pipe(GameTestHelper helper, int x, int z, PipeMaterial material) {
        helper.setBlock(at(x, z), TransportRegistry.getPipe(PipeType.POWER, material));
    }

    private static TilePipe tile(GameTestHelper helper, int x, int z) {
        return helper.getBlockEntity(at(x, z), TilePipe.class);
    }

    private static void assertPipesEmpty(GameTestHelper helper, int x, int fromZ, int toZ) {
        for (int z = fromZ; z <= toZ; z++) {
            EnergyHandler handler = tile(helper, x, z).getEnergyHandler(Direction.UP);
            helper.assertTrue(handler != null, "power pipe without an energy handler");
            helper.assertValueEqual(handler.getAmountAsLong(), 0L, "energy stored in the pipe at z = " + z);
        }
    }

    /**
     * Section 14, delivery: a redstone engine (the test engine: 10 FE pulses) through five wood power pipes powers a
     * machine. Everything the engine pushed reaches the machine, in pulses of 10 FE; the pipes hold nothing.
     */
    static void engineToMachine(GameTestHelper helper) {
        helper.setBlock(at(0, 0), EngineTests.engineBlock.defaultBlockState().setValue(BlockEngine.FACING, Direction.SOUTH));
        EngineTests.TestEngine engine = helper.getBlockEntity(at(0, 0), EngineTests.TestEngine.class);
        engine.energy = 500;
        helper.setBlock(at(0, 0).below(), Blocks.REDSTONE_BLOCK);
        for (int z = 1; z <= 5; z++) {
            pipe(helper, 0, z, PipeMaterial.WOOD);
        }
        helper.setBlock(at(0, 6), machineBlock);
        Machine machine = helper.getBlockEntity(at(0, 6), Machine.class);

        helper.onEachTick(() -> assertPipesEmpty(helper, 0, 1, 5));
        helper.succeedWhen(() -> {
            helper.assertTrue(machine.received >= 50, "the machine got " + machine.received + " FE");
            helper.assertValueEqual(machine.received % 10, 0L, "pulses of 10 FE");
            float pushed = 500 - engine.energy;
            helper.assertTrue(Math.abs(pushed - machine.received) < 1e-3F,
                    "the engine pushed " + pushed + " FE, the machine got " + machine.received);
            helper.assertTrue(engine.getMeter().energyOutPerSecond() > 0, "nothing on the engine's meter");
        });
    }

    /**
     * Section 7.4, send phase: a source pushing 150 FE a tick into a junction with two sinks taking at most 100 and 200
     * FE per insert splits it 50 / 100, in proportion to their requests; the source is never a receiver.
     */
    static void splitting(GameTestHelper helper) {
        helper.setBlock(at(2, 0), sourceBlock);
        Source source = helper.getBlockEntity(at(2, 0), Source.class);
        source.perTick = 150;
        pipe(helper, 2, 1, PipeMaterial.GOLD); // the junction
        pipe(helper, 1, 1, PipeMaterial.GOLD);
        pipe(helper, 3, 1, PipeMaterial.GOLD);
        helper.setBlock(at(0, 1), sinkBlock);
        helper.setBlock(at(4, 1), sinkBlock);
        Sink west = helper.getBlockEntity(at(0, 1), Sink.class);
        Sink east = helper.getBlockEntity(at(4, 1), Sink.class);
        west.handler.maxInsert = 100;
        east.handler.maxInsert = 200;

        helper.runAtTickTime(60, () -> {
            helper.assertTrue(source.pushed > 0, "nothing was pushed");
            helper.assertValueEqual(west.handler.stored + east.handler.stored, source.pushed, "FE in the sinks");
            helper.assertValueEqual(east.handler.stored, 2 * west.handler.stored, "east gets twice what west gets");
            helper.assertValueEqual(source.pushed % 150, 0L, "every push went through whole");
            assertPipesEmpty(helper, 1, 1, 1);
            helper.succeed();
        });
    }

    /**
     * Section 14, caps: a source pushing 100 000 FE a tick into five pipes of one tier delivers that tier's FE/t to a
     * sink with unlimited demand, averaged over ticks 21-120 (after the first second's burst), ± one tick's worth.
     */
    static void tierCaps(GameTestHelper helper, PipeMaterial material) {
        helper.setBlock(at(0, 0), sourceBlock);
        Source source = helper.getBlockEntity(at(0, 0), Source.class);
        source.perTick = 100_000;
        for (int z = 1; z <= 5; z++) {
            pipe(helper, 0, z, material);
        }
        helper.setBlock(at(0, 6), sinkBlock);
        Sink sink = helper.getBlockEntity(at(0, 6), Sink.class);

        long[] start = new long[1];
        helper.runAtTickTime(WINDOW_START, () -> start[0] = sink.handler.stored);
        helper.runAtTickTime(WINDOW_END, () -> {
            long rate = tile(helper, 0, 3).getTierStats().powerPerTick();
            long moved = sink.handler.stored - start[0];
            long expected = rate * (WINDOW_END - WINDOW_START);
            helper.assertTrue(Math.abs(moved - expected) <= rate, material.getSerializedName() + ": delivered " + moved
                    + " FE in " + (WINDOW_END - WINDOW_START) + " ticks, expected " + expected + " ± " + rate);
            helper.assertValueEqual(sink.handler.stored, source.pushed, "FE pushed and FE delivered");
            assertPipesEmpty(helper, 0, 1, 5);
            helper.succeed();
        });
    }

    /**
     * Section 7.4, Power Limiter (Q7): a Director on a power pipe lowers its refill. A gold line (1 280 FE/t) with a 160
     * FE/t limit on one pipe passes 160 FE/t; a limit above the tier changes nothing. The saturated pipe is overloaded
     * after 60 ticks; the receivers asked for more than it passed (Q21). The Pipe Contents flow (#60) reads the same. An
     * idle line whose sink wants power requests energy without any push (asked directly), and nothing flows there.
     */
    static void limiter(GameTestHelper helper) {
        helper.setBlock(at(0, 0), sourceBlock);
        Source source = helper.getBlockEntity(at(0, 0), Source.class);
        source.perTick = 100_000;
        for (int z = 1; z <= 3; z++) {
            pipe(helper, 0, z, PipeMaterial.GOLD);
        }
        helper.setBlock(at(0, 4), sinkBlock);
        Sink sink = helper.getBlockEntity(at(0, 4), Sink.class);
        tile(helper, 0, 2).setPowerLimit(160);
        tile(helper, 0, 3).setPowerLimit(100_000);
        pipe(helper, 2, 1, PipeMaterial.GOLD);
        helper.setBlock(at(2, 2), sinkBlock);

        long[] start = new long[1];
        helper.runAtTickTime(WINDOW_START, () -> start[0] = sink.handler.stored);
        helper.runAtTickTime(WINDOW_END, () -> {
            helper.assertValueEqual(tile(helper, 0, 2).getPowerRefill(), 160L, "limited refill");
            helper.assertValueEqual(tile(helper, 0, 3).getPowerRefill(), 1280L, "a limit above the tier");
            long moved = sink.handler.stored - start[0];
            long expected = 160L * (WINDOW_END - WINDOW_START);
            helper.assertTrue(Math.abs(moved - expected) <= 160, "delivered " + moved + " FE, expected " + expected);
            ServerLevel level = helper.getLevel();
            helper.assertTrue(PowerRouting.isOverloaded(level, tile(helper, 0, 2)), "the limited pipe is not overloaded");
            helper.assertFalse(PowerRouting.isOverloaded(level, tile(helper, 0, 1)), "the gold pipe is overloaded");
            helper.assertTrue(PowerRouting.requestsEnergy(level, tile(helper, 0, 2)), "no unmet request");
            PipeFlow limited = tile(helper, 0, 2).gateHolder.flow;
            helper.assertTrue(limited.isFlowing() && limited.isOverloaded() && limited.requestsEnergy(),
                    "Pipe Contents on the limited pipe");
            PipeFlow gold = tile(helper, 0, 1).gateHolder.flow;
            helper.assertTrue(gold.isFlowing() && !gold.isOverloaded(), "Pipe Contents on the gold pipe");
            PipeFlow idle = tile(helper, 2, 1).gateHolder.flow;
            helper.assertTrue(!idle.isFlowing() && idle.requestsEnergy() && !idle.isOverloaded(),
                    "Pipe Contents on the idle line");
            helper.succeed();
        });
    }

    private static BlockBehaviour.Properties properties(Identifier id) {
        return BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, id));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, path);
    }

    /** A creative-style generator: every tick pushes {@code perTick} FE into each neighbour's energy handler. */
    static final class Source extends BlockEntity {
        int perTick;
        long pushed;

        Source(BlockPos pos, BlockState state) {
            super(sourceTile, pos, state);
        }

        void tick() {
            if (perTick <= 0)
                return;
            for (Direction side : Direction.values()) {
                EnergyHandler handler = level.getCapability(Capabilities.Energy.BLOCK, worldPosition.relative(side),
                        side.getOpposite());
                if (handler == null)
                    continue;
                try (Transaction transaction = Transaction.openRoot()) {
                    pushed += handler.insert(perTick, transaction);
                    transaction.commit();
                }
            }
        }
    }

    /** Another mod's consumer: a Forge Energy store with no limit but {@code maxInsert} per insert. */
    static final class Sink extends BlockEntity {
        final Store handler = new Store();

        Sink(BlockPos pos, BlockState state) {
            super(sinkTile, pos, state);
        }
    }

    static final class Store extends SnapshotJournal<Long> implements EnergyHandler {
        long stored;
        int maxInsert = Integer.MAX_VALUE;

        @Override
        public long getAmountAsLong() {
            return stored;
        }

        @Override
        public long getCapacityAsLong() {
            return Long.MAX_VALUE;
        }

        @Override
        public int insert(int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonNegative(amount);
            int moved = Math.min(amount, maxInsert);
            if (moved > 0) {
                updateSnapshots(transaction);
                stored += moved;
            }
            return moved;
        }

        @Override
        public int extract(int amount, TransactionContext transaction) {
            return 0;
        }

        @Override
        protected Long createSnapshot() {
            return stored;
        }

        @Override
        protected void revertToSnapshot(Long snapshot) {
            stored = snapshot;
        }
    }

    /** A BuildCraft machine: a PowerHandler behind the Forge Energy adapter; counts what its work uses. */
    static final class Machine extends BlockEntity implements IPowerReceptor {
        final PowerHandler powerHandler = new PowerHandler(this, PowerHandler.Type.MACHINE);
        long received = 0;

        Machine(BlockPos pos, BlockState state) {
            super(machineTile, pos, state);
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

        @Override
        public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                BlockEntityType<T> type) {
            return level.isClientSide() ? null : (tickLevel, pos, tickState, tile) -> {
                if (tile instanceof Source source)
                    source.tick();
                else if (tile instanceof Machine machine)
                    machine.powerHandler.update();
            };
        }
    }
}
