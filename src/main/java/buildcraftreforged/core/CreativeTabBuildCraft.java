/*
 * Ported from BuildCraft 4.2.2, buildcraft/core/CreativeTabBuildCraft.java (no header in the original).
 * Modified by Buildcraft Reforged, 2026: creative tabs are registry entries built with CreativeModeTab.Builder;
 * items join a tab through add() instead of Item.setCreativeTab, and lists of stacks (the facades) through addStacks().
 */
package buildcraftreforged.core;

import buildcraftreforged.transport.ItemFacade;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public enum CreativeTabBuildCraft {

    MACHINES,
    FACADES;
    private final List<Supplier<? extends ItemLike>> items = new ArrayList<>();
    private final List<Supplier<? extends Collection<ItemStack>>> stacks = new ArrayList<>();
    private DeferredHolder<CreativeModeTab, CreativeModeTab> tab;

    /**
     * Registers one tab per constant, named {@code machines} and {@code facades}, each sorted after the one before.
     * Called once by CoreRegistry.
     */
    static void registerAll(DeferredRegister<CreativeModeTab> register) {
        CreativeTabBuildCraft previous = null;
        for (CreativeTabBuildCraft value : values()) {
            CreativeModeTab.Builder builder = CreativeModeTab.builder()
                    .title(value.translate())
                    .icon(value::getItem)
                    .displayItems((parameters, output) -> {
                        value.items.forEach(item -> output.accept(item.get()));
                        value.stacks.forEach(list -> output.acceptAll(list.get()));
                    });
            if (previous != null) {
                builder.withTabsBefore(previous.tab.getId());
            }
            value.tab = register.register(value.getLabel(), builder::build);
            previous = value;
        }
    }

    public CreativeModeTab get() {
        return tab.get();
    }

    /** Puts an item in this tab, in the order added. The 1.6.4 equivalent was Item.setCreativeTab(get()). */
    public void add(Supplier<? extends ItemLike> item) {
        items.add(item);
    }

    /** Puts stacks in this tab after the items, such as one facade per block (4.2.2 ItemFacade.getSubItems). */
    public void addStacks(Supplier<? extends Collection<ItemStack>> list) {
        stacks.add(list);
    }

    private String getLabel() {
        return name().toLowerCase(Locale.ENGLISH);
    }

    private Component translate() {
        return Component.translatable("tab." + name().toLowerCase(Locale.ENGLISH));
    }

    private ItemStack getItem() {
        switch (this) {
        case FACADES:
            return ItemFacade.getStack(Blocks.STONE_BRICKS.defaultBlockState());
        default:
            return new ItemStack(CoreRegistry.diamondGearItem.get());
        }
    }
}
