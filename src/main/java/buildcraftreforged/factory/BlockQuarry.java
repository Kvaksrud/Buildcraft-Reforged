/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: Material.iron, the hardness, resistance and step sound move to registration;
 * the metadata orientation becomes FACING and getIcon becomes the block model; placedBy is set through setPlacedBy;
 * breakBlock's frame handling moves to TileQuarry.preRemoveSideEffects and removes the frame at once instead of
 * marking it for decay (plan section 19.3); searchFrames and the commented-out frame search are dropped (unused in
 * 4.2.2); quarryOneTimeUse empties the drops in getDrops; the wrench goes through WrenchHelper; using the block without
 * a wrench opens the status panel and the red light shows a blocked quarry (plan section 10).
 */
package buildcraftreforged.factory;

import buildcraftreforged.api.core.Position;
import buildcraftreforged.core.BlockBuildCraft;
import buildcraftreforged.core.BuildCraftConfiguration;
import buildcraftreforged.core.Box;
import buildcraftreforged.core.WrenchHelper;
import buildcraftreforged.core.status.StatusPanel;
import buildcraftreforged.core.utils.Utils;
import java.util.List;
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
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public class BlockQuarry extends BlockBuildCraft {

    /**
     * 4.2.2 kept the front in the metadata: textureFront on that side, textureTop on top, textureSide elsewhere. The
     * block model does the same per facing.
     */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public BlockQuarry(Properties properties) {
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
        if (entityliving instanceof Player player && world.getBlockEntity(pos) instanceof TileQuarry tq) {
            tq.placedBy = player;
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileQuarry(pos, state);
    }

    /**
     * 4.2.2 breakBlock's markFrameForDecay on every edge of the box: frame blocks there became meta 1 and decayed on
     * random ticks. They are removed at once now (plan section 19.3).
     */
    public static void removeFrame(Level world, Box box) {
        // X - Axis
        for (int x = box.xMin; x <= box.xMax; x++) {
            removeFrame(world, x, box.yMin, box.zMin);
            removeFrame(world, x, box.yMax, box.zMin);
            removeFrame(world, x, box.yMin, box.zMax);
            removeFrame(world, x, box.yMax, box.zMax);
        }

        // Z - Axis
        for (int z = box.zMin + 1; z <= box.zMax - 1; z++) {
            removeFrame(world, box.xMin, box.yMin, z);
            removeFrame(world, box.xMax, box.yMin, z);
            removeFrame(world, box.xMin, box.yMax, z);
            removeFrame(world, box.xMax, box.yMax, z);
        }

        // Y - Axis
        for (int y = box.yMin + 1; y <= box.yMax - 1; y++) {

            removeFrame(world, box.xMin, y, box.zMin);
            removeFrame(world, box.xMax, y, box.zMin);
            removeFrame(world, box.xMin, y, box.zMax);
            removeFrame(world, box.xMax, y, box.zMax);
        }
    }

    private static void removeFrame(Level world, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (world.getBlockState(pos).is(FactoryRegistry.frameBlock.get())) {
            world.removeBlock(pos, false);
        }
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (BuildCraftConfiguration.COMMON_SPEC.isLoaded() && BuildCraftConfiguration.quarryOneTimeUse.get()) {
            return List.of();
        }
        return super.getDrops(state, params);
    }

    /** Restart the quarry with a wrench (4.2.2 onBlockActivated); sneaking drops through. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos, Player entityplayer,
            InteractionHand hand, BlockHitResult hitResult) {
        // Drop through if the player is sneaking
        if (entityplayer.isShiftKeyDown())
            return InteractionResult.PASS;

        if (WrenchHelper.isWrench(stack) && WrenchHelper.canWrench(entityplayer, hand, pos)
                && world.getBlockEntity(pos) instanceof TileQuarry tile) {
            if (!world.isClientSide()) {
                tile.reinitalize();
            }
            WrenchHelper.wrenchUsed(entityplayer, hand, pos);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    /** 4.2.2 had no GUI here; using the block opens the status panel (plan section 10). */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (player.isShiftKeyDown() || !(world.getBlockEntity(pos) instanceof TileQuarry tile))
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
