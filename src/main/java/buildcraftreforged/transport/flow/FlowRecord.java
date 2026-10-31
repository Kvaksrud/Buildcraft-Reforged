package buildcraftreforged.transport.flow;

import buildcraftreforged.api.transport.IPipeTile.PipeType;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

/**
 * What flowed through one pipe over the last measuring window (docs/TRANSPORT.md 9.1): all a client needs to animate
 * the pipe, and what the Pipe Contents trigger reads. Built from a {@link FlowAccumulator} at every 10-tick boundary.
 *
 * @param kind     the pipe's kind
 * @param content  the {@link Item} or {@link Fluid} that last passed, null for power and when nothing flows
 * @param colour   the walk colour, a {@code DyeColor} id 0-15, or {@link #NO_COLOUR} (items only)
 * @param sides    bits 0-2: entry side 0-5 or {@link #CENTRE}; bits 3-5: exit side 0-5 or {@link #CENTRE} (a sink)
 * @param rate     items per second, mB per tick or FE per tick, over the last 20 ticks
 * @param overload power only: the overload counter of 7.4 is up
 */
public record FlowRecord(PipeType kind, @Nullable Holder<?> content, byte colour, byte sides, float rate,
        boolean overload) {

    public static final byte NO_COLOUR = 16;
    /** The entry side of a transfer that started inside the pipe, and the exit side at a Void sink. */
    public static final int CENTRE = 6;
    public static final byte NO_SIDES = (byte) (CENTRE | CENTRE << 3);
    /** The relative change of rate that is worth sending (docs/TRANSPORT.md 9.3). */
    public static final float SEND_THRESHOLD = 0.125F;

    private static final StreamCodec<RegistryFriendlyByteBuf, Holder<Item>> ITEM = ByteBufCodecs.holderRegistry(Registries.ITEM);
    private static final StreamCodec<RegistryFriendlyByteBuf, Holder<Fluid>> FLUID = ByteBufCodecs.holderRegistry(Registries.FLUID);
    private static final PipeType[] KINDS = PipeType.values();

    /**
     * kind (byte), content (a presence byte and the registry id, items and fluids only), colour, sides, rate (float),
     * overload (bool): 12 bytes at most.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, FlowRecord> STREAM_CODEC = StreamCodec.of(
            FlowRecord::encode, FlowRecord::decode);

    public static FlowRecord none(PipeType kind) {
        return new FlowRecord(kind, null, NO_COLOUR, NO_SIDES, 0, false);
    }

    public boolean isFlowing() {
        return rate > 0;
    }

    public int entrySide() {
        return sides & 7;
    }

    public int exitSide() {
        return sides >> 3 & 7;
    }

    /** The {@code sides} byte for an entry and exit side, each 0-5 or {@link #CENTRE}. */
    public static byte sides(int entry, int exit) {
        return (byte) (entry | exit << 3);
    }

    /** The item or fluid holder of a routed resource, for {@code content}; null for anything else. */
    public static @Nullable Holder<?> contentOf(Object resource) {
        if (resource instanceof ItemResource item)
            return item.isEmpty() ? null : item.typeHolder();
        if (resource instanceof FluidResource fluid)
            return fluid.isEmpty() ? null : fluid.typeHolder();
        return null;
    }

    /**
     * The send rule of docs/TRANSPORT.md 9.3: whether this record differs enough from {@code sent}, the last one the
     * clients got, to send it. Content, colour, sides and overload changes, the rate crossing zero, and a rate change of
     * more than {@link #SEND_THRESHOLD} of the larger rate.
     */
    public boolean differsFrom(FlowRecord sent) {
        if (kind != sent.kind || !sameContent(content, sent.content) || colour != sent.colour || sides != sent.sides
                || overload != sent.overload || isFlowing() != sent.isFlowing())
            return true;
        return Math.abs(rate - sent.rate) > SEND_THRESHOLD * Math.max(rate, sent.rate);
    }

    private static boolean sameContent(@Nullable Holder<?> a, @Nullable Holder<?> b) {
        if (a == null || b == null)
            return a == b;
        return a.value() == b.value();
    }

    @SuppressWarnings("unchecked")
    private static void encode(RegistryFriendlyByteBuf buffer, FlowRecord record) {
        buffer.writeByte(record.kind.ordinal());
        if (record.kind == PipeType.ITEM || record.kind == PipeType.FLUID) {
            buffer.writeBoolean(record.content != null);
            if (record.content != null) {
                if (record.kind == PipeType.ITEM)
                    ITEM.encode(buffer, (Holder<Item>) record.content);
                else
                    FLUID.encode(buffer, (Holder<Fluid>) record.content);
            }
        }
        buffer.writeByte(record.colour);
        buffer.writeByte(record.sides);
        buffer.writeFloat(record.rate);
        buffer.writeBoolean(record.overload);
    }

    private static FlowRecord decode(RegistryFriendlyByteBuf buffer) {
        int kindIndex = buffer.readByte();
        if (kindIndex < 0 || kindIndex >= KINDS.length)
            throw new DecoderException("Unknown pipe kind " + kindIndex);
        PipeType kind = KINDS[kindIndex];
        Holder<?> content = null;
        if ((kind == PipeType.ITEM || kind == PipeType.FLUID) && buffer.readBoolean())
            content = kind == PipeType.ITEM ? ITEM.decode(buffer) : FLUID.decode(buffer);
        byte colour = buffer.readByte();
        byte sides = buffer.readByte();
        float rate = buffer.readFloat();
        boolean overload = buffer.readBoolean();
        if (colour < 0 || colour > NO_COLOUR || (sides & 7) > CENTRE || (sides >> 3 & 7) > CENTRE || (sides & 0xC0) != 0
                || !(rate >= 0) || Float.isInfinite(rate))
            throw new DecoderException("Bad flow record");
        return new FlowRecord(kind, content, colour, sides, rate, overload);
    }
}
