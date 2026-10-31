package buildcraftreforged.gametest;

import buildcraftreforged.api.gates.ITrigger;
import buildcraftreforged.api.transport.IPipeTile.PipeType;
import buildcraftreforged.core.CoreRegistry;
import buildcraftreforged.core.status.MachineStatus;
import buildcraftreforged.core.status.StatusPanel;
import buildcraftreforged.core.status.StatusReason;
import buildcraftreforged.gametest.OutputTestBlocks.OutputTestTile;
import buildcraftreforged.transport.PipeMaterial;
import buildcraftreforged.transport.TransportRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * #63: blocked output (docs/PLAN.md 8.3, docs/TRANSPORT.md 6.3 and 14). A machine whose output nobody takes stops,
 * shows the red light, reports Blocked with the reason, and its Machine trigger reads "blocked"; once something takes
 * the output all three clear and the machine goes on. Built in the padded area from y = 2, like item routing. Nothing
 * is ever dropped.
 */
final class BlockedOutputTests {
    static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();
    static final int MAX_TICKS = 200;
    private static final int Y = 2;
    private static final BlockPos MACHINE = new BlockPos(0, Y, 0);
    /** Long enough for pipe masks and the network to settle and for the machine to try a few times. */
    private static final int SETTLE = 20;

    static {
        TESTS.put("blocked_output_pipe_to_full_chest", BlockedOutputTests::pipeToFullChest);
        TESTS.put("blocked_output_full_chest", BlockedOutputTests::fullChest);
        TESTS.put("blocked_output_no_destination", BlockedOutputTests::noDestination);
    }

    private BlockedOutputTests() {
    }

    private static OutputTestTile machine(GameTestHelper helper) {
        helper.setBlock(MACHINE, OutputTestBlocks.outputTestBlock);
        return helper.getBlockEntity(MACHINE, OutputTestTile.class);
    }

    private static Container chest(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, Blocks.CHEST);
        return (Container) helper.getBlockEntity(pos, BlockEntity.class);
    }

    private static void fill(Container chest) {
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            chest.setItem(slot, new ItemStack(Items.STONE, 64));
        }
    }

    private static int cobblestone(Container chest) {
        int count = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            if (chest.getItem(slot).is(Items.COBBLESTONE))
                count += chest.getItem(slot).getCount();
        }
        return count;
    }

    /** The three signals of a blocked machine: red light, status panel State and Reason, Machine trigger. */
    private static void assertBlocked(GameTestHelper helper, OutputTestTile tile, StatusReason reason) {
        ITrigger blocked = CoreRegistry.triggerMachineBlocked.get();
        helper.assertBlockProperty(MACHINE, StatusPanel.BLOCKED, true);
        helper.assertValueEqual(tile.getStatusState(), MachineStatus.BLOCKED, "State line");
        helper.assertValueEqual(tile.getStatusReason(), reason, "Reason line");
        helper.assertTrue(blocked.isTriggerActive(Direction.UP, tile, null), "Machine trigger not blocked");
        helper.assertFalse(CoreRegistry.triggerMachineInactive.get().isTriggerActive(Direction.UP, tile, null),
                "Work Done while blocked");
        // The machine stopped: one cobblestone waits, no more were made.
        helper.assertValueEqual(tile.output.getPending().size(), 1, "pending stacks");
        helper.assertValueEqual(tile.output.getPending().getFirst().getCount(), 1, "pending items");
        helper.assertEntityNotPresent(EntityTypes.ITEM);
    }

    private static void assertRunning(GameTestHelper helper, OutputTestTile tile) {
        helper.assertBlockProperty(MACHINE, StatusPanel.BLOCKED, false);
        helper.assertValueEqual(tile.getStatusState(), MachineStatus.WORKING, "State line");
        helper.assertValueEqual(tile.getStatusReason(), StatusReason.NONE, "Reason line");
        helper.assertFalse(CoreRegistry.triggerMachineBlocked.get().isTriggerActive(Direction.UP, tile, null),
                "Machine trigger still blocked");
        helper.assertEntityNotPresent(EntityTypes.ITEM);
    }

    /**
     * TRANSPORT.md 14: the machine's only output pipe leads to a full chest. Blocked with "Output blocked"; the pending
     * item survives a save; emptying the chest clears all three signals within a tick or two and the items arrive.
     */
    static void pipeToFullChest(GameTestHelper helper) {
        OutputTestTile tile = machine(helper);
        helper.setBlock(new BlockPos(0, Y, 1), TransportRegistry.getPipe(PipeType.ITEM, PipeMaterial.GOLD));
        helper.setBlock(new BlockPos(0, Y, 2), TransportRegistry.getPipe(PipeType.ITEM, PipeMaterial.GOLD));
        Container chest = chest(helper, new BlockPos(0, Y, 3));
        fill(chest);

        helper.runAfterDelay(SETTLE, () -> {
            assertBlocked(helper, tile, StatusReason.OUTPUT_FULL);
            int made = tile.made;

            CompoundTag tag = tile.saveWithFullMetadata(helper.getLevel().registryAccess());
            OutputTestTile copy = new OutputTestTile(tile.getBlockPos(), tile.getBlockState());
            copy.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag));
            helper.assertTrue(copy.output.hasPending() && copy.output.getPending().getFirst().is(Items.COBBLESTONE),
                    "pending output not saved");

            chest.setItem(0, ItemStack.EMPTY);
            helper.runAfterDelay(3, () -> {
                assertRunning(helper, tile);
                helper.succeedWhen(() -> {
                    helper.assertTrue(tile.made > made + 5, "the machine did not go on: " + tile.made);
                    helper.assertTrue(cobblestone(chest) >= 5, "cobblestone in the chest: " + cobblestone(chest));
                    helper.assertEntityNotPresent(EntityTypes.ITEM);
                });
            });
        });
    }

    /** A full chest right beside the machine: "Output blocked"; one free slot lets it go on. */
    static void fullChest(GameTestHelper helper) {
        OutputTestTile tile = machine(helper);
        Container chest = chest(helper, new BlockPos(1, Y, 0));
        fill(chest);

        helper.runAfterDelay(SETTLE, () -> {
            assertBlocked(helper, tile, StatusReason.OUTPUT_FULL);
            chest.setItem(5, ItemStack.EMPTY);
            helper.runAfterDelay(2, () -> {
                assertRunning(helper, tile);
                helper.succeedWhen(() -> helper.assertTrue(cobblestone(chest) >= 5, "cobblestone in the chest"));
            });
        });
    }

    /**
     * Nothing that takes items: first nothing at all around the machine, then a pipe leading nowhere. Both are
     * "No destination". A chest at the end of the pipe unblocks it.
     */
    static void noDestination(GameTestHelper helper) {
        OutputTestTile tile = machine(helper);

        helper.runAfterDelay(3, () -> {
            assertBlocked(helper, tile, StatusReason.NO_DESTINATION);
            helper.setBlock(new BlockPos(0, Y, 1), TransportRegistry.getPipe(PipeType.ITEM, PipeMaterial.GOLD));
            helper.setBlock(new BlockPos(0, Y, 2), TransportRegistry.getPipe(PipeType.ITEM, PipeMaterial.GOLD));

            helper.runAfterDelay(SETTLE, () -> {
                assertBlocked(helper, tile, StatusReason.NO_DESTINATION);
                Container chest = chest(helper, new BlockPos(0, Y, 3));
                helper.succeedWhen(() -> {
                    assertRunning(helper, tile);
                    helper.assertTrue(cobblestone(chest) >= 5, "cobblestone in the chest: " + cobblestone(chest));
                });
            });
        });
    }
}
