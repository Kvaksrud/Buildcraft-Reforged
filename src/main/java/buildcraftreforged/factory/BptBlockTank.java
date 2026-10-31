/*
 * Modified by Buildcraft Reforged, 2026: ported from buildcraft/factory/BptBlockTank.java (no file header at 4.2.2);
 * keyed by Block (#76).
 */

package buildcraftreforged.factory;

import buildcraftreforged.api.blueprints.BptBlock;
import buildcraftreforged.api.blueprints.BptSlotInfo;
import buildcraftreforged.api.blueprints.IBptContext;
import net.minecraft.world.level.block.Block;

/**
 * A tank, built empty: no block entity data is captured, so the fluid is never copied (the residual liquid rule,
 * plan section 3), and the requirement is the tank's item.
 */
public class BptBlockTank extends BptBlock {

    public BptBlockTank(Block block) {
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
