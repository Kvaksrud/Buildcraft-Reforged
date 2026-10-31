package buildcraftreforged.gametest;

import buildcraftreforged.api.transport.IPipeTile.PipeType;
import buildcraftreforged.core.network.PacketData;
import buildcraftreforged.transport.PipeFlow;
import buildcraftreforged.transport.PipeMaterial;
import buildcraftreforged.transport.PipeModuleType;
import buildcraftreforged.transport.TilePipe;
import buildcraftreforged.transport.TransportRegistry;
import buildcraftreforged.transport.flow.FlowRecord;
import buildcraftreforged.transport.flow.FlowRecordsPayload;
import buildcraftreforged.transport.flow.FlowSync;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * #59: flow records and their sync in a real level (docs/TRANSPORT.md 9, section 14). The measurement, the send rule
 * and the codecs are unit-tested ({@code FlowRecordTest}); these check what routing records on real pipes, how often
 * a running line sends, the per-chunk payload, the update tag and the Pipe Contents trigger. They build in the padded
 * area from y = 2 (see {@link OilFuelTests#PADDING}).
 */
final class FlowRecordTests {
    private static final int Y = 2;

    static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();
    static final int MAX_TICKS = 300;

    static {
        TESTS.put("flow_records_item_line", FlowRecordTests::itemLine);
        TESTS.put("flow_records_stop", FlowRecordTests::stop);
        TESTS.put("flow_records_fluid_and_power", FlowRecordTests::fluidAndPower);
    }

    private FlowRecordTests() {
    }

    private static BlockPos at(int x, int z) {
        return new BlockPos(x, Y, z);
    }

    private static void pipe(GameTestHelper helper, int x, int z, PipeType kind, PipeMaterial material) {
        helper.setBlock(at(x, z), TransportRegistry.getPipe(kind, material));
    }

    private static TilePipe tile(GameTestHelper helper, int x, int z) {
        return helper.getBlockEntity(at(x, z), TilePipe.class);
    }

    private static byte sides(Direction entry, Direction exit) {
        return FlowRecord.sides(entry.get3DDataValue(), exit.get3DDataValue());
    }

    /** Collects the payloads FlowSync sends for this test's level until {@link #close}. */
    private record Capture(List<FlowRecordsPayload> payloads, BiConsumer<ServerLevel, FlowRecordsPayload> listener) {
        static Capture start(GameTestHelper helper) {
            List<FlowRecordsPayload> payloads = new ArrayList<>();
            ServerLevel level = helper.getLevel();
            Capture capture = new Capture(payloads, (sent, payload) -> {
                if (sent == level)
                    payloads.add(payload);
            });
            FlowSync.SEND_LISTENERS.add(capture.listener);
            return capture;
        }

        void close() {
            FlowSync.SEND_LISTENERS.remove(listener);
        }
    }

    /** The record payloads carried for the block at {@code pos}, in the order they were sent. */
    private static List<FlowRecord> sentFor(List<FlowRecordsPayload> payloads, BlockPos pos) {
        List<FlowRecord> records = new ArrayList<>();
        for (FlowRecordsPayload payload : payloads) {
            for (int i = 0; i < payload.records().size(); i++) {
                if (payload.pos(i).equals(pos))
                    records.add(payload.records().get(i));
            }
        }
        return records;
    }

    /**
     * Section 14: chest, Extractor on a wood pipe, two wood pipes, chest: one item a second, every second. The middle
     * pipe's record shows cobblestone at 1 item/s from north to south; it is sent once when the flow starts and never
     * again while it is steady (at most twice a second in any case); the payload, the update tag and the Pipe Contents
     * trigger carry the same record.
     */
    static void itemLine(GameTestHelper helper) {
        helper.setBlock(at(0, 0), Blocks.CHEST);
        for (int z = 1; z <= 3; z++) {
            pipe(helper, 0, z, PipeType.ITEM, PipeMaterial.WOOD);
        }
        helper.setBlock(at(0, 4), Blocks.CHEST);
        Container source = (Container) helper.getBlockEntity(at(0, 0), BlockEntity.class);
        for (int slot = 0; slot < 4; slot++) {
            source.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        tile(helper, 0, 1).setModuleType(PipeModuleType.EXTRACTOR);
        Capture capture = Capture.start(helper);
        BlockPos middle = helper.absolutePos(at(0, 2));

        int[] steadyFrom = new int[1];
        helper.runAtTickTime(80, () -> {
            TilePipe pipe = tile(helper, 0, 2);
            FlowRecord record = pipe.getFlowTracker().record();
            helper.assertValueEqual(record.rate(), 1F, "items per second");
            helper.assertTrue(record.content() != null && record.content().value() == Items.COBBLESTONE,
                    "content " + record.content());
            helper.assertValueEqual(record.sides(), sides(Direction.NORTH, Direction.SOUTH), "sides");
            helper.assertValueEqual(record.colour(), FlowRecord.NO_COLOUR, "colour");
            steadyFrom[0] = pipe.getFlowTracker().sendCount();
            helper.assertTrue(steadyFrom[0] >= 1, "the record was never sent");

            PipeFlow flow = pipe.gateHolder.flow;
            helper.assertTrue(flow.isFlowing() && flow.itemContent().is(Items.COBBLESTONE), "Pipe Contents");

            // The update tag carries the record to a player who starts watching the chunk.
            byte[] data = PacketData.write(helper.getLevel().registryAccess(), pipe::writePacketPayload);
            TilePipe client = new TilePipe(middle, pipe.getBlockState());
            RegistryFriendlyByteBuf buffer = PacketData.wrap(data, helper.getLevel().registryAccess());
            try {
                client.readPacketPayload(buffer);
            } finally {
                buffer.release();
            }
            helper.assertFalse(client.getFlowTracker().client().differsFrom(record), "record in the update tag: "
                    + client.getFlowTracker().client());
        });
        helper.runAtTickTime(280, () -> {
            TilePipe pipe = tile(helper, 0, 2);
            int sends = pipe.getFlowTracker().sendCount();
            helper.assertValueEqual(sends, steadyFrom[0], "records sent while the flow was steady");
            helper.assertTrue(sends <= 2 * 280 / 20, sends + " records in 280 ticks");
            capture.close();
            List<FlowRecord> sent = sentFor(capture.payloads(), middle);
            helper.assertValueEqual(sent.size(), sends, "payload entries for the pipe");
            helper.assertFalse(sent.getLast().differsFrom(pipe.getFlowTracker().record()), "last payload entry");
            for (int z = 1; z <= 3; z++) {
                helper.assertValueEqual(tile(helper, 0, z).getFlowTracker().record().rate(), 1F, "rate at z = " + z);
            }
            helper.succeed();
        });
    }

    /**
     * Section 9.3: pushes through a gold line, then nothing. The record falls to zero within two boundaries of the last
     * push, is sent once more as zero, and the Pipe Contents trigger reads Empty.
     */
    static void stop(GameTestHelper helper) {
        for (int z = 1; z <= 3; z++) {
            pipe(helper, 0, z, PipeType.ITEM, PipeMaterial.GOLD);
        }
        helper.setBlock(at(0, 4), Blocks.CHEST);
        Capture capture = Capture.start(helper);
        BlockPos first = helper.absolutePos(at(0, 1));

        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (tick >= 30 && tick < 90 && tick % 5 == 0) {
                ResourceHandler<ItemResource> handler = tile(helper, 0, 1).getItemHandler(Direction.NORTH);
                try (Transaction transaction = Transaction.openRoot()) {
                    helper.assertValueEqual(handler.insert(ItemResource.of(Items.DIRT), 1, transaction), 1, "push");
                    transaction.commit();
                }
            }
        });
        helper.runAtTickTime(85, () -> {
            FlowRecord record = tile(helper, 0, 1).getFlowTracker().record();
            helper.assertValueEqual(record.rate(), 4F, "items per second while pushing");
            helper.assertValueEqual(record.sides(), sides(Direction.NORTH, Direction.SOUTH), "sides");
        });
        helper.runAtTickTime(140, () -> {
            TilePipe pipe = tile(helper, 0, 1);
            helper.assertFalse(pipe.getFlowTracker().record().isFlowing(), "the record after the flow stopped");
            helper.assertFalse(pipe.getFlowTracker().sent().isFlowing(), "the clients' record after the flow stopped");
            helper.assertFalse(pipe.gateHolder.flow.isFlowing(), "Pipe Contents after the flow stopped");
            capture.close();
            List<FlowRecord> sent = sentFor(capture.payloads(), first);
            helper.assertTrue(!sent.isEmpty() && !sent.getLast().isFlowing(), "the zero record was not sent: " + sent);
            helper.assertValueEqual(sent.stream().filter(r -> !r.isFlowing()).count(), 1L, "zero records sent");
            helper.succeed();
        });
    }

    /**
     * Section 9.2 for the other kinds: a wood fluid line from an endless water source records 20 mB/t of water, its
     * tier's cap; a gold power line saturated by a large source records 1 280 FE/t, its tier's cap, and overload once
     * the 7.4 counter is full.
     */
    static void fluidAndPower(GameTestHelper helper) {
        helper.setBlock(at(0, 0), FluidRoutingTests.sourceBlock);
        helper.getBlockEntity(at(0, 0), FluidRoutingTests.Source.class).handler.fluid = FluidResource.of(Fluids.WATER);
        for (int z = 1; z <= 3; z++) {
            pipe(helper, 0, z, PipeType.FLUID, PipeMaterial.WOOD);
        }
        helper.setBlock(at(0, 4), FluidRoutingTests.sinkBlock);
        tile(helper, 0, 1).setModuleType(PipeModuleType.EXTRACTOR);

        helper.setBlock(at(2, 0), PowerRoutingTests.sourceBlock);
        helper.getBlockEntity(at(2, 0), PowerRoutingTests.Source.class).perTick = 100_000;
        for (int z = 1; z <= 3; z++) {
            pipe(helper, 2, z, PipeType.POWER, PipeMaterial.GOLD);
        }
        helper.setBlock(at(2, 4), PowerRoutingTests.sinkBlock);

        helper.runAtTickTime(200, () -> {
            TilePipe fluidPipe = tile(helper, 0, 2);
            FlowRecord fluid = fluidPipe.getFlowTracker().record();
            helper.assertValueEqual(fluid.rate(), (float) fluidPipe.getTierStats().fluidPerTick(), "mB per tick");
            helper.assertTrue(fluid.content() != null && fluid.content().value() == Fluids.WATER, "fluid " + fluid);
            helper.assertValueEqual(fluid.sides(), sides(Direction.NORTH, Direction.SOUTH), "fluid sides");
            helper.assertTrue(fluidPipe.gateHolder.flow.fluidContent().is(Fluids.WATER), "Pipe Contents fluid");

            for (int z = 1; z <= 3; z++) {
                TilePipe powerPipe = tile(helper, 2, z);
                FlowRecord power = powerPipe.getFlowTracker().record();
                helper.assertValueEqual(power.rate(), (float) powerPipe.getTierStats().powerPerTick(),
                        "FE per tick at z = " + z);
                helper.assertTrue(power.overload(), "overload at z = " + z);
                helper.assertValueEqual(power.sides(), sides(Direction.NORTH, Direction.SOUTH), "power sides at z = " + z);
                helper.assertTrue(powerPipe.getFlowTracker().sent().overload(), "overload sent at z = " + z);
            }
            helper.succeed();
        });
    }
}
