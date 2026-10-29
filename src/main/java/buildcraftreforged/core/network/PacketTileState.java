/*
 * Ported from buildcraft/core/network/PacketTileState.java (BuildCraft 4.2.2, MMPL; the original has no header).
 * Modified by Buildcraft Reforged, 2026: a client-bound CustomPacketPayload with an explicit StreamCodec; each state is
 * written to its own length-prefixed byte array when it is added, so one bad state cannot corrupt the next.
 */

package buildcraftreforged.core.network;

import buildcraftreforged.BuildcraftReforged;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server-to-client: refreshes some {@link IClientState}s of an {@link ISyncedTile}. Build one with
 * {@link #PacketTileState(BlockPos, RegistryAccess)} and {@link #addStateForSerialization}, then send it with
 * {@code PacketDistributor.sendToPlayersTrackingChunk(level, new ChunkPos(pos), packet)}.
 */
public final class PacketTileState implements CustomPacketPayload {
    public static final int MAX_STATES = 32;

    public static final Type<PacketTileState> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "tile_state"));

    public record StateWithId(byte stateId, byte[] data) {
        static final StreamCodec<ByteBuf, StateWithId> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BYTE, StateWithId::stateId,
                ByteBufCodecs.byteArray(PacketData.MAX_PAYLOAD_BYTES), StateWithId::data,
                StateWithId::new);
    }

    public static final StreamCodec<ByteBuf, PacketTileState> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, PacketTileState::pos,
            StateWithId.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_STATES)), PacketTileState::states,
            PacketTileState::new);

    private final BlockPos pos;
    private final List<StateWithId> stateList;
    private final RegistryAccess registryAccess;

    /**
     * Constructor for outgoing packets
     *
     * @param pos the position of the tile to sync
     */
    public PacketTileState(BlockPos pos, RegistryAccess registryAccess) {
        this.pos = pos.immutable();
        this.stateList = new ArrayList<>();
        this.registryAccess = registryAccess;
    }

    /** Constructor for incoming packets. */
    PacketTileState(BlockPos pos, List<StateWithId> states) {
        this.pos = pos;
        this.stateList = List.copyOf(states);
        this.registryAccess = RegistryAccess.EMPTY;
    }

    public BlockPos pos() {
        return pos;
    }

    public List<StateWithId> states() {
        return stateList;
    }

    public void addStateForSerialization(byte stateId, IClientState state) {
        if (stateList.size() >= MAX_STATES) {
            throw new IllegalStateException("A tile state packet holds at most " + MAX_STATES + " states");
        }
        byte[] data = PacketData.write(registryAccess, state::writeData);
        if (data.length > PacketData.MAX_PAYLOAD_BYTES) {
            throw new IllegalStateException("State " + stateId + " is " + data.length + " bytes, more than "
                    + PacketData.MAX_PAYLOAD_BYTES);
        }
        stateList.add(new StateWithId(stateId, data));
    }

    /** Client side: reads each state into the tile's matching {@link IClientState}. */
    public void applyStates(ISyncedTile tile, RegistryAccess registryAccess) {
        for (StateWithId stateWithId : stateList) {
            IClientState state = tile.getStateInstance(stateWithId.stateId());
            if (state == null) {
                BuildcraftReforged.LOGGER.warn("Tile state packet at {} has unknown state id {}", pos,
                        stateWithId.stateId());
                continue;
            }
            RegistryFriendlyByteBuf data = PacketData.wrap(stateWithId.data(), registryAccess);
            try {
                state.readData(data);
            } finally {
                data.release();
            }
            tile.afterStateUpdated(stateWithId.stateId());
        }
    }

    @Override
    public Type<PacketTileState> type() {
        return TYPE;
    }
}
