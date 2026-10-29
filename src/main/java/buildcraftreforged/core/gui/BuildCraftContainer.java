/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: on AbstractContainerMenu; ICrafting crafters are the players that opened the
 * menu (PlayerContainerEvent); widget packets are PacketGuiWidget payloads; getGuiReturnHandler() added so the server
 * can check a PacketGuiReturn against the open menu. Only the networking half is ported so far.
 */

package buildcraftreforged.core.gui;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.core.gui.widgets.Widget;
import buildcraftreforged.core.network.IGuiReturnHandler;
import buildcraftreforged.core.network.PacketGuiWidget;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

@EventBusSubscriber(modid = BuildcraftReforged.MOD_ID)
public abstract class BuildCraftContainer extends AbstractContainerMenu {

    private final List<Widget> widgets = new ArrayList<>();
    private final List<Player> crafters = new ArrayList<>();
    private final int inventorySize;

    public BuildCraftContainer(@Nullable MenuType<?> menuType, int containerId, int inventorySize) {
        super(menuType, containerId);
        this.inventorySize = inventorySize;
    }

    public List<Widget> getWidgets() {
        return widgets;
    }

    public void addWidget(Widget widget) {
        widget.addToContainer(this);
        widgets.add(widget);
    }

    /**
     * The block entity whose GUI this is, if it accepts {@code PacketGuiReturn}. A return packet is applied only when
     * the sender has this menu open and this handler sits at the packet's position.
     */
    public @Nullable IGuiReturnHandler getGuiReturnHandler() {
        return null;
    }

    public void sendWidgetDataToClient(Widget widget, Player player, byte[] data) {
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer,
                    new PacketGuiWidget(containerId, widgets.indexOf(widget), data));
        }
    }

    public void handleWidgetClientData(int widgetId, RegistryFriendlyByteBuf data) {
        if (widgetId < 0 || widgetId >= widgets.size()) {
            BuildcraftReforged.LOGGER.warn("Widget packet for widget {} of a menu with {} widgets", widgetId,
                    widgets.size());
            return;
        }
        widgets.get(widgetId).handleClientPacketData(data);
    }

    public void addCraftingToCrafters(Player player) {
        crafters.add(player);
        for (Widget widget : widgets) {
            widget.initWidget(player);
        }
    }

    public void removeCraftingFromCrafters(Player player) {
        crafters.remove(player);
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        for (Widget widget : widgets) {
            for (Player player : crafters) {
                widget.updateWidget(player);
            }
        }
    }

    public int getInventorySize() {
        return inventorySize;
    }

    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (event.getContainer() instanceof BuildCraftContainer container) {
            container.addCraftingToCrafters(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        if (event.getContainer() instanceof BuildCraftContainer container) {
            container.removeCraftingFromCrafters(event.getEntity());
        }
    }
}
