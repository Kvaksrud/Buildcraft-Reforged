/*
 * Ported from buildcraft/core/ItemBlockBuildCraft.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: ItemBlock becomes BlockItem; getMetadata is dropped with block metadata, and
 * getItemDisplayName with it, since BlockItem.getName already translates the block's description id.
 */
package buildcraftreforged.core;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;

public class ItemBlockBuildCraft extends BlockItem {

    public ItemBlockBuildCraft(Block block, Properties properties) {
        super(block, properties);
    }
}
