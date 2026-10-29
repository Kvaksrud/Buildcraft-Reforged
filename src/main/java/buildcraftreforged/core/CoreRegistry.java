/**
 * Copyright (c) 2011-2014, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: the registration half of buildcraft/BuildCraftCore.java, on DeferredRegister;
 * numeric ids, Configuration and LanguageRegistry names removed.
 */

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

    // TODO(#21): new ItemBuildCraft() once the base classes are ported, as in 4.2.2.
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
