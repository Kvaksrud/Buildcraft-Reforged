/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: ICrafting is Player and the packet stream a RegistryFriendlyByteBuf. The
 * client-side methods (draw, mouse handling, tool tips) are no longer stripped from the server, but only GuiBuildCraft
 * calls them. Mouse buttons keep the 1.6.4 numbering (0 left, 1 right, 2 middle); GuiBuildCraft converts.
 */

package buildcraftreforged.core.gui.widgets;

import buildcraftreforged.core.gui.BuildCraftContainer;
import buildcraftreforged.core.gui.GuiBuildCraft;
import buildcraftreforged.core.gui.tooltips.IToolTipProvider;
import buildcraftreforged.core.gui.tooltips.ToolTip;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Player;

/**
 *
 * @author CovertJaguar <http://www.railcraft.info/>
 */
public class Widget implements IToolTipProvider {

    public final int x;
    public final int y;
    public final int u;
    public final int v;
    public final int w;
    public final int h;
    public boolean hidden;
    protected BuildCraftContainer container;

    public Widget(int x, int y, int u, int v, int w, int h) {
        this.x = x;
        this.y = y;
        this.u = u;
        this.v = v;
        this.w = w;
        this.h = h;
    }

    public void addToContainer(BuildCraftContainer container) {
        this.container = container;
    }

    @Override
    public final boolean isMouseOver(int mouseX, int mouseY) {
        return mouseX >= x - 1 && mouseX < x + w + 1 && mouseY >= y - 1 && mouseY < y + h + 1;
    }

    public boolean handleMouseClick(int mouseX, int mouseY, int mouseButton) {
        return false;
    }

    public void handleMouseRelease(int mouseX, int mouseY, int eventType) {
    }

    public void handleMouseMove(int mouseX, int mouseY, int mouseButton, long time) {
    }

    /** Client side: data the server sent with {@link BuildCraftContainer#sendWidgetDataToClient}. */
    public void handleClientPacketData(RegistryFriendlyByteBuf data) {
    }

    public void draw(GuiBuildCraft<?> gui, int guiX, int guiY, int mouseX, int mouseY) {
        gui.drawTexturedModalRect(guiX + x, guiY + y, u, v, w, h);
    }

    @Override
    public ToolTip getToolTip() {
        return null;
    }

    @Override
    public boolean isToolTipVisible() {
        return true;
    }

    public void initWidget(Player player) {
    }

    public void updateWidget(Player player) {
    }
}
