/*
 * Ported from buildcraft/core/network/PacketHandler.java (BuildCraft 4.2.2, MMPL; the original has no header).
 * Modified by Buildcraft Reforged, 2026: the packet-id switch over one Packet250CustomPayload channel is replaced by
 * typed payloads on one PayloadRegistrar. Tile updates and widget data are client-bound only (PacketHandlerClient);
 * the one server-bound packet, PacketGuiReturn, is checked against the sender's open menu, distance and value ranges,
 * and logged and dropped when it fails (docs/PLAN.md section 19.2).
 */

package buildcraftreforged.core.network;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.core.gui.BuildCraftContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jspecify.annotations.Nullable;

/**
 * The mod's one payload channel: every payload is a {@code buildcraftreforged:*} type registered here under
 * {@link #PROTOCOL_VERSION}. Client-bound handlers are registered by {@link PacketHandlerClient}.
 */
public final class PacketHandler {
    /** Bump when any payload's codec changes; client and server must match. */
    public static final String PROTOCOL_VERSION = "1";

    /** Extra reach, in blocks, on top of the player's block interaction range. */
    public static final double REACH_BUFFER = 1.0;

    private PacketHandler() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(PacketHandler::onRegisterPayloadHandlers);
    }

    private static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        // Client-bound: handlers in PacketHandlerClient.
        registrar.playToClient(PacketTileState.TYPE, PacketTileState.STREAM_CODEC);
        registrar.playToClient(PacketGuiWidget.TYPE, PacketGuiWidget.STREAM_CODEC);
        // Server-bound: GUI actions only, each validated before it is applied.
        registrar.playToServer(PacketGuiReturn.TYPE, PacketGuiReturn.STREAM_CODEC, PacketHandler::onGuiReturn);
    }

    private static void onGuiReturn(PacketGuiReturn packet, IPayloadContext context) {
        processGuiReturn(packet, Sender.of(context.player()));
    }

    /**
     * Applies a GUI return if the sender may send it. Returns false, after logging why, when the packet was dropped.
     */
    static boolean processGuiReturn(PacketGuiReturn packet, Sender sender) {
        String reason = checkMenuAccess(sender, packet.containerId(), packet.pos());
        if (reason != null) {
            reject("gui_return", sender, reason);
            return false;
        }
        IGuiReturnHandler handler = ((BuildCraftContainer) sender.openMenu()).getGuiReturnHandler();
        RegistryFriendlyByteBuf data = PacketData.wrap(packet.data(), sender.registryAccess());
        try {
            handler.readGuiData(data, sender.player());
            return true;
        } catch (InvalidPacketException e) {
            reject("gui_return", sender, e.getMessage());
        } catch (RuntimeException e) {
            reject("gui_return", sender, "malformed data: " + e);
        } finally {
            data.release();
        }
        return false;
    }

    /**
     * Checks that the sender has the {@link BuildCraftContainer} with {@code containerId} open, that the menu belongs
     * to a live block entity at {@code pos} in the sender's level, that the menu is still valid for the sender, and
     * that {@code pos} is within reach. Every server-bound payload that acts on a block goes through this.
     *
     * @return null when the sender may act, otherwise the reason to drop the packet
     */
    public static @Nullable String checkMenuAccess(Sender sender, int containerId, BlockPos pos) {
        AbstractContainerMenu menu = sender.openMenu();
        if (containerId == 0 || menu == null || menu.containerId != containerId) {
            return "container " + containerId + " is not the open menu";
        }
        if (!(menu instanceof BuildCraftContainer container)) {
            return "open menu is not a Buildcraft menu";
        }
        IGuiReturnHandler handler = container.getGuiReturnHandler();
        if (handler == null) {
            return "open menu takes no GUI data";
        }
        if (!handler.getBlockPos().equals(pos)) {
            return "open menu belongs to " + handler.getBlockPos().toShortString() + ", not " + pos.toShortString();
        }
        if (handler.isRemoved() || handler.getLevel() != sender.level()) {
            return "block at " + pos.toShortString() + " is gone";
        }
        if (!sender.menuStillValid(menu)) {
            return "open menu is no longer valid";
        }
        if (!sender.canReach(pos)) {
            return pos.toShortString() + " is out of reach";
        }
        return null;
    }

    /** Logs a dropped client packet. */
    public static void reject(String packetName, Sender sender, String reason) {
        BuildcraftReforged.LOGGER.warn("Dropped {} packet from {}: {}", packetName, sender.name(), reason);
    }

    /**
     * What the validation needs to know about the player who sent a packet. {@link #of(Player)} is the real one; tests
     * supply their own.
     */
    public interface Sender {
        String name();

        @Nullable Player player();

        @Nullable AbstractContainerMenu openMenu();

        @Nullable Level level();

        RegistryAccess registryAccess();

        boolean menuStillValid(AbstractContainerMenu menu);

        boolean canReach(BlockPos pos);

        static Sender of(Player player) {
            return new Sender() {
                @Override
                public String name() {
                    return player.getGameProfile().name();
                }

                @Override
                public Player player() {
                    return player;
                }

                @Override
                public AbstractContainerMenu openMenu() {
                    return player.containerMenu;
                }

                @Override
                public Level level() {
                    return player.level();
                }

                @Override
                public RegistryAccess registryAccess() {
                    return player.level().registryAccess();
                }

                @Override
                public boolean menuStillValid(AbstractContainerMenu menu) {
                    return menu.stillValid(player);
                }

                @Override
                public boolean canReach(BlockPos pos) {
                    return player.isAlive() && player.isWithinBlockInteractionRange(pos, REACH_BUFFER);
                }
            };
        }
    }
}
