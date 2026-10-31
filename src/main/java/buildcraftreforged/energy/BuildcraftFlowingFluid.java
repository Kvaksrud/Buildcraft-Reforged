/*
 * Buildcraft Reforged, 2026: the flow half of 4.2.2's BlockBuildcraftFluid. Forge 1.6.4's BlockFluidClassic was block
 * and flow logic in one; vanilla now splits them into LiquidBlock and FlowingFluid, so the rules that kept oil and fuel
 * apart from other liquids live here: BlockBuildcraftFluid.canDisplace (they never flow into another liquid) and
 * Material.water (no other liquid flows into them; lava hardens against them, see EnergyRegistry).
 */
package buildcraftreforged.energy;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;

/**
 * Oil and fuel. Forge 1.6.4's BlockFluidClassic flowed 7 blocks (quantaPerBlock 8) and ticked every viscosity / 200
 * ticks; the registry passes that tick rate in. Sources never form from flowing fluid, as in BlockFluidClassic.
 */
public abstract class BuildcraftFlowingFluid extends BaseFlowingFluid {

    protected BuildcraftFlowingFluid(Properties properties) {
        super(properties);
    }

    /**
     * BlockBuildcraftFluid.canDisplace: a liquid below is not displaced. BlockFluidClassic then spread sideways over it,
     * so the fluid spreads to the sides instead of stopping.
     */
    @Override
    protected void spread(ServerLevel level, BlockPos pos, BlockState state, FluidState fluidState) {
        FluidState belowFluid = level.getFluidState(pos.below());
        if (!fluidState.isEmpty() && !belowFluid.isEmpty() && !belowFluid.getType().isSame(this)) {
            spreadToSides(level, pos, fluidState, state);
            return;
        }
        super.spread(level, pos, state, fluidState);
    }

    /** FlowingFluid.spreadToSides, which is private. */
    private void spreadToSides(ServerLevel level, BlockPos pos, FluidState fluidState, BlockState state) {
        int neighbor = fluidState.getAmount() - getDropOff(level);
        if (fluidState.getValue(FALLING)) {
            neighbor = 7;
        }

        if (neighbor > 0) {
            for (Map.Entry<Direction, FluidState> entry : getSpread(level, pos, state).entrySet()) {
                BlockPos neighborPos = pos.relative(entry.getKey());
                spreadTo(level, neighborPos, level.getBlockState(neighborPos), entry.getKey(), entry.getValue());
            }
        }
    }

    /** Material.water in 4.2.2: water could not displace water-material blocks, and nothing else flowed into them. */
    @Override
    protected boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos, Fluid fluid, Direction direction) {
        return false;
    }

    public static class Flowing extends BuildcraftFlowingFluid {
        public Flowing(Properties properties) {
            super(properties);
            registerDefaultState(getStateDefinition().any().setValue(LEVEL, 7));
        }

        @Override
        protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
            super.createFluidStateDefinition(builder);
            builder.add(LEVEL);
        }

        @Override
        public int getAmount(FluidState state) {
            return state.getValue(LEVEL);
        }

        @Override
        public boolean isSource(FluidState state) {
            return false;
        }
    }

    public static class Source extends BuildcraftFlowingFluid {
        public Source(Properties properties) {
            super(properties);
        }

        @Override
        public int getAmount(FluidState state) {
            return 8;
        }

        @Override
        public boolean isSource(FluidState state) {
            return true;
        }
    }
}
