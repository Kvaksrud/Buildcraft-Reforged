/*
 * Copyright (c) SpaceToad, 2011-2012
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: a client-bound CustomPacketPayload with an explicit StreamCodec; the client
 * side moved to PacketHandlerClient.
 */

package buildcraftreforged.core.network;

import buildcraftreforged.BuildcraftReforged;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server-to-client: data for one widget of the open {@code BuildCraftContainer}. Sent with
 * {@code BuildCraftContainer.sendWidgetDataToClient}.
 *
 * @author CovertJaguar <http://www.railcraft.info/>
 */
public final class PacketGuiWidget implements CustomPacketPayload {
    public static final Type<PacketGuiWidget> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "gui_widget"));

    public static final StreamCodec<ByteBuf, PacketGuiWidget> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PacketGuiWidget::windowId,
            ByteBufCodecs.VAR_INT, PacketGuiWidget::widgetId,
            ByteBufCodecs.byteArray(PacketData.MAX_PAYLOAD_BYTES), PacketGuiWidget::payload,
            PacketGuiWidget::new);

    private final int windowId, widgetId;
    private final byte[] payload;

    public PacketGuiWidget(int windowId, int widgetId, byte[] data) {
        this.windowId = windowId;
        this.widgetId = widgetId;
        this.payload = data;
    }

    public int windowId() {
        return windowId;
    }

    public int widgetId() {
        return widgetId;
    }

    public byte[] payload() {
        return payload;
    }

    @Override
    public Type<PacketGuiWidget> type() {
        return TYPE;
    }
}
