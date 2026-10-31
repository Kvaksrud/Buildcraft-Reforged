package buildcraftreforged.gametest;

import buildcraftreforged.core.status.MachineStatus;
import buildcraftreforged.energy.EnergyRegistry;
import buildcraftreforged.factory.FactoryRegistry;
import buildcraftreforged.factory.TileFloodGate;
import buildcraftreforged.factory.TilePump;
import buildcraftreforged.factory.TileTank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * #65: the pump and the flood gate (4.2.2 TilePump, TileFloodGate). The pump finds the fluid below it, lowers its tube,
 * drains source blocks top layer first for 100 FE each and pushes into a tank beside it; water is not consumed once 9
 * or more blocks are found (consumeWater off); the flood gate fills a hole from the bottom up. All built in the padded
 * area from y = 2.
 */
final class PumpTests {
    /** 4.2.2 useEnergy(10, 10): 10 MJ per block. */
    private static final int ENERGY_PER_BLOCK = 100;
    /** 4.2.2 configure(1, 15, ...): at most 15 MJ per receive. */
    private static final int MAX_RECEIVED = 150;
    /** Pumping a block can take up to 128 ticks to find the fluid, 100 ticks per block to lower the tube, 128 to queue. */
    static final int POOL_TICKS = 900;
    static final int OIL_TICKS = 2000;
    static final int FLOOD_TICKS = 900;

    private PumpTests() {
    }

    private static void box(GameTestHelper helper, int x0, int y0, int z0, int x1, int y1, int z1, Block block) {
        for (int x = x0; x <= x1; x++)
            for (int y = y0; y <= y1; y++)
                for (int z = z0; z <= z1; z++)
                    helper.setBlock(new BlockPos(x, y, z), block);
    }

