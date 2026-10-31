package buildcraftreforged.gametest;

import buildcraftreforged.core.fluids.FluidUtils;
import buildcraftreforged.core.fluids.Tank;
import buildcraftreforged.core.fluids.TankManager;
import buildcraftreforged.energy.BlockBuildcraftFluid;
import buildcraftreforged.energy.EnergyRegistry;
import buildcraftreforged.energy.ItemBucketBuildcraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * #42: oil and fuel place from their buckets, are picked up again, fill and empty a tank, flow 7 blocks at their 4.2.2
 * tick rates (oil every 7 ticks, fuel every 5) without forming new sources, stay apart from water, harden lava, and
 * turn into fire in the Nether instead of exploding.
 */
final class OilFuelTests {
    /**
     * These tests build in the padded area around the 1x1x1 structure, which GameTest encases in barriers up to y = 1;
     * the layouts start at y = 2. The padding clears the area first.
     */
    static final int PADDING = 10;
    /** Placement of the flow sources, after the channels are built. */
    private static final int FLOW_START = 1;

    private OilFuelTests() {
    }

    /** Places the source from the bucket, picks it up again, and moves the bucket through a tank both ways. */
    static void placementAndBuckets(GameTestHelper helper) {
        check(helper, EnergyRegistry.fluidOil.get(), EnergyRegistry.blockOil.get(), EnergyRegistry.bucketOil.get(),
                new BlockPos(0, 3, 0), "tile.blockOil", "fluid.oil");
        check(helper, EnergyRegistry.fluidFuel.get(), EnergyRegistry.blockFuel.get(), EnergyRegistry.bucketFuel.get(),
                new BlockPos(2, 3, 0), "tile.blockFuel", "fluid.fuel");
        helper.succeed();
    }

