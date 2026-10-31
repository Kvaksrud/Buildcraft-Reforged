package buildcraftreforged.gametest;

import buildcraftreforged.api.transport.IPipeTile.PipeType;
import buildcraftreforged.transport.PipeMaterial;
import buildcraftreforged.transport.PipeModuleType;
import buildcraftreforged.transport.TilePipe;
import buildcraftreforged.transport.TransportRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * #50: item routing in a real level (docs/TRANSPORT.md 4-6, section 14). The walk and the budget arithmetic are
 * unit-tested ({@code PipeRoutingTest}); these check extraction, pushes through the pipe capability, junctions, budgets
 * and the tier rates with chests, hoppers and pipe block entities. They build in the padded area from y = 2 (see
 * {@link OilFuelTests#PADDING}). Nothing may ever be dropped: each test ends with no item entity in its area.
 */
final class ItemRoutingTests {
    private static final int Y = 2;
    /** Masks settle on the server tick after a placement; budgets fill meanwhile. */
    private static final int SETTLE = 3;

    private ItemRoutingTests() {
    }

    private static void pipe(GameTestHelper helper, int x, int z, PipeMaterial material) {
        helper.setBlock(new BlockPos(x, Y, z), TransportRegistry.getPipe(PipeType.ITEM, material));
    }

    private static TilePipe tile(GameTestHelper helper, int x, int z) {
        return helper.getBlockEntity(new BlockPos(x, Y, z), TilePipe.class);
    }

    private static Container chest(GameTestHelper helper, int x, int z) {
        return (Container) helper.getBlockEntity(new BlockPos(x, Y, z), BlockEntity.class);
    }

    private static int count(Container container, @Nullable Item item) {
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (item == null || stack.is(item))
                count += stack.getCount();
        }
        return count;
    }

    private static int count(Container container) {
        return count(container, null);
    }

    /** Pushes {@code amount} of {@code item} into the pipe's item capability on {@code side}; returns what it took. */
    private static int push(GameTestHelper helper, int x, int z, Direction side, Item item, int amount, boolean commit) {
        ResourceHandler<ItemResource> handler = tile(helper, x, z).getItemHandler(side);
        helper.assertTrue(handler != null, "item pipe has no item handler on " + side);
        try (Transaction transaction = Transaction.openRoot()) {
            int moved = handler.insert(ItemResource.of(item), amount, transaction);
            if (commit)
                transaction.commit();
            return moved;
        }
    }

    /**
     * Section 14: chest, Extractor pipe, two pipes, chest. Every item of two kinds arrives, in slot order; the source
     * ends empty; nothing is dropped.
     */
    static void chestToChest(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, Y, 0), Blocks.CHEST);
        for (int z = 1; z <= 3; z++) {
            pipe(helper, 0, z, PipeMaterial.GOLD);
        }
        helper.setBlock(new BlockPos(0, Y, 4), Blocks.CHEST);
        Container source = chest(helper, 0, 0);
        source.setItem(0, new ItemStack(Items.COBBLESTONE, 10));
        source.setItem(5, new ItemStack(Items.DIRT, 6));
        tile(helper, 0, 1).setModuleType(PipeModuleType.EXTRACTOR);

        helper.succeedWhen(() -> {
            Container target = chest(helper, 0, 4);
            helper.assertValueEqual(count(target, Items.COBBLESTONE), 10, "cobblestone in the target");
            helper.assertValueEqual(count(target, Items.DIRT), 6, "dirt in the target");
            helper.assertValueEqual(count(source), 0, "items left in the source");
            helper.assertEntityNotPresent(EntityTypes.ITEM);
        });
    }

    /** Section 6.2: a hopper pushes into a pipe through its item capability, and the pipe routes on to a chest. */
    static void hopperPush(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, Y + 1, 1), Blocks.HOPPER); // facing down, into the pipe
        pipe(helper, 0, 1, PipeMaterial.GOLD);
        pipe(helper, 0, 2, PipeMaterial.GOLD);
        helper.setBlock(new BlockPos(0, Y, 3), Blocks.CHEST);
        HopperBlockEntity hopper = helper.getBlockEntity(new BlockPos(0, Y + 1, 1), HopperBlockEntity.class);
        hopper.setItem(0, new ItemStack(Items.DIAMOND, 5));

        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(chest(helper, 0, 3), Items.DIAMOND), 5, "diamonds in the chest");
            helper.assertValueEqual(count(hopper), 0, "diamonds left in the hopper");
            helper.assertEntityNotPresent(EntityTypes.ITEM);
        });
    }

    /**
     * A junction with four ways out: a chest beside it, a chest at the end of two pipes, a dead-end branch and a branch
     * to a full chest. The walk backs out of the last two (Q1), so every item lands in one of the first two, about
     * half in each with no look-ahead (4.2.2 junction rule); the full chest is untouched.
     */
    static void branching(GameTestHelper helper) {
        PipeMaterial ender = PipeMaterial.ENDER;
        pipe(helper, 2, 2, ender); // the junction
        helper.setBlock(new BlockPos(2, Y, 1), Blocks.CHEST); // north, beside it
        pipe(helper, 3, 2, ender);
        pipe(helper, 4, 2, ender);
        helper.setBlock(new BlockPos(5, Y, 2), Blocks.CHEST); // east, two pipes on
        pipe(helper, 2, 3, ender);
        pipe(helper, 2, 4, ender); // south, a dead end
        pipe(helper, 1, 2, ender);
        helper.setBlock(new BlockPos(0, Y, 2), Blocks.CHEST); // west, full
        Container full = chest(helper, 0, 2);
        for (int slot = 0; slot < full.getContainerSize(); slot++) {
            full.setItem(slot, new ItemStack(Items.STONE, 64));
        }

        // Ender pipes hold 512 items' worth after a second.
        helper.runAfterDelay(25, () -> {
            int items = 200;
            for (int i = 0; i < items; i++) {
                helper.assertValueEqual(push(helper, 2, 2, Direction.UP, Items.COBBLESTONE, 1, true), 1, "push " + i);
            }
            int north = count(chest(helper, 2, 1));
            int east = count(chest(helper, 5, 2));
            helper.assertValueEqual(north + east, items, "items in the two chests");
            helper.assertTrue(north >= 70 && east >= 70, "uneven split: north " + north + ", east " + east);
            helper.assertValueEqual(count(full, Items.COBBLESTONE), 0, "cobblestone in the full chest");
            helper.assertEntityNotPresent(EntityTypes.ITEM);
            helper.succeed();
        });
    }

    /**
     * Section 5: a wood line holds one item's worth after a second (the cap), takes one item of five (PARTIAL), then
     * nothing until it has refilled for 20 ticks. A simulated push changes nothing. In a gold, wood, gold line the
     * wood pipe is the bottleneck.
     */
    static void budgets(GameTestHelper helper) {
        pipe(helper, 0, 1, PipeMaterial.WOOD);
        pipe(helper, 0, 2, PipeMaterial.WOOD);
        helper.setBlock(new BlockPos(0, Y, 3), Blocks.CHEST);
        pipe(helper, 2, 1, PipeMaterial.GOLD);
        pipe(helper, 2, 2, PipeMaterial.WOOD);
        pipe(helper, 2, 3, PipeMaterial.GOLD);
        helper.setBlock(new BlockPos(2, Y, 4), Blocks.CHEST);

        helper.runAfterDelay(40, () -> {
            helper.assertValueEqual(push(helper, 0, 1, Direction.NORTH, Items.COBBLESTONE, 5, false), 1, "simulated push");
            helper.assertValueEqual(count(chest(helper, 0, 3)), 0, "items after a simulated push");
            helper.assertValueEqual(push(helper, 0, 1, Direction.NORTH, Items.COBBLESTONE, 5, true), 1, "first push");
            helper.assertValueEqual(push(helper, 0, 1, Direction.NORTH, Items.COBBLESTONE, 5, true), 0, "second push");
            helper.assertValueEqual(tile(helper, 0, 1).injectItem(new ItemStack(Items.COBBLESTONE), true, Direction.NORTH), 0,
                    "injectItem with an empty budget");

            helper.assertValueEqual(push(helper, 2, 1, Direction.NORTH, Items.COBBLESTONE, 64, true), 1,
                    "push through the wood bottleneck");
            helper.assertValueEqual(push(helper, 2, 1, Direction.NORTH, Items.COBBLESTONE, 64, true), 0,
                    "second push through the wood bottleneck");

            helper.runAfterDelay(20, () -> {
                helper.assertValueEqual(tile(helper, 0, 1).injectItem(new ItemStack(Items.COBBLESTONE), true, Direction.NORTH),
                        1, "injectItem after 20 ticks");
                helper.assertValueEqual(count(chest(helper, 0, 3)), 2, "items in the wood line's chest");
                helper.assertValueEqual(count(chest(helper, 2, 4)), 1, "items in the bottleneck line's chest");
                helper.assertEntityNotPresent(EntityTypes.ITEM);
                helper.succeed();
            });
        });
    }

    /** First and last tick of the measured window of {@link #throughput}: past the start, before the source runs dry. */
    static final int WINDOW_START = 20;
    static final int WINDOW_END = 60;

    /**
     * Section 14, 10-tier throughput: chest of cobblestone, Extractor pipe, four pipes of the same tier, chest. Over
     * 40 ticks the target gains {@code 2 × itemsPerSecond}, ± 1 for where the window cuts the budget's remainder. From
     * 20 items/s up a pull can also be short by the rest of one tick's worth: when the Extractor's slot runs out it
     * moves less that tick and catches up the next (8.2).
     */
    static void throughput(GameTestHelper helper, PipeMaterial material) {
        helper.setBlock(new BlockPos(0, Y, 0), Blocks.CHEST);
        for (int z = 1; z <= 5; z++) {
            pipe(helper, 0, z, material);
        }
        helper.setBlock(new BlockPos(0, Y, 6), Blocks.CHEST);
        Container source = chest(helper, 0, 0);
        for (int slot = 0; slot < source.getContainerSize(); slot++) {
            source.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        tile(helper, 0, 1).setModuleType(PipeModuleType.EXTRACTOR);

        int[] start = new int[1];
        helper.runAtTickTime(WINDOW_START, () -> start[0] = count(chest(helper, 0, 6)));
        helper.runAtTickTime(WINDOW_END, () -> {
            int rate = tile(helper, 0, 3).getTierStats().itemsPerSecond();
            int moved = count(chest(helper, 0, 6)) - start[0];
            int expected = rate * (WINDOW_END - WINDOW_START) / 20;
            int tolerance = rate < 20 ? 1 : (rate + 19) / 20 + 1;
            helper.assertTrue(Math.abs(moved - expected) <= tolerance, material.getSerializedName() + ": moved " + moved
                    + " items in " + (WINDOW_END - WINDOW_START) + " ticks, expected " + expected + " ± " + tolerance);
            helper.assertValueEqual(count(source) + count(chest(helper, 0, 6)), 27 * 64, "items in both chests");
            helper.assertEntityNotPresent(EntityTypes.ITEM);
            helper.succeed();
        });
    }
}
