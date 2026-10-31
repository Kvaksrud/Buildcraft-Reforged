package buildcraftreforged.gametest.status;

import buildcraftreforged.core.BlockBuildCraft;
import buildcraftreforged.core.status.StatusPanel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/** A working block without a GUI: right-click opens the status panel, and the model has the red light. */
public class BlockSampleStatus extends BlockBuildCraft {
    public BlockSampleStatus(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(StatusPanel.BLOCKED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(StatusPanel.BLOCKED);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileSampleStatus(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof TileSampleStatus tile) {
            StatusPanel.open(player, tile);
        }
        return InteractionResult.SUCCESS;
    }
}
