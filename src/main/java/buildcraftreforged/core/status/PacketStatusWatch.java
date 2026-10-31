/*
 * Buildcraft Reforged, 2026: new file, no 4.2.2 original. The status panel's "open" and "closed" message (plan
 * section 10: data is sent only while the panel is open).
 */
package buildcraftreforged.core.status;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.core.gui.BuildCraftContainer;
import buildcraftreforged.core.network.PacketHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;

/**
 * Client-to-server: the status ledger of the open menu opened ({@code watching} true) or closed. The server accepts it
 * only for the sender's open {@link BuildCraftContainer} that has a {@link StatusWidget} and is still valid for them.
 */
public record PacketStatusWatch(int containerId, boolean watching) implements CustomPacketPayload {
    public static final Type<PacketStatusWatch> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "status_watch"));

    public static final StreamCodec<ByteBuf, PacketStatusWatch> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PacketStatusWatch::containerId,
            ByteBufCodecs.BOOL, PacketStatusWatch::watching,
            PacketStatusWatch::new);

    /** Client only. */
    public void sendPacket() {
        ClientPacketDistributor.sendToServer(this);
    }

    /** Registered in {@link PacketHandler}. */
    public static void handle(PacketStatusWatch packet, IPayloadContext context) {
        process(packet, PacketHandler.Sender.of(context.player()));
    }

    /** Starts or stops the sender's reports. Returns false, after logging why, when the packet was dropped. */
    public static boolean process(PacketStatusWatch packet, PacketHandler.Sender sender) {
        String reason = check(sender, packet.containerId());
        if (reason != null) {
            PacketHandler.reject("status_watch", sender, reason);
            return false;
        }
        StatusWidget widget = StatusWidget.find((BuildCraftContainer) sender.openMenu());
        widget.setWatching(sender.player(), packet.watching());
        return true;
    }

    private static @Nullable String check(PacketHandler.Sender sender, int containerId) {
        AbstractContainerMenu menu = sender.openMenu();
        if (containerId == 0 || menu == null || menu.containerId != containerId) {
            return "container " + containerId + " is not the open menu";
        }
        if (!(menu instanceof BuildCraftContainer container) || StatusWidget.find(container) == null) {
            return "open menu has no status panel";
        }
        if (sender.player() == null || !sender.menuStillValid(menu)) {
            return "open menu is no longer valid";
        }
        return null;
    }

    @Override
    public Type<PacketStatusWatch> type() {
        return TYPE;
    }
}
