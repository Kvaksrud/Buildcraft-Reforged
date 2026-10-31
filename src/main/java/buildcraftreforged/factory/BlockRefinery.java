/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: BlockContainer becomes BaseEntityBlock with BlockBuildCraft's ticker;
 * Material.iron, the hardness and the creative tab move to registration; the metadata orientation becomes FACING;
 * blockByEntityModel becomes an invisible block shape drawn by RenderRefinery, with refineryBack as the particle
 * texture; rotateBlock is IRotatableBlock; onBlockActivated becomes useItemOn (wrench and fluid containers) and
 * useWithoutItem (the GUI, through openMenu).
 */
package buildcraftreforged.factory;

import buildcraftreforged.api.core.Position;
import buildcraftreforged.core.BlockBuildCraft;
import buildcraftreforged.core.IRotatableBlock;
import buildcraftreforged.core.WrenchHelper;
import buildcraftreforged.core.fluids.FluidUtils;
import buildcraftreforged.core.utils.Utils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import org.jspecify.annotations.Nullable;

public class BlockRefinery extends BaseEntityBlock implements IRotatableBlock {

    /** 4.2.2 kept the orientation in the metadata; RenderRefinery turns the model to it. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public BlockRefinery(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.EAST));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** 4.2.2 getRenderType returned blockByEntityModel: the tile renderer draws the whole block. */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileRefinery(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return BlockBuildCraft::updateEntity;
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity entityliving,
            ItemStack stack) {
        super.setPlacedBy(world, pos, state, entityliving, stack);

        if (world.getBlockEntity(pos) instanceof TileRefinery tile) {
            tile.onBlockPlacedBy(entityliving, stack);
        }

        if (entityliving == null)
            return;

        Direction orientation = Utils.get2dOrientation(
                new Position(entityliving.getX(), entityliving.getY(), entityliving.getZ()),
                new Position(pos.getX(), pos.getY(), pos.getZ()));

        world.setBlock(pos, state.setValue(FACING, orientation.getOpposite()), Block.UPDATE_CLIENTS);
    }

    @Override
    public boolean rotateBlock(Level world, BlockPos pos, Direction axis) {
        BlockState state = world.getBlockState(pos);

        Direction next = switch (state.getValue(FACING)) {
            case WEST -> Direction.SOUTH;
            case EAST -> Direction.NORTH;
            case NORTH -> Direction.WEST;
            default -> Direction.EAST;
        };
        world.setBlock(pos, state.setValue(FACING, next), Block.UPDATE_ALL);
        return true;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /**
     * 4.2.2 onBlockActivated, the parts that need a held item: a sneaking wrench resets the filters, and any item but
     * an empty bucket is tried as a fluid container that fills the refinery (fill, not drain). Everything else falls
     * through to useWithoutItem, which opens the GUI as 4.2.2 did.
     */
    @Override
    protected InteractionResult useItemOn(ItemStack current, BlockState state, Level world, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hitResult) {
        if (!(world.getBlockEntity(pos) instanceof TileRefinery tile))
            return InteractionResult.PASS;

        if (player.isShiftKeyDown() && WrenchHelper.isWrench(current) && WrenchHelper.canWrench(player, hand, pos)) {
            tile.resetFilters();
            WrenchHelper.wrenchUsed(player, hand, pos);
            return InteractionResult.SUCCESS;
        }

        if (!current.isEmpty() && !current.is(Items.BUCKET)) {
            if (!world.isClientSide()) {
                if (FluidUtils.handleRightClick(tile, player, hand, true, false))
                    return InteractionResult.SUCCESS;
            } else if (ItemAccess.forPlayerInteraction(player, hand).getCapability(Capabilities.Fluid.ITEM) != null) {
                return InteractionResult.SUCCESS;
            }
        }

        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (!(world.getBlockEntity(pos) instanceof TileRefinery tile))
            return InteractionResult.PASS;

        if (!world.isClientSide()) {
            player.openMenu(tile, pos);
        }

        return InteractionResult.SUCCESS;
    }
}
