package buildcraftreforged.gametest;

import buildcraftreforged.api.recipes.AssemblyRecipe;
import buildcraftreforged.api.transport.IPipeTile.PipeType;
import buildcraftreforged.core.status.MachineStatus;
import buildcraftreforged.core.status.StatusPanel;
import buildcraftreforged.core.status.StatusReason;
import buildcraftreforged.core.utils.Utils;
import buildcraftreforged.energy.EnergyRegistry;
import buildcraftreforged.energy.TileEngineStone;
import buildcraftreforged.factory.FactoryRegistry;
import buildcraftreforged.factory.TileMiningWell;
import buildcraftreforged.factory.TilePump;
import buildcraftreforged.silicon.AssemblyRecipeManager;
import buildcraftreforged.silicon.SiliconRegistry;
import buildcraftreforged.silicon.TileAssemblyTable;
import buildcraftreforged.transport.PipeMaterial;
import buildcraftreforged.transport.TransportRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * #130: the seams between pieces built in parallel. The assembly table hands what no inventory takes to an item pipe
 * beside it; {@code Utils.addToRandomInventoryAround} skips engines; the mining well and the pump report blocked output
 * through {@code MachineOutput}. In the padded area (see {@link OilFuelTests#PADDING}).
 */
final class SeamTests {
    static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();
    static final int MAX_TICKS = 600;

    static {
        TESTS.put("seam_assembly_table_to_pipe", SeamTests::assemblyTableToPipe);
        TESTS.put("seam_inventory_skips_engine", SeamTests::inventorySkipsEngine);
        TESTS.put("seam_mining_well_blocked_output", SeamTests::miningWellBlockedOutput);
        TESTS.put("seam_pump_blocked_output", SeamTests::pumpBlockedOutput);
    }

    private SeamTests() {
    }

    /**
     * A table with a stone item pipe beside it that leads to a chest: the two crafted diamonds travel through the pipe
     * into the chest and nothing is dropped (4.2.2 addToRandomPipeAround after addToRandomInventoryAround).
     */
    static void assemblyTableToPipe(GameTestHelper helper) {
        BlockPos tablePos = new BlockPos(2, 2, 2);
        helper.setBlock(tablePos, SiliconRegistry.assemblyTableBlock.get());
        helper.setBlock(tablePos.east(), TransportRegistry.getPipe(PipeType.ITEM, PipeMaterial.STONE));
        helper.setBlock(tablePos.east(2), Blocks.CHEST);
        TileAssemblyTable table = helper.getBlockEntity(tablePos, TileAssemblyTable.class);
        table.setItem(0, new ItemStack(Items.REDSTONE));
        table.setItem(1, new ItemStack(Items.IRON_INGOT, 2));
        AssemblyRecipe diamonds = AssemblyRecipeManager.findRecipe(helper.getLevel(), new ItemStack(Items.DIAMOND));
        helper.assertTrue(diamonds != null, "no test assembly recipe for diamonds");

        // Pipe budgets start empty on load; let them fill before the table crafts. The plan is made after the table's
        // first tick, which would reset a plan made before it.
        helper.runAtTickTime(25, () -> {
            table.planOutput(diamonds);
            table.receiveLaserEnergy(400);
        });
        helper.runAtTickTime(40, () -> {
            helper.assertValueEqual(count(helper, tablePos.east(2), Items.DIAMOND), 2, "diamonds in the chest behind the pipe");
            helper.assertTrue(table.getItem(0).isEmpty() && table.getItem(1).isEmpty(), "inputs not used up");
            helper.assertEntityNotPresent(EntityTypes.ITEM);
            helper.succeed();
        });
    }

    /**
     * A stone engine takes any item in its slot, but addToRandomInventoryAround passes it by, as 4.2.2 skipped
     * TileEngine; a chest on another side gets the coal.
     */
    static void inventorySkipsEngine(GameTestHelper helper) {
        BlockPos origin = new BlockPos(2, 2, 2);
        helper.setBlock(origin.east(), EnergyRegistry.engineStone.block().get());
        TileEngineStone engine = helper.getBlockEntity(origin.east(), TileEngineStone.class);
        BlockPos at = helper.absolutePos(origin);

        int added = Utils.addToRandomInventoryAround(helper.getLevel(), at.getX(), at.getY(), at.getZ(),
                new ItemStack(Items.COAL, 4));
        helper.assertValueEqual(added, 0, "coal added with only an engine around");
        helper.assertTrue(engine.getItem(0).isEmpty(), "coal put into the engine");

        helper.setBlock(origin.west(), Blocks.CHEST);
        added = Utils.addToRandomInventoryAround(helper.getLevel(), at.getX(), at.getY(), at.getZ(),
                new ItemStack(Items.COAL, 4));
        helper.assertValueEqual(added, 4, "coal added beside a chest");
        helper.assertValueEqual(count(helper, origin.west(), Items.COAL), 4, "coal in the chest");
        helper.assertTrue(engine.getItem(0).isEmpty(), "coal put into the engine");
        helper.succeed();
    }

    /**
     * A well with nothing beside it digs one block, keeps the dirt and stops: blocked, Reason "No destination", red
     * light, nothing thrown out. The dirt survives a save and load. A chest placed beside it takes the dirt and the well
     * digs on to bedrock.
     */
    static void miningWellBlockedOutput(GameTestHelper helper) {
        BlockPos well = new BlockPos(2, 5, 2);
        helper.setBlock(well.below(), Blocks.DIRT);
        helper.setBlock(well.below(2), Blocks.DIRT);
        helper.setBlock(well.below(3), Blocks.BEDROCK);
        ServerPlayer placer = helper.makeMockServerPlayerInLevel();
        placer.snapTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(0, 12, 0))));
        helper.setBlock(well, FactoryRegistry.miningWellBlock.get());
        BlockPos absolute = helper.absolutePos(well);
        BlockState state = helper.getLevel().getBlockState(absolute);
        FactoryRegistry.miningWellBlock.get().setPlacedBy(helper.getLevel(), absolute, state, placer,
                new ItemStack(FactoryRegistry.miningWellBlock.get()));
        TileMiningWell tile = helper.getBlockEntity(well, TileMiningWell.class);

        helper.onEachTick(() -> {
            if (helper.getLevel().getBlockEntity(absolute) instanceof TileMiningWell)
                power(helper, absolute);
        });

        helper.runAtTickTime(40, () -> {
            helper.assertBlockPresent(FactoryRegistry.plainPipeBlock.get(), well.below());
            helper.assertBlockPresent(Blocks.DIRT, well.below(2));
            helper.assertValueEqual(tile.output.getPending().size(), 1, "pending stacks");
            helper.assertTrue(tile.isBlocked(), "the well is not blocked");
            helper.assertValueEqual(tile.getStatusState(), MachineStatus.BLOCKED, "State line");
            helper.assertValueEqual(tile.getStatusReason(), StatusReason.NO_DESTINATION, "Reason line");
            helper.assertTrue(helper.getBlockState(well).getValue(StatusPanel.BLOCKED), "no red light");
            helper.assertEntityNotPresent(EntityTypes.ITEM);

            TileMiningWell reloaded = new TileMiningWell(absolute, state);
            reloaded.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(),
                    tile.saveWithFullMetadata(helper.getLevel().registryAccess())));
            helper.assertValueEqual(reloaded.output.getPending().size(), 1, "pending stacks after loading");

            helper.setBlock(well.east(), Blocks.CHEST);
        });

        helper.runAtTickTime(100, () -> {
            helper.assertBlockPresent(FactoryRegistry.plainPipeBlock.get(), well.below(2));
            helper.assertValueEqual(count(helper, well.east(), Items.DIRT), 2, "dirt in the chest");
            helper.assertFalse(tile.isBlocked(), "the well is still blocked");
            helper.assertFalse(helper.getBlockState(well).getValue(StatusPanel.BLOCKED), "red light still on");
            helper.assertEntityNotPresent(EntityTypes.ITEM);
            helper.succeed();
        });
    }

    /**
     * A pump over water with a full tank cannot take the next block: blocked, Reason "Output blocked", red light, the
     * water left in place. Emptying the tank clears it.
     */
    static void pumpBlockedOutput(GameTestHelper helper) {
        for (int x = 1; x <= 3; x++)
            for (int z = 1; z <= 3; z++)
                for (int y = 1; y <= 2; y++)
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
        BlockPos water = new BlockPos(2, 2, 2);
        helper.setBlock(water, Blocks.WATER);
        BlockPos pumpPos = water.above();
        helper.setBlock(pumpPos, FactoryRegistry.pumpBlock.get());
        TilePump pump = helper.getBlockEntity(pumpPos, TilePump.class);
        pump.getTank().fill(new FluidStack(Fluids.WATER, TilePump.MAX_LIQUID), true);

        // 0: waiting for the tube and the first check; 1: blocked, checked the tick after; 2: drained.
        int[] phase = {0};
        helper.onEachTick(() -> {
            if (phase[0] == 0 && pump.isBlocked()) {
                phase[0] = 1;
            } else if (phase[0] == 1) {
                helper.assertTrue(pump.isBlocked(), "the pump with a full tank is not blocked");
                helper.assertValueEqual(pump.getStatusState(), MachineStatus.BLOCKED, "State line");
                helper.assertValueEqual(pump.getStatusReason(), StatusReason.OUTPUT_FULL, "Reason line");
                helper.assertTrue(helper.getBlockState(pumpPos).getValue(StatusPanel.BLOCKED), "no red light");
                helper.assertTrue(helper.getBlockState(water).getFluidState().isSource(), "water pumped into a full tank");
                pump.getTank().drain(TilePump.MAX_LIQUID, true);
                phase[0] = 2;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertValueEqual(phase[0], 2, "phase");
            helper.assertFalse(pump.isBlocked(), "the pump is still blocked with an empty tank");
            helper.assertValueEqual(pump.getStatusReason(), StatusReason.NONE, "Reason line");
            helper.assertFalse(helper.getBlockState(pumpPos).getValue(StatusPanel.BLOCKED), "red light still on");
        });
    }

    private static void power(GameTestHelper helper, BlockPos absolute) {
        EnergyHandler handler = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, absolute, Direction.UP);
        helper.assertTrue(handler != null, "no energy capability");
        try (Transaction tx = Transaction.openRoot()) {
            handler.insert(1000, tx);
            tx.commit();
        }
    }

    private static int count(GameTestHelper helper, BlockPos pos, Item item) {
        Container container = (Container) helper.getBlockEntity(pos, BlockEntity.class);
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.is(item))
                count += stack.getCount();
        }
        return count;
    }
}
