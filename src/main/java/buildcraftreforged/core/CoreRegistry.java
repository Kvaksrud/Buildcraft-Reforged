package buildcraftreforged.core;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Registry entries of the core module, the registration half of 4.2.2's BuildCraftCore. Field names are the 4.2.2
 * field names; registry names are the 4.2.2 unlocalised names in snake case; the translation key stays the 4.2.2 one.
 */
public final class CoreRegistry {
    public static final ModuleRegisters REGISTERS = new ModuleRegisters();

    public static final DeferredItem<Item> woodenGearItem = gear("woodenGearItem");
    public static final DeferredItem<Item> stoneGearItem = gear("stoneGearItem");
    public static final DeferredItem<Item> ironGearItem = gear("ironGearItem");
    public static final DeferredItem<Item> goldGearItem = gear("goldGearItem");
    public static final DeferredItem<Item> diamondGearItem = gear("diamondGearItem");

    static {
        CreativeTabBuildCraft.registerAll(REGISTERS.creativeModeTabs);
    }

    private CoreRegistry() {
    }

    // TODO(#21): ItemBuildCraft once the base classes are ported.
    private static DeferredItem<Item> gear(String unlocalizedName) {
        DeferredItem<Item> item = REGISTERS.items.registerSimpleItem(RegistryNames.of(unlocalizedName),
                properties -> properties.overrideDescription("item." + unlocalizedName));
        CreativeTabBuildCraft.MACHINES.add(item);
        return item;
    }

    public static void register(IEventBus modBus) {
        REGISTERS.register(modBus);
    }
}
