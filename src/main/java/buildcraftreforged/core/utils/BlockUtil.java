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
 * is rebuilt from destroyBlock, particles and sound; blocks are broken as a fake player (the machine owner's, #27, see
 * BuildCraftPlayer) after BreakBlockEvent allows it, and breakBlock says whether it broke the block.
 */
package buildcraftreforged.core.utils;

import buildcraftreforged.api.core.BuildCraftAPI;
import buildcraftreforged.core.BuildCraftConfiguration;
import buildcraftreforged.energy.EnergyRegistry;
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
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import org.jspecify.annotations.Nullable;

public class BlockUtil {

    /**
     * 4.2.2's {@code CoreProxy.proxy.getBuildCraftPlayer(world)}: the "[BuildCraft]" fake player, for callers with no
     * machine at hand. Machines use their owner's player instead, BuildCraftPlayer.get(tile), and pass it to the
     * overloads below that take a Player.
     */
    public static Player getBuildCraftPlayer(ServerLevel world) {
        return BuildCraftPlayer.get(world, null);
    }

    public static @Nullable List<ItemStack> getItemStackFromBlock(ServerLevel world, int i, int j, int k) {
        return getItemStackFromBlock(world, i, j, k, getBuildCraftPlayer(world));
    }

    /** The drops of the block, with player as breaker. Does not ask BreakBlockEvent; see BuildCraftPlayer.canBreak. */
    public static @Nullable List<ItemStack> getItemStackFromBlock(ServerLevel world, int i, int j, int k, Player player) {
        BlockPos pos = new BlockPos(i, j, k);
        BlockState state = world.getBlockState(pos);

        if (state.isAir())
            return null;

        // 4.2.2 fired ForgeEventFactory.fireBlockHarvesting here; the loot tables now take the breaker and NeoForge's
        // BlockDropsEvent fires when the drops are spawned through Block.dropResources, which breakBlock does not use.
        List<ItemStack> dropsList = Block.getDrops(state, world, pos, world.getBlockEntity(pos), player, ItemStack.EMPTY);

        return new ArrayList<ItemStack>(dropsList);
    }

    public static boolean breakBlock(Level world, int x, int y, int z) {
        return breakBlock(world, x, y, z, BuildCraftConfiguration.itemLifespan.get());
    }

    public static boolean breakBlock(Level world, int x, int y, int z, int forcedLifespan) {
        Player player = world instanceof ServerLevel serverLevel ? getBuildCraftPlayer(serverLevel) : null;
        return breakBlock(world, x, y, z, forcedLifespan, player);
    }

    /** Breaks the block as player (the machine owner's, from BuildCraftPlayer.get(tile)) with the configured lifespan. */
    public static boolean breakBlock(Level world, int x, int y, int z, @Nullable Player player) {
        return breakBlock(world, x, y, z, BuildCraftConfiguration.itemLifespan.get(), player);
    }

    /**
     * Breaks the block as player and drops what it drops. False, with the block left alone, when BreakBlockEvent was
     * cancelled for player (a protected or claimed block); 4.2.2 broke blocks without asking. A null player (client
     * side) asks no one.
     */
    public static boolean breakBlock(Level world, int x, int y, int z, int forcedLifespan, @Nullable Player player) {
        BlockPos pos = new BlockPos(x, y, z);

        if (player != null && !BuildCraftPlayer.canBreak(player, world, pos))
            return false;

        if (!world.isEmptyBlock(pos) && BuildCraftConfiguration.dropBrokenBlocks.get() && world instanceof ServerLevel serverLevel && serverLevel.getGameRules().get(GameRules.BLOCK_DROPS)) {
            List<ItemStack> items = getItemStackFromBlock(serverLevel, x, y, z, player != null ? player : getBuildCraftPlayer(serverLevel));

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
        return true;
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

        if (state.is(EnergyRegistry.blockOil.get()))
            return false;

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
