package buildcraftreforged.gametest;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.core.utils.BlockUtil;
import buildcraftreforged.core.utils.BuildCraftPlayer;
import buildcraftreforged.gametest.status.SampleStatus;
import buildcraftreforged.gametest.status.TileSampleStatus;
import com.mojang.authlib.GameProfile;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/**
 * #27: the placer's profile is recorded and saved, machines act as a fake player with that profile, and a cancelled
 * BreakBlockEvent or EntityPlaceEvent leaves the block alone. {@link #PROTECTED} stands in for a claim mod: it refuses
 * every break and placement at its positions, for every player.
 */
@EventBusSubscriber(modid = BuildcraftReforged.MOD_ID)
final class OwnerTests {
    private static final BlockPos MACHINE = new BlockPos(1, 1, 1);
    private static final BlockPos FREE = new BlockPos(0, 1, 0);
    private static final BlockPos CLAIMED = new BlockPos(2, 1, 2);

    /** Absolute positions the stand-in claim mod protects. */
    private static final Set<BlockPos> PROTECTED = ConcurrentHashMap.newKeySet();
    /** The UUID of the last player the stand-in saw breaking or placing at a test position. */
    private static volatile UUID lastActor;

    private OwnerTests() {
    }

    @SubscribeEvent
    static void onBreak(BreakBlockEvent event) {
        lastActor = event.getPlayer().getUUID();
        if (PROTECTED.contains(event.getPos()))
            event.setCanceled(true);
    }

