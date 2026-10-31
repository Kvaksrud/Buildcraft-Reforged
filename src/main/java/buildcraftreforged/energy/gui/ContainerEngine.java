/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: one registered MenuType per engine, with a client constructor that reads the
 * block position; detectAndSendChanges/updateProgressBar with sendGUINetworkData/getGUINetworkData become the engine's
 * guiData data slots; canInteractWith becomes stillValid; the status panel's widget (plan section 10) is new.
 */
package buildcraftreforged.energy.gui;

import buildcraftreforged.core.gui.BuildCraftContainer;
import buildcraftreforged.core.status.StatusWidget;
import buildcraftreforged.energy.EnergyRegistry.EngineEntry;
import buildcraftreforged.energy.TileEngineStone;
import buildcraftreforged.energy.TileEngineWithInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;

public class ContainerEngine extends BuildCraftContainer {

    protected TileEngineWithInventory engine;
    public final StatusWidget statusWidget;

    public ContainerEngine(MenuType<?> menuType, int containerId, Inventory inventoryplayer, TileEngineWithInventory tileEngine) {
        super(menuType, containerId, tileEngine.getContainerSize());

        engine = tileEngine;

        if (tileEngine instanceof TileEngineStone) {
            addSlot(new Slot(tileEngine, 0, 80, 41));
        } else {
            addSlot(new Slot(tileEngine, 0, 52, 41));
        }

        for (int i = 0; i < 3; i++) {
            for (int k = 0; k < 9; k++) {
                addSlot(new Slot(inventoryplayer, k + i * 9 + 9, 8 + k * 18, 84 + i * 18));
            }

        }

        for (int j = 0; j < 9; j++) {
            addSlot(new Slot(inventoryplayer, j, 8 + j * 18, 142));
        }

        addDataSlots(tileEngine.guiData);

        statusWidget = new StatusWidget(tileEngine);
        addWidget(statusWidget);
    }

    /**
     * Client side: the engine at the position the open-menu packet carried. If it went away before the menu arrived, a
     * detached one stands in; the menu closes on the next stillValid check.
     */
    public static <T extends TileEngineWithInventory> TileEngineWithInventory clientTile(Inventory inventory, BlockPos pos,
            EngineEntry<T> entry) {
        if (inventory.player.level().getBlockEntity(pos) instanceof TileEngineWithInventory tile) {
            return tile;
        }
        return entry.tile().get().create(pos, entry.block().get().defaultBlockState());
    }

    public TileEngineWithInventory getEngine() {
        return engine;
    }

    public boolean isUsableByPlayer(Player entityplayer) {
        return engine.stillValid(entityplayer);
    }

    @Override
    public boolean stillValid(Player entityplayer) {
        return Container.stillValidBlockEntity(engine, entityplayer);
    }
}
