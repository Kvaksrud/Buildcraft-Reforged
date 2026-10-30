package buildcraftreforged.gametest.gui;

import buildcraftreforged.core.gui.GuiBuildCraft;
import buildcraftreforged.core.gui.tooltips.ToolTip;
import buildcraftreforged.core.gui.tooltips.ToolTipLine;
import buildcraftreforged.core.gui.widgets.Widget;
import buildcraftreforged.core.network.PacketData;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Player;

/**
 * Sends the tile's counter to each player with the menu open whenever it changes, the way 4.2.2 widgets synced
 * their data, and draws it as the Stirling engine's flame gauge.
 */
public class CounterWidget extends Widget {
    private final TileSampleGui tile;
    private final Map<Player, Integer> lastSent = new WeakHashMap<>();
    /** Client side: the last value the server sent. */
    public int value;

    private final ToolTipLine line = new ToolTipLine();
    private final ToolTip toolTip = new ToolTip() {
        @Override
        public void refresh() {
            line.text = "Counter: " + value;
        }
    };

    public CounterWidget(TileSampleGui tile, int x, int y, int u, int v, int w, int h) {
        super(x, y, u, v, w, h);
        this.tile = tile;
        toolTip.add(line);
    }

    @Override
    public void initWidget(Player player) {
        send(player);
    }

    @Override
    public void updateWidget(Player player) {
        Integer last = lastSent.get(player);
        if (last == null || last != tile.counter) {
            send(player);
        }
    }

    private void send(Player player) {
        lastSent.put(player, tile.counter);
        container.sendWidgetDataToClient(this, player, getData(player.registryAccess()));
    }

    public byte[] getData(RegistryAccess registryAccess) {
        return PacketData.write(registryAccess, data -> data.writeVarInt(tile.counter));
    }

    @Override
    public void handleClientPacketData(RegistryFriendlyByteBuf data) {
        value = data.readVarInt();
    }

    @Override
    public void draw(GuiBuildCraft<?> gui, int guiX, int guiY, int mouseX, int mouseY) {
        int scale = Math.floorMod(value, h + 1);
        gui.drawTexturedModalRect(guiX + x, guiY + y + h - scale, u, v + h - scale, w, scale);
    }

    @Override
    public ToolTip getToolTip() {
        return toolTip;
    }
}
