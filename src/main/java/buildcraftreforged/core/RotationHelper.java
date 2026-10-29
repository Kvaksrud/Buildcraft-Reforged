package buildcraftreforged.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Stands in for 1.6.4 Forge's {@code Block.rotateBlock} and {@code RotationHelper}: our blocks rotate themselves
 * through {@link IRotatableBlock}, any other block by turning its facing or axis property around the clicked face.
 */
public final class RotationHelper {
    private RotationHelper() {
    }

    public static boolean rotateBlock(Level level, BlockPos pos, Direction axis) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof IRotatableBlock rotatable) {
            return rotatable.rotateBlock(level, pos, axis);
        }
        return rotateVanillaBlock(level, pos, state, axis);
    }

    /**
     * Turns the first facing property around the axis of the clicked face (around the vertical axis when the
     * property is horizontal only), skipping orientations the block cannot stay in, or cycles the first axis
     * property. Two-part blocks (doors, beds, double chests, extended pistons) are left alone, as 1.6.4 did.
     */
    public static boolean rotateVanillaBlock(Level level, BlockPos pos, BlockState state, Direction axis) {
        if (isMultiPart(state)) {
            return false;
        }
        for (Property<?> property : state.getProperties()) {
            if (property.getValueClass() == Direction.class) {
                @SuppressWarnings("unchecked")
                EnumProperty<Direction> facing = (EnumProperty<Direction>) property;
                return rotateFacing(level, pos, state, facing, axis);
            }
            if (property == BlockStateProperties.AXIS || property == BlockStateProperties.HORIZONTAL_AXIS) {
                return place(level, pos, state.cycle(property));
            }
        }
        if (axis.getAxis() == Direction.Axis.Y) {
            return place(level, pos, state.rotate(level, pos, Rotation.CLOCKWISE_90));
        }
        return false;
    }

    private static boolean rotateFacing(Level level, BlockPos pos, BlockState state, EnumProperty<Direction> facing,
            Direction axis) {
        Direction.Axis around = facing.getPossibleValues().contains(Direction.UP) ? axis.getAxis() : Direction.Axis.Y;
        Direction current = state.getValue(facing);
        Direction next = current;
        for (int i = 0; i < 3; i++) {
            next = next.getClockWise(around);
            if (next == current) {
                return false;
            }
            if (facing.getPossibleValues().contains(next)) {
                BlockState rotated = state.setValue(facing, next);
                if (rotated.canSurvive(level, pos)) {
                    return place(level, pos, rotated);
                }
            }
        }
        return false;
    }

    private static boolean isMultiPart(BlockState state) {
        return state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                || state.hasProperty(BlockStateProperties.BED_PART)
                || state.hasProperty(BlockStateProperties.CHEST_TYPE)
                        && state.getValue(BlockStateProperties.CHEST_TYPE) != ChestType.SINGLE
                || state.hasProperty(BlockStateProperties.EXTENDED) && state.getValue(BlockStateProperties.EXTENDED);
    }

    private static boolean place(Level level, BlockPos pos, BlockState rotated) {
        if (rotated == level.getBlockState(pos)) {
            return false;
        }
        return level.setBlock(pos, Block.updateFromNeighbourShapes(rotated, level, pos), Block.UPDATE_ALL);
    }
}
