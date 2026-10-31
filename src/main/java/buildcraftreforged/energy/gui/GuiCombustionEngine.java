/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: the screen gets its menu from the registered MenuType instead of building
 * the ContainerEngine itself; the title and labels are translated components; the fluid's still sprite comes from
 * FluidRenderer and is tinted with the fluid's colour (4.2.2: the tank's colorRenderCache, sent by the server), cut to the gauge's height with a scissor instead of a partial
 * icon; GL11.glColor4f becomes setColor.
 */
package buildcraftreforged.energy.gui;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.core.fluids.Tank;
import buildcraftreforged.core.render.FluidRenderer;
import buildcraftreforged.energy.TileEngineIron;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.fluids.FluidStack;

/* Client only. */
public class GuiCombustionEngine extends GuiEngine {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "textures/gui/combustion_engine_gui.png");

    public GuiCombustionEngine(ContainerEngine container, Inventory inventoryplayer, Component title) {
        super(container, TEXTURE, inventoryplayer, title);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int par1, int par2) {
        super.drawGuiContainerForegroundLayer(par1, par2);
        String title = Component.translatable("tile.engineIron").getString();
        drawString(title, getCenteredOffset(title), 6, 0x404040);
        drawString(Component.translatable("gui.inventory").getString(), 8, (imageHeight - 96) + 2, 0x404040);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float f, int x, int y) {
        super.drawGuiContainerBackgroundLayer(f, x, y);
        int j = (width - imageWidth) / 2;
        int k = (height - imageHeight) / 2;

        TileEngineIron engine = (TileEngineIron) tile;

        if (engine.getScaledBurnTime(58) > 0) {
            displayGauge(j, k, 19, 104, engine.getScaledBurnTime(58), engine.getFuel(), engine.tankFuel);
        }

        if (engine.getScaledCoolant(58) > 0) {
            displayGauge(j, k, 19, 122, engine.getScaledCoolant(58), engine.getCoolant(), engine.tankCoolant);
        }
    }

    private void displayGauge(int j, int k, int line, int col, int squaled, FluidStack liquid, Tank tank) {
        if (liquid.isEmpty()) {
            return;
        }
        int start = 0;

        TextureAtlasSprite liquidIcon = FluidRenderer.getFluidTexture(liquid, false);
        int color = 0xFF000000 | FluidRenderer.getColorForFluidStack(liquid);

        if (liquidIcon != null) {
            while (true) {
                int x;

                if (squaled > 16) {
                    x = 16;
                    squaled -= 16;
                } else {
                    x = squaled;
                    squaled = 0;
                }

                // 4.2.2 drew the top x pixels of the icon, bottom-aligned; the scissor keeps the bottom x pixels.
                int top = k + line + 58 - x - start;
                graphics.enableScissor(j + col, top, j + col + 16, top + x);
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, liquidIcon, j + col, top, 16, 16, color);
                graphics.disableScissor();
                start = start + 16;

                if (x == 0 || squaled == 0) {
                    break;
                }
            }
        }

        setColor(1.0F, 1.0F, 1.0F, 1.0F);
        bindTexture(TEXTURE);
        drawTexturedModalRect(j + col, k + line, 176, 0, 16, 60);
    }
}
