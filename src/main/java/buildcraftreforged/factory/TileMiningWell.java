/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: power in FE (MJ x 10); the column is dug as the owner's fake player, and a
 * refused BreakBlockEvent stops the well (blocked) instead of breaking the block; the world bottom is the level's
 * minimum Y; invalidate becomes preRemoveSideEffects; the status panel (plan section 10) is new.
 */
package buildcraftreforged.factory;

import buildcraftreforged.api.gates.IAction;
import buildcraftreforged.api.power.IPowerReceptor;
import buildcraftreforged.api.power.PowerHandler;
import buildcraftreforged.api.power.PowerHandler.PowerReceiver;
import buildcraftreforged.api.power.PowerHandler.Type;
import buildcraftreforged.core.BuildCraftConfiguration;
import buildcraftreforged.core.IMachine;
import buildcraftreforged.core.TileBuildCraft;
import buildcraftreforged.core.status.FlowMeter;
import buildcraftreforged.core.status.IStatusProvider;
import buildcraftreforged.core.status.MachineStatus;
import buildcraftreforged.core.status.StatusReason;
import buildcraftreforged.core.status.StatusReport;
import buildcraftreforged.core.utils.BlockUtil;
import buildcraftreforged.core.utils.BuildCraftPlayer;
import buildcraftreforged.core.utils.Utils;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.jspecify.annotations.Nullable;

public class TileMiningWell extends TileBuildCraft implements IMachine, IPowerReceptor, IStatusProvider {

    /** 4.2.2 BuildCraftFactory.MINING_MJ_COST_PER_BLOCK, 64 MJ, in FE. */
    public static final long MINING_ENERGY_COST_PER_BLOCK = 640;

    boolean isDigging = true;
    private PowerHandler powerHandler;
    /**
     * Not in 4.2.2: BreakBlockEvent was cancelled for the owner at the next block (a claim or protection). The well
     * stops there and tries again on its next work cycle (status panel Reason, Machine trigger blocked).
     */
    private boolean isBlocked;
    /** Not in 4.2.2: the status panel's Flow and Power lines, counted per block dug. */
    public final FlowMeter flowMeter = new FlowMeter(() -> level == null ? 0 : level.getGameTime());

    public TileMiningWell(BlockPos pos, BlockState state) {
        super(FactoryRegistry.miningWellTile.get(), pos, state);
        powerHandler = new PowerHandler(this, Type.MACHINE);

        float multiplier = miningMultiplier();
        long mj = (long) (MINING_ENERGY_COST_PER_BLOCK * multiplier);
        powerHandler.configure((long) (1000 * multiplier), (long) (1000 * multiplier), mj, (long) (10000 * multiplier));
        powerHandler.configurePowerPerdition(10, 1);
    }

    /** 4.2.2 BuildCraftFactory.miningMultiplier; the default until the common config is loaded. */
    static float miningMultiplier() {
        return BuildCraftConfiguration.COMMON_SPEC.isLoaded() ? BuildCraftConfiguration.miningMultiplier.get().floatValue()
                : BuildCraftConfiguration.miningMultiplier.getDefault().floatValue();
    }

    /** 4.2.2 BuildCraftFactory.miningDepth; the default until the common config is loaded. */
    static int miningDepth() {
        return BuildCraftConfiguration.COMMON_SPEC.isLoaded() ? BuildCraftConfiguration.miningDepth.get()
                : BuildCraftConfiguration.miningDepth.getDefault();
    }

