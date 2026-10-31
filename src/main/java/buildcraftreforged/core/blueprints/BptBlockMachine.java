/*
 * Buildcraft Reforged, 2026: new (#76). 4.2.2 had no schematic for these machines and copied their tile data.
 */

package buildcraftreforged.core.blueprints;

import buildcraftreforged.api.blueprints.BptBlock;
import buildcraftreforged.api.blueprints.BptSlotInfo;
import buildcraftreforged.api.blueprints.IBptContext;
import net.minecraft.world.level.block.Block;

/**
 * A BuildCraft machine built as a new one, from its item: the quarry, mining well, pump, flood gate and filler.
 *
 * <p>4.2.2 had no schematic for them, so the default copied the whole tile into the blueprint and back. That copied
 * the pump's and flood gate's fluid (the residual liquid fix accepted for pumps, plan section 3, BuildCraft#1790), the
 * filler's building blocks (built for free, a duplicate), and the quarry's and filler's working box, which is in world
 * coordinates, so a built quarry went on digging the original's hole. As {@code BptBlockTank} and 4.2.2's
 * {@code BptBlockEngine} and {@code BptBlockRefinery} already did for theirs, nothing of the block entity is captured,
 * and the requirement is the block's item. The facing is turned by the block state.
 */
public class BptBlockMachine extends BptBlock {

    public BptBlockMachine(Block block) {
        super(block);
    }

    @Override
    public void initializeFromWorld(BptSlotInfo slot, IBptContext context, int x, int y, int z) {
    }

    @Override
    public void buildBlock(BptSlotInfo slot, IBptContext context) {
        context.world().setBlock(slot.pos(), slot.state, 3);
    }
}
