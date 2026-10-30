package buildcraftreforged.core.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import buildcraftreforged.core.inventory.filters.ArrayStackFilter;
import buildcraftreforged.core.inventory.filters.StackFilter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Insert and extract through the transactors, on SimpleInventory and on an item ResourceHandler. */
class TransactorTest {

    @BeforeAll
    static void bindItemComponents() {
        ItemComponents.bind();
    }

    @Test
    void getTransactorForPicksTheTransactorByType() {
        assertInstanceOf(TransactorSimple.class, Transactor.getTransactorFor(new SimpleInventory(1, "test", 64)));
        assertInstanceOf(TransactorSimple.class, Transactor.getTransactorFor(new SimpleContainer(1)));
        assertInstanceOf(TransactorHandler.class, Transactor.getTransactorFor(new ItemStacksResourceHandler(1)));
        assertNull(Transactor.getTransactorFor("not an inventory"));
    }

    @Test
    void addFillsMatchingStacksBeforeEmptySlots() {
        SimpleInventory inv = new SimpleInventory(3, "test", 64);
        inv.setItem(2, new ItemStack(Items.COBBLESTONE, 60));

        ItemStack added = Transactor.getTransactorFor(inv).add(new ItemStack(Items.COBBLESTONE, 10), null, true);

        assertEquals(10, added.getCount());
        assertEquals(64, inv.getItem(2).getCount());
        assertEquals(6, inv.getItem(0).getCount());
        assertTrue(inv.getItem(1).isEmpty());
    }

    @Test
    void addWithoutDoAddOnlyCounts() {
        SimpleInventory inv = new SimpleInventory(2, "test", 64);

        ItemStack added = Transactor.getTransactorFor(inv).add(new ItemStack(Items.DIRT, 100), null, false);

        assertEquals(100, added.getCount());
        assertTrue(inv.isEmpty());
    }

    @Test
    void addStopsAtTheInventoryStackLimitAndReportsTheRest() {
        SimpleInventory inv = new SimpleInventory(2, "test", 16);
        inv.setItem(1, new ItemStack(Items.STONE, 16));

        ItemStack offered = new ItemStack(Items.DIRT, 40);
        ItemStack added = Transactor.getTransactorFor(inv).add(offered, null, true);

        assertEquals(16, added.getCount());
        assertEquals(40, offered.getCount(), "the offered stack is not changed");
        assertEquals(16, inv.getItem(0).getCount());
        assertEquals(Items.STONE, inv.getItem(1).getItem());
    }

    @Test
    void addDoesNotMergeDifferentComponents() {
        SimpleInventory inv = new SimpleInventory(2, "test", 64);
        ItemStack named = new ItemStack(Items.DIRT, 1);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("named"));
        inv.setItem(0, named);

        Transactor.getTransactorFor(inv).add(new ItemStack(Items.DIRT, 3), null, true);

        assertEquals(1, inv.getItem(0).getCount());
        assertEquals(3, inv.getItem(1).getCount());
    }

    @Test
    void removeTakesOneMatchingItem() {
        SimpleInventory inv = new SimpleInventory(2, "test", 64);
        inv.setItem(0, new ItemStack(Items.DIRT, 5));
        inv.setItem(1, new ItemStack(Items.STONE, 5));
        ITransactor transactor = Transactor.getTransactorFor(inv);

        ItemStack peek = transactor.remove(new ArrayStackFilter(new ItemStack(Items.STONE)), null, false);
        assertEquals(Items.STONE, peek.getItem());
        assertEquals(1, peek.getCount());
        assertEquals(5, inv.getItem(1).getCount());

        ItemStack removed = transactor.remove(new ArrayStackFilter(new ItemStack(Items.STONE)), null, true);
        assertEquals(1, removed.getCount());
        assertEquals(4, inv.getItem(1).getCount());
        assertEquals(5, inv.getItem(0).getCount());

        assertTrue(transactor.remove(new ArrayStackFilter(new ItemStack(Items.GLASS)), null, true).isEmpty());
    }

    @Test
    void removeEmptiesTheSlotOnTheLastItem() {
        SimpleInventory inv = new SimpleInventory(1, "test", 64);
        inv.setItem(0, new ItemStack(Items.DIRT, 1));

        assertEquals(1, Transactor.getTransactorFor(inv).remove(StackFilter.ALL, null, true).getCount());
        assertTrue(inv.getItem(0).isEmpty());
    }

    @Test
    void roundRobinAddsOneAtATimeToTheSmallestStack() {
        SimpleInventory inv = new SimpleInventory(3, "test", 64);
        inv.setItem(0, new ItemStack(Items.DIRT, 4));
        inv.setItem(1, new ItemStack(Items.DIRT, 1));
        inv.setItem(2, new ItemStack(Items.DIRT, 2));

        int added = new TransactorRoundRobin(inv).inject(new ItemStack(Items.DIRT, 5), null, true);

        assertEquals(5, added);
        assertEquals(4, inv.getItem(0).getCount());
        assertEquals(4, inv.getItem(1).getCount());
        assertEquals(4, inv.getItem(2).getCount());
    }

    @Test
    void roundRobinNeverStartsANewStack() {
        SimpleInventory inv = new SimpleInventory(2, "test", 64);

        assertEquals(0, new TransactorRoundRobin(inv).inject(new ItemStack(Items.DIRT, 5), null, true));
        assertTrue(inv.isEmpty());
    }

    @Test
    void moveOneItemMovesOnlyWhenTheDestinationTakesIt() {
        SimpleInventory source = new SimpleInventory(1, "source", 64);
        source.setItem(0, new ItemStack(Items.DIRT, 3));
        SimpleInventory dest = new SimpleInventory(1, "dest", 64);

        assertEquals(Items.DIRT, InvUtils.moveOneItem(source, null, dest, null, StackFilter.ALL).getItem());
        assertEquals(2, source.getItem(0).getCount());
        assertEquals(1, dest.getItem(0).getCount());

        dest.setItem(0, new ItemStack(Items.STONE, 64));
        assertTrue(InvUtils.moveOneItem(source, null, dest, null, StackFilter.ALL).isEmpty());
        assertEquals(2, source.getItem(0).getCount());
    }

    @Test
    void handlerTransactorInsertsAndExtractsThroughTransactions() {
        ItemStacksResourceHandler handler = new ItemStacksResourceHandler(2);
        ITransactor transactor = Transactor.getTransactorFor(handler);

        assertEquals(70, transactor.add(new ItemStack(Items.DIRT, 70), null, false).getCount());
        assertTrue(handler.getResource(0).isEmpty(), "simulated add changes nothing");

        assertEquals(70, transactor.add(new ItemStack(Items.DIRT, 70), null, true).getCount());
        assertEquals(64, handler.getAmountAsInt(0));
        assertEquals(6, handler.getAmountAsInt(1));

        assertEquals(1, transactor.remove(StackFilter.ALL, null, false).getCount());
        assertEquals(64, handler.getAmountAsInt(0), "simulated remove changes nothing");
        assertEquals(Items.DIRT, transactor.remove(StackFilter.ALL, null, true).getItem());
        assertEquals(63, handler.getAmountAsInt(0));
        assertTrue(transactor.remove(new ArrayStackFilter(new ItemStack(Items.STONE)), null, true).isEmpty());
    }
}
