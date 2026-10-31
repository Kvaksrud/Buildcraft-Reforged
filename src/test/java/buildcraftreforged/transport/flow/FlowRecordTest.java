package buildcraftreforged.transport.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import buildcraftreforged.api.transport.IPipeTile.PipeType;
import buildcraftreforged.transport.render.PipeRendererTESR;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.Test;

/** docs/TRANSPORT.md section 14, #59: rate measurement, the send rule and the stream codec of the flow record (9). */
class FlowRecordTest {

    private static final RegistryAccess REGISTRIES = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    private static final byte WEST_TO_EAST = FlowRecord.sides(Direction.WEST.get3DDataValue(),
            Direction.EAST.get3DDataValue());

    private static Holder<?> cobblestone() {
        return BuiltInRegistries.ITEM.wrapAsHolder(Items.COBBLESTONE);
    }

    private static void add(FlowAccumulator accumulator, long amount, Holder<?> content, long now) {
        try (Transaction transaction = Transaction.openRoot()) {
            accumulator.add(amount, content, FlowRecord.NO_COLOUR, Direction.WEST, Direction.EAST, now, transaction);
            transaction.commit();
        }
    }

    private static FlowRecord item(float rate) {
        return new FlowRecord(PipeType.ITEM, cobblestone(), FlowRecord.NO_COLOUR, WEST_TO_EAST, rate, false);
    }

    // 9.2: two 10-tick buckets, in each kind's unit.

    @Test
    void itemsPerSecondIsTheSumOfTwoBuckets() {
        FlowAccumulator accumulator = new FlowAccumulator(PipeType.ITEM, null);
        add(accumulator, 3, cobblestone(), 100); // bucket [100, 110)
        add(accumulator, 2, cobblestone(), 109);
        add(accumulator, 4, cobblestone(), 115); // bucket [110, 120)
        add(accumulator, 7, cobblestone(), 120); // the open bucket, not counted yet
        assertEquals(9, accumulator.rate(120));
        FlowRecord record = accumulator.record(120, false);
        assertEquals(9, record.rate());
        assertSame(Items.COBBLESTONE, record.content().value());
        assertEquals(Direction.WEST.get3DDataValue(), record.entrySide());
        assertEquals(Direction.EAST.get3DDataValue(), record.exitSide());
        // Ten ticks on, the oldest bucket has left the window.
        assertEquals(11, accumulator.rate(130));
        assertEquals(7, accumulator.rate(140));
        assertEquals(0, accumulator.rate(150));
        assertEquals(FlowRecord.none(PipeType.ITEM), accumulator.record(150, false));
    }

    @Test
    void fluidAndPowerArePerTick() {
        FlowAccumulator fluid = new FlowAccumulator(PipeType.FLUID, null);
        FlowAccumulator power = new FlowAccumulator(PipeType.POWER, null);
        for (long tick = 200; tick < 220; tick++) {
            add(fluid, 40, BuiltInRegistries.FLUID.wrapAsHolder(Fluids.WATER), tick);
            add(power, 80, null, tick);
        }
        assertEquals(40, fluid.rate(220));
        assertSame(Fluids.WATER, fluid.record(220, false).content().value());
        assertEquals(80, power.rate(220));
        FlowRecord record = power.record(220, true);
        assertNull(record.content());
        assertTrue(record.overload());
        assertFalse(fluid.record(220, true).overload(), "only power pipes are overloaded");
        // A gap longer than the window clears everything.
        assertEquals(0, power.rate(260));
    }

    @Test
    void abortedTransferRecordsNothing() {
        FlowAccumulator accumulator = new FlowAccumulator(PipeType.ITEM, null);
        try (Transaction transaction = Transaction.openRoot()) {
            accumulator.add(5, cobblestone(), FlowRecord.NO_COLOUR, null, null, 100, transaction);
        }
        assertEquals(0, accumulator.rate(120));
    }

    // 9.3: the send rule.

    @Test
    void steadyRateSendsNothing() {
        assertFalse(item(10).differsFrom(item(10)));
        assertFalse(item(10).differsFrom(item(9)), "a 10 % change");
        assertFalse(FlowRecord.none(PipeType.ITEM).differsFrom(FlowRecord.none(PipeType.ITEM)));
    }

