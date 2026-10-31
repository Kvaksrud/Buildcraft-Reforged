package buildcraftreforged.transport;

import buildcraftreforged.api.gates.ActionManager;
import buildcraftreforged.api.gates.IAction;
import buildcraftreforged.api.gates.ITrigger;
import buildcraftreforged.api.gates.ITriggerParameter;
import buildcraftreforged.api.gates.TriggerParameter;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The programming of a gate, kept on the gate item (data component {@code buildcraftreforged:gate_settings}) when the
 * gate is taken off a pipe, and put back when it is fitted again (plan section 6, item NBT → data components). 4.2.2
 * kept triggers only in the pipe's save and lost them when the gate came off. One entry per gate position that holds a
 * trigger, a parameter or an action.
 */
public record GateSettings(List<Entry> entries) {

    public static final GateSettings EMPTY = new GateSettings(List.of());

    public record Entry(int position, Optional<ITrigger> trigger, ItemStack parameter, Optional<IAction> action) {

        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(0, 7).fieldOf("position").forGetter(Entry::position),
                ActionManager.TRIGGER_CODEC.optionalFieldOf("trigger").forGetter(Entry::trigger),
                ItemStack.OPTIONAL_CODEC.optionalFieldOf("parameter", ItemStack.EMPTY).forGetter(Entry::parameter),
                ActionManager.ACTION_CODEC.optionalFieldOf("action").forGetter(Entry::action))
                .apply(instance, Entry::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Entry::position,
                ByteBufCodecs.optional(ActionManager.TRIGGER_STREAM_CODEC), Entry::trigger,
                ItemStack.OPTIONAL_STREAM_CODEC, Entry::parameter,
                ByteBufCodecs.optional(ActionManager.ACTION_STREAM_CODEC), Entry::action,
                Entry::new);

        /** Item stacks have no value equality; components need it. */
        @Override
        public boolean equals(Object other) {
            return other instanceof Entry entry && position == entry.position && trigger.equals(entry.trigger)
                    && action.equals(entry.action) && ItemStack.matches(parameter, entry.parameter);
        }

        @Override
        public int hashCode() {
            return Objects.hash(position, trigger, action, ItemStack.hashItemAndComponents(parameter));
        }
    }

    public static final Codec<GateSettings> CODEC =
            Entry.CODEC.sizeLimitedListOf(8).xmap(GateSettings::new, GateSettings::entries);
    public static final StreamCodec<RegistryFriendlyByteBuf, GateSettings> STREAM_CODEC =
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list(8)).map(GateSettings::new, GateSettings::entries);

    public GateSettings {
        entries = List.copyOf(entries);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public static GateSettings of(@Nullable ITrigger[] triggers, @Nullable ITriggerParameter[] parameters,
            @Nullable IAction[] actions) {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            ItemStack parameter = parameters[i] == null ? ItemStack.EMPTY : parameters[i].getItemStack().copy();
            if (triggers[i] != null || actions[i] != null || !parameter.isEmpty())
                entries.add(new Entry(i, Optional.ofNullable(triggers[i]), parameter, Optional.ofNullable(actions[i])));
        }
        return entries.isEmpty() ? EMPTY : new GateSettings(entries);
    }

    /** Puts the settings into a gate with {@code positions} trigger/action pairs; entries beyond them are dropped. */
    public void applyTo(@Nullable ITrigger[] triggers, @Nullable ITriggerParameter[] parameters,
            @Nullable IAction[] actions, int positions) {
        for (Entry entry : entries) {
            int i = entry.position();
            if (i >= positions)
                continue;
            triggers[i] = entry.trigger().orElse(null);
            actions[i] = entry.action().orElse(null);
            parameters[i] = entry.parameter().isEmpty() ? null : TriggerParameter.of(entry.parameter());
        }
    }
}
