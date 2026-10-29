package buildcraftreforged.core.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class PacketCodecTest {
    private static final BlockPos POS = new BlockPos(-120, 64, 30_000);

    private static <T> T roundTrip(StreamCodec<ByteBuf, T> codec, T value) {
        ByteBuf buf = Unpooled.buffer();
        codec.encode(buf, value);
        T decoded = codec.decode(buf);
        assertEquals(0, buf.readableBytes(), "codec left bytes unread");
        return decoded;
    }

    private static <T> ByteBuf encode(StreamCodec<ByteBuf, T> codec, T value) {
        ByteBuf buf = Unpooled.buffer();
        codec.encode(buf, value);
        return buf;
    }

    @Test
    void guiReturnRoundTrips() {
        byte[] data = {1, 2, 3, -1};
        PacketGuiReturn decoded = roundTrip(PacketGuiReturn.STREAM_CODEC, new PacketGuiReturn(7, POS, data));
        assertEquals(7, decoded.containerId());
        assertEquals(POS, decoded.pos());
        assertArrayEquals(data, decoded.data());
    }

    @Test
    void guiWidgetRoundTrips() {
        byte[] data = {9, 8, 7};
        PacketGuiWidget decoded = roundTrip(PacketGuiWidget.STREAM_CODEC, new PacketGuiWidget(3, 2, data));
        assertEquals(3, decoded.windowId());
        assertEquals(2, decoded.widgetId());
        assertArrayEquals(data, decoded.payload());
    }

    @Test
    void tileStateRoundTripsAndApplies() {
        TestState core = new TestState(42, "pipe");
        TestState render = new TestState(-5, "");
        PacketTileState packet = new PacketTileState(POS, RegistryAccess.EMPTY);
        packet.addStateForSerialization((byte) 0, core);
        packet.addStateForSerialization((byte) 1, render);

        PacketTileState decoded = roundTrip(PacketTileState.STREAM_CODEC, packet);
        assertEquals(POS, decoded.pos());
        assertEquals(2, decoded.states().size());

        TestTile tile = new TestTile();
        decoded.applyStates(tile, RegistryAccess.EMPTY);
        assertEquals(42, tile.states[0].number);
        assertEquals("pipe", tile.states[0].text);
        assertEquals(-5, tile.states[1].number);
        assertEquals("", tile.states[1].text);
        assertEquals(List.of((byte) 0, (byte) 1), tile.updated);
    }

    @Test
    void tileStateSkipsUnknownStateIds() {
        PacketTileState packet = new PacketTileState(POS, RegistryAccess.EMPTY);
        packet.addStateForSerialization((byte) 9, new TestState(1, "x"));
        packet.addStateForSerialization((byte) 1, new TestState(2, "y"));

        TestTile tile = new TestTile();
        roundTrip(PacketTileState.STREAM_CODEC, packet).applyStates(tile, RegistryAccess.EMPTY);
        assertEquals(2, tile.states[1].number);
        assertEquals(List.of((byte) 1), tile.updated);
    }

    @Test
    void tileStateRefusesTooManyStates() {
        PacketTileState packet = new PacketTileState(POS, RegistryAccess.EMPTY);
        for (int i = 0; i < PacketTileState.MAX_STATES; i++) {
            packet.addStateForSerialization((byte) i, new TestState(i, ""));
        }
        assertThrows(IllegalStateException.class,
                () -> packet.addStateForSerialization((byte) 0, new TestState(0, "")));

        List<PacketTileState.StateWithId> tooMany = new ArrayList<>(packet.states());
        tooMany.add(new PacketTileState.StateWithId((byte) 0, new byte[0]));
        ByteBuf buf = encodeUnchecked(POS, tooMany);
        assertThrows(RuntimeException.class, () -> PacketTileState.STREAM_CODEC.decode(buf));
    }

    @Test
    void truncatedPacketsFailToDecode() {
        ByteBuf full = encode(PacketGuiReturn.STREAM_CODEC, new PacketGuiReturn(7, POS, new byte[] {1, 2, 3, 4}));
        ByteBuf truncated = full.slice(0, full.readableBytes() - 2);
        assertThrows(RuntimeException.class, () -> PacketGuiReturn.STREAM_CODEC.decode(truncated));

        ByteBuf widget = encode(PacketGuiWidget.STREAM_CODEC, new PacketGuiWidget(1, 1, new byte[] {5, 6}));
        ByteBuf widgetTruncated = widget.slice(0, 1);
        assertThrows(RuntimeException.class, () -> PacketGuiWidget.STREAM_CODEC.decode(widgetTruncated));
    }

    @Test
    void oversizedGuiReturnFailsToDecode() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(7);
        BlockPos.STREAM_CODEC.encode(buf, POS);
        buf.writeVarInt(PacketData.MAX_PAYLOAD_BYTES + 1);
        buf.writeBytes(new byte[PacketData.MAX_PAYLOAD_BYTES + 1]);
        assertThrows(RuntimeException.class, () -> PacketGuiReturn.STREAM_CODEC.decode(buf));
    }

    /** Writes a tile state packet without the sender-side limit, as a modified server could. */
    private static ByteBuf encodeUnchecked(BlockPos pos, List<PacketTileState.StateWithId> states) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        BlockPos.STREAM_CODEC.encode(buf, pos);
        buf.writeVarInt(states.size());
        for (PacketTileState.StateWithId state : states) {
            buf.writeByte(state.stateId());
            buf.writeByteArray(state.data());
        }
        return buf;
    }

    static final class TestState implements IClientState {
        int number;
        String text;

        TestState(int number, String text) {
            this.number = number;
            this.text = text;
        }

        @Override
        public void writeData(RegistryFriendlyByteBuf data) {
            data.writeVarInt(number);
            data.writeUtf(text);
        }

        @Override
        public void readData(RegistryFriendlyByteBuf data) {
            number = data.readVarInt();
            text = data.readUtf();
        }
    }

    static final class TestTile implements ISyncedTile {
        final TestState[] states = {new TestState(0, "?"), new TestState(0, "?")};
        final List<Byte> updated = new ArrayList<>();

        @Override
        public @Nullable IClientState getStateInstance(byte stateId) {
            return stateId >= 0 && stateId < states.length ? states[stateId] : null;
        }

        @Override
        public void afterStateUpdated(byte stateId) {
            updated.add(stateId);
        }
    }
}
