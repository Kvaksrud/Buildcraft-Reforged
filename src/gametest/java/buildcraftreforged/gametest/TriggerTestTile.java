package buildcraftreforged.gametest;

import buildcraftreforged.api.gates.IAction;
import buildcraftreforged.core.CoreRegistry;
import buildcraftreforged.core.IMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/**
 * A machine with two item slots and one 4000 mB tank, exposed as capabilities on every side: the block the trigger
 * GameTests (#31) evaluate against. Allows On and Off but not Loop.
 */
final class TriggerTestTile extends BlockEntity implements IMachine {
    final ItemStacksResourceHandler inventory = new ItemStacksResourceHandler(2);
    final FluidStacksResourceHandler tank = new FluidStacksResourceHandler(1, 4000);
    boolean active;
    boolean blocked;

    TriggerTestTile(BlockPos pos, BlockState state) {
        super(TriggerTestBlocks.triggerTestTile, pos, state);
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public boolean isBlocked() {
        return blocked;
    }

    @Override
    public boolean manageFluids() {
        return true;
    }

    @Override
    public boolean manageSolids() {
        return true;
    }

    @Override
    public boolean allowAction(IAction action) {
        return action == CoreRegistry.actionOn.get() || action == CoreRegistry.actionOff.get();
    }
}
