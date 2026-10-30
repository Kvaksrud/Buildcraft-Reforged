/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: BlockContainer becomes BaseEntityBlock with BlockBuildCraft's ticker;
 * metadata becomes the FACING property; the bounding box becomes the block shape; Material.circuits, the light value,
 * the creative tab and the item move to registration; the marker model render id becomes JSON models (RenderingMarkers);
 * breakBlock's Utils.preDestroyBlock runs from TileBuildCraft.preRemoveSideEffects.
 */
package buildcraftreforged.builders;

import buildcraftreforged.core.BlockBuildCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Not a BlockBuildCraft, as in 4.2.2. Not waterloggable: a 1.6.4 marker in the way of flowing water was washed off, as
 * a torch is, and having no collision shape gives the same here.
 */
public class BlockMarker extends BaseEntityBlock {

    /** 4.2.2's metadata: the side the marker was placed against, pointing away from its support. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    public BlockMarker(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    private static VoxelShape getBoundingBox(Direction dir) {
        double w = 0.15;
        double h = 0.65;

        switch (dir) {
            case DOWN:
                return Shapes.box(0.5F - w, 1F - h, 0.5F - w, 0.5F + w, 1F, 0.5F + w);
            case UP:
                return Shapes.box(0.5F - w, 0F, 0.5F - w, 0.5F + w, h, 0.5F + w);
            case SOUTH:
                return Shapes.box(0.5F - w, 0.5F - w, 0F, 0.5F + w, 0.5F + w, h);
            case NORTH:
                return Shapes.box(0.5F - w, 0.5F - w, 1 - h, 0.5F + w, 0.5F + w, 1);
            case EAST:
                return Shapes.box(0F, 0.5F - w, 0.5F - w, h, 0.5F + w, 0.5F + w);
            default:
                return Shapes.box(1 - h, 0.5F - w, 0.5F - w, 1F, 0.5F + w, 0.5F + w);
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return getBoundingBox(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileMarker(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return BlockBuildCraft::updateEntity;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (world.getBlockEntity(pos) instanceof TileMarker marker) {
            marker.tryConnection();
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level world, BlockPos pos, Block block,
            @Nullable Orientation orientation, boolean movedByPiston) {
        if (world.getBlockEntity(pos) instanceof TileMarker marker) {
            marker.updateSignals();
        }
        dropTorchIfCantStay(state, world, pos);
    }

    /** 4.2.2 canPlaceBlockOnSide: the block behind the marker must be a normal cube (BuildersProxy.canPlaceTorch). */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        Direction dir = state.getValue(FACING);
        return canPlaceTorch(world, pos.relative(dir.getOpposite()));
    }

    /** 4.2.2 BuildersProxy.canPlaceTorch: 1.6.4's renderAsNormalBlock is a full opaque cube. */
    public static boolean canPlaceTorch(LevelReader world, BlockPos pos) {
        return world.getBlockState(pos).isSolidRender();
    }

    /** 4.2.2 onBlockPlaced returned the clicked side as the metadata. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getClickedFace());
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    protected void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, world, pos, oldState, movedByPiston);
        dropTorchIfCantStay(state, world, pos);
    }

    private void dropTorchIfCantStay(BlockState state, Level world, BlockPos pos) {
        if (!world.isClientSide() && !canSurvive(state, world, pos)) {
            dropResources(state, world, pos, world.getBlockEntity(pos));
            world.removeBlock(pos, false);
        }
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
