/*
 * Buildcraft Reforged, 2026: new file, no 4.2.2 original. GameTests for the engine wrench, the engine Reason line and
 * the quarry's power faces.
 */
package buildcraftreforged.gametest;

import buildcraftreforged.core.status.StatusReason;
import buildcraftreforged.energy.BlockEngine;
import buildcraftreforged.energy.EnergyRegistry;
import buildcraftreforged.energy.TileEngineWood;
import buildcraftreforged.factory.BlockQuarry;
import buildcraftreforged.factory.FactoryRegistry;
import buildcraftreforged.factory.TileQuarry;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;

/**
 * The wrench turns an engine through all six faces, a sneaking wrench back; a running engine says why it delivers
 * nothing; the quarry takes power on every face, its front included.
 */
final class EngineWrenchTests {
    static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();
    /** A cold redstone engine pulses slowly; give it time to warm up and pulse into the quarry. */
    static final int MAX_TICKS = 400;

    private static final BlockPos ENGINE = new BlockPos(2, 2, 2);

    static {
        TESTS.put("engine_wrench_cycles_faces", EngineWrenchTests::wrenchCyclesFaces);
        TESTS.put("engine_status_reasons", EngineWrenchTests::statusReasons);
        TESTS.put("quarry_power_on_every_face", EngineWrenchTests::quarryPowerOnEveryFace);
    }

    private EngineWrenchTests() {
    }

    private static TileEngineWood placeEngine(GameTestHelper helper, BlockPos pos, Direction facing) {
        helper.setBlock(pos, Blocks.AIR);
        helper.setBlock(pos, EnergyRegistry.engineWood.block().get().defaultBlockState().setValue(BlockEngine.FACING, facing));
        return helper.getBlockEntity(pos, TileEngineWood.class);
    }

    /** Six sneaking wrench clicks visit every face once and come back; engines turn on a sneaking click only. */
    static void wrenchCyclesFaces(GameTestHelper helper) {
        TileEngineWood engine = placeEngine(helper, ENGINE, Direction.UP);
        BlockEngine block = EnergyRegistry.engineWood.block().get();
        Level level = helper.getLevel();
        BlockPos abs = helper.absolutePos(ENGINE);
        Direction[] forward = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.DOWN, Direction.UP};
        for (Direction expected : forward) {
            helper.assertTrue(block.rotateBlock(level, abs, Direction.UP), "the wrench did not turn the engine");
            helper.assertValueEqual(engine.getOrientation(), expected, "facing after a sneaking wrench click");
        }
        helper.assertTrue(block.rotatesOnSneakOnly(), "engines should turn on a sneaking click only");
        helper.succeed();
    }

    /**
     * A running redstone engine facing air reports No power receiver; facing a quarry it reports nothing and feeds it,
     * although its pulse is below the quarry's minimum intake (decided 1 Nov 2026).
     */
    static void statusReasons(GameTestHelper helper) {
        TileEngineWood engine = placeEngine(helper, ENGINE, Direction.EAST);
        helper.setBlock(ENGINE.below(), Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(2, () -> {
            helper.assertValueEqual(engine.getStatusReason(), StatusReason.NO_POWER_TARGET, "reason facing air");
            helper.setBlock(ENGINE.east(), FactoryRegistry.quarryBlock.get().defaultBlockState()
                    .setValue(BlockQuarry.FACING, Direction.WEST));
            // The engine's TileBuffer refreshes a neighbour within 20 ticks (4.2.2).
            // The engine's TileBuffer refreshes a neighbour within 20 ticks (4.2.2); then it pulses into the quarry.
            helper.succeedWhen(() -> {
                helper.assertValueEqual(engine.getStatusReason(), StatusReason.NONE, "reason facing a quarry");
                helper.assertTrue(engine.getMeter().energyOutPerSecond() > 0,
                        "the redstone engine delivered nothing to the quarry");
                // Perdition does not eat the trickle below the quarry's activation energy: three pulses stay stored.
                TileQuarry quarry = helper.getBlockEntity(ENGINE.east(), TileQuarry.class);
                helper.assertTrue(quarry.getPowerReceiver(Direction.WEST).getEnergyStored() >= 30,
                        "the quarry lost the redstone engine's pulses");
            });
        });
    }

    /** The quarry takes power on all six faces; the front (FACING) is not special. */
    static void quarryPowerOnEveryFace(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, FactoryRegistry.quarryBlock.get().defaultBlockState().setValue(BlockQuarry.FACING, Direction.NORTH));
        TileQuarry quarry = helper.getBlockEntity(pos, TileQuarry.class);
        for (Direction side : Direction.values()) {
            helper.assertTrue(quarry.getPowerReceiver(side) != null, "no power receiver on the " + side + " face");
            helper.assertTrue(helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(pos), side) != null,
                    "no energy capability on the " + side + " face");
        }
        helper.succeed();
    }
}
