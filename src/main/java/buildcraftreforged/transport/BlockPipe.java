package buildcraftreforged.transport;

import buildcraftreforged.api.transport.IPipeTile.PipeType;
import buildcraftreforged.core.BlockBuildCraft;
import buildcraftreforged.core.BuildCraftConfiguration;
import buildcraftreforged.core.DefaultProps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A pipe block (docs/TRANSPORT.md 3.1). One subclass per kind, instantiated once per tier from the table in
 * {@link TransportRegistry}. The block state carries only {@code waterlogged}; connections live in {@link TilePipe} and
 * reach the client model as model data. Water has no effect on connections or routing.
 *
 * <p>4.2.2 had one {@code BlockGenericPipe} for every pipe, with the pipe object in the tile; here the kind and the
 * material are fixed per block.
 */
public abstract class BlockPipe extends BlockBuildCraft implements SimpleWaterloggedBlock {

    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public final PipeType kind;
    public final PipeMaterial material;

    protected BlockPipe(PipeType kind, PipeMaterial material, Properties properties) {
        super(properties);
        this.kind = kind;
        this.material = material;
        registerDefaultState(stateDefinition.any().setValue(WATERLOGGED, false));
    }

    /**
     * Registration properties: 4.2.2 {@code BlockGenericPipe} hardness ({@code pipes.durability}, default 0.25; the
     * configured value is applied when breaking, see {@link #getDestroyProgress}), not a full cube.
     */
    public static Properties pipeProperties(Properties properties) {
        return properties.strength((float) DefaultProps.PIPES_DURABILITY).noOcclusion().dynamicShape();
    }

    public int getTier() {
        return material.tier;
    }

    /**
     * Whether the block on {@code side} exposes this kind's capability on the face towards the pipe (rule 4 of
     * docs/TRANSPORT.md 3.2).
     */
    protected abstract boolean isEndpoint(TilePipe pipe, Direction side);

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WATERLOGGED);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluid = context.getLevel().getFluidState(context.getClickedPos());
        return defaultBlockState().setValue(WATERLOGGED, fluid.is(Fluids.WATER));
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (state.getValue(WATERLOGGED))
            ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
            @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TilePipe pipe)
            pipe.scheduleConnectionUpdate();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return PipeShapes.get(level.getBlockEntity(pos) instanceof TilePipe pipe ? pipe.getConnectionMask() : 0);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    /** Vanilla's progress formula with the configured {@code pipes.durability} as the hardness. */
    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        float hardness = (float) pipeDurability();
        if (hardness <= 0)
            return 1F;
        int modifier = net.neoforged.neoforge.event.EventHooks.doPlayerHarvestCheck(player, state, level, pos) ? 30 : 100;
        return player.getDestroySpeed(state, pos) / hardness / modifier;
    }

    private static double pipeDurability() {
        return BuildCraftConfiguration.COMMON_SPEC.isLoaded() ? BuildCraftConfiguration.pipeDurability.get()
                : DefaultProps.PIPES_DURABILITY;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TilePipe(pos, state);
    }

    /** Pipes tick on the server only, to recompute connections when something changed; the client is sent the mask. */
    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide() ? null : super.getTicker(level, state, type);
    }
}
