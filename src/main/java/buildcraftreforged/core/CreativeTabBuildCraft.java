/*
 * Ported from BuildCraft 4.2.2, buildcraft/core/CreativeTabBuildCraft.java (no header in the original).
 * Modified by Buildcraft Reforged, 2026: creative tabs are registry entries built with CreativeModeTab.Builder;
 * items join a tab through add() instead of Item.setCreativeTab.
 */
package buildcraftreforged.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public enum CreativeTabBuildCraft {

    MACHINES,
    FACADES;
    private final List<Supplier<? extends ItemLike>> items = new ArrayList<>();
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
                    .displayItems((parameters, output) -> value.items.forEach(item -> output.accept(item.get())));
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

    private String getLabel() {
        return name().toLowerCase(Locale.ENGLISH);
    }

    private Component translate() {
        return Component.translatable("tab." + name().toLowerCase(Locale.ENGLISH));
    }

    private ItemStack getItem() {
        switch (this) {
        case FACADES:
            // TODO: ItemFacade.getStack(stone bricks, 0) once facades are ported
            return new ItemStack(Items.STONE_BRICKS);
        default:
            return new ItemStack(CoreRegistry.diamondGearItem.get());
        }
    }
}
