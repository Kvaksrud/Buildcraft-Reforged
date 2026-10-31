package buildcraftreforged.transport.flow;

import buildcraftreforged.BuildcraftReforged;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;

/**
 * Server-to-client: the flow records of one chunk's pipes that changed at one 10-tick boundary (docs/TRANSPORT.md 9.3).
 * {@code localPos[i]} is the position of the pipe of {@code records[i]} in the chunk, packed by {@link #pack}: 4 bits x,
 * 4 bits z, and y + {@link #Y_OFFSET} above them, written as a VarInt (3 bytes for any build height).
 */
public record FlowRecordsPayload(ChunkPos chunk, int[] localPos, List<FlowRecord> records) implements CustomPacketPayload {

    public static final Type<FlowRecordsPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "flow_records"));

    /** More than a chunk can hold of pipes at any build height; a longer list is refused. */
    public static final int MAX_RECORDS = 1 << 17;
    static final int Y_OFFSET = 1 << 11;

    public static final StreamCodec<RegistryFriendlyByteBuf, FlowRecordsPayload> STREAM_CODEC = StreamCodec.of(
            FlowRecordsPayload::encode, FlowRecordsPayload::decode);

    public FlowRecordsPayload {
        if (localPos.length != records.size())
            throw new IllegalArgumentException(localPos.length + " positions for " + records.size() + " records");
    }

    public static int pack(BlockPos pos) {
        return (pos.getY() + Y_OFFSET) << 8 | (pos.getZ() & 15) << 4 | (pos.getX() & 15);
    }

    /** The block position of entry {@code i}. */
    public BlockPos pos(int i) {
        int packed = localPos[i];
        return new BlockPos(chunk.getMinBlockX() + (packed & 15), (packed >>> 8) - Y_OFFSET,
                chunk.getMinBlockZ() + (packed >> 4 & 15));
    }

    private static void encode(RegistryFriendlyByteBuf buffer, FlowRecordsPayload payload) {
        ChunkPos.STREAM_CODEC.encode(buffer, payload.chunk);
        buffer.writeVarInt(payload.localPos.length);
        for (int i = 0; i < payload.localPos.length; i++) {
            buffer.writeVarInt(payload.localPos[i]);
            FlowRecord.STREAM_CODEC.encode(buffer, payload.records.get(i));
        }
    }

    private static FlowRecordsPayload decode(RegistryFriendlyByteBuf buffer) {
        ChunkPos chunk = ChunkPos.STREAM_CODEC.decode(buffer);
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_RECORDS)
            throw new DecoderException("Bad flow record count " + count);
        int[] localPos = new int[count];
        List<FlowRecord> records = new ArrayList<>(Math.min(count, 1024));
        for (int i = 0; i < count; i++) {
            localPos[i] = buffer.readVarInt();
            records.add(FlowRecord.STREAM_CODEC.decode(buffer));
        }
        return new FlowRecordsPayload(chunk, localPos, records);
    }

    @Override
    public Type<FlowRecordsPayload> type() {
        return TYPE;
    }
}
