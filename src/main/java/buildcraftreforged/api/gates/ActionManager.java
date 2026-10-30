/*
 * Modified by Buildcraft Reforged, 2026: ported from buildcraft/api/gates/ActionManager.java (no file header at
 * 4.2.2); the trigger and action registries replace the numeric legacy ids, BlockEntity replaces TileEntity.
 */

package buildcraftreforged.api.gates;

import buildcraftreforged.api.transport.IPipe;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.registries.RegistryBuilder;
import org.jspecify.annotations.Nullable;

public class ActionManager {

    /**
     * The trigger registry, {@code buildcraftreforged:trigger}. Gates save and sync triggers by registry id; 4.2.2
     * saved them by unique tag and, before that, by numeric id. Registered in {@code NewRegistryEvent} by the core
     * module.
     */
    public static final ResourceKey<Registry<ITrigger>> TRIGGER_REGISTRY_KEY =
            ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath("buildcraftreforged", "trigger"));
    public static final ResourceKey<Registry<IAction>> ACTION_REGISTRY_KEY =
            ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath("buildcraftreforged", "action"));
    public static final Registry<ITrigger> TRIGGER_REGISTRY =
            new RegistryBuilder<>(TRIGGER_REGISTRY_KEY).sync(true).create();
    public static final Registry<IAction> ACTION_REGISTRY =
            new RegistryBuilder<>(ACTION_REGISTRY_KEY).sync(true).create();

    /** Codecs for saving triggers and actions, and for data components (gate items, P3). */
    public static final Codec<ITrigger> TRIGGER_CODEC = TRIGGER_REGISTRY.byNameCodec();
    public static final Codec<IAction> ACTION_CODEC = ACTION_REGISTRY.byNameCodec();
    public static final StreamCodec<RegistryFriendlyByteBuf, ITrigger> TRIGGER_STREAM_CODEC =
            ByteBufCodecs.registry(TRIGGER_REGISTRY_KEY);
    public static final StreamCodec<RegistryFriendlyByteBuf, IAction> ACTION_STREAM_CODEC =
            ByteBufCodecs.registry(ACTION_REGISTRY_KEY);

    /** By 4.2.2 unique tag, for 4.2.2 saves (plan P9). New code looks up the registries. */
    public static Map<String, ITrigger> triggers = new HashMap<String, ITrigger>();
    public static Map<String, IAction> actions = new HashMap<String, IAction>();
    private static LinkedList<ITriggerProvider> triggerProviders = new LinkedList<ITriggerProvider>();
    private static LinkedList<IActionProvider> actionProviders = new LinkedList<IActionProvider>();

    public static void registerTriggerProvider(ITriggerProvider provider) {
        if (provider != null && !triggerProviders.contains(provider)) {
            triggerProviders.add(provider);
        }
    }

    public static void registerTrigger(ITrigger trigger) {
        triggers.put(trigger.getUniqueTag(), trigger);
    }

    public static void registerAction(IAction action) {
        actions.put(action.getUniqueTag(), action);
    }

    public static LinkedList<ITrigger> getNeighborTriggers(Block block, @Nullable BlockEntity entity) {
        LinkedList<ITrigger> triggers = new LinkedList<ITrigger>();

        for (ITriggerProvider provider : triggerProviders) {
            LinkedList<ITrigger> toAdd = provider.getNeighborTriggers(block, entity);

            if (toAdd != null) {
                for (ITrigger t : toAdd) {
                    if (!triggers.contains(t)) {
                        triggers.add(t);
                    }
                }
            }
        }

        return triggers;
    }

    public static void registerActionProvider(IActionProvider provider) {
        if (provider != null && !actionProviders.contains(provider)) {
            actionProviders.add(provider);
        }
    }

    //TODO: Use sets not lists
    public static LinkedList<IAction> getNeighborActions(Block block, @Nullable BlockEntity entity) {
        LinkedList<IAction> actions = new LinkedList<IAction>();

        for (IActionProvider provider : actionProviders) {
            LinkedList<IAction> toAdd = provider.getNeighborActions(block, entity);

            if (toAdd != null) {
                for (IAction t : toAdd) {
                    if (!actions.contains(t)) {
                        actions.add(t);
                    }
                }
            }
        }

        return actions;
    }

    public static LinkedList<ITrigger> getPipeTriggers(IPipe pipe) {
        LinkedList<ITrigger> triggers = new LinkedList<ITrigger>();

        for (ITriggerProvider provider : triggerProviders) {
            LinkedList<ITrigger> toAdd = provider.getPipeTriggers(pipe);

            if (toAdd != null) {
                for (ITrigger t : toAdd) {
                    if (!triggers.contains(t)) {
                        triggers.add(t);
                    }
                }
            }
        }

        return triggers;
    }

    public static @Nullable ITrigger getTrigger(Identifier id) {
        return TRIGGER_REGISTRY.getValue(id);
    }

    public static @Nullable IAction getAction(Identifier id) {
        return ACTION_REGISTRY.getValue(id);
    }
}
