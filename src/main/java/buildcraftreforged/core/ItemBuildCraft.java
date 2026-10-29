/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: the id constructor becomes Item.Properties; the creative tab and the
 * unlocalised name move to registration; registerIcons becomes an item model; getItemDisplayName is dropped, since
 * Item.getName already translates the description id; shouldPassSneakingClickToBlock becomes doesSneakBypassUse.
 */
package buildcraftreforged.core;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;

public class ItemBuildCraft extends Item {

    private boolean passSneakClick = false;

    public ItemBuildCraft(Properties properties) {
        super(properties);
    }

    public Item setPassSneakClick(boolean passClick) {
        this.passSneakClick = passClick;
        return this;
    }

    @Override
    public boolean doesSneakBypassUse(ItemStack stack, LevelReader world, BlockPos pos, Player player) {
        return passSneakClick;
    }
}
