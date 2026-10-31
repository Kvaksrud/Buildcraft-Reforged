/*
 * Copyright (c) SpaceToad, 2011-2012
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: Forge's BlockFluidClassic becomes vanilla LiquidBlock; the flow rules
 * (canDisplace, displaceIfPossible) move to BuildcraftFlowingFluid. Icons come from the client FluidModel
 * (EnergyProxyClient). In the Nether a flammable fluid now turns into fire instead of exploding (docs/PLAN.md
 * section 19.2, config fluids.netherIgnition). flammable is read through a supplier so oil follows burnOil as loaded.
 * The drip particle goes through the drop particle type.
 */
package buildcraftreforged.energy;

import buildcraftreforged.core.BuildCraftConfiguration;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.redstone.Orientation;
import org.jspecify.annotations.Nullable;

/**
 *
 * @author CovertJaguar <http://www.railcraft.info/>
 */
public class BlockBuildcraftFluid extends LiquidBlock {

    protected float particleRed;
    protected float particleGreen;
    protected float particleBlue;

    public BlockBuildcraftFluid(FlowingFluid fluid, BlockBehaviour.Properties properties) {
        super(fluid, properties);
    }
    protected BooleanSupplier flammable = () -> false;
    protected int flammability = 0;

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation,
            boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        if (!level.isClientSide() && flammable.getAsBoolean() && level.dimension() == Level.NETHER
                && BuildCraftConfiguration.netherIgnition.get()) {
            // 4.2.2: world.newExplosion(null, x, y, z, 4F, true, true) before removing the block.
            level.setBlockAndUpdate(pos, BaseFireBlock.getState(level, pos));
        }
    }

    public BlockBuildcraftFluid setFlammable(boolean flammable) {
        return setFlammable(() -> flammable);
    }

    public BlockBuildcraftFluid setFlammable(BooleanSupplier flammable) {
        this.flammable = flammable;
        return this;
    }

    public BlockBuildcraftFluid setFlammability(int flammability) {
        this.flammability = flammability;
        return this;
    }

    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return flammable.getAsBoolean() ? 300 : 0;
    }

    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return flammability;
    }

    @Override
    public boolean isFlammable(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return flammable.getAsBoolean();
    }

    @Override
    public boolean isFireSource(BlockState state, LevelReader level, BlockPos pos, Direction direction) {
        return flammable.getAsBoolean() && flammability == 0;
    }

    public BlockBuildcraftFluid setParticleColor(float particleRed, float particleGreen, float particleBlue) {
        this.particleRed = particleRed;
        this.particleGreen = particleGreen;
        this.particleBlue = particleBlue;
        return this;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource rand) {
        super.animateTick(state, level, pos, rand);

        BlockPos below = pos.below();
        if (rand.nextInt(10) == 0 && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                && !level.getBlockState(pos.below(2)).isSolid()) {
            double px = pos.getX() + rand.nextFloat();
            double py = pos.getY() - 1.05D;
            double pz = pos.getZ() + rand.nextFloat();

            level.addParticle(ColorParticleOption.create(EnergyRegistry.dropParticle.get(), particleRed, particleGreen, particleBlue),
                    px, py, pz, 0.0D, 0.0D, 0.0D);
        }
    }
}
