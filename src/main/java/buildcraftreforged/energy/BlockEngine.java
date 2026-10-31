/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: one block per engine instead of metadata 0-2, each made with the engine's
 * tile factory (createTileEntity becomes newBlockEntity; getSubBlocks and damageDropped go with the metadata); the
 * orientation becomes the FACING block state; isBlockSolidOnSide becomes the support shape; rotateBlock is
 * IRotatableBlock; onBlockActivated becomes useItemOn/useWithoutItem; onPostBlockPlaced becomes setPlacedBy;
 * onNeighborBlockChange becomes neighborChanged; randomDisplayTick becomes animateTick; Material.iron and the icons
 * move to registration and the renderer (#39); the wrench restarts an engine shut down by overheating (#40); comparator
 * output is new (plan section 9, #41).
 */
package buildcraftreforged.energy;

import buildcraftreforged.core.BlockBuildCraft;
import buildcraftreforged.core.IRotatableBlock;
import buildcraftreforged.transport.BlockPipe;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class BlockEngine extends BlockBuildCraft implements IRotatableBlock {

    /** 4.2.2 TileEngine.orientation: the side the engine pushes power out of. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    /** The engine's base plate, the face opposite the output (4.2.2 isBlockSolidOnSide). */
    private static final Map<Direction, VoxelShape> SOLID_BACK = new EnumMap<>(Direction.class);

    static {
        SOLID_BACK.put(Direction.UP, Block.box(0, 0, 0, 16, 4, 16));
        SOLID_BACK.put(Direction.DOWN, Block.box(0, 12, 0, 16, 16, 16));
        SOLID_BACK.put(Direction.SOUTH, Block.box(0, 0, 0, 16, 16, 4));
        SOLID_BACK.put(Direction.NORTH, Block.box(0, 0, 12, 16, 16, 16));
        SOLID_BACK.put(Direction.EAST, Block.box(0, 0, 0, 4, 16, 16));
        SOLID_BACK.put(Direction.WEST, Block.box(12, 0, 0, 16, 16, 16));
    }

    private final BlockEntityType.BlockEntitySupplier<? extends TileEngine> tileFactory;

    public BlockEngine(Properties properties, BlockEntityType.BlockEntitySupplier<? extends TileEngine> tileFactory) {
        super(properties);
        this.tileFactory = tileFactory;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }

    /**
     * 4.2.2's Material.iron and BlockBuildCraft's hardness 5: metal, needs a pickaxe to drop (add the block to
     * minecraft:mineable/pickaxe), not an opaque cube, so no redstone conduction or suffocation (nor view blocking, which
     * follows suffocation).
     */
    public static Properties engineProperties(Properties properties) {
        return defaultProperties(properties).mapColor(MapColor.METAL).sound(SoundType.METAL).requiresCorrectToolForDrops()
                .noOcclusion().isRedstoneConductor((state, level, pos) -> false)
                .isSuffocating((state, level, pos) -> false);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return tileFactory.create(pos, state);
    }

    /** Only the back of an engine is solid: torches and the like attach there and nowhere else. */
    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return SOLID_BACK.get(state.getValue(FACING));
    }

    @Override
    public boolean rotatesOnSneakOnly() {
        return true;
    }

    /**
     * A sneaking wrench click turns the engine one face on, through all six (decided 31 Oct 2026, sneak only from
     * 1 Nov 2026; 4.2.2 turned on a plain click and jumped to the next face with a power receiver). Faces with nothing
     * to power are allowed: the status panel says so.
     */
    @Override
    public boolean rotateBlock(Level world, BlockPos pos, Direction axis) {
        BlockEntity tile = world.getBlockEntity(pos);
        if (tile instanceof TileEngine engine) {
            // An engine shut down by overheating is restarted by the wrench, not turned (plan section 9).
            if (engine.restartAfterOverheat())
                return true;
            engine.cycleOrientation(false);
            return true;
        }
        return false;
    }

    /** Structure and blueprint rotation turn the facing (BUGFIXES, BuildCraft#1771). */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** Do not open guis when having a pipe in hand: the click places the pipe. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hitResult) {
        if (stack.getItem() instanceof BlockItem item && item.getBlock() instanceof BlockPipe) {
            return InteractionResult.PASS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        // Drop through if the player is sneaking
        if (player.isShiftKeyDown())
            return InteractionResult.PASS;

        BlockEntity tile = world.getBlockEntity(pos);
        if (tile instanceof TileEngine) {
            return ((TileEngine) tile).onBlockActivated(player, hitResult.getDirection()) ? InteractionResult.SUCCESS
                    : InteractionResult.PASS;
        }

        return InteractionResult.PASS;
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity entity, ItemStack stack) {
        super.setPlacedBy(world, pos, state, entity, stack);
        if (world.isClientSide() || !(world.getBlockEntity(pos) instanceof TileEngine tile))
            return;
        tile.setOrientation(Direction.UP);
        if (!tile.isOrientationValid())
            tile.switchOrientation(true);
    }

    @Override
    public void animateTick(BlockState state, Level world, BlockPos pos, RandomSource random) {
        if (!(world.getBlockEntity(pos) instanceof TileEngine tile) || !tile.isBurning())
            return;

        float f = pos.getX() + 0.5F;
        float f1 = pos.getY() + 0.0F + (random.nextFloat() * 6F) / 16F;
        float f2 = pos.getZ() + 0.5F;
        float f3 = 0.52F;
        float f4 = random.nextFloat() * 0.6F - 0.3F;

        world.addParticle(DustParticleOptions.REDSTONE, f - f3, f1, f2 + f4, 0.0D, 0.0D, 0.0D);
        world.addParticle(DustParticleOptions.REDSTONE, f + f3, f1, f2 + f4, 0.0D, 0.0D, 0.0D);
        world.addParticle(DustParticleOptions.REDSTONE, f + f4, f1, f2 - f3, 0.0D, 0.0D, 0.0D);
        world.addParticle(DustParticleOptions.REDSTONE, f + f4, f1, f2 + f3, 0.0D, 0.0D, 0.0D);
    }

    @Override
    protected void neighborChanged(BlockState state, Level world, BlockPos pos, Block block,
            @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, world, pos, block, orientation, movedByPiston);
        if (world.getBlockEntity(pos) instanceof TileEngine tile) {
            tile.checkRedstonePower();
        }
    }

    /** Not in 4.2.2 (plan section 9): the engine's heat, 15 while it overheats. */
    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level world, BlockPos pos, Direction direction) {
        if (world.getBlockEntity(pos) instanceof TileEngine tile)
            return tile.calculateComparatorInputOverride();
        return 0;
    }
}
