/** 
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 * 
 * BuildCraft is distributed under the terms of the Minecraft Mod Public 
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: an AbstractContainerMenu with a MenuType and a client constructor that finds
 * the block entity from the position in the open-menu packet; the status panel's widget (plan section 10) is new.
 */

package buildcraftreforged.builders.gui;

import buildcraftreforged.builders.BuildersRegistry;
import buildcraftreforged.builders.TileBuilder;
import buildcraftreforged.core.gui.BuildCraftContainer;
import buildcraftreforged.core.status.StatusWidget;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;

public class ContainerBuilder extends BuildCraftContainer {

    Inventory playerIInventory;
    public final TileBuilder builder;
    public final StatusWidget statusWidget;

    /** Client side: the block position comes with the open-menu packet. */
    public ContainerBuilder(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, clientTile(playerInventory, extraData.readBlockPos()));
    }

    public ContainerBuilder(int containerId, Inventory playerInventory, TileBuilder builder) {
        super(BuildersRegistry.builderMenu.get(), containerId, builder.getContainerSize());
        this.playerIInventory = playerInventory;
        this.builder = builder;

        addSlot(new Slot(builder, 0, 80, 27));

        for (int k = 0; k < 3; k++) {
            for (int j1 = 0; j1 < 9; j1++) {
                addSlot(new Slot(builder, 1 + j1 + k * 9, 8 + j1 * 18, 72 + k * 18));
            }

        }

        for (int l = 0; l < 3; l++) {
            for (int k1 = 0; k1 < 9; k1++) {
                addSlot(new Slot(playerInventory, k1 + l * 9 + 9, 8 + k1 * 18, 140 + l * 18));
            }

        }

        for (int i1 = 0; i1 < 9; i1++) {
            addSlot(new Slot(playerInventory, i1, 8 + i1 * 18, 198));
        }

        statusWidget = new StatusWidget(builder);
        addWidget(statusWidget);
    }

    private static TileBuilder clientTile(Inventory playerInventory, BlockPos pos) {
        if (playerInventory.player.level().getBlockEntity(pos) instanceof TileBuilder tile) {
            return tile;
        }
        // The block went away before the menu arrived; the menu closes on the next stillValid check.
        return new TileBuilder(pos, BuildersRegistry.builderBlock.get().defaultBlockState());
    }

    @Override
    public boolean stillValid(Player entityplayer) {
        return builder.stillValid(entityplayer);
    }

}
