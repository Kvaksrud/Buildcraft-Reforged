/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: takes the Fluid instead of a block id. The creative tab, translation key and
 * icon move to registration and resources; setContainerItem becomes craftRemainder. NeoForge gives the fluid item
 * capability only to the exact BucketItem class, so EnergyRegistry registers it for these buckets.
 */
package buildcraftreforged.energy;

import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;

public class ItemBucketBuildcraft extends BucketItem {

    public ItemBucketBuildcraft(Fluid fluid, Item.Properties properties) {
        super(fluid, properties.craftRemainder(Items.BUCKET).stacksTo(1));
    }
}
