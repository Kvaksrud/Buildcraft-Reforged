/*
 * New in Buildcraft Reforged, 2026 (#27); 4.2.2 had CoreProxy.getBuildCraftPlayer, a fake player named
 * "[BuildCraft]" that broke and placed blocks without asking anyone.
 */
package buildcraftreforged.core.utils;

import buildcraftreforged.core.TileBuildCraft;
import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.EventHooks;
import org.jspecify.annotations.Nullable;

/**
 * The fake player machines break and place blocks as. It carries the game profile of the player who placed the
 * machine (TileBuildCraft.getOwnerProfile), so protection, claim and team mods see that player: a quarry works inside
 * its owner's claim and stops at anyone else's. Every break goes through BreakBlockEvent ({@link #canBreak}), every
 * placement through EntityPlaceEvent ({@link #placeBlock}, {@link #useItemOn}).
 *
 * <p>Mining well, quarry, builder and filler: get the player once per action with {@link #get(TileBuildCraft)}, then
 * break with {@code BlockUtil.breakBlock(..., player)} or ask {@link #canBreak} before taking the drops yourself
 * ({@code BlockUtil.getItemStackFromBlock(..., player)}), and place with {@link #placeBlock} or {@link #useItemOn}. A
 * refused action leaves the world unchanged; the machine decides whether to skip the block or stop.
 */
public final class BuildCraftPlayer {

    /**
     * 4.2.2's "[BuildCraft]" player, for machines no player placed (or placed before #27). Its UUID is fixed and
     * belongs to no real player, so claim mods treat it as a stranger.
     */
    public static final GameProfile DEFAULT = new GameProfile(
            UUID.nameUUIDFromBytes("buildcraftreforged:[BuildCraft]".getBytes(StandardCharsets.UTF_8)), "[BuildCraft]");

    private BuildCraftPlayer() {
    }

    /** The fake player with owner's profile, or with {@link #DEFAULT} when owner is null. */
    public static FakePlayer get(ServerLevel level, @Nullable GameProfile owner) {
        return FakePlayerFactory.get(level, owner != null ? owner : DEFAULT);
    }

    /** The fake player of the tile's owner. Server side only: null while the tile has no server level. */
    public static @Nullable FakePlayer get(TileBuildCraft tile) {
        if (tile.getLevel() instanceof ServerLevel serverLevel)
            return get(serverLevel, tile.getOwnerProfile());
        return null;
    }

    /**
     * Fires BreakBlockEvent (older NeoForge's BlockEvent.BreakEvent) for player at pos, as a survival player
     * breaking it by hand would. False when a listener cancelled it; nothing is broken either way. Air is always
     * breakable.
     */
    public static boolean canBreak(Player player, Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir())
            return true;
        return !CommonHooks.fireBlockBreak(level, GameType.SURVIVAL, player, pos, state).isCanceled();
    }

    /**
     * Sets state at pos as player, then fires EntityPlaceEvent as vanilla does after a placement; a cancelled event
     * restores the block that was there. True when the block stays.
     */
    public static boolean placeBlock(Player player, ServerLevel level, BlockPos pos, BlockState state) {
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos);
        if (!level.setBlock(pos, state, Block.UPDATE_ALL))
            return false;
        if (EventHooks.onBlockPlace(player, snapshot, Direction.UP)) {
            level.restoringBlockSnapshots = true;
            snapshot.restore(snapshot.getFlags() | Block.UPDATE_CLIENTS);
            level.restoringBlockSnapshots = false;
            return false;
        }
        return true;
    }

    /**
     * Runs place, which sets one or more blocks directly (a schematic's buildBlock), as player: the blocks it sets are
     * captured as NeoForge's item placement does, EntityPlaceEvent (or EntityMultiPlaceEvent for a door or bed) is
     * fired, and a cancelled event restores every captured block. Otherwise the deferred onPlace and neighbour updates
     * run. True when the blocks stay. Added for the builder (#78).
     */
    public static boolean placeAs(Player player, ServerLevel level, Runnable place) {
        level.captureBlockSnapshots = true;
        try {
            place.run();
        } finally {
            level.captureBlockSnapshots = false;
        }

        List<BlockSnapshot> snapshots = new ArrayList<>(level.capturedBlockSnapshots);
        level.capturedBlockSnapshots.clear();

        boolean refused = false;
        if (snapshots.size() > 1) {
            refused = EventHooks.onMultiBlockPlace(player, snapshots, Direction.UP);
        } else if (snapshots.size() == 1) {
            refused = EventHooks.onBlockPlace(player, snapshots.get(0), Direction.UP);
        }

        if (refused) {
            for (BlockSnapshot snapshot : snapshots.reversed()) {
                level.restoringBlockSnapshots = true;
                snapshot.restore(snapshot.getFlags() | Block.UPDATE_CLIENTS);
                level.restoringBlockSnapshots = false;
            }
            return false;
        }

        for (BlockSnapshot snapshot : snapshots) {
            BlockState oldState = snapshot.getState();
            BlockState newState = level.getBlockState(snapshot.getPos());
            newState.onPlace(level, snapshot.getPos(), oldState, false);
            level.markAndNotifyBlock(snapshot.getPos(), level.getChunkAt(snapshot.getPos()), oldState, newState,
                    snapshot.getFlags(), 512);
        }
        return true;
    }

    /**
     * 4.2.2's {@code stack.getItem().onItemUse(stack, buildCraftPlayer, world, x, y, z, side, ...)}: uses stack on
     * face of the block at against, as player. This is the standard item path, so NeoForge fires EntityPlaceEvent and
     * reverts a refused placement. stack is used up as the item decides; player's hand is empty again afterwards.
     */
    public static InteractionResult useItemOn(Player player, ItemStack stack, BlockPos against, Direction face) {
        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        try {
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(against).relative(face, 0.5), face, against, false);
            return stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
        }
    }
}
