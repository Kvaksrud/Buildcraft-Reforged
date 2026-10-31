package buildcraftreforged.gametest;

import buildcraftreforged.factory.BlockTank;
import buildcraftreforged.factory.FactoryRegistry;
import buildcraftreforged.factory.TileTank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * #64: the Tank (4.2.2 BlockTank/TileTank). Stacked tanks share one fluid handler that fills from the bottom up and
 * drains from the bottom, fluid falls into the tank below, buckets fill and empty the stack, comparators read the
 * stack's fill level and a glowing fluid lights the block.
 */
final class TankTests {
    private static final int CAPACITY = 16000;
    private static final BlockPos BOTTOM = new BlockPos(1, 1, 1);
    private static final BlockPos MIDDLE = BOTTOM.above();
    private static final BlockPos TOP = MIDDLE.above();
    private static final FluidResource WATER = FluidResource.of(Fluids.WATER);

    private TankTests() {
    }

    /** Places a tank and lets it look at its neighbours, as placement and neighbour shape updates do in game. */
    private static TileTank placeTank(GameTestHelper helper, BlockPos pos) {
        BlockPos absolute = helper.absolutePos(pos);
        BlockState state = Block.updateFromNeighbourShapes(FactoryRegistry.tankBlock.get().defaultBlockState(),
                helper.getLevel(), absolute);
        helper.setBlock(pos, state);
        return helper.getBlockEntity(pos, TileTank.class);
    }

