package buildcraftreforged.core.network;

import io.netty.buffer.Unpooled;
import java.util.function.Consumer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;

/**
 * Helpers for the opaque byte payloads that tile states, GUI returns and widgets carry, and bounded reads for the
 * server side. Replaces 4.2.2's {@code DataOutputStream}/{@code DataInputStream} plumbing.
 *
 * <p>Every read of a client-supplied value on the server goes through one of the {@code read...} methods with the
 * range the value may take. A value outside the range throws {@link InvalidPacketException}, and the packet is
 * dropped before anything is applied.
 */
public final class PacketData {
    /** Upper bound for one GUI return, widget or tile state payload, in bytes. */
    public static final int MAX_PAYLOAD_BYTES = 32767;

    private PacketData() {}

    /** Runs {@code writer} against a fresh buffer and returns what it wrote. */
    public static byte[] write(RegistryAccess registryAccess, Consumer<RegistryFriendlyByteBuf> writer) {
        RegistryFriendlyByteBuf buf =
                new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess, ConnectionType.NEOFORGE);
        try {
            writer.accept(buf);
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return bytes;
        } finally {
            buf.release();
        }
    }

    /** Wraps a received payload for reading. */
    public static RegistryFriendlyByteBuf wrap(byte[] data, RegistryAccess registryAccess) {
        return new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(data), registryAccess, ConnectionType.NEOFORGE);
    }

    public static int readInt(RegistryFriendlyByteBuf data, int min, int max) throws InvalidPacketException {
        require(data, Integer.BYTES);
        return checkRange(data.readInt(), min, max);
    }

    public static int readVarInt(RegistryFriendlyByteBuf data, int min, int max) throws InvalidPacketException {
        try {
            return checkRange(data.readVarInt(), min, max);
        } catch (RuntimeException e) {
            throw new InvalidPacketException("malformed var int: " + e.getMessage());
        }
    }

    public static int readByte(RegistryFriendlyByteBuf data, int min, int max) throws InvalidPacketException {
        require(data, Byte.BYTES);
        return checkRange(data.readByte(), min, max);
    }

    public static boolean readBoolean(RegistryFriendlyByteBuf data) throws InvalidPacketException {
        return readByte(data, 0, 1) == 1;
    }

    public static <E extends Enum<E>> E readEnum(RegistryFriendlyByteBuf data, Class<E> type)
            throws InvalidPacketException {
        E[] values = type.getEnumConstants();
        return values[readVarInt(data, 0, values.length - 1)];
    }

    public static String readUtf(RegistryFriendlyByteBuf data, int maxLength) throws InvalidPacketException {
        try {
            return data.readUtf(maxLength);
        } catch (RuntimeException e) {
            throw new InvalidPacketException("malformed string: " + e.getMessage());
        }
    }

    public static int checkRange(int value, int min, int max) throws InvalidPacketException {
        if (value < min || value > max) {
            throw new InvalidPacketException("value " + value + " outside [" + min + ", " + max + "]");
        }
        return value;
    }

    private static void require(RegistryFriendlyByteBuf data, int bytes) throws InvalidPacketException {
        if (data.readableBytes() < bytes) {
            throw new InvalidPacketException("payload ends early");
        }
    }
}
