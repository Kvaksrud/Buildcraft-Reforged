package buildcraftreforged.core.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** SimpleInventory's slot semantics, the inventory views and StackHelper's comparisons. */
class InventoryTest {

    @BeforeAll
    static void bindItemComponents() {
        ItemComponents.bind();
    }

    @Test
    void setItemClampsToTheStackLimit() {
        SimpleInventory inv = new SimpleInventory(1, "test", 16);
        ItemStack stack = new ItemStack(Items.DIRT, 40);

        inv.setItem(0, stack);

        assertEquals(16, inv.getItem(0).getCount());
        assertEquals(16, stack.getCount(), "4.2.2 clamped the stack it was given");
    }

    @Test
    void removeItemSplitsOrTakesTheWholeStack() {
        SimpleInventory inv = new SimpleInventory(1, "test", 64);
        inv.setItem(0, new ItemStack(Items.DIRT, 10));

        assertEquals(4, inv.removeItem(0, 4).getCount());
        assertEquals(6, inv.getItem(0).getCount());
        assertEquals(6, inv.removeItem(0, 20).getCount());
        assertTrue(inv.getItem(0).isEmpty());
        assertTrue(inv.removeItem(0, 1).isEmpty());
    }

    @Test
    void saveAndLoadKeepTheSlots() {
        SimpleInventory inv = new SimpleInventory(4, "test", 64);
        inv.setItem(1, new ItemStack(Items.DIRT, 7));
        inv.setItem(3, new ItemStack(Items.STONE, 2));

        TagValueOutput output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        inv.writeToNBT(output);
        CompoundTag tag = output.buildResult();
        assertEquals(2, tag.getListOrEmpty("items").size());

        SimpleInventory loaded = new SimpleInventory(4, "test", 64);
        loaded.readFromNBT(TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, tag));
        assertTrue(loaded.getItem(0).isEmpty());
        assertEquals(7, loaded.getItem(1).getCount());
        assertEquals(Items.STONE, loaded.getItem(3).getItem());
    }

    @Test
    void mapperAndConcatenatorAddressTheBackingSlots() {
        SimpleInventory a = new SimpleInventory(2, "a", 64);
        SimpleInventory b = new SimpleInventory(3, "b", 64);
        InventoryConcatenator all = InventoryConcatenator.make().add(a).add(b);
        InventoryMapper tail = new InventoryMapper(b, 1, 2);

        all.setItem(3, new ItemStack(Items.DIRT, 1));
        assertEquals(5, all.getContainerSize());
        assertEquals(Items.DIRT, b.getItem(1).getItem());
        assertEquals(Items.DIRT, tail.getItem(0).getItem());
        assertEquals(2, tail.getContainerSize());
    }

    @Test
    void copyDoesNotTouchTheOriginal() {
        SimpleInventory inv = new SimpleInventory(1, "test", 64);
        inv.setItem(0, new ItemStack(Items.DIRT, 5));
        InventoryCopy copy = new InventoryCopy(inv);

        copy.removeItem(0, 5);

        assertTrue(copy.getItem(0).isEmpty());
        assertEquals(5, inv.getItem(0).getCount());
    }

    @Test
    void stackHelperMergesOnlyEqualComponentsButMatchesAnyDamage() {
        ItemStack fresh = new ItemStack(Items.IRON_PICKAXE);
        ItemStack worn = new ItemStack(Items.IRON_PICKAXE);
        worn.setDamageValue(10);
        StackHelper helper = StackHelper.instance();

        assertFalse(helper.canStacksMerge(fresh, worn));
        assertTrue(helper.canStacksMerge(new ItemStack(Items.DIRT), new ItemStack(Items.DIRT, 5)));
        assertFalse(helper.canStacksMerge(ItemStack.EMPTY, new ItemStack(Items.DIRT)));
        assertTrue(helper.isMatchingItem(fresh, worn), "4.2.2 ignored damage for items without subtypes");
        assertFalse(helper.isMatchingItem(new ItemStack(Items.DIRT), new ItemStack(Items.STONE)));
        assertEquals(10, helper.mergeStacks(new ItemStack(Items.DIRT, 10), new ItemStack(Items.DIRT, 50), false));
    }
}
