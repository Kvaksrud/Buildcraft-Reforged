/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: moved from buildcraft/core/BlockSpring.java (4.2.2 had no header on it) to the
 * energy module with oil worldgen. The water spring is dropped (docs/PLAN.md section 3), so the block is the oil spring
 * only and needs no metadata. Normal hardness (obsidian) and no drops instead of unbreakable (docs/PLAN.md section
 * 19.2). Ticks go through the block's scheduled tick; the worldgen placement schedules the first one (OilPopulate).
 */
package buildcraftreforged.energy;

import buildcraftreforged.core.BuildCraftConfiguration;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class BlockSpring extends Block {

    public enum EnumSpring {

        OIL(6000, 8, () -> EnergyRegistry.blockOil.get(), BuildCraftConfiguration.spawnOilSprings::get);
        public static final EnumSpring[] VALUES = values();
        public final int tickRate, chance;
        /** 4.2.2 set liquidBlock and canGen from BuildCraftEnergy.preInit; here they are read when the spring ticks. */
        public final Supplier<Block> liquidBlock;
        public final BooleanSupplier canGen;

        private EnumSpring(int tickRate, int chance, Supplier<Block> liquidBlock, BooleanSupplier canGen) {
            this.tickRate = tickRate;
            this.chance = chance;
            this.liquidBlock = liquidBlock;
            this.canGen = canGen;
        }
    }

    public final EnumSpring spring;

    public BlockSpring(EnumSpring spring, Properties properties) {
        super(properties);
        this.spring = spring;
    }

    @Override
    protected void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        assertSpring(world, pos, random);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        // 4.2.2 setTickRandomly(true): a random tick restarts the schedule, for springs placed without one.
        if (!world.getBlockTicks().hasScheduledTick(pos, this)) {
            assertSpring(world, pos, random);
        }
    }

    @Override
    protected void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, world, pos, oldState, movedByPiston);
        world.scheduleTick(pos, this, spring.tickRate);
    }

    private void assertSpring(ServerLevel world, BlockPos pos, RandomSource rand) {
        world.scheduleTick(pos, this, spring.tickRate);
        if (!spring.canGen.getAsBoolean() || spring.liquidBlock == null) {
            return;
        }
        BlockPos above = pos.above();
        if (!world.isEmptyBlock(above)) {
            return;
        }
        if (spring.chance != -1 && rand.nextInt(spring.chance) != 0) {
            return;
        }
        world.setBlockAndUpdate(above, spring.liquidBlock.get().defaultBlockState());
    }
}