    /**
     * Dig the next available piece of land if not done. As soon as it reaches
     * bedrock, lava or goes below 0, it's considered done.
     */
    @Override
    public void doWork(PowerHandler workProvider) {
        long mj = (long) (MINING_ENERGY_COST_PER_BLOCK * miningMultiplier());
        if (powerHandler.useEnergy(mj, mj, true) != mj)
            return;
        flowMeter.energyIn(mj);

        if (!(level instanceof ServerLevel world))
            return;

        int xCoord = worldPosition.getX();
        int yCoord = worldPosition.getY();
        int zCoord = worldPosition.getZ();

        int depth = yCoord - 1;

        while (world.getBlockState(new BlockPos(xCoord, depth, zCoord)).is(FactoryRegistry.plainPipeBlock.get())) {
            depth = depth - 1;
        }

        // 1.6.4's "depth < 1" kept the well above bedrock layer 0; the world now starts at getMinY().
        if (depth < world.getMinY() + 1 || depth < yCoord - miningDepth() || !BlockUtil.canChangeBlock(world, xCoord, depth, zCoord)) {
            isDigging = false;
            return;
        }

        BlockPos pos = new BlockPos(xCoord, depth, zCoord);

        // New: the owner's fake player must be allowed to break the block (#27). A refusal stops the well here, it
        // does not skip the block.
        FakePlayer owner = BuildCraftPlayer.get(this);
        if (owner == null || !BuildCraftPlayer.canBreak(owner, world, pos)) {
            isBlocked = true;
            return;
        }
        isBlocked = false;

        boolean wasAir = world.isEmptyBlock(pos);

        List<ItemStack> stacks = BlockUtil.getItemStackFromBlock(world, xCoord, depth, zCoord, owner);

        world.setBlockAndUpdate(pos, FactoryRegistry.plainPipeBlock.get().defaultBlockState());

        if (wasAir)
            return;

        if (stacks == null || stacks.isEmpty())
            return;

        for (ItemStack stack : stacks) {
            flowMeter.itemsOut(stack.getCount());

            stack.shrink(Utils.addToRandomInventoryAround(world, xCoord, yCoord, zCoord, stack));
            if (stack.getCount() <= 0)
                continue;

            stack.shrink(Utils.addToRandomPipeAround(world, xCoord, yCoord, zCoord, null, stack));
            if (stack.getCount() <= 0)
                continue;

            // Throw the object away.
            // TODO: factorize that code

            float f = world.getRandom().nextFloat() * 0.8F + 0.1F;
            float f1 = world.getRandom().nextFloat() * 0.8F + 0.1F;
            float f2 = world.getRandom().nextFloat() * 0.8F + 0.1F;

            ItemEntity entityitem = new ItemEntity(world, xCoord + f, yCoord + f1 + 0.5F, zCoord + f2, stack);

            entityitem.lifespan = BuildCraftConfiguration.itemLifespan.get();
            entityitem.setPickUpDelay(10);

            float f3 = 0.05F;
            entityitem.setDeltaMovement((float) world.getRandom().nextGaussian() * f3,
                    (float) world.getRandom().nextGaussian() * f3 + 1.0F,
                    (float) world.getRandom().nextGaussian() * f3);
            world.addFreshEntity(entityitem);
        }
    }

    /** 4.2.2 invalidate: the tile going away takes its pipe column with it. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null && !level.isClientSide() && pos.getY() > level.getMinY() + 2) {
            BlockMiningWell.removePipes(level, pos.getX(), pos.getY(), pos.getZ());
        }
    }

    @Override
    public boolean isActive() {
        return isDigging;
    }

    @Override
    public boolean isBlocked() {
        return isBlocked;
    }

    @Override
    public PowerReceiver getPowerReceiver(@Nullable Direction side) {
        return powerHandler.getPowerReceiver();
    }

    @Override
    public boolean manageFluids() {
        return false;
    }

    @Override
    public boolean manageSolids() {
        return true;
    }

    @Override
    public boolean allowAction(IAction action) {
        return false;
    }

    // Status panel (plan section 10), not in 4.2.2.

    @Override
    public MachineStatus getStatusState() {
        if (isBlocked)
            return MachineStatus.BLOCKED;
        if (!isDigging)
            return MachineStatus.IDLE;
        if (powerHandler.getEnergyStored() < powerHandler.getActivationEnergy())
            return MachineStatus.NO_POWER;
        return MachineStatus.WORKING;
    }

    @Override
    public StatusReason getStatusReason() {
        return isBlocked ? StatusReason.PROTECTED_BLOCK : StatusReason.NONE;
    }

    @Override
    public FlowMeter getStatusFlow() {
        return flowMeter;
    }

    @Override
    public StatusReport.Power getStatusPower() {
        return StatusReport.Power.drawn(flowMeter, powerHandler.getEnergyStored(), powerHandler.getMaxEnergyStored());
    }
}