    @Test
    void changesThatSend() {
        assertTrue(item(10).differsFrom(item(8.5F)), "a 15 % change");
        assertTrue(item(8.5F).differsFrom(item(10)), "a 15 % drop");
        assertTrue(item(1).differsFrom(FlowRecord.none(PipeType.ITEM)), "rate crossing zero");
        assertTrue(FlowRecord.none(PipeType.ITEM).differsFrom(item(1)), "flow stopping");
        FlowRecord dirt = new FlowRecord(PipeType.ITEM, BuiltInRegistries.ITEM.wrapAsHolder(Items.DIRT),
                FlowRecord.NO_COLOUR, WEST_TO_EAST, 10, false);
        assertTrue(dirt.differsFrom(item(10)), "content");
        assertTrue(new FlowRecord(PipeType.ITEM, cobblestone(), (byte) 3, WEST_TO_EAST, 10, false).differsFrom(item(10)),
                "colour");
        assertTrue(new FlowRecord(PipeType.ITEM, cobblestone(), FlowRecord.NO_COLOUR, FlowRecord.NO_SIDES, 10, false)
                .differsFrom(item(10)), "sides");
        FlowRecord power = new FlowRecord(PipeType.POWER, null, FlowRecord.NO_COLOUR, WEST_TO_EAST, 80, false);
        assertTrue(new FlowRecord(PipeType.POWER, null, FlowRecord.NO_COLOUR, WEST_TO_EAST, 80, true).differsFrom(power),
                "overload");
    }

    @Test
    void trackerSendsAtMostOncePerBoundaryAndNothingWhenSteady() {
        FlowTracker tracker = new FlowTracker(PipeType.ITEM, () -> {
        });
        int sends = 0;
        // One item every 20 ticks: every window holds one item, so the rate is 1 item/s throughout.
        for (long tick = 0; tick <= 400; tick++) {
            if (tick % 20 == 5)
                add(tracker.accumulator, 1, cobblestone(), tick);
            if (tick % FlowAccumulator.BUCKET_TICKS == 0 && tracker.boundary(tick, false) != null)
                sends++;
        }
        assertEquals(1, sends, "only the start of the flow is sent");
        assertEquals(1, tracker.sendCount());
        // The last item passed at 385: the window [390, 410) is empty, so one record of zero, then the pipe is idle.
        assertFalse(tracker.isIdle());
        assertTrue(tracker.boundary(410, false) != null);
        assertTrue(tracker.isIdle());
        assertNull(tracker.boundary(420, false));
        assertEquals(2, tracker.sendCount());
    }

    // 9.1: the stream codec.

