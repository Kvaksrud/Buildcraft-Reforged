/**
 * Copyright (c) SpaceToad, 2011-2012
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: getIconFromDamage and registerIcons become the item model, which shows
 * blueprint_clean without a BptItemData component and blueprint_used with one; setCreativeTab(null) is the item
 * not being added to a tab.
 */

package buildcraftreforged.builders;

public class ItemBptBluePrint extends ItemBptBase {

    public ItemBptBluePrint(Properties properties) {
        super(properties);
    }
}
