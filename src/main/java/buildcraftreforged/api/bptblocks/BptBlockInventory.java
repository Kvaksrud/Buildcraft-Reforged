/*
 * Modified by Buildcraft Reforged, 2026: ported from buildcraft/api/bptblocks/BptBlockInventory.java (no file header
 * at 4.2.2); IInventory becomes Container; the contents are requirements and are built, as plan section 13.4 decides,
 * instead of being emptied (#76).
 */

package buildcraftreforged.api.bptblocks;

import buildcraftreforged.api.blueprints.BptBlock;
import buildcraftreforged.api.blueprints.BptBlockUtils;
import buildcraftreforged.api.blueprints.BptSlotInfo;
import buildcraftreforged.api.blueprints.IBptContext;
import java.util.LinkedList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * A block with an inventory (brewing stand, assembly table).
 *
 * <p>4.2.2 built the block from its saved tile data and then emptied every slot, so a rebuilt chest was always empty
 * while the blueprint still held its contents. Plan section 13.4 has inventories carry their contents as requirements
 * instead, the way 4.2.2's {@code BptBlockAutoWorkbench} did: the contents are recorded with {@link BptBlockUtils} at
 * capture, the builder must supply them, and they are put back after the block is built. The builder has paid for
 * every item it places, so nothing is duplicated.
 */
public class BptBlockInventory extends BptBlock {

    public BptBlockInventory(Block block) {
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

        initializeContents(slot, context, new BlockPos(x, y, z));
    }

    @Override
    public void buildBlock(BptSlotInfo slot, IBptContext context) {
        super.buildBlock(slot, context);

        buildContents(slot, context);
    }

    /**
     * Records the contents of the container at {@code pos}, if its block entity is one. While capturing, the slot's
     * own position is still relative to the box, so the world position is passed.
     */
    static void initializeContents(BptSlotInfo slot, IBptContext context, BlockPos pos) {
        BlockEntity tile = context.world().getBlockEntity(pos);

        if (tile instanceof Container inv) {
            BptBlockUtils.initializeInventoryContents(slot, context, inv);
        }
    }

    /** 4.2.2 emptied each slot here; the recorded contents are set instead, the same slot for slot. */
    static void buildContents(BptSlotInfo slot, IBptContext context) {
        BlockEntity tile = context.world().getBlockEntity(slot.pos());

        if (tile instanceof Container inv) {
            inv.clearContent();
            BptBlockUtils.buildInventoryContents(slot, context, inv);
            tile.setChanged();
        }
    }

}
