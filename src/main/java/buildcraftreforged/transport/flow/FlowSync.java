package buildcraftreforged.transport.flow;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.core.network.PacketHandler;
import buildcraftreforged.transport.TilePipe;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Measures and sends flow records (docs/TRANSPORT.md 9.2, 9.3). A pipe joins its level's active set when something
 * passes it. At every 10-tick boundary ({@code gameTime % 10 == 0}, after the level tick) each active pipe measures its
 * record; records that differ enough from what the clients have are batched per chunk into one
 * {@link FlowRecordsPayload} sent to the players tracking that chunk. A pipe leaves the set once nothing flows and the
 * clients know it, so idle pipes cost nothing.
 */
@EventBusSubscriber(modid = BuildcraftReforged.MOD_ID)
public final class FlowSync {

    private static final Map<Level, Set<TilePipe>> ACTIVE = new WeakHashMap<>();

    /** See every payload before it is sent (GameTests); empty outside tests. */
    public static final List<BiConsumer<ServerLevel, FlowRecordsPayload>> SEND_LISTENERS = new CopyOnWriteArrayList<>();

    private FlowSync() {
    }

    /** The client half is registered by {@code TransportClient}. */
    @SubscribeEvent
    static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar(PacketHandler.PROTOCOL_VERSION).playToClient(FlowRecordsPayload.TYPE,
                FlowRecordsPayload.STREAM_CODEC);
    }

    /** Puts {@code pipe} on its level's list of pipes measured at each boundary. Server only. */
    public static void markActive(TilePipe pipe) {
        if (pipe.getLevel() instanceof ServerLevel level) {
            synchronized (ACTIVE) {
                ACTIVE.computeIfAbsent(level, key -> Collections.newSetFromMap(new IdentityHashMap<>())).add(pipe);
            }
        }
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && level.getGameTime() % FlowAccumulator.BUCKET_TICKS == 0)
            boundary(level);
    }

    @SubscribeEvent
    static void onLevelUnload(LevelEvent.Unload event) {
        synchronized (ACTIVE) {
            ACTIVE.remove(event.getLevel());
        }
    }

    /** One boundary: measures every active pipe and sends what changed, one payload per chunk. */
    public static void boundary(ServerLevel level) {
        Set<TilePipe> active;
        synchronized (ACTIVE) {
            active = ACTIVE.get(level);
        }
        if (active == null || active.isEmpty())
            return;
        long now = level.getGameTime();
        Map<Long, List<TilePipe>> changedByChunk = new LinkedHashMap<>();
        for (Iterator<TilePipe> it = active.iterator(); it.hasNext(); ) {
            TilePipe pipe = it.next();
            if (pipe.isRemoved() || pipe.getLevel() != level) {
                it.remove();
                continue;
            }
            FlowTracker tracker = pipe.getFlowTracker();
            if (tracker.boundary(now, pipe.isFlowOverloaded()) != null)
                changedByChunk.computeIfAbsent(ChunkPos.containing(pipe.getBlockPos()).pack(), key -> new ArrayList<>())
                        .add(pipe);
            if (tracker.isIdle())
                it.remove();
        }
        changedByChunk.forEach((packed, pipes) -> {
            int[] localPos = new int[pipes.size()];
            List<FlowRecord> records = new ArrayList<>(pipes.size());
            for (int i = 0; i < pipes.size(); i++) {
                BlockPos pos = pipes.get(i).getBlockPos();
                localPos[i] = FlowRecordsPayload.pack(pos);
                records.add(pipes.get(i).getFlowTracker().sent());
            }
            FlowRecordsPayload payload = new FlowRecordsPayload(ChunkPos.unpack(packed), localPos, records);
            for (BiConsumer<ServerLevel, FlowRecordsPayload> listener : SEND_LISTENERS)
                listener.accept(level, payload);
            // Only players whose connection negotiated the payload: a client without the mod, or a GameTest mock
            // player, would otherwise make NeoForge throw (as TileBuildCraft.sendNetworkUpdate).
            for (ServerPlayer player : level.getChunkSource().chunkMap.getPlayers(payload.chunk(), false)) {
                if (player.connection.hasChannel(FlowRecordsPayload.TYPE))
                    PacketDistributor.sendToPlayer(player, payload);
            }
        });
    }

    /** How many pipes of {@code level} are measured at each boundary (for tests). */
    public static int activeCount(ServerLevel level) {
        synchronized (ACTIVE) {
            Set<TilePipe> active = ACTIVE.get(level);
            return active == null ? 0 : active.size();
        }
    }
}
