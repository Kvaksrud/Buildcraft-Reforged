package buildcraftreforged.core.network;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.core.gui.BuildCraftContainer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-side handlers for the client-bound payloads registered in {@link PacketHandler}: the {@code STATE_UPDATE}
 * and {@code GUI_WIDGET} cases of 4.2.2's {@code PacketHandler}, and the client half of {@code PacketGuiWidget}.
 */
@EventBusSubscriber(modid = BuildcraftReforged.MOD_ID, value = Dist.CLIENT)
public final class PacketHandlerClient {
    private PacketHandlerClient() {}

    @SubscribeEvent
    public static void onRegisterClientPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(PacketTileState.TYPE, PacketHandlerClient::onTileState);
        event.register(PacketGuiWidget.TYPE, PacketHandlerClient::onGuiWidget);
    }

    private static void onTileState(PacketTileState packet, IPayloadContext context) {
        Level level = context.player().level();
        if (!level.isLoaded(packet.pos())) {
            return;
        }
        BlockEntity tile = level.getBlockEntity(packet.pos());
        if (tile instanceof ISyncedTile syncedTile) {
            try {
                packet.applyStates(syncedTile, level.registryAccess());
            } catch (RuntimeException e) {
                BuildcraftReforged.LOGGER.warn("Could not apply tile state at {}", packet.pos(), e);
            }
        }
    }

    private static void onGuiWidget(PacketGuiWidget packet, IPayloadContext context) {
        Player player = context.player();
        if (player.containerMenu instanceof BuildCraftContainer container
                && container.containerId == packet.windowId()) {
            RegistryFriendlyByteBuf data = PacketData.wrap(packet.payload(), player.level().registryAccess());
            try {
                container.handleWidgetClientData(packet.widgetId(), data);
            } catch (RuntimeException e) {
                BuildcraftReforged.LOGGER.warn("Could not apply widget {} data", packet.widgetId(), e);
            } finally {
                data.release();
            }
        }
    }
}
