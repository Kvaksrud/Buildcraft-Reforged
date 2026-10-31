package buildcraftreforged.gametest;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.api.transport.IPipeTile.PipeType;
import buildcraftreforged.factory.FactoryRegistry;
import buildcraftreforged.factory.TileTank;
import buildcraftreforged.transport.PipeFlow;
import buildcraftreforged.transport.PipeMaterial;
import buildcraftreforged.transport.PipeModuleType;
import buildcraftreforged.transport.TilePipe;
import buildcraftreforged.transport.TransportRegistry;
import buildcraftreforged.transport.route.FluidRouting;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * #51: fluid routing in a real level (docs/TRANSPORT.md 7.2, 7.3, section 14). The walk, budgets and the lock are
 * unit-tested ({@code PipeRoutingTest}, {@code FluidPipeStateTest}); these check Extractors on tanks, pushes through the
 * pipe capability, junctions, the Void module, the tier caps and the one-fluid lock with pipe block entities. They build
 * in the padded area from y = 2 (see {@link OilFuelTests#PADDING}). Two test blocks stand in for other mods: an endless
 * source of one fluid and a sink that takes any fluid without limit.
 */
final class FluidRoutingTests {
    private static final int Y = 2;
    /** Not constants: this class loads during registration, before fluids are ready to make resources of. */
    private static FluidResource water() {
        return FluidResource.of(Fluids.WATER);
    }

    private static FluidResource lava() {
        return FluidResource.of(Fluids.LAVA);
    }

    static final Identifier SOURCE_ID = id("gametest_fluid_source");
    static final Identifier SINK_ID = id("gametest_fluid_sink");
    static Block sourceBlock;
    static Block sinkBlock;
    static BlockEntityType<Source> sourceTile;
    static BlockEntityType<Sink> sinkTile;

    /** First and last tick of the measured window of {@link #tierCaps}: after the start, as #52's power caps. */
    static final int WINDOW_START = 20;
    static final int WINDOW_END = 120;
    static final int MAX_TICKS = 300;

    private FluidRoutingTests() {
    }

    static void register(RegisterEvent event) {
        event.register(Registries.BLOCK, registry -> {
            sourceBlock = new TestBlock(properties(SOURCE_ID), Source::new);
            sinkBlock = new TestBlock(properties(SINK_ID), Sink::new);
            registry.register(SOURCE_ID, sourceBlock);
            registry.register(SINK_ID, sinkBlock);
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, registry -> {
            sourceTile = new BlockEntityType<>(Source::new, sourceBlock);
            sinkTile = new BlockEntityType<>(Sink::new, sinkBlock);
            registry.register(SOURCE_ID, sourceTile);
            registry.register(SINK_ID, sinkTile);
        });
    }

    static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, sourceTile, (tile, side) -> tile.handler);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, sinkTile, (tile, side) -> tile.handler);
    }

    private static BlockPos at(int x, int z) {
        return new BlockPos(x, Y, z);
    }

    private static void pipe(GameTestHelper helper, int x, int z, PipeMaterial material) {
        helper.setBlock(at(x, z), TransportRegistry.getPipe(PipeType.FLUID, material));
    }

    private static TilePipe tile(GameTestHelper helper, int x, int z) {
        return helper.getBlockEntity(at(x, z), TilePipe.class);
    }

    private static TileTank tank(GameTestHelper helper, int x, int z) {
        helper.setBlock(at(x, z), FactoryRegistry.tankBlock.get());
        return helper.getBlockEntity(at(x, z), TileTank.class);
    }

    private static long amount(ResourceHandler<FluidResource> handler) {
        long amount = 0;
        for (int index = 0; index < handler.size(); index++) {
            amount += handler.getAmountAsLong(index);
        }
        return amount;
    }

    private static void fill(GameTestHelper helper, ResourceHandler<FluidResource> handler, FluidResource fluid, int amount) {
        try (Transaction transaction = Transaction.openRoot()) {
            helper.assertValueEqual(handler.insert(fluid, amount, transaction), amount, "filled into the tank");
            transaction.commit();
        }
    }

    /** Pushes {@code amount} of {@code fluid} into the pipe's fluid capability on {@code side}; returns what it took. */
    private static int push(GameTestHelper helper, int x, int z, Direction side, FluidResource fluid, int amount,
            boolean commit) {
        ResourceHandler<FluidResource> handler = tile(helper, x, z).getFluidHandler(side);
        helper.assertTrue(handler != null, "fluid pipe has no fluid handler on " + side);
        try (Transaction transaction = Transaction.openRoot()) {
            int moved = handler.insert(fluid, amount, transaction);
            if (commit)
                transaction.commit();
            return moved;
        }
    }

    /**
     * Section 14 and 8.2: a tank of 4 000 mB water, an Extractor on a gold fluid pipe, two gold pipes and an empty tank.
     * Everything arrives; the pipes hold nothing and give nothing back; the Pipe Contents flow reads water.
     */
    static void tankToTank(GameTestHelper helper) {
        TileTank source = tank(helper, 0, 0);
        for (int z = 1; z <= 3; z++) {
            pipe(helper, 0, z, PipeMaterial.GOLD);
        }
        TileTank target = tank(helper, 0, 4);
        fill(helper, source, water(), 4000);
        tile(helper, 0, 1).setModuleType(PipeModuleType.EXTRACTOR);

        boolean[] sawFlow = new boolean[1];
        helper.onEachTick(() -> {
            for (int z = 1; z <= 3; z++) {
                ResourceHandler<FluidResource> handler = tile(helper, 0, z).getFluidHandler(Direction.UP);
                helper.assertValueEqual(amount(handler), 0L, "fluid stored in the pipe at z = " + z);
            }
            PipeFlow flow = tile(helper, 0, 2).gateHolder.flow;
            if (flow.isFlowing()) {
                helper.assertTrue(flow.fluidContent().is(Fluids.WATER), "Pipe Contents shows " + flow.fluidContent());
                sawFlow[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertValueEqual(amount(target), 4000L, "water in the target");
            helper.assertValueEqual(amount(source), 0L, "water left in the source");
            helper.assertTrue(sawFlow[0], "the Pipe Contents flow never read water");
            try (Transaction transaction = Transaction.openRoot()) {
                helper.assertValueEqual(tile(helper, 0, 2).getFluidHandler(Direction.UP).extract(0, water(), 100, transaction),
                        0, "drained from a pipe");
            }
        });
    }

    /**
     * Section 7.2: an endless water source, an Extractor on an iron pipe and a junction with a tank on each side. Each
     * transfer picks one side at random (Q2), so over 100 transfers both tanks get a fair share and nothing is lost.
     */
    static void splitting(GameTestHelper helper) {
        helper.setBlock(at(2, 0), sourceBlock);
        Source source = helper.getBlockEntity(at(2, 0), Source.class);
        source.handler.fluid = water();
        pipe(helper, 2, 1, PipeMaterial.IRON);
        pipe(helper, 2, 2, PipeMaterial.IRON); // the junction
        TileTank west = tank(helper, 1, 2);
        TileTank east = tank(helper, 3, 2);
        tile(helper, 2, 1).setModuleType(PipeModuleType.EXTRACTOR);

        helper.runAtTickTime(110, () -> {
            long a = amount(west);
            long b = amount(east);
            helper.assertTrue(a + b >= 80 * 100, "only " + (a + b) + " mB arrived");
            helper.assertValueEqual(a + b, source.handler.extracted, "mB drained and mB in the tanks");
            helper.assertTrue(a * 4 >= a + b && b * 4 >= a + b, "uneven split: west " + a + ", east " + b);
            helper.succeed();
        });
    }

    /**
     * Section 5 and 6.2: a wood fluid line (20 mB/t) holds 400 mB after a second (the cap). A simulated push changes
     * nothing; a push takes 400 of 1 000, the next one nothing until the line has refilled. A Void module takes what its
     * budget allows and deletes it (4.2.2 {@code PipeFluidsVoid}).
     */
    static void pushAndVoid(GameTestHelper helper) {
        pipe(helper, 0, 1, PipeMaterial.WOOD);
        pipe(helper, 0, 2, PipeMaterial.WOOD);
        TileTank target = tank(helper, 0, 3);
        pipe(helper, 3, 1, PipeMaterial.WOOD);
        tile(helper, 3, 1).setModuleType(PipeModuleType.VOID);

        helper.runAfterDelay(40, () -> {
            helper.assertValueEqual(push(helper, 0, 1, Direction.NORTH, water(), 1000, false), 400, "simulated push");
            helper.assertValueEqual(amount(target), 0L, "water after a simulated push");
            helper.assertValueEqual(push(helper, 0, 1, Direction.NORTH, water(), 1000, true), 400, "first push");
            helper.assertValueEqual(push(helper, 0, 1, Direction.NORTH, water(), 1000, true), 0, "second push");
            helper.assertValueEqual(push(helper, 0, 2, Direction.EAST, water(), 1000, true), 0, "push into a spent pipe");
            helper.assertValueEqual(push(helper, 3, 1, Direction.NORTH, lava(), 1000, true), 400, "push into the Void");

            helper.runAfterDelay(20, () -> {
                helper.assertValueEqual(push(helper, 0, 1, Direction.NORTH, water(), 1000, true), 400, "push after 20 ticks");
                helper.assertValueEqual(amount(target), 800L, "water in the tank");
                helper.succeed();
            });
        });
    }

    /**
     * Section 14, mixing refusal (7.3): a water tank and a lava tank, each with an Extractor on an iron pipe, share a
     * trunk to a sink that takes any fluid. Until all the water has reached the sink the trunk is locked to it and the
     * lava tank is unchanged; lava flows {@code fluidLockTicks} after the last water. The lava Extractor is fitted after water
     * has started, so the first transfer through the trunk is water's.
     */
    static void mixingRefusal(GameTestHelper helper) {
        TileTank water = tank(helper, 0, 0);
        pipe(helper, 0, 1, PipeMaterial.IRON);
        pipe(helper, 1, 1, PipeMaterial.IRON); // the trunk
        pipe(helper, 2, 1, PipeMaterial.IRON);
        TileTank lava = tank(helper, 2, 0);
        pipe(helper, 1, 2, PipeMaterial.IRON);
        helper.setBlock(at(1, 3), sinkBlock);
        Sink sink = helper.getBlockEntity(at(1, 3), Sink.class);
        fill(helper, water, water(), 4000);
        fill(helper, lava, lava(), 4000);
        tile(helper, 0, 1).setModuleType(PipeModuleType.EXTRACTOR);

        long[] lastWater = {-1};
        long[] firstLava = {-1};
        long[] previous = {0, 4000};
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(amount(water) < 4000, "water has not started");
            tile(helper, 2, 1).setModuleType(PipeModuleType.EXTRACTOR);
        });
        helper.onEachTick(() -> {
            long tick = helper.getTick();
            long waterDelivered = sink.handler.stored.getLong(water());
            long lavaNow = amount(lava);
            if (waterDelivered > previous[0])
                lastWater[0] = tick;
            if (lavaNow < previous[1] && firstLava[0] < 0)
                firstLava[0] = tick;
            if (waterDelivered < 4000 && tick > 10) {
                helper.assertValueEqual(lavaNow, 4000L, "lava in its tank while water was flowing");
                helper.assertTrue(tile(helper, 1, 1).getFluidState().lockedFluid(helper.getLevel().getGameTime(),
                        FluidRouting.lockTicks()).equals(water()), "the trunk is not locked to water");
            }
            previous[0] = waterDelivered;
            previous[1] = lavaNow;
        });
        // The empty water tank takes lava too, through the trunk and the water Extractor's pipe, which pulls it out again.
        helper.succeedWhen(() -> {
            helper.assertValueEqual(sink.handler.stored.getLong(water()), 4000L, "water in the sink");
            helper.assertValueEqual(amount(lava), 0L, "lava left in its tank");
            helper.assertValueEqual(sink.handler.stored.getLong(lava()) + amount(water), 4000L, "lava delivered");
            helper.assertTrue(firstLava[0] - lastWater[0] >= FluidRouting.lockTicks(), "lava flowed at tick "
                    + firstLava[0] + ", water last flowed at tick " + lastWater[0]);
        });
    }

    /**
     * Section 14, tier caps: an endless water source, an Extractor and three pipes of one tier into a sink with no
     * limit. Over ticks 21-120 the sink gains {@code 100 × fluidPerTick} mB, ± one tick's worth.
     */
    static void tierCaps(GameTestHelper helper, PipeMaterial material) {
        helper.setBlock(at(0, 0), sourceBlock);
        Source source = helper.getBlockEntity(at(0, 0), Source.class);
        source.handler.fluid = water();
        for (int z = 1; z <= 4; z++) {
            pipe(helper, 0, z, material);
        }
        helper.setBlock(at(0, 5), sinkBlock);
        Sink sink = helper.getBlockEntity(at(0, 5), Sink.class);
        tile(helper, 0, 1).setModuleType(PipeModuleType.EXTRACTOR);

        long[] start = new long[1];
        helper.runAtTickTime(WINDOW_START, () -> start[0] = sink.handler.stored.getLong(water()));
        helper.runAtTickTime(WINDOW_END, () -> {
            long rate = tile(helper, 0, 3).getTierStats().fluidPerTick();
            long moved = sink.handler.stored.getLong(water()) - start[0];
            long expected = rate * (WINDOW_END - WINDOW_START);
            helper.assertTrue(Math.abs(moved - expected) <= rate, material.getSerializedName() + ": moved " + moved
                    + " mB in " + (WINDOW_END - WINDOW_START) + " ticks, expected " + expected + " ± " + rate);
            helper.assertValueEqual(sink.handler.stored.getLong(water()), source.handler.extracted, "mB drained and delivered");
            helper.succeed();
        });
    }

    private static BlockBehaviour.Properties properties(Identifier id) {
        return BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, id));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, path);
    }

    /** Another mod's endless tank of one fluid: one slot that always holds a bucket-free ocean of {@code fluid}. */
    static final class Source extends BlockEntity {
        final Endless handler = new Endless();

        Source(BlockPos pos, BlockState state) {
            super(sourceTile, pos, state);
        }
    }

    static final class Endless extends SnapshotJournal<Long> implements ResourceHandler<FluidResource> {
        FluidResource fluid = FluidResource.EMPTY;
        long extracted;

        @Override
        public int size() {
            return 1;
        }

        @Override
        public FluidResource getResource(int index) {
            return fluid;
        }

        @Override
        public long getAmountAsLong(int index) {
            return fluid.isEmpty() ? 0 : Integer.MAX_VALUE;
        }

        @Override
        public long getCapacityAsLong(int index, FluidResource resource) {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            return false;
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return 0;
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            if (fluid.isEmpty() || !resource.equals(fluid) || amount == 0)
                return 0;
            updateSnapshots(transaction);
            extracted += amount;
            return amount;
        }

        @Override
        protected Long createSnapshot() {
            return extracted;
        }

        @Override
        protected void revertToSnapshot(Long snapshot) {
            extracted = snapshot;
        }
    }

    /** Another mod's consumer: takes any fluid, any amount, and counts it per fluid. */
    static final class Sink extends BlockEntity {
        final Drain handler = new Drain();

        Sink(BlockPos pos, BlockState state) {
            super(sinkTile, pos, state);
        }
    }

    static final class Drain extends SnapshotJournal<Object2LongOpenHashMap<FluidResource>>
            implements ResourceHandler<FluidResource> {
        Object2LongOpenHashMap<FluidResource> stored = new Object2LongOpenHashMap<>();

        @Override
        public int size() {
            return 1;
        }

        @Override
        public FluidResource getResource(int index) {
            return FluidResource.EMPTY;
        }

        @Override
        public long getAmountAsLong(int index) {
            return 0;
        }

        @Override
        public long getCapacityAsLong(int index, FluidResource resource) {
            return Long.MAX_VALUE;
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            return true;
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            if (amount == 0)
                return 0;
            updateSnapshots(transaction);
            stored.addTo(resource, amount);
            return amount;
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return 0;
        }

        @Override
        protected Object2LongOpenHashMap<FluidResource> createSnapshot() {
            return new Object2LongOpenHashMap<>(stored);
        }

        @Override
        protected void revertToSnapshot(Object2LongOpenHashMap<FluidResource> snapshot) {
            stored = snapshot;
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
    }
}
