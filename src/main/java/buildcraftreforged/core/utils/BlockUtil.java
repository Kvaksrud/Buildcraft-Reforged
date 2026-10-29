/**
 * Copyright (c) 2011-2014, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: block ids and metadata become BlockState; the options come from
 * BuildCraftConfiguration; Forge fluid blocks become
 * LiquidBlock; the drop list comes from loot tables with the BuildCraft player as breaker; the single-block explosion
 * is rebuilt from destroyBlock, particles and sound; the BuildCraft player is a marked seam for the owner-profile fake
 * player (#27).
 */
package buildcraftreforged.core.utils;

import buildcraftreforged.api.core.BuildCraftAPI;
import buildcraftreforged.core.BuildCraftConfiguration;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import org.jspecify.annotations.Nullable;

public class BlockUtil {

    /**
     * SEAM(#27): the player BuildCraft machines break and place blocks as; 4.2.2's
     * {@code CoreProxy.proxy.getBuildCraftPlayer(world)}. #27 replaces this with the fake player carrying the owner's
     * game profile, so claim mods see the owner. Every break path in this class asks this method for its player; place
     * paths (builder, filler, pump) must ask it too when they are ported.
     */
    public static Player getBuildCraftPlayer(ServerLevel world) {
        return FakePlayerFactory.getMinecraft(world);
    }

    public static @Nullable List<ItemStack> getItemStackFromBlock(ServerLevel world, int i, int j, int k) {
        BlockPos pos = new BlockPos(i, j, k);
        BlockState state = world.getBlockState(pos);

        if (state.isAir())
            return null;

        // 4.2.2 fired ForgeEventFactory.fireBlockHarvesting here; the loot tables now take the breaker and NeoForge's
        // BlockDropsEvent fires when the drops are spawned through Block.dropResources, which breakBlock does not use.
        List<ItemStack> dropsList = Block.getDrops(state, world, pos, world.getBlockEntity(pos), getBuildCraftPlayer(world), ItemStack.EMPTY);

        return new ArrayList<ItemStack>(dropsList);
    }

    public static void breakBlock(Level world, int x, int y, int z) {
        breakBlock(world, x, y, z, BuildCraftConfiguration.itemLifespan.get());
    }

    public static void breakBlock(Level world, int x, int y, int z, int forcedLifespan) {
        BlockPos pos = new BlockPos(x, y, z);

        // SEAM(#27): 4.2.2 broke blocks without asking anyone. #27 fires BlockEvent.BreakEvent for
        // getBuildCraftPlayer(world) here and returns without breaking when it is cancelled.
        if (!world.isEmptyBlock(pos) && BuildCraftConfiguration.dropBrokenBlocks.get() && world instanceof ServerLevel serverLevel && serverLevel.getGameRules().get(GameRules.BLOCK_DROPS)) {
            List<ItemStack> items = getItemStackFromBlock(serverLevel, x, y, z);

            for (ItemStack item : items) {
                float var = 0.7F;
                double dx = world.getRandom().nextFloat() * var + (1.0F - var) * 0.5D;
                double dy = world.getRandom().nextFloat() * var + (1.0F - var) * 0.5D;
                double dz = world.getRandom().nextFloat() * var + (1.0F - var) * 0.5D;
                ItemEntity entityitem = new ItemEntity(world, x + dx, y + dy, z + dz, item);

                entityitem.lifespan = forcedLifespan;
                entityitem.setPickUpDelay(10);

                world.addFreshEntity(entityitem);
            }
        }

        world.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    }

    public static boolean canChangeBlock(Level world, int x, int y, int z) {
        return canChangeBlock(world.getBlockState(new BlockPos(x, y, z)), world, x, y, z);
    }

    public static boolean isAnObstructingBlock(BlockState state, Level world, int x, int y, int z) {
        if (state.isAir())
            return false;
        return true;
    }

    public static boolean canChangeBlock(BlockState state, Level world, int x, int y, int z) {
        if (state.isAir())
            return true;

        if (state.getDestroySpeed(world, new BlockPos(x, y, z)) < 0)
            return false;

        // TODO(#42): if (state.is(BuildCraftEnergy.blockOil)) return false;

        if (state.is(Blocks.LAVA))
            return false;

        return true;
    }

    public static boolean isSoftBlock(Level world, int x, int y, int z) {
        return isSoftBlock(world.getBlockState(new BlockPos(x, y, z)), world, x, y, z);
    }

    public static boolean isSoftBlock(BlockState state, Level world, int x, int y, int z) {
        return state.isAir() || BuildCraftAPI.softBlocks.contains(state.getBlock());
    }

    /**
     * Returns true if a block cannot be harvested without a tool.
     */
    public static boolean isToughBlock(Level world, int x, int y, int z) {
        return world.getBlockState(new BlockPos(x, y, z)).requiresCorrectToolForDrops();
    }

    public static boolean isFullFluidBlock(Level world, int x, int y, int z) {
        return isFullFluidBlock(world.getBlockState(new BlockPos(x, y, z)), world, x, y, z);
    }

    public static boolean isFullFluidBlock(BlockState state, Level world, int x, int y, int z) {
        if (state.getBlock() instanceof LiquidBlock)
            return state.getValue(LiquidBlock.LEVEL) == 0;
        return false;
    }

    /** The source fluid of a fluid block, or null. Water and lava are LiquidBlocks too now. */
    public static @Nullable Fluid getFluid(BlockState state) {
        if (state.getBlock() instanceof LiquidBlock fluidBlock) {
            return fluidBlock.fluid;
        }
        return null;
    }

    public static @Nullable FluidStack drainBlock(Level world, int x, int y, int z, boolean doDrain) {
        return drainBlock(world.getBlockState(new BlockPos(x, y, z)), world, x, y, z, doDrain);
    }

    public static @Nullable FluidStack drainBlock(BlockState state, Level world, int x, int y, int z, boolean doDrain) {
        if (state.getBlock() instanceof LiquidBlock fluidBlock) {
            if (state.getValue(LiquidBlock.LEVEL) != 0)
                return null;
            if (doDrain)
                world.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
            return new FluidStack(fluidBlock.fluid, FluidType.BUCKET_VOLUME);
        }
        return null;
    }

    /**
     * Create an explosion which only affects a single block.
     */
    public static void explodeBlock(Level world, int x, int y, int z) {
        if (!(world instanceof ServerLevel serverLevel))
            return;

        // 4.2.2 built a size 3 Explosion with this one block as its only affected position and ran only its second
        // half (sound, particles, drops); the same effects without an Explosion object:
        serverLevel.playSound(null, x + .5, y + .5, z + .5, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 4.0F,
                (1.0F + (world.getRandom().nextFloat() - world.getRandom().nextFloat()) * 0.2F) * 0.7F);
        serverLevel.sendParticles(ParticleTypes.EXPLOSION, x + .5, y + .5, z + .5, 1, 0, 0, 0, 0);
        world.destroyBlock(new BlockPos(x, y, z), true);
    }
}
