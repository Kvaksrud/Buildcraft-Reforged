package buildcraftreforged.gametest.gui;

import buildcraftreforged.core.BlockBuildCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Opens the sample menu on right-click, as 4.2.2 blocks did with {@code entityplayer.openGui}. */
public class BlockSampleGui extends BlockBuildCraft {
    public BlockSampleGui(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileSampleGui(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TileSampleGui tile) {
            player.openMenu(tile, pos);
        }
        return InteractionResult.SUCCESS;
    }
}
