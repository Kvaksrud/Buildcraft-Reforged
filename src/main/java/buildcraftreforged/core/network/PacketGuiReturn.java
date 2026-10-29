/*
 * Ported from buildcraft/core/network/PacketGuiReturn.java (BuildCraft 4.2.2, MMPL; the original has no header).
 * Modified by Buildcraft Reforged, 2026: a server-bound CustomPacketPayload with an explicit StreamCodec. It carries
 * the open menu's container id instead of a dimension id, and the entity form is dropped (no 4.2.2 GUI used it).
 * Reading and validation moved to PacketHandler.
 */

package buildcraftreforged.core.network;

import buildcraftreforged.BuildcraftReforged;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * Client-to-server: a GUI sends its block entity's settings back. The server applies it only when the sender has this
 * container open, the container belongs to the block at {@link #pos()}, the sender is within reach, and
 * {@link IGuiReturnHandler#readGuiData} accepts every value.
 *
 * @author CovertJaguar <railcraft.wikispaces.com>
 */
public final class PacketGuiReturn implements CustomPacketPayload {
    public static final Type<PacketGuiReturn> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "gui_return"));

    public static final StreamCodec<ByteBuf, PacketGuiReturn> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PacketGuiReturn::containerId,
            BlockPos.STREAM_CODEC, PacketGuiReturn::pos,
            ByteBufCodecs.byteArray(PacketData.MAX_PAYLOAD_BYTES), PacketGuiReturn::data,
            PacketGuiReturn::new);

    private final int containerId;
    private final BlockPos pos;
    private final byte[] data;

    public PacketGuiReturn(int containerId, IGuiReturnHandler obj) {
        this(containerId, obj, null);
    }

    public PacketGuiReturn(int containerId, IGuiReturnHandler obj, byte @Nullable [] extraData) {
        this(containerId, obj.getBlockPos(), guiData(obj, extraData));
    }

    PacketGuiReturn(int containerId, BlockPos pos, byte[] data) {
        this.containerId = containerId;
        this.pos = pos.immutable();
        this.data = data;
    }

    private static byte[] guiData(IGuiReturnHandler obj, byte @Nullable [] extraData) {
        if (obj.getLevel() == null) {
            throw new IllegalStateException("GUI return handler at " + obj.getBlockPos() + " has no level");
        }
        return PacketData.write(obj.getLevel().registryAccess(), data -> {
            obj.writeGuiData(data);
            if (extraData != null) {
                data.writeBytes(extraData);
            }
        });
    }

    public int containerId() {
        return containerId;
    }

    public BlockPos pos() {
        return pos;
    }

    public byte[] data() {
        return data;
    }

    /** Client only. */
    public void sendPacket() {
        ClientPacketDistributor.sendToServer(this);
    }

    @Override
    public Type<PacketGuiReturn> type() {
        return TYPE;
    }
}
