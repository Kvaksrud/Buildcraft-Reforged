/*
 * Modified by Buildcraft Reforged, 2026: ported from buildcraft/api/bptblocks/BptBlockRotateInventory.java (no file
 * header at 4.2.2); IInventory becomes Container; the contents are requirements and are built, as plan section 13.4
 * decides, instead of being emptied (#76).
 */

package buildcraftreforged.api.bptblocks;

import buildcraftreforged.api.blueprints.BptBlockUtils;
import buildcraftreforged.api.blueprints.BptSlotInfo;
import buildcraftreforged.api.blueprints.IBptContext;
import java.util.LinkedList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * A block with a facing and an inventory (chests, furnaces, dispensers). As {@link BptBlockInventory}: the contents
 * are recorded at capture, are requirements, and are put back after building (4.2.2 emptied them).
 */
public class BptBlockRotateInventory extends BptBlockRotateMeta {

    public BptBlockRotateInventory(Block block) {
        super(block);

    }

    @Override
    public void addRequirements(BptSlotInfo slot, IBptContext context, LinkedList<ItemStack> requirements) {
        super.addRequirements(slot, context, requirements);

        BptBlockUtils.requestInventoryContents(slot, context, requirements);
    }

    @Override
    public void initializeFromWorld(BptSlotInfo slot, IBptContext context, int x, int y, int z) {
        super.initializeFromWorld(slot, context, x, y, z);

        BptBlockInventory.initializeContents(slot, context, new BlockPos(x, y, z));
    }

    @Override
    public void buildBlock(BptSlotInfo slot, IBptContext context) {
        super.buildBlock(slot, context);

        BptBlockInventory.buildContents(slot, context);
    }

}