    private static ResourceHandler<FluidResource> handler(GameTestHelper helper, BlockPos pos) {
        ResourceHandler<FluidResource> handler = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK,
                helper.absolutePos(pos), Direction.NORTH);
        helper.assertTrue(handler != null, "no fluid capability at " + pos);
        return handler;
    }

    private static boolean joinedBelow(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockState(pos).getValue(BlockTank.JOINED_BELOW);
    }

    static void stacking(GameTestHelper helper) {
        TileTank bottom = placeTank(helper, BOTTOM);
        TileTank middle = placeTank(helper, MIDDLE);
        TileTank top = placeTank(helper, TOP);

        helper.assertFalse(joinedBelow(helper, BOTTOM), "bottom tank drawn as stacked");
        helper.assertTrue(joinedBelow(helper, MIDDLE), "middle tank not drawn as stacked");
        helper.assertTrue(joinedBelow(helper, TOP), "top tank not drawn as stacked");
        helper.assertTrue(top.getBottomTank() == bottom, "top's bottom tank");
        helper.assertTrue(bottom.getTopTank() == top, "bottom's top tank");

        // Filling through the top tank starts at the bottom and goes up (4.2.2 fill).
        ResourceHandler<FluidResource> handler = handler(helper, TOP);
        helper.assertTrue(handler == top, "the tile is not the block's fluid handler");
        try (Transaction tx = Transaction.openRoot()) {
            helper.assertValueEqual(handler.insert(WATER, 40000, tx), 40000, "water inserted");
            tx.commit();
        }
        helper.assertValueEqual(bottom.tank.getFluidAmount(), CAPACITY, "bottom amount");
        helper.assertValueEqual(middle.tank.getFluidAmount(), CAPACITY, "middle amount");
        helper.assertValueEqual(top.tank.getFluidAmount(), 8000, "top amount");

        // Every tank shows the whole stack (4.2.2 getTankInfo).
        helper.assertValueEqual(middle.getAmountAsLong(0), 40000L, "composite amount");
        helper.assertValueEqual(middle.getCapacityAsLong(0, WATER), 3L * CAPACITY, "composite capacity");
        helper.assertTrue(middle.getResource(0).equals(WATER), "composite resource");

        // Another fluid is refused by the whole stack.
        try (Transaction tx = Transaction.openRoot()) {
            helper.assertValueEqual(handler.insert(FluidResource.of(Fluids.LAVA), 1000, tx), 0, "lava into a water stack");
        }
        helper.assertFalse(handler.isValid(0, FluidResource.of(Fluids.LAVA)), "lava valid in a water stack");

        // Comparators read the stack's fill level: 40000 * 15 / 48000.
        BlockState topState = helper.getBlockState(TOP);
        helper.assertValueEqual(topState.getAnalogOutputSignal(helper.getLevel(), helper.absolutePos(TOP), Direction.EAST), 12,
                "comparator signal");

        // Breaking the middle tank splits the stack.
        helper.setBlock(MIDDLE, Blocks.AIR);
        helper.assertFalse(joinedBelow(helper, TOP), "top tank still drawn as stacked");
        helper.assertTrue(top.getBottomTank() == top, "top tank still sees a tank below");
        helper.assertValueEqual(top.getCapacityAsLong(0, WATER), (long) CAPACITY, "capacity after the split");
        helper.succeed();
    }

    static void transfer(GameTestHelper helper) {
        TileTank bottom = placeTank(helper, BOTTOM);
        TileTank top = placeTank(helper, MIDDLE);

        // An aborted insert across both tanks leaves both as they were.
        try (Transaction tx = Transaction.openRoot()) {
            helper.assertValueEqual(top.insert(WATER, 20000, tx), 20000, "inserted before abort");
        }
        helper.assertTrue(bottom.tank.isEmpty() && top.tank.isEmpty(), "abort left fluid behind");

        // Fluid in an upper tank falls into the one below (4.2.2 moveFluidBelow).
        top.tank.fill(new FluidStack(Fluids.WATER, 5000), true);

        // A comparator beside the bottom tank follows the stack.
        BlockPos comparator = BOTTOM.east();
        helper.setBlock(comparator.below(), Blocks.STONE);
        helper.setBlock(comparator, Blocks.COMPARATOR.defaultBlockState().setValue(ComparatorBlock.FACING, Direction.WEST));

        helper.succeedWhen(() -> {
            helper.assertValueEqual(top.tank.getFluidAmount(), 0, "top amount");
            helper.assertValueEqual(bottom.tank.getFluidAmount(), 5000, "bottom amount");
            // 5000 * 15 / 32000
            helper.assertValueEqual(helper.getBlockEntity(comparator, ComparatorBlockEntity.class).getOutputSignal(), 2,
                    "comparator output");

            // Draining through the top tank takes from the bottom one; another fluid takes nothing.
            try (Transaction tx = Transaction.openRoot()) {
                helper.assertValueEqual(top.extract(FluidResource.of(Fluids.LAVA), 1000, tx), 0, "lava from a water stack");
                helper.assertValueEqual(top.extract(WATER, 3000, tx), 3000, "water extracted");
                tx.commit();
            }
            helper.assertValueEqual(bottom.tank.getFluidAmount(), 2000, "bottom amount after extract");
        });
    }

    private static InteractionResult use(GameTestHelper helper, Player player, BlockPos pos) {
        BlockPos absolute = helper.absolutePos(pos);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
        return helper.getBlockState(pos).useItemOn(player.getMainHandItem(), helper.getLevel(), player,
                InteractionHand.MAIN_HAND, hit);
    }

    static void buckets(GameTestHelper helper) {
        TileTank bottom = placeTank(helper, BOTTOM);
        TileTank top = placeTank(helper, MIDDLE);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        // A water bucket empties into the stack; clicking the top tank fills the bottom one.
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        helper.assertTrue(use(helper, player, MIDDLE).consumesAction(), "water bucket refused");
        helper.assertValueEqual(bottom.tank.getFluidAmount(), 1000, "amount after a bucket");
        helper.assertTrue(player.getMainHandItem().is(Items.BUCKET), "bucket not emptied: " + player.getMainHandItem());

        // An empty bucket fills from the stack.
        helper.assertTrue(use(helper, player, MIDDLE).consumesAction(), "empty bucket refused");
        helper.assertTrue(bottom.tank.isEmpty(), "tank not drained");
        helper.assertTrue(player.getMainHandItem().is(Items.WATER_BUCKET), "bucket not filled: " + player.getMainHandItem());

        // An empty bucket on an empty tank does nothing and leaves the click to the item.
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
        helper.assertFalse(use(helper, player, BOTTOM).consumesAction(), "empty bucket on an empty tank used the click");

        // Two water buckets: one goes in and the empty bucket goes to the inventory (BuildCraft#2353's neighbour:
        // 4.2.2 lost it).
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET, 2));
        helper.assertTrue(use(helper, player, BOTTOM).consumesAction(), "stack of water buckets refused");
        helper.assertValueEqual(bottom.tank.getFluidAmount(), 1000, "amount after a stacked bucket");
        helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "water buckets left");
        helper.assertValueEqual(player.getInventory().countItem(Items.BUCKET), 1, "empty buckets returned");

        // A full stack still takes the click, so the bucket keeps its water and places nothing.
        try (Transaction tx = Transaction.openRoot()) {
            top.insert(WATER, 2 * CAPACITY, tx);
            tx.commit();
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        helper.assertTrue(use(helper, player, BOTTOM).consumesAction(), "full tank let the bucket through");
        helper.assertTrue(player.getMainHandItem().is(Items.WATER_BUCKET), "bucket emptied into a full tank");

        // Creative mode does not use up the container (4.2.2 checked capabilities.isCreativeMode).
        Player creative = helper.makeMockPlayer(GameType.CREATIVE);
        // The mock player reports the game type but does not apply its abilities.
        GameType.CREATIVE.updatePlayerAbilities(creative.getAbilities());
        TileTank other = placeTank(helper, BOTTOM.east(2));
        creative.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LAVA_BUCKET));
        helper.assertTrue(use(helper, creative, BOTTOM.east(2)).consumesAction(), "creative lava bucket refused");
        helper.assertValueEqual(other.tank.getFluidAmount(), 1000, "amount after a creative bucket");
        helper.assertTrue(creative.getMainHandItem().is(Items.LAVA_BUCKET), "creative bucket emptied");

        // Lava lights the tank (4.2.2 getLightValue from the fluid's luminosity).
        helper.succeedWhen(() -> helper.assertValueEqual(helper.getBlockState(BOTTOM.east(2))
                .getLightEmission(helper.getLevel(), helper.absolutePos(BOTTOM.east(2))), 15, "tank light"));
    }
}