    @SubscribeEvent
    static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() != null)
            lastActor = event.getEntity().getUUID();
        if (PROTECTED.contains(event.getPos()))
            event.setCanceled(true);
    }

    /** A mock player out of the way, so it obstructs no placement. */
    private static ServerPlayer placer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.snapTo(Vec3.atBottomCenterOf(helper.absolutePos(MACHINE).above(4)));
        return player;
    }

    private static TileSampleStatus placeMachine(GameTestHelper helper, Player placer) {
        helper.setBlock(MACHINE, SampleStatus.block);
        BlockPos pos = helper.absolutePos(MACHINE);
        BlockState state = helper.getLevel().getBlockState(pos);
        SampleStatus.block.setPlacedBy(helper.getLevel(), pos, state, placer, new ItemStack(SampleStatus.block));
        return helper.getBlockEntity(MACHINE, TileSampleStatus.class);
    }

    /**
     * Placing records the placer's UUID and name, the status panel's Owner line shows the name, the profile survives a
     * save and load, and the machine's fake player carries it. A block no player placed acts as "[BuildCraft]".
     */
    static void recordedAndSaved(GameTestHelper helper) {
        ServerPlayer placer = placer(helper);
        TileSampleStatus tile = placeMachine(helper, placer);
        GameProfile profile = tile.getOwnerProfile();
        helper.assertTrue(profile != null, "no owner profile after placement");
        helper.assertValueEqual(profile.id(), placer.getUUID(), "owner UUID");
        helper.assertValueEqual(profile.name(), placer.getGameProfile().name(), "owner name");
        helper.assertValueEqual(tile.getStatusOwner(), placer.getGameProfile().name(), "Owner line");

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        tile.saveCustomOnly(output);
        CompoundTag tag = output.buildResult();
        TileSampleStatus loaded = new TileSampleStatus(tile.getBlockPos(), tile.getBlockState());
        loaded.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag));
        helper.assertValueEqual(loaded.getOwnerProfile(), profile, "owner profile after load");
        helper.assertValueEqual(loaded.getOwner(), profile.name(), "owner name after load");

        FakePlayer fake = BuildCraftPlayer.get(tile);
        helper.assertTrue(fake != null, "no fake player on the server");
        helper.assertValueEqual(fake.getUUID(), placer.getUUID(), "fake player UUID");
        helper.assertValueEqual(fake.getGameProfile().name(), placer.getGameProfile().name(), "fake player name");

        helper.setBlock(MACHINE.above(), SampleStatus.block);
        TileSampleStatus unowned = helper.getBlockEntity(MACHINE.above(), TileSampleStatus.class);
        helper.assertTrue(unowned.getOwnerProfile() == null, "unowned block has a profile");
        helper.assertValueEqual(BuildCraftPlayer.get(unowned).getGameProfile(), BuildCraftPlayer.DEFAULT, "default player");
        helper.succeed();
    }

    /**
     * The machine breaks as its owner: a protected block is refused and stays, an unprotected one is broken and drops,
     * and the listener saw the owner's UUID both times.
     */
    static void breakAsOwner(GameTestHelper helper) {
        ServerPlayer placer = placer(helper);
        TileSampleStatus tile = placeMachine(helper, placer);
        FakePlayer owner = BuildCraftPlayer.get(tile);
        ServerLevel level = helper.getLevel();
        helper.setBlock(FREE, Blocks.DIRT);
        helper.setBlock(CLAIMED, Blocks.DIRT);
        BlockPos claimed = helper.absolutePos(CLAIMED);
        BlockPos free = helper.absolutePos(FREE);
        PROTECTED.add(claimed);
        try {
            lastActor = null;
            helper.assertFalse(BuildCraftPlayer.canBreak(owner, level, claimed), "protected block may be broken");
            helper.assertFalse(BlockUtil.breakBlock(level, claimed.getX(), claimed.getY(), claimed.getZ(), owner),
                    "protected block reported broken");
            helper.assertBlockPresent(Blocks.DIRT, CLAIMED);
            helper.assertValueEqual(lastActor, placer.getUUID(), "breaker seen at the protected block");

            lastActor = null;
            helper.assertTrue(BlockUtil.breakBlock(level, free.getX(), free.getY(), free.getZ(), owner),
                    "unprotected block not broken");
            helper.assertBlockPresent(Blocks.AIR, FREE);
            helper.assertValueEqual(lastActor, placer.getUUID(), "breaker seen at the unprotected block");
        } finally {
            PROTECTED.remove(claimed);
        }
        // The drop flies off with a random velocity and can leave the 1x1x1 test area, so look around the block.
        helper.succeedWhen(() -> helper.assertItemEntityPresent(Items.DIRT, FREE, 3.0));
    }

    /**
     * Placement as the owner: a protected position refuses both a direct placement and an item use and keeps the
     * block that was there; an unprotected one takes both.
     */
    static void placeAsOwner(GameTestHelper helper) {
        ServerPlayer placer = placer(helper);
        TileSampleStatus tile = placeMachine(helper, placer);
        FakePlayer owner = BuildCraftPlayer.get(tile);
        ServerLevel level = helper.getLevel();
        BlockPos claimed = helper.absolutePos(CLAIMED);
        BlockPos free = helper.absolutePos(FREE);
        helper.setBlock(CLAIMED.below(), Blocks.DIRT);
        helper.setBlock(FREE.below(), Blocks.DIRT);
        PROTECTED.add(claimed);
        try {
            lastActor = null;
            helper.assertFalse(BuildCraftPlayer.placeBlock(owner, level, claimed, Blocks.DIRT.defaultBlockState()),
                    "protected placement accepted");
            helper.assertBlockPresent(Blocks.AIR, CLAIMED);
            helper.assertValueEqual(lastActor, placer.getUUID(), "placer seen at the protected position");

            ItemStack bricks = new ItemStack(Items.BRICKS, 2);
            InteractionResult refused = BuildCraftPlayer.useItemOn(owner, bricks, claimed.below(), Direction.UP);
            helper.assertFalse(refused.consumesAction(), "protected item use accepted");
            helper.assertBlockPresent(Blocks.AIR, CLAIMED);
            helper.assertValueEqual(bricks.getCount(), 2, "bricks used up by a refused placement");

            helper.assertTrue(BuildCraftPlayer.placeBlock(owner, level, free, Blocks.DIRT.defaultBlockState()),
                    "unprotected placement refused");
            helper.assertBlockPresent(Blocks.DIRT, FREE);
            helper.setBlock(FREE, Blocks.AIR);
            InteractionResult used = BuildCraftPlayer.useItemOn(owner, bricks, free.below(), Direction.UP);
            helper.assertTrue(used.consumesAction(), "unprotected item use refused: " + used);
            helper.assertBlockPresent(Blocks.BRICKS, FREE);
            helper.assertValueEqual(bricks.getCount(), 1, "bricks left after placing one");
            helper.assertTrue(owner.getMainHandItem().isEmpty(), "fake player still holds the stack");
        } finally {
            PROTECTED.remove(claimed);
        }
        helper.succeed();
    }
}
