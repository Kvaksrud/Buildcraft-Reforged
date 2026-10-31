package buildcraftreforged.transport;

import buildcraftreforged.core.BlockBuildCraft;
import buildcraftreforged.core.IRotatableBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.jspecify.annotations.Nullable;

/**
 * The Basic Extractor (docs/TRANSPORT.md 10, PLAN 8.2): a stepped nozzle, its wide plate against an inventory and its
 * narrow end towards an item pipe. {@link #FACING} is the inventory face. Placed against a block, the plate faces the
 * clicked block; a wrench turns it to the next face with an item handler (4.2.2 {@code PipeLogicWood.switchSource}).
 * The block entity ({@link TileBasicExtractor}) does the work.
 */
public class BlockBasicExtractor extends BlockBuildCraft implements IRotatableBlock {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    /**
     * The three steps from the inventory face to the pipe face, in pixels: half width, start depth, end depth. The
     * narrow end is the 8-pixel pipe's width (4.2.2 pipe centre 4–12 px). The renderer draws the stripes on them.
     */
    public static final int[][] STEPS = {{7, 0, 4}, {5, 4, 9}, {4, 9, 16}};

    private static final VoxelShape[] SHAPES = new VoxelShape[6];

    static {
        for (Direction facing : Direction.values()) {
            VoxelShape shape = Shapes.empty();
            for (int[] step : STEPS) {
                double[] box = box(facing, step[0], step[1], step[2]);
                shape = Shapes.or(shape, Block.box(box[0], box[1], box[2], box[3], box[4], box[5]));
            }
            SHAPES[facing.get3DDataValue()] = shape;
        }
    }

    public BlockBasicExtractor(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    /**
     * A step's box in pixels ({@code minX, minY, minZ, maxX, maxY, maxZ}) for a block whose inventory face is
     * {@code facing}: {@code depth0} to {@code depth1} pixels in from that face, {@code half} pixels either side of the
     * centre across it.
     */
    public static double[] box(Direction facing, double half, double depth0, double depth1) {
        double[] min = {8 - half, 8 - half, 8 - half};
        double[] max = {8 + half, 8 + half, 8 + half};
        int axis = facing.getAxis().ordinal();
        boolean positive = facing.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        min[axis] = positive ? 16 - depth1 : depth0;
        max[axis] = positive ? 16 - depth0 : depth1;
        return new double[] {min[0], min[1], min[2], max[0], max[1], max[2]};
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** The plate goes against the block that was clicked. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).get3DDataValue()];
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    /**
     * 4.2.2 {@code switchSource}: the next face after the current one, in side order, with an item handler that is not
     * a pipe. Stays put when there is none.
     */
    @Override
    public boolean rotateBlock(Level level, BlockPos pos, Direction axis) {
        BlockState state = level.getBlockState(pos);
        Direction current = state.getValue(FACING);
        for (int i = 1; i <= 6; i++) {
            Direction next = Direction.from3DDataValue((current.get3DDataValue() + i) % 6);
            if (next != current && hasInventory(level, pos, next)) {
                level.setBlock(pos, state.setValue(FACING, next), Block.UPDATE_ALL);
                level.invalidateCapabilities(pos);
                return true;
            }
        }
        return false;
    }

    private static boolean hasInventory(Level level, BlockPos pos, Direction side) {
        BlockPos neighbour = pos.relative(side);
        if (!level.isLoaded(neighbour) || level.getBlockEntity(neighbour) instanceof TilePipe)
            return false;
        var handler = level.getCapability(Capabilities.Item.BLOCK, neighbour, side.getOpposite());
        return handler != null && handler.size() > 0;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileBasicExtractor(pos, state);
    }
}
