/*
 * Modified by Buildcraft Reforged, 2026: ported from buildcraft/core/blueprints/BptSlot.java (no file header at 4.2.2);
 * the schematic comes from the slot's block instead of blockBptProps[blockId]; mirror added; clone copies the stacks.
 */

package buildcraftreforged.core.blueprints;

import buildcraftreforged.api.blueprints.BlueprintManager;
import buildcraftreforged.api.blueprints.BptBlock;
import buildcraftreforged.api.blueprints.BptSlotInfo;
import buildcraftreforged.api.blueprints.IBptContext;
import java.util.LinkedList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Mirror;
import org.jspecify.annotations.Nullable;

/**
 * A slot of a blueprint or template, with the builder's working state. Ported from 4.2.2
 * {@code buildcraft.core.blueprints.BptSlot}. Every call goes to the schematic of the slot's block
 * ({@link BlueprintManager#getBptBlock}), where 4.2.2 indexed {@code blockBptProps} by block id.
 */
public class BptSlot extends BptSlotInfo {

    public enum Mode {
        ClearIfInvalid, Build
    };

    public Mode mode = Mode.Build;
    public @Nullable ItemStack stackToUse;

    private BptBlock bptBlock() {
        return BlueprintManager.getBptBlock(state.getBlock());
    }

    public boolean isValid(IBptContext context) {
        return bptBlock().isValid(this, context);
    }

    public void rotateLeft(IBptContext context) {
        bptBlock().rotateLeft(this, context);
    }

    /** New: mirroring had no 4.2.2 counterpart (see {@link BptBase#mirror}). */
    public void mirror(IBptContext context, Mirror mirror) {
        bptBlock().mirror(this, context, mirror);
    }

    public boolean ignoreBuilding() {
        return bptBlock().ignoreBuilding(this);
    }

    public void initializeFromWorld(IBptContext context, int xs, int ys, int zs) {
        bptBlock().initializeFromWorld(this, context, xs, ys, zs);
    }

    public void postProcessing(IBptContext context) {
        bptBlock().postProcessing(this, context);
    }

    public LinkedList<ItemStack> getRequirements(IBptContext context) {
        LinkedList<ItemStack> res = new LinkedList<ItemStack>();

        bptBlock().addRequirements(this, context, res);

        return res;
    }

    public final void buildBlock(IBptContext context) {
        bptBlock().buildBlock(this, context);
    }

    // returns what was used
    public ItemStack useItem(IBptContext context, ItemStack req, ItemStack stack) {
        return bptBlock().useItem(this, context, req, stack);
    }

    /**
     * 4.2.2 cloned the requirements list but shared its stacks, so a builder that used up a requirement changed the
     * blueprint's copy too. The stacks are copied now.
     */
    @Override
    public BptSlot clone() {
        BptSlot obj = new BptSlot();

        obj.x = x;
        obj.y = y;
        obj.z = z;
        obj.state = state;
        obj.cpt = cpt.copy();

        for (ItemStack stack : storedRequirements) {
            obj.storedRequirements.add(stack.copy());
        }

        if (stackToUse != null) {
            obj.stackToUse = stackToUse.copy();
        }

        obj.mode = mode;

        return obj;
    }

}
