/*
 * Buildcraft Reforged, 2026: new file, no 4.2.2 original. One reading of the status panel's six lines (plan section 10).
 */
package buildcraftreforged.core.status;

import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.function.IntFunction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.Nullable;

/**
 * What the status panel shows, read from an {@link IStatusProvider} on the server and sent to the player who has the
 * panel open. A null line is not shown: a block without power has no Power line, only engines have a Heat line.
 *
 * @param state the State line
 * @param reason the Reason line; {@link StatusReason#NONE} hides it
 * @param flow the Flow line: what moved over the last second
 * @param power the Power line
 * @param heat the Heat line (engines)
 * @param owner the Owner line: the name of the player who placed the block
 */
public record StatusReport(MachineStatus state, StatusReason reason, @Nullable Flow flow, @Nullable Power power,
        @Nullable Heat heat, @Nullable String owner) {

    /** Longest owner name sent; player names are at most 16 characters. */
    public static final int MAX_OWNER_LENGTH = 64;

    /**
     * Items and fluid moved over the last second ({@link FlowMeter#WINDOW} ticks).
     *
     * @param itemsIn items per second in
     * @param itemsOut items per second out
     * @param fluidIn mB per second in; the panel shows mB/t
     * @param fluidOut mB per second out
     */
    public record Flow(int itemsIn, int itemsOut, int fluidIn, int fluidOut) {
        public static final Flow NONE = new Flow(0, 0, 0, 0);

        public static final StreamCodec<ByteBuf, Flow> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Flow::itemsIn,
                ByteBufCodecs.VAR_INT, Flow::itemsOut,
                ByteBufCodecs.VAR_INT, Flow::fluidIn,
                ByteBufCodecs.VAR_INT, Flow::fluidOut,
                Flow::new);

        public boolean hasItems() {
            return itemsIn > 0 || itemsOut > 0;
        }

        public boolean hasFluid() {
            return fluidIn > 0 || fluidOut > 0;
        }
    }

    /**
     * @param rate FE/t drawn (a consumer) or produced (an engine), averaged over the last second
     * @param producing true when {@link #rate} is produced
     * @param stored FE stored
     * @param capacity FE that fit
     */
    public record Power(float rate, boolean producing, long stored, long capacity) {
        public static final StreamCodec<ByteBuf, Power> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, Power::rate,
                ByteBufCodecs.BOOL, Power::producing,
                ByteBufCodecs.VAR_LONG, Power::stored,
                ByteBufCodecs.VAR_LONG, Power::capacity,
                Power::new);

        /** A consumer: the rate is what the meter counted with {@link FlowMeter#energyIn}. */
        public static Power drawn(FlowMeter meter, long stored, long capacity) {
            return new Power(meter.energyInPerSecond() / (float) FlowMeter.WINDOW, false, stored, capacity);
        }

        /** A producer: the rate is what the meter counted with {@link FlowMeter#energyOut}. */
        public static Power produced(FlowMeter meter, long stored, long capacity) {
            return new Power(meter.energyOutPerSecond() / (float) FlowMeter.WINDOW, true, stored, capacity);
        }
    }

    /**
     * @param stage the engine's heat stage
     * @param overheatSeconds seconds left on the overheat timer (plan section 9), or {@link #NO_COUNTDOWN}
     */
    public record Heat(HeatStage stage, int overheatSeconds) {
        public static final int NO_COUNTDOWN = -1;

        public static final StreamCodec<ByteBuf, Heat> STREAM_CODEC = StreamCodec.composite(
                enumCodec(HeatStage.values()), Heat::stage,
                ByteBufCodecs.VAR_INT, Heat::overheatSeconds,
                Heat::new);

        public Heat {
            overheatSeconds = Math.max(NO_COUNTDOWN, overheatSeconds);
        }

        public boolean hasCountdown() {
            return overheatSeconds >= 0;
        }
    }

    public static final StreamCodec<ByteBuf, StatusReport> STREAM_CODEC = StreamCodec.composite(
            enumCodec(MachineStatus.values()), StatusReport::state,
            enumCodec(StatusReason.values()), StatusReport::reason,
            ByteBufCodecs.optional(Flow.STREAM_CODEC), report -> Optional.ofNullable(report.flow()),
            ByteBufCodecs.optional(Power.STREAM_CODEC), report -> Optional.ofNullable(report.power()),
            ByteBufCodecs.optional(Heat.STREAM_CODEC), report -> Optional.ofNullable(report.heat()),
            ByteBufCodecs.optional(ByteBufCodecs.stringUtf8(MAX_OWNER_LENGTH)), report -> Optional.ofNullable(report.owner()),
            (state, reason, flow, power, heat, owner) -> new StatusReport(state, reason, flow.orElse(null),
                    power.orElse(null), heat.orElse(null), owner.orElse(null)));

    public StatusReport {
        if (owner != null && owner.length() > MAX_OWNER_LENGTH) {
            owner = owner.substring(0, MAX_OWNER_LENGTH);
        }
    }

    /** Reads every line from provider. Server side. */
    public static StatusReport of(IStatusProvider provider) {
        FlowMeter meter = provider.getStatusFlow();
        return new StatusReport(provider.getStatusState(), provider.getStatusReason(),
                meter == null ? null : meter.snapshot(), provider.getStatusPower(), provider.getStatusHeat(),
                provider.getStatusOwner());
    }

    /** An enum as its ordinal; an unknown ordinal fails to decode. */
    static <E extends Enum<E>> StreamCodec<ByteBuf, E> enumCodec(E[] values) {
        IntFunction<E> byId = id -> {
            if (id < 0 || id >= values.length) {
                throw new io.netty.handler.codec.DecoderException("Unknown ordinal " + id);
            }
            return values[id];
        };
        return ByteBufCodecs.idMapper(byId, Enum::ordinal);
    }
}
