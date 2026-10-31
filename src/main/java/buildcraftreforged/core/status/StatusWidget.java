/*
 * Buildcraft Reforged, 2026: new file, no 4.2.2 original. Carries the status panel's data to the player who has it
 * open (plan section 10).
 */
package buildcraftreforged.core.status;

import buildcraftreforged.core.gui.BuildCraftContainer;
import buildcraftreforged.core.gui.widgets.Widget;
import buildcraftreforged.core.network.PacketData;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * An invisible widget that sends a {@link StatusReport} to a player only while that player has the status panel open.
 * A machine menu adds one ({@code addWidget(new StatusWidget(tile))}) and its screen adds a
 * {@link buildcraftreforged.core.gui.StatusLedger}; opening the ledger sends {@link PacketStatusWatch} with true,
 * closing it (or closing the menu) stops the data. {@link ContainerStatus}, the panel of blocks without a GUI, is
 * watched from the moment it opens.
 */
public class StatusWidget extends Widget {
    /** Fewest ticks between two reports to one player; an unchanged report is not sent again. */
    public static final int SEND_INTERVAL = 10;

    private final IStatusProvider provider;
    private final boolean watchedOnOpen;
    /** Server side: the players with the panel open, and what each was sent last. */
    private final Map<Player, Watch> watchers = new WeakHashMap<>();

    /** Client side: the last report the server sent, null until the first arrives. */
    public @Nullable StatusReport report;
    /** Client side: what this client last told the server. */
    private boolean watched;

    public StatusWidget(IStatusProvider provider) {
        this(provider, false);
    }

    /** @param watchedOnOpen true when the panel is the whole screen, so it is open as soon as the menu is */
    public StatusWidget(IStatusProvider provider, boolean watchedOnOpen) {
        super(0, 0, 0, 0, 0, 0);
        this.provider = provider;
        this.watchedOnOpen = watchedOnOpen;
        hidden = true;
    }

    /** The status widget of a menu, or null when it has none. */
    public static @Nullable StatusWidget find(BuildCraftContainer container) {
        for (Widget widget : container.getWidgets()) {
            if (widget instanceof StatusWidget status) {
                return status;
            }
        }
        return null;
    }

    @Override
    public void initWidget(Player player) {
        if (watchedOnOpen) {
            setWatching(player, true);
        }
    }

    /** Server side: starts or stops sending reports to player. */
    public void setWatching(Player player, boolean watching) {
        if (watching) {
            watchers.putIfAbsent(player, new Watch());
        } else {
            watchers.remove(player);
        }
    }

    public boolean isWatching(Player player) {
        return watchers.containsKey(player);
    }

    @Override
    public void updateWidget(Player player) {
        byte[] data = pollData(player);
        if (data != null) {
            container.sendWidgetDataToClient(this, player, data);
        }
    }

    /**
     * Server side: the report to send player now, or null when player is not watching, the last report went out less
     * than {@link #SEND_INTERVAL} ticks ago, or nothing changed. The first report after the panel opens goes out at once.
     */
    public byte @Nullable [] pollData(Player player) {
        Watch watch = watchers.get(player);
        if (watch == null) {
            return null;
        }
        long now = player.level().getGameTime();
        if (watch.lastReport != null && now - watch.lastTime < SEND_INTERVAL) {
            return null;
        }
        StatusReport current = StatusReport.of(provider);
        if (current.equals(watch.lastReport)) {
            return null;
        }
        watch.lastReport = current;
        watch.lastTime = now;
        return PacketData.write(player.registryAccess(), data -> StatusReport.STREAM_CODEC.encode(data, current));
    }

    @Override
    public void handleClientPacketData(RegistryFriendlyByteBuf data) {
        report = StatusReport.STREAM_CODEC.decode(data);
    }

    /**
     * Client side: tells the server the panel opened or closed. Called by
     * {@link buildcraftreforged.core.gui.StatusLedger}; does nothing when the server already knows.
     */
    public void setWatched(boolean watched) {
        if (this.watched == watched || watchedOnOpen) {
            return;
        }
        this.watched = watched;
        if (!watched) {
            report = null;
        }
        new PacketStatusWatch(container.containerId, watched).sendPacket();
    }

    private static final class Watch {
        @Nullable StatusReport lastReport;
        long lastTime;
    }
}
