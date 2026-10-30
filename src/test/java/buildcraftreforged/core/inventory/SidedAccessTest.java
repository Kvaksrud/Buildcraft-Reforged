package buildcraftreforged.core.inventory;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import buildcraftreforged.core.inventory.InventoryIterator.IInvSlot;
import buildcraftreforged.core.inventory.filters.StackFilter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Per-side access, as 4.2.2's ISidedInventory gave it: through InventoryIterator and the transactors, and through the
 * item capability that InvUtils.getItemHandler builds. The sample is shaped like a crafting machine: inputs 0 and 1
 * on every side but the bottom, output 2 on the bottom only, insert-only and extract-only respectively.
 */
class SidedAccessTest {

    @BeforeAll
    static void bindItemComponents() {
        ItemComponents.bind();
    }

    static class SidedSample extends SimpleInventory implements WorldlyContainer {
        static final int[] INPUTS = { 0, 1 };
        static final int[] OUTPUT = { 2 };

        SidedSample() {
            super(3, "sided", 64);
        }

        @Override
        public int[] getSlotsForFace(Direction side) {
            return side == Direction.DOWN ? OUTPUT : INPUTS;
        }

        @Override
        public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
            return slot != 2;
        }

        @Override
        public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
            return slot == 2;
        }
    }

    private static List<Integer> indices(Iterable<IInvSlot> slots) {
        List<Integer> list = new ArrayList<>();
        for (IInvSlot slot : slots) {
            list.add(slot.getIndex());
        }
        return list;
    }

    private static int insert(ResourceHandler<ItemResource> handler, ItemStack stack) {
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = handler.insert(ItemResource.of(stack), stack.getCount(), transaction);
            transaction.commit();
            return inserted;
        }
    }

    private static int extract(ResourceHandler<ItemResource> handler, int index, int amount) {
        try (Transaction transaction = Transaction.openRoot()) {
            int extracted = handler.extract(index, handler.getResource(index), amount, transaction);
            transaction.commit();
            return extracted;
        }
    }

    @Test
    void iteratorShowsTheSlotsOfTheSide() {
        SidedSample inv = new SidedSample();

        assertEquals(List.of(0, 1), indices(InventoryIterator.getIterable(inv, Direction.UP)));
        assertEquals(List.of(0, 1), indices(InventoryIterator.getIterable(inv, Direction.NORTH)));
        assertEquals(List.of(2), indices(InventoryIterator.getIterable(inv, Direction.DOWN)));
        assertEquals(List.of(0, 1, 2), indices(InventoryIterator.getIterable(inv, null)), "no side reaches every slot");
        assertEquals(List.of(0, 1, 2), indices(InventoryIterator.getIterable(new SimpleInventory(3, "plain", 64), Direction.DOWN)));
    }

    @Test
    void transactorRespectsTheSide() {
        SidedSample inv = new SidedSample();
        ITransactor transactor = Transactor.getTransactorFor(inv);

        assertEquals(0, transactor.add(new ItemStack(Items.DIRT, 5), Direction.DOWN, true).getCount(), "the bottom only takes out");
        assertEquals(5, transactor.add(new ItemStack(Items.DIRT, 5), Direction.UP, true).getCount());
        assertEquals(5, inv.getItem(0).getCount());

        assertTrue(transactor.remove(StackFilter.ALL, Direction.UP, true).isEmpty(), "inputs cannot be taken out");
        inv.setItem(2, new ItemStack(Items.STONE, 2));
        assertEquals(Items.STONE, transactor.remove(StackFilter.ALL, Direction.DOWN, true).getItem());
        assertEquals(1, inv.getItem(2).getCount());
        assertEquals(5, InvUtils.countItems(inv, Direction.UP, StackFilter.ALL));
        assertEquals(1, InvUtils.countItems(inv, Direction.DOWN, StackFilter.ALL));
    }

    @Test
    void itemHandlerShowsTheSlotsOfEachSide() {
        SidedSample inv = new SidedSample();
        inv.setItem(1, new ItemStack(Items.DIRT, 3));
        inv.setItem(2, new ItemStack(Items.STONE, 4));

        ResourceHandler<ItemResource> top = InvUtils.getItemHandler(inv, Direction.UP);
        ResourceHandler<ItemResource> bottom = InvUtils.getItemHandler(inv, Direction.DOWN);
        ResourceHandler<ItemResource> internal = InvUtils.getItemHandler(inv, null);

        assertEquals(2, top.size());
        assertEquals(Items.DIRT, top.getResource(1).getItem());
        assertEquals(1, bottom.size());
        assertEquals(Items.STONE, bottom.getResource(0).getItem());
        assertEquals(3, internal.size());
    }

    @Test
    void itemHandlerChecksInsertAndExtractPerSide() {
        SidedSample inv = new SidedSample();
        inv.setItem(2, new ItemStack(Items.STONE, 4));
        ResourceHandler<ItemResource> top = InvUtils.getItemHandler(inv, Direction.UP);
        ResourceHandler<ItemResource> bottom = InvUtils.getItemHandler(inv, Direction.DOWN);

        assertEquals(0, insert(bottom, new ItemStack(Items.STONE, 1)), "the output takes nothing in");
        assertEquals(10, insert(top, new ItemStack(Items.DIRT, 10)));
        assertEquals(10, inv.getItem(0).getCount());

        assertEquals(0, extract(top, 0, 5), "inputs cannot be taken out");
        assertEquals(10, inv.getItem(0).getCount());
        assertEquals(3, extract(bottom, 0, 3));
        assertEquals(1, inv.getItem(2).getCount());
    }

    @Test
    void plainInventoryShowsEverySlotFromEverySide() {
        SimpleInventory inv = new SimpleInventory(3, "plain", 16);

        for (Direction side : Direction.values()) {
            assertEquals(3, InvUtils.getItemHandler(inv, side).size());
        }
        assertEquals(40, insert(InvUtils.getItemHandler(inv, Direction.EAST), new ItemStack(Items.DIRT, 40)));
        assertEquals(16, inv.getItem(0).getCount(), "the inventory's stack limit holds through the handler");
        assertEquals(8, inv.getItem(2).getCount());
        assertNull(InvUtils.getItemHandler("not an inventory", Direction.UP));
    }

    @Test
    void wrapperSimpleExposesEverySlotAsSided() {
        SimpleInventory inv = new SimpleInventory(2, "plain", 64);
        WorldlyContainer wrapped = InventoryWrapper.getWrappedInventory(inv);

        assertArrayEquals(new int[] { 0, 1 }, wrapped.getSlotsForFace(Direction.SOUTH));
        assertTrue(wrapped.canTakeItemThroughFace(1, new ItemStack(Items.DIRT), Direction.DOWN));
    }
}
