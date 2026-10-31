/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: BlockContainer becomes BaseEntityBlock with BlockBuildCraft's ticker;
 * Material.glass, the hardness and the creative tab move to registration; the bounding box becomes the block shape;
 * the icons become JSON models, with getBlockTexture's "tank below" test as the JOINED_BELOW state and
 * shouldSideBeRendered as skipRendering; onBlockActivated becomes useItemOn over the fluid item capability;
 * getLightValue reads the auxiliary light manager; comparator output is new (plan section 6).
 */
package buildcraftreforged.factory;

import buildcraftreforged.core.BlockBuildCraft;
import buildcraftreforged.core.fluids.FluidUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.world.AuxiliaryLightManager;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;

public class BlockTank extends BaseEntityBlock {

    /**
     * 4.2.2 getBlockTexture drew the sides with textureStackedSide when the block below was a tank, and
     * textureBottomSide otherwise. Models cannot look at neighbours, so the block state carries it.
     */
    public static final BooleanProperty JOINED_BELOW = BooleanProperty.create("joined_below");

    private static final VoxelShape SHAPE = Shapes.box(0.125F, 0F, 0.125F, 0.875F, 1F, 0.875F);

    public BlockTank(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(JOINED_BELOW, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(JOINED_BELOW);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileTank(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return BlockBuildCraft::updateEntity;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState below = context.getLevel().getBlockState(context.getClickedPos().below());
        return defaultBlockState().setValue(JOINED_BELOW, below.is(this));
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader world, ScheduledTickAccess ticks, BlockPos pos,
            Direction direction, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (direction == Direction.DOWN)
            return state.setValue(JOINED_BELOW, neighbourState.is(this));
        return state;
    }

    /**
     * 4.2.2 onBlockActivated. A filled container empties into the stack of tanks and an empty one fills from it, through
     * TileTank's handler (fill from the bottom up, drain from the bottom). The container exchange goes through the
     * item fluid capability, which also covers creative mode and returns the emptied container to the inventory.
     */
    @Override
    protected InteractionResult useItemOn(ItemStack current, BlockState state, Level world, BlockPos pos,
            Player entityplayer, InteractionHand hand, BlockHitResult hitResult) {
        if (!current.isEmpty() && world.getBlockEntity(pos) instanceof TileTank tank) {
            ResourceHandler<FluidResource> container = ItemAccess.forPlayerInteraction(entityplayer, hand).oneByOne()
                    .getCapability(Capabilities.Fluid.ITEM);
            if (container != null) {
                boolean filledContainer = !ResourceHandlerUtil.isEmpty(container);

                if (world.isClientSide()) {
                    return filledContainer || tank.getAmountAsLong(0) > 0 ? InteractionResult.SUCCESS
                            : InteractionResult.TRY_WITH_EMPTY_HAND;
                }

                // Handle filled containers: 4.2.2 used the click even when the tank took nothing, so a full tank
                // never lets a bucket place its fluid.
                if (FluidUtils.handleRightClick(tank, entityplayer, hand, true, true) || filledContainer)
                    return InteractionResult.SUCCESS;
            }
        }

        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    /** 4.2.2 shouldSideBeRendered: no top or bottom face between two tanks. */
    @Override
    protected boolean skipRendering(BlockState state, BlockState neighborState, Direction direction) {
        if (direction.getAxis() == Direction.Axis.Y)
            return neighborState.is(this);
        return super.skipRendering(state, neighborState, direction);
    }

    @Override
    public boolean hasDynamicLightEmission(BlockState state) {
        return true;
    }

    /**
     * 4.2.2 getLightValue returned the tile's getFluidLightLevel. The light engine may not read block entities, so
     * TileTank copies it into the chunk's auxiliary light manager, which is read here.
     */
    @Override
    public int getLightEmission(BlockState state, BlockGetter world, BlockPos pos) {
        AuxiliaryLightManager lightManager = world.getAuxLightManager(pos);
        if (lightManager != null)
            return lightManager.getLightAt(pos);
        return super.getLightEmission(state, world, pos);
    }

    /** Not in 4.2.2 (plan section 6); BuildCraft 7.1 gave tanks this comparator output. */
    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level world, BlockPos pos, Direction direction) {
        if (world.getBlockEntity(pos) instanceof TileTank tank)
            return tank.calculateComparatorInputOverride();
        return 0;
    }
}