    private static void power(GameTestHelper helper, BlockPos pos) {
        EnergyHandler handler = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(pos),
                Direction.UP);
        helper.assertTrue(handler != null, "the pump has no energy capability");
        try (Transaction tx = Transaction.openRoot()) {
            handler.insert(MAX_RECEIVED, tx);
            tx.commit();
        }
    }

    private static ResourceHandler<FluidResource> fluids(GameTestHelper helper, BlockPos pos) {
        ResourceHandler<FluidResource> handler = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK,
                helper.absolutePos(pos), Direction.NORTH);
        helper.assertTrue(handler != null, "no fluid capability at " + pos);
        return handler;
    }

    private static boolean isSource(GameTestHelper helper, BlockPos pos, Fluid fluid) {
        BlockState state = helper.getBlockState(pos);
        return state.getBlock() instanceof LiquidBlock && state.getFluidState().is(fluid) && state.getFluidState().isSource();
    }

    /**
     * A 1x2 water pool under one pump is drained into the tank beside it (fewer than 9 blocks: the water is consumed);
     * a 3x3 pool under another is pumped but stays (9 blocks found, consumeWater off). Without power nothing moves.
     */
    static void smallPool(GameTestHelper helper) {
        // Pump A over a 1x2 pool at y = 3, a tank on its east side.
        box(helper, 1, 2, 1, 4, 4, 3, Blocks.STONE);
        helper.setBlock(new BlockPos(2, 3, 2), Blocks.WATER);
        helper.setBlock(new BlockPos(3, 3, 2), Blocks.WATER);
        BlockPos pumpA = new BlockPos(2, 4, 2);
        helper.setBlock(pumpA, FactoryRegistry.pumpBlock.get());
        helper.setBlock(pumpA.east(), FactoryRegistry.tankBlock.get());
        TilePump tileA = helper.getBlockEntity(pumpA, TilePump.class);
        TileTank tank = helper.getBlockEntity(pumpA.east(), TileTank.class);

        // Pump B over a 3x3 pool at y = 3.
        box(helper, 7, 2, 1, 11, 4, 5, Blocks.STONE);
        box(helper, 8, 3, 2, 10, 3, 4, Blocks.WATER);
        BlockPos pumpB = new BlockPos(9, 4, 3);
        helper.setBlock(pumpB, FactoryRegistry.pumpBlock.get());
        TilePump tileB = helper.getBlockEntity(pumpB, TilePump.class);

        // The pump takes no fluid from outside.
        try (Transaction tx = Transaction.openRoot()) {
            helper.assertValueEqual(fluids(helper, pumpA).insert(FluidResource.of(Fluids.WATER), 1000, tx), 0,
                    "water inserted into a pump");
        }

        // No power for the first 400 ticks: the tube goes down, nothing is pumped.
        helper.runAfterDelay(400, () -> {
            helper.assertValueEqual(tileA.getAimY(), helper.absolutePos(pumpA).getY() - 1, "pump A's aim");
            helper.assertTrue(tileA.getTube() != null && Math.abs(tileA.getTubeY() - tileA.getAimY()) <= 0.01,
                    "pump A's tube did not reach the pool: " + tileA.getTubeY());
            helper.assertTrue(isSource(helper, new BlockPos(2, 3, 2), Fluids.WATER)
                    && isSource(helper, new BlockPos(3, 3, 2), Fluids.WATER), "water pumped without power");
            helper.assertValueEqual(tileA.getTank().getFluidAmount(), 0, "pump A's tank without power");
            helper.assertValueEqual(tileA.getStatusState(), MachineStatus.NO_POWER, "pump A's status without power");
        });

        helper.onEachTick(() -> {
            if (helper.getTick() > 400) {
                power(helper, pumpA);
                power(helper, pumpB);
            }
        });

        helper.succeedWhen(() -> {
            helper.assertValueEqual(tank.tank.getFluidAmount(), 2000, "water pushed into the tank");
            helper.assertValueEqual(tank.tank.getFluid().getFluid(), Fluids.WATER, "the tank's fluid");
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(2, 3, 2));
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 3, 2));
            helper.assertTrue(tileB.getTank().getFluidAmount() >= 2000,
                    "pump B pumped " + tileB.getTank().getFluidAmount() + " mB");
            for (int x = 8; x <= 10; x++)
                for (int z = 2; z <= 4; z++)
                    helper.assertTrue(isSource(helper, new BlockPos(x, 3, z), Fluids.WATER),
                            "pump B consumed the water at " + x + ", " + z);
        });
    }

    /**
     * A 2x2x2 oil pool two blocks under the pump: the tube goes down to the top layer, which is drained first, then to
     * the bottom layer. 8000 mB end up in the pump.
     */
    static void oil(GameTestHelper helper) {
        Fluid oil = EnergyRegistry.fluidOil.get();
        box(helper, 1, 2, 1, 4, 7, 4, Blocks.STONE);
        box(helper, 2, 3, 2, 3, 4, 3, EnergyRegistry.blockOil.get());
        helper.setBlock(new BlockPos(2, 5, 2), Blocks.AIR);
        helper.setBlock(new BlockPos(2, 6, 2), Blocks.AIR);
        BlockPos pump = new BlockPos(2, 7, 2);
        helper.setBlock(pump, FactoryRegistry.pumpBlock.get());
        TilePump tile = helper.getBlockEntity(pump, TilePump.class);
        int bottom = helper.absolutePos(pump).getY() - 4;

        helper.onEachTick(() -> {
            power(helper, pump);
            boolean lowerTouched = false;
            boolean upperLeft = false;
            for (int x = 2; x <= 3; x++) {
                for (int z = 2; z <= 3; z++) {
                    lowerTouched |= !isSource(helper, new BlockPos(x, 3, z), oil);
                    upperLeft |= isSource(helper, new BlockPos(x, 4, z), oil);
                }
            }
            if (lowerTouched && upperLeft)
                helper.fail("the bottom layer was pumped before the top layer");
        });

        helper.succeedWhen(() -> {
            helper.assertValueEqual(tile.getTank().getFluidAmount(), 8000, "oil in the pump");
            helper.assertValueEqual(tile.getTank().getFluid().getFluid(), oil, "the pump's fluid");
            helper.assertValueEqual(tile.getAimY(), bottom, "the tube's last aim");
            for (int x = 2; x <= 3; x++)
                for (int y = 3; y <= 4; y++)
                    for (int z = 2; z <= 3; z++)
                        helper.assertBlockPresent(Blocks.AIR, new BlockPos(x, y, z));
        });
    }

    /**
     * A flood gate in a stone floor over a 3x3x2 hole, fed water through its fluid capability: the hole fills with
     * water sources, and nothing goes past the floor. The gate gives no fluid back.
     */
    static void floodGate(GameTestHelper helper) {
        box(helper, 1, 2, 1, 7, 5, 7, Blocks.STONE);
        box(helper, 3, 3, 3, 5, 4, 5, Blocks.AIR);
        BlockPos gate = new BlockPos(4, 5, 4);
        helper.setBlock(gate, FactoryRegistry.floodGateBlock.get());
        TileFloodGate tile = helper.getBlockEntity(gate, TileFloodGate.class);

        try (Transaction tx = Transaction.openRoot()) {
            helper.assertValueEqual(fluids(helper, gate).insert(FluidResource.of(Fluids.WATER), 5000, tx), 2000,
                    "water taken by the flood gate");
            tx.commit();
        }
        try (Transaction tx = Transaction.openRoot()) {
            helper.assertValueEqual(fluids(helper, gate).extract(FluidResource.of(Fluids.WATER), 1000, tx), 0,
                    "water taken out of the flood gate");
        }

        helper.onEachTick(() -> {
            try (Transaction tx = Transaction.openRoot()) {
                fluids(helper, gate).insert(FluidResource.of(Fluids.WATER), 1000, tx);
                tx.commit();
            }
        });

        helper.succeedWhen(() -> {
            for (int x = 3; x <= 5; x++)
                for (int y = 3; y <= 4; y++)
                    for (int z = 3; z <= 5; z++)
                        helper.assertTrue(isSource(helper, new BlockPos(x, y, z), Fluids.WATER),
                                "no water source at " + x + ", " + y + ", " + z);
            helper.assertBlockPresent(FactoryRegistry.floodGateBlock.get(), gate);
            helper.assertBlockPresent(Blocks.STONE, gate.east());
            helper.assertBlockPresent(Blocks.AIR, gate.above());
            helper.assertValueEqual(tile.getTank().getFluid().getFluid(), Fluids.WATER, "the gate's fluid");
        });
    }
}
