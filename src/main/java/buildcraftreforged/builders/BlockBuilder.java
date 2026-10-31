/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: BlockContainer becomes BlockBuildCraft; Material.iron, the hardness and the
 * creative tab move to registration; the metadata orientation becomes FACING and the icons the JSON model
 * (builder_front on the facing side, builder_top on top, builder_side elsewhere), with the status panel's red light;
 * onBlockActivated becomes useItemOn (any wrench in c:tools/wrench turns the block, the 4.2.2 cycle, now also
 * IRotatableBlock for our wrench) and useWithoutItem (openMenu with the tile as the MenuProvider); breakBlock's
 * Utils.preDestroyBlock runs from TileBuildCraft.preRemoveSideEffects; addCreativeItems is dropped.
 */
package buildcraftreforged.builders;

import buildcraftreforged.api.core.Position;
import buildcraftreforged.core.BlockBuildCraft;
import buildcraftreforged.core.IRotatableBlock;
import buildcraftreforged.core.WrenchHelper;
import buildcraftreforged.core.status.StatusPanel;
import buildcraftreforged.core.utils.Utils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public class BlockBuilder extends BlockBuildCraft implements IRotatableBlock {

    /**
     * 4.2.2 kept the front's ForgeDirection in the metadata. Metadata 0 (down, before the block was placed by a
     * player) built without turning, as west does, so the default is west.
     */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public BlockBuilder(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.WEST).setValue(StatusPanel.BLOCKED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, StatusPanel.BLOCKED);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileBuilder(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack current, BlockState state, Level world, BlockPos pos,
            Player entityplayer, InteractionHand hand, BlockHitResult hitResult) {

        // Drop through if the player is sneaking
        if (entityplayer.isShiftKeyDown())
            return InteractionResult.TRY_WITH_EMPTY_HAND;

        if (WrenchHelper.isWrench(current) && WrenchHelper.canWrench(entityplayer, hand, pos)) {
            rotateBlock(world, pos, hitResult.getDirection());
            WrenchHelper.wrenchUsed(entityplayer, hand, pos);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player entityplayer,
            BlockHitResult hitResult) {

        // Drop through if the player is sneaking
        if (entityplayer.isShiftKeyDown())
            return InteractionResult.PASS;

        if (!world.isClientSide() && world.getBlockEntity(pos) instanceof TileBuilder builder) {
            entityplayer.openMenu(builder, pos);
        }
        return InteractionResult.SUCCESS;
    }

    /** 4.2.2's wrench branch of onBlockActivated: west to south, east to north, north to west, south to east. */
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
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity entityliving,
            ItemStack stack) {
        super.setPlacedBy(world, pos, state, entityliving, stack);

        if (entityliving == null)
            return;

        Direction orientation = Utils.get2dOrientation(
                new Position(entityliving.getX(), entityliving.getY(), entityliving.getZ()),
                new Position(pos.getX(), pos.getY(), pos.getZ()));

        world.setBlock(pos, state.setValue(FACING, orientation.getOpposite()), Block.UPDATE_CLIENTS);
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