    private static FlowRecord roundTrip(FlowRecord record, int[] size) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), REGISTRIES);
        FlowRecord.STREAM_CODEC.encode(buffer, record);
        size[0] = buffer.readableBytes();
        FlowRecord decoded = FlowRecord.STREAM_CODEC.decode(buffer);
        assertEquals(0, buffer.readableBytes(), "codec left bytes unread");
        return decoded;
    }

    @Test
    void codecRoundTripUnder16Bytes() {
        List<FlowRecord> records = new ArrayList<>();
        records.add(item(64));
        records.add(new FlowRecord(PipeType.ITEM, BuiltInRegistries.ITEM.wrapAsHolder(Items.DIAMOND_BLOCK), (byte) 15,
                FlowRecord.sides(FlowRecord.CENTRE, 2), 0.5F, false));
        records.add(new FlowRecord(PipeType.FLUID, BuiltInRegistries.FLUID.wrapAsHolder(Fluids.LAVA),
                FlowRecord.NO_COLOUR, WEST_TO_EAST, 1280, false));
        records.add(new FlowRecord(PipeType.POWER, null, FlowRecord.NO_COLOUR, WEST_TO_EAST, 40960, true));
        for (PipeType kind : PipeType.values())
            records.add(FlowRecord.none(kind));
        int[] size = new int[1];
        for (FlowRecord record : records) {
            FlowRecord decoded = roundTrip(record, size);
            assertFalse(decoded.differsFrom(record), record + " came back as " + decoded);
            assertEquals(record.rate(), decoded.rate());
            assertTrue(size[0] < 16, record + " takes " + size[0] + " bytes");
        }
    }

    @Test
    void codecRefusesBadRecords() {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), REGISTRIES);
        buffer.writeByte(PipeType.POWER.ordinal());
        buffer.writeByte(FlowRecord.NO_COLOUR);
        buffer.writeByte(7); // entry side 7
        buffer.writeFloat(1);
        buffer.writeBoolean(false);
        assertThrows(RuntimeException.class, () -> FlowRecord.STREAM_CODEC.decode(buffer));
    }

    @Test
    void payloadRoundTrip() {
        ChunkPos chunk = new ChunkPos(-3, 7);
        BlockPos low = new BlockPos(-48 + 5, -64, 112 + 9);
        BlockPos high = new BlockPos(-48 + 15, 319, 112);
        FlowRecordsPayload payload = new FlowRecordsPayload(chunk,
                new int[]{FlowRecordsPayload.pack(low), FlowRecordsPayload.pack(high)},
                List.of(item(3), FlowRecord.none(PipeType.ITEM)));
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), REGISTRIES);
        FlowRecordsPayload.STREAM_CODEC.encode(buffer, payload);
        FlowRecordsPayload decoded = FlowRecordsPayload.STREAM_CODEC.decode(buffer);
        assertEquals(0, buffer.readableBytes());
        assertEquals(chunk, decoded.chunk());
        assertEquals(low, decoded.pos(0));
        assertEquals(high, decoded.pos(1));
        assertEquals(3, decoded.records().get(0).rate());
    }

    // 9.4: icon spacing.

    @Test
    void itemIconsAreSpacedByTheRate() {
        assertEquals(20, PipeRendererTESR.itemInterval(1));
        assertEquals(5, PipeRendererTESR.itemInterval(4));
        assertEquals(PipeRendererTESR.MIN_ITEM_INTERVAL, PipeRendererTESR.itemInterval(512), "a full stream");
    }

    @Test
    void displayedSpacingIsAPowerOfTwo() {
        assertEquals(16, PipeRendererTESR.displayInterval(20));
        assertEquals(4, PipeRendererTESR.displayInterval(5));
        assertEquals(16, PipeRendererTESR.displayInterval(19), "a small rate change keeps the spacing");
        assertEquals(PipeRendererTESR.MIN_ITEM_INTERVAL, PipeRendererTESR.displayInterval(1));
    }

    /** The path coordinate of an icon in a straight east-bound line of pipes at x = 1000 and up. */
    private static List<Double> eastBoundIcons(double time, float interval) {
        List<Double> coordinates = new ArrayList<>();
        for (int x = 1000; x < 1004; x++) {
            for (float progress : PipeRendererTESR.itemProgresses(time, new BlockPos(x, 64, -300), Direction.WEST.get3DDataValue(),
                    Direction.EAST.get3DDataValue(), interval))
                coordinates.add(x - 0.5 + progress);
        }
        return coordinates;
    }

    @Test
    void itemIconsFlowAcrossNeighbouringPipes() {
        float interval = 4;
        double spacing = interval / PipeRendererTESR.ITEM_CROSSING_TICKS;
        double time = 123_456_789.25;
        List<Double> now = eastBoundIcons(time, interval);
        assertEquals(Math.round(4 / spacing), now.size(), "evenly spaced over four pipes");
        for (int i = 1; i < now.size(); i++) {
            assertEquals(spacing, now.get(i) - now.get(i - 1), 1e-6, "even spacing across pipe borders");
        }
        // A quarter of a crossing later every icon is a quarter of a block further on, in whichever pipe it now is.
        List<Double> later = eastBoundIcons(time + PipeRendererTESR.ITEM_CROSSING_TICKS / 4.0, interval);
        for (double coordinate : now) {
            double moved = coordinate + 0.25;
            if (moved < 1003.5)
                assertTrue(later.stream().anyMatch(c -> Math.abs(c - moved) < 1e-6), "icon at " + coordinate + " did not move on");
        }
    }

    @Test
    void itemIconsMoveTowardsTheExit() {
        BlockPos pos = new BlockPos(-7, 70, 12);
        int down = Direction.UP.get3DDataValue();
        int out = Direction.DOWN.get3DDataValue();
        // One icon every 4 blocks; at tick 18 it is 0.3 of the way down this pipe (path coordinate -y = -70.2).
        float[] before = PipeRendererTESR.itemProgresses(18, pos, down, out, 40);
        float[] after = PipeRendererTESR.itemProgresses(19, pos, down, out, 40);
        assertEquals(1, before.length);
        assertEquals(1, after.length);
        assertEquals(0.3F, before[0], 1e-5F);
        assertEquals(0.1F, after[0] - before[0], 1e-5F);
    }
}
