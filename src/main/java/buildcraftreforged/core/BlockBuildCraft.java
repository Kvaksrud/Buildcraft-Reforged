/*
 * Ported from buildcraft/core/BlockBuildCraft.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: BlockContainer becomes BaseEntityBlock with a BlockEntityTicker that calls
 * TileBuildCraft.updateEntity every tick; Material and the creative tab move to registration; breakBlock's
 * Utils.preDestroyBlock runs from TileBuildCraft.preRemoveSideEffects; getLightValue becomes getLightEmission, read
 * from the auxiliary light manager.
 */
package buildcraftreforged.core;

import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.world.AuxiliaryLightManager;
import org.jspecify.annotations.Nullable;

/**
 * Registration supplies what 4.2.2's constructor set: the creative tab (CreativeTabBuildCraft.MACHINES.add) and the
 * hardness, by passing the properties through {@link #defaultProperties}.
 */
public abstract class BlockBuildCraft extends BaseEntityBlock {

    protected static boolean keepInventory = false;
    protected final Random rand = new Random();

    protected BlockBuildCraft(Properties properties) {
        super(properties);
    }

    /** 4.2.2's setHardness(5F): destroy time 5 and, through 1.6.4's hardness * 5 rule, explosion resistance 5. */
    public static Properties defaultProperties(Properties properties) {
        return properties.strength(5F);
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity entity, ItemStack stack) {
        super.setPlacedBy(world, pos, state, entity, stack);
        BlockEntity tile = world.getBlockEntity(pos);
        if (tile instanceof TileBuildCraft) {
            ((TileBuildCraft) tile).onBlockPlacedBy(entity, stack);
        }
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return BlockBuildCraft::updateEntity;
    }

    /** The 1.6.4 world tick: every tick, both sides, unless the tile's canUpdate says no. */
    public static void updateEntity(Level level, BlockPos pos, BlockState state, BlockEntity tile) {
        if (tile instanceof TileBuildCraft && ((TileBuildCraft) tile).canUpdate()) {
            ((TileBuildCraft) tile).updateEntity();
        }
    }

    @Override
    public boolean hasDynamicLightEmission(BlockState state) {
        return true;
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter world, BlockPos pos) {
        // The tile's IMachine.isActive is mirrored into the auxiliary light manager by TileBuildCraft.updateEntity,
        // because the light engine may not read block entities.
        AuxiliaryLightManager lightManager = world.getAuxLightManager(pos);
        if (lightManager != null)
            return super.getLightEmission(state, world, pos) + lightManager.getLightAt(pos);
        return super.getLightEmission(state, world, pos);
    }
}
