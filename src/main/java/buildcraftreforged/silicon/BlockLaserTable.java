/*
 * Ported from buildcraft/silicon/BlockLaserTable.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: BlockContainer becomes BlockBuildCraft; the metadata variants (0 Assembly
 * Table, 1 Advanced Crafting Table) become separate blocks, each registered with the block entity it creates, so
 * ItemLaserTable's names become the blocks' own descriptions; Material.iron, the hardness and the creative tab move to
 * registration; the block bounds become the shape; the icons become the JSON models; openGui becomes openMenu with
 * the tile as the MenuProvider; breakBlock's Utils.preDestroyBlock runs from TileBuildCraft.preRemoveSideEffects.
 */
package buildcraftreforged.silicon;

import buildcraftreforged.core.BlockBuildCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BlockLaserTable extends BlockBuildCraft {

    /** 4.2.2 setBlockBounds(0, 0, 0, 1, 9F / 16F, 1). */
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 9, 16);

    /** 4.2.2 createTileEntity chose the tile by metadata; each block now has its own. */
    private final BlockEntityType.BlockEntitySupplier<? extends BlockEntity> tile;

    public BlockLaserTable(Properties properties, BlockEntityType.BlockEntitySupplier<? extends BlockEntity> tile) {
        super(properties);
        this.tile = tile;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player entityplayer,
            BlockHitResult hitResult) {
        // Drop through if the player is sneaking
        if (entityplayer.isShiftKeyDown())
            return InteractionResult.PASS;

        if (!world.isClientSide() && world.getBlockEntity(pos) instanceof MenuProvider menu) {
            entityplayer.openMenu(menu, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return tile.create(pos, state);
    }
}
