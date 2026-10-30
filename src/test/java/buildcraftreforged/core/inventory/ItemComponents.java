package buildcraftreforged.core.inventory;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Binds data components to the few items these tests use. The game binds every item's components when a server or
 * client loads its registries and tags; the unit test setup loads neither, and an ItemStack of a real item cannot be
 * made until its item has components. The maps hold only what the inventory code reads: the stack size, and damage for
 * the pickaxe.
 */
final class ItemComponents {

    private ItemComponents() {
    }

    static synchronized void bind() {
        for (Item item : new Item[] { Items.DIRT, Items.STONE, Items.COBBLESTONE, Items.GLASS }) {
            bind(item, DataComponentMap.builder().addAll(DataComponents.COMMON_ITEM_COMPONENTS).build());
        }
        bind(Items.IRON_PICKAXE, DataComponentMap.builder().addAll(DataComponents.COMMON_ITEM_COMPONENTS)
                .set(DataComponents.MAX_STACK_SIZE, 1).set(DataComponents.MAX_DAMAGE, 250).set(DataComponents.DAMAGE, 0)
                .build());
    }

    @SuppressWarnings("deprecation") // builtInRegistryHolder: there is no other way to reach the item's holder here
    private static void bind(Item item, DataComponentMap components) {
        Holder.Reference<Item> holder = item.builtInRegistryHolder();
        if (!holder.areComponentsBound()) {
            holder.bindComponents(components);
        }
    }
}
