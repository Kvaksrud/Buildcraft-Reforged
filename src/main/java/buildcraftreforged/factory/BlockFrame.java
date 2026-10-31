/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: Material.glass and the hardness move to registration; idDropped -1 becomes
 * noLootTable; the legacy pipe model, the connection-dependent collision, selection and ray-trace boxes become six
 * connection properties, a multipart block model and the shape; waterlogging is new (plan section 6); the decay of
 * frames marked by a broken quarry (meta 1 and updateTick) is gone, because BlockQuarry now removes its frame at once
 * (plan section 19.3).
 */

package buildcraftreforged.factory;

import buildcraftreforged.core.CoreConstants;
import buildcraftreforged.core.IFramePipeConnection;
import buildcraftreforged.core.utils.Utils;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class BlockFrame extends Block implements IFramePipeConnection, SimpleWaterloggedBlock {

    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    /** Whether Utils.checkLegacyPipesConnections joins this frame to the neighbour on that side. */
    public static final Map<Direction, BooleanProperty> CONNECTED = PipeBlock.PROPERTY_BY_DIRECTION;

    /** The shape for each set of connections, indexed by a bit per Direction ordinal. */
    private static final VoxelShape[] SHAPES = new VoxelShape[64];

    static {
        double min = CoreConstants.PIPE_MIN_POS, max = CoreConstants.PIPE_MAX_POS;
        for (int mask = 0; mask < 64; mask++) {
            VoxelShape shape = Shapes.box(min, min, min, max, max, max);
            for (Direction dir : Direction.values()) {
                if ((mask & (1 << dir.ordinal())) == 0)
                    continue;
                shape = Shapes.or(shape, Shapes.box(
                        dir == Direction.WEST ? 0.0 : min, dir == Direction.DOWN ? 0.0 : min, dir == Direction.NORTH ? 0.0 : min,
                        dir == Direction.EAST ? 1.0 : max, dir == Direction.UP ? 1.0 : max, dir == Direction.SOUTH ? 1.0 : max));
            }
            SHAPES[mask] = shape;
        }
    }

    public BlockFrame(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any().setValue(WATERLOGGED, false);
        for (BooleanProperty property : CONNECTED.values()) {
            state = state.setValue(property, false);
        }
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WATERLOGGED);
        for (BooleanProperty property : CONNECTED.values()) {
            builder.add(property);
        }
    }

    /**
     * The frame state for pos, with its connections to the blocks around it and waterlogged when water stands there.
     * The quarry places frames with this state.
     */
    public BlockState getConnectedState(BlockGetter world, BlockPos pos) {
        BlockState state = defaultBlockState().setValue(WATERLOGGED, world.getFluidState(pos).is(Fluids.WATER));
        for (Direction dir : Direction.values()) {
            state = state.setValue(CONNECTED.get(dir), isConnected(world, pos, dir));
        }
        return state;
    }

    /** 4.2.2 asked Utils.checkLegacyPipesConnections for each side whenever it needed a box. */
    private static boolean isConnected(BlockGetter world, BlockPos pos, Direction dir) {
        BlockPos other = pos.relative(dir);
        return Utils.checkLegacyPipesConnections(world, pos.getX(), pos.getY(), pos.getZ(), other.getX(), other.getY(),
                other.getZ());
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return getConnectedState(context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (state.getValue(WATERLOGGED))
            ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return state.setValue(CONNECTED.get(directionToNeighbour), isConnected(level, pos, directionToNeighbour));
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    /** 4.2.2's getCollisionBoundingBoxFromPool, addCollisionBoxesToList and collisionRayTrace: the core plus arms. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        int mask = 0;
        for (Direction dir : Direction.values()) {
            if (state.getValue(CONNECTED.get(dir)))
                mask |= 1 << dir.ordinal();
        }
        return SHAPES[mask];
    }

    @Override
    public boolean isPipeConnected(BlockGetter blockAccess, int x1, int y1, int z1, int x2, int y2, int z2) {
        return blockAccess.getBlockState(new BlockPos(x2, y2, z2)).getBlock() == this;
    }
}