    private static void check(GameTestHelper helper, FlowingFluid fluid, BlockBuildcraftFluid block, ItemBucketBuildcraft bucket,
            BlockPos pos, String blockKey, String fluidKey) {
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(pos);
        helper.setBlock(pos.below(), Blocks.STONE);
        helper.assertValueEqual(block.getDescriptionId(), blockKey, "block translation key");
        helper.assertValueEqual(fluid.getFluidType().getDescriptionId(), fluidKey, "fluid translation key");
        helper.assertTrue(fluid.getBucket() == bucket, "the fluid's bucket is " + fluid.getBucket());
        helper.assertTrue(new ItemStack(bucket).getCraftingRemainder().is(Items.BUCKET), "bucket leaves no empty bucket in crafting");

        helper.assertTrue(bucket.emptyContents(null, level, abs, null), "the bucket placed nothing; before "
                + level.getBlockState(abs) + ", legacy " + fluid.defaultFluidState().createLegacyBlock() + ", fluid of block "
                + block.defaultBlockState().getFluidState() + " inside " + level.isInsideBuildHeight(abs.getY()));
        FluidState placed = level.getFluidState(abs);
        helper.assertTrue(placed.is(fluid) && placed.isSource(), "placed " + placed);
        helper.assertTrue(level.getBlockState(abs).is(block), "placed block " + level.getBlockState(abs));

        // 4.2.2 BucketHandler.fillCustomBucket: the source gives a full bucket and leaves air.
        BlockState state = level.getBlockState(abs);
        ItemStack picked = block.pickupBlock(null, level, abs, state);
        helper.assertTrue(picked.is(bucket), "picked up " + picked);
        helper.assertTrue(level.getBlockState(abs).isAir(), "the source is still there");

        // FluidContainerRegistry: the bucket holds 1000 mB for tanks, and an empty bucket takes it back.
        Tank tank = new Tank("tank", 16000, null);
        TankManager<Tank> tankManager = new TankManager<>(tank);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(bucket));
        helper.assertTrue(FluidUtils.handleRightClick(tankManager, player, InteractionHand.MAIN_HAND, true, true), "tank refused the bucket");
        helper.assertTrue(FluidStack.matches(tank.getFluid(), new FluidStack(fluid, 1000)), "tank holds " + tank.getFluid());
        helper.assertTrue(player.getMainHandItem().is(Items.BUCKET), "bucket not emptied: " + player.getMainHandItem());
        helper.assertTrue(FluidUtils.handleRightClick(tankManager, player, InteractionHand.MAIN_HAND, true, true), "empty bucket refused");
        helper.assertTrue(tank.isEmpty(), "tank not drained: " + tank.getFluid());
        helper.assertTrue(player.getMainHandItem().is(bucket), "bucket not filled: " + player.getMainHandItem());
    }

    /**
     * Three walled channels along x: oil, fuel, and oil with two sources one block apart. Sources go in at tick 1, after
     * the channels; the flow reaches x = k after k tick delays.
     */
    static void flow(GameTestHelper helper) {
        int[] channels = {0, 2, 4};
        for (int z : channels) {
            for (int x = -1; x <= 10; x++) {
                helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, 3, z - 1), Blocks.STONE);
                helper.setBlock(new BlockPos(x, 3, z + 1), Blocks.STONE);
            }
            helper.setBlock(new BlockPos(-1, 3, z), Blocks.STONE);
        }
        helper.runAfterDelay(FLOW_START, () -> {
            helper.setBlock(new BlockPos(0, 3, 0), EnergyRegistry.blockOil.get());
            helper.setBlock(new BlockPos(0, 3, 2), EnergyRegistry.blockFuel.get());
            helper.setBlock(new BlockPos(0, 3, 4), EnergyRegistry.blockOil.get());
            helper.setBlock(new BlockPos(2, 3, 4), EnergyRegistry.blockOil.get());
        });

        // Oil reaches x = 4 at 28 ticks and x = 5 at 35; fuel x = 6 at 30 and x = 7 at 35.
        helper.runAfterDelay(FLOW_START + 32, () -> {
            assertFluid(helper, new BlockPos(4, 3, 0), EnergyRegistry.fluidOil.get(), 4);
            assertEmpty(helper, new BlockPos(5, 3, 0));
            assertFluid(helper, new BlockPos(6, 3, 2), EnergyRegistry.fluidFuel.get(), 2);
            assertEmpty(helper, new BlockPos(7, 3, 2));
        });

        helper.runAfterDelay(FLOW_START + 100, () -> {
            for (int x = 1; x <= 7; x++) {
                assertFluid(helper, new BlockPos(x, 3, 0), EnergyRegistry.fluidOil.get(), 8 - x);
                assertFluid(helper, new BlockPos(x, 3, 2), EnergyRegistry.fluidFuel.get(), 8 - x);
            }
            assertEmpty(helper, new BlockPos(8, 3, 0));
            assertEmpty(helper, new BlockPos(8, 3, 2));
            // No new source between two sources (BlockFluidClassic never made one).
            assertFluid(helper, new BlockPos(1, 3, 4), EnergyRegistry.fluidOil.get(), 7);
            helper.succeed();
        });
    }

    private static void assertFluid(GameTestHelper helper, BlockPos pos, Fluid fluid, int amount) {
        FluidState state = helper.getLevel().getFluidState(helper.absolutePos(pos));
        helper.assertTrue(state.getType().isSame(fluid) && state.getAmount() == amount && !state.isSource(),
                "at " + pos + " expected flowing " + fluid + " amount " + amount + ", found " + state + " amount " + state.getAmount());
    }

    private static void assertEmpty(GameTestHelper helper, BlockPos pos) {
        helper.assertTrue(helper.getLevel().getFluidState(helper.absolutePos(pos)).isEmpty(), "fluid at " + pos);
    }

    /**
     * Material.water in 4.2.2: oil does not sink into water but spreads over it; water does not flow into oil; lava next
     * to oil or fuel hardens as next to water.
     */
    static void liquids(GameTestHelper helper) {
        // A pool of three water sources, walled two high, with an oil source dropped on its middle.
        for (int x = -1; x <= 3; x++) {
            for (int z = -1; z <= 1; z++) {
                helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
                boolean wall = x == -1 || x == 3 || z != 0;
                helper.setBlock(new BlockPos(x, 3, z), wall ? Blocks.STONE : Blocks.WATER);
                helper.setBlock(new BlockPos(x, 4, z), wall ? Blocks.STONE : Blocks.AIR);
            }
        }
        // Oil under a water source, walled.
        BlockPos oil = new BlockPos(6, 3, 0);
        for (BlockPos wall : new BlockPos[] {oil.below(), oil.north(), oil.south(), oil.east(), oil.west(), oil.above().north(),
                oil.above().south(), oil.above().east(), oil.above().west()})
            helper.setBlock(wall, Blocks.STONE);
        helper.setBlock(oil, EnergyRegistry.blockOil.get());
        helper.setBlock(oil.above(), Blocks.WATER);
        // Lava sources with fuel and oil poured next to them. NeoForge runs fluid interactions when the lava's neighbour
        // changes (LiquidBlock.neighborChanged), not when the lava itself is placed.
        BlockPos fuel = new BlockPos(9, 3, 0);
        BlockPos oil2 = new BlockPos(9, 3, 3);
        trench(helper, fuel);
        trench(helper, oil2);
        helper.setBlock(fuel.east(), Blocks.LAVA);
        helper.setBlock(oil2.east(), Blocks.LAVA);
        helper.runAfterDelay(1, () -> {
            helper.setBlock(new BlockPos(1, 4, 0), EnergyRegistry.blockOil.get());
            helper.setBlock(fuel, EnergyRegistry.blockFuel.get());
            helper.setBlock(oil2, EnergyRegistry.blockOil.get());
        });

        helper.runAfterDelay(40, () -> {
            for (int x = 0; x <= 2; x++) {
                FluidState water = helper.getLevel().getFluidState(helper.absolutePos(new BlockPos(x, 3, 0)));
                helper.assertTrue(water.is(Fluids.WATER) && water.isSource(),
                        "water at x " + x + " became " + water);
            }
            assertFluid(helper, new BlockPos(0, 4, 0), EnergyRegistry.fluidOil.get(), 7);
            assertFluid(helper, new BlockPos(2, 4, 0), EnergyRegistry.fluidOil.get(), 7);

            FluidState oilState = helper.getLevel().getFluidState(helper.absolutePos(oil));
            helper.assertTrue(oilState.is(EnergyRegistry.fluidOil.get()) && oilState.isSource(), "oil under water became " + oilState);
            helper.assertBlockPresent(Blocks.WATER, oil.above());

            helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(fuel.east())).is(Blocks.OBSIDIAN), "lava next to fuel is "
                    + helper.getLevel().getBlockState(helper.absolutePos(fuel.east())) + ", fuel cell holds "
                    + helper.getLevel().getBlockState(helper.absolutePos(fuel)));
            helper.assertBlockPresent(Blocks.OBSIDIAN, oil2.east());
            helper.succeed();
        });
    }

    /** A walled two-block trench running east from pos. */
    private static void trench(GameTestHelper helper, BlockPos pos) {
        for (int x = -1; x <= 2; x++) {
            helper.setBlock(pos.offset(x, -1, 0), Blocks.STONE);
            helper.setBlock(pos.offset(x, 0, -1), Blocks.STONE);
            helper.setBlock(pos.offset(x, 0, 1), Blocks.STONE);
        }
        helper.setBlock(pos.west(), Blocks.STONE);
        helper.setBlock(pos.east(2), Blocks.STONE);
    }

    /** docs/PLAN.md section 19.2: fuel in the Nether becomes fire on a neighbour change; no explosion. */
    static void netherIgnition(GameTestHelper helper) {
        BlockPos fuel = new BlockPos(1, 3, 1);
        for (int x = 0; x <= 2; x++)
            for (int z = 0; z <= 2; z++)
                helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
        helper.setBlock(fuel, EnergyRegistry.blockFuel.get());
        helper.runAfterDelay(1, () -> helper.setBlock(fuel.east(), Blocks.STONE));
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.FIRE, fuel);
            for (int x = 0; x <= 2; x++)
                for (int z = 0; z <= 2; z++)
                    helper.assertBlockPresent(Blocks.STONE, new BlockPos(x, 2, z));
            helper.assertBlockPresent(Blocks.STONE, fuel.east());
        });
    }

    /** The overworld: the same neighbour change leaves the fuel alone. */
    static void noIgnitionOutsideNether(GameTestHelper helper) {
        BlockPos fuel = new BlockPos(1, 3, 1);
        for (int x = 0; x <= 2; x++)
            for (int z = 0; z <= 2; z++)
                helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
        helper.setBlock(fuel, EnergyRegistry.blockFuel.get());
        helper.runAfterDelay(1, () -> helper.setBlock(fuel.east(), Blocks.STONE));
        helper.runAfterDelay(20, () -> {
            FluidState state = helper.getLevel().getFluidState(helper.absolutePos(fuel));
            helper.assertTrue(state.is(EnergyRegistry.fluidFuel.get()) && state.isSource(), "fuel became " + state);
            BlockBuildcraftFluid block = EnergyRegistry.blockFuel.get();
            BlockState blockState = block.defaultBlockState();
            helper.assertValueEqual(block.getFlammability(blockState, helper.getLevel(), helper.absolutePos(fuel), Direction.UP), 5,
                    "fuel flammability");
            helper.assertValueEqual(block.getFireSpreadSpeed(blockState, helper.getLevel(), helper.absolutePos(fuel), Direction.UP), 300,
                    "fuel fire spread");
            helper.assertTrue(!block.isFireSource(blockState, helper.getLevel(), helper.absolutePos(fuel), Direction.UP),
                    "fuel is a fire source");
            Block oil = EnergyRegistry.blockOil.get();
            helper.assertTrue(oil.isFireSource(oil.defaultBlockState(), helper.getLevel(), helper.absolutePos(fuel), Direction.UP),
                    "oil is no fire source");
            helper.succeed();
        });
    }
}
