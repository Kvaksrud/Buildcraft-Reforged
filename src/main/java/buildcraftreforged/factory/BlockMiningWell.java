/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: BlockContainer becomes BlockBuildCraft on BaseEntityBlock; Material.ground,
 * the hardness, resistance and step sound move to registration; the metadata orientation becomes FACING and getIcon
 * becomes the block model; onBlockPlacedBy calls super, so the owner is recorded (docs/BUGFIXES.md); breakBlock
 * becomes affectNeighborsAfterRemoval; the status panel opens on use and the red light shows a refused break (plan
 * section 10).
 */
package buildcraftreforged.factory;

import buildcraftreforged.api.core.Position;
import buildcraftreforged.core.BlockBuildCraft;
import buildcraftreforged.core.status.StatusPanel;
import buildcraftreforged.core.utils.Utils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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

public class BlockMiningWell extends BlockBuildCraft {

    /**
     * 4.2.2 kept the front in the metadata: textureFront on that side, textureBack on the opposite side and on the
     * bottom, textureTop on top, textureSides elsewhere. The block model does the same per facing.
     */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public BlockMiningWell(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(StatusPanel.BLOCKED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, StatusPanel.BLOCKED);
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
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel world, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, world, pos, movedByPiston);
        removePipes(world, pos.getX(), pos.getY(), pos.getZ());
    }

    public static void removePipes(Level world, int x, int y, int z) {
        for (int depth = y - 1; depth > world.getMinY(); depth--) {
            BlockPos pos = new BlockPos(x, depth, z);
            if (!world.getBlockState(pos).is(FactoryRegistry.plainPipeBlock.get())) {
                break;
            }
            world.removeBlock(pos, false);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileMiningWell(pos, state);
    }

    /** 4.2.2 had no GUI here; using the block opens the status panel (plan section 10). */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (!(world.getBlockEntity(pos) instanceof TileMiningWell tile))
            return InteractionResult.PASS;

        StatusPanel.open(player, tile);
        return InteractionResult.SUCCESS;
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
