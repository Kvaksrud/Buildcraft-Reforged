package buildcraftreforged.transport.module;

import buildcraftreforged.core.inventory.SimpleInventory;
import buildcraftreforged.transport.PipeModuleType;
import buildcraftreforged.transport.TilePipe;
import buildcraftreforged.transport.TransportRegistry;
import net.minecraft.util.RandomSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.Resource;
import org.jspecify.annotations.Nullable;

/**
 * The Sorter (docs/TRANSPORT.md 8.5; 4.2.2 {@code PipeItemsDiamond}, {@code GuiDiamondPipe},
 * {@code ContainerDiamondPipe}): 54 ghost filter slots, one row of nine per side in side order. An item leaves by a
 * side whose row holds it, more often the more slots of the row hold it; with no such side, by a side whose row is
 * empty; never by a side whose row holds only other items. "Same item" is {@code ItemStack.isSameItem}. Items only.
 */
public class ModuleSorter extends PipeModule {

    public static final int ROW = 9;
    public static final int SLOTS = ROW * 6;

    public final SimpleInventory filters = new SimpleInventory(SLOTS, "Filters", 1);

    ModuleSorter() {
        super(PipeModuleType.SORTER);
    }

    @Override
    public int orderCandidates(TilePipe pipe, int[] sides, int count, @Nullable Resource resource, int colour,
            RandomSource random) {
        if (!(resource instanceof ItemResource item))
            return -1;
        boolean[] filtered = new boolean[6];
        int[] matches = new int[6];
        for (int i = 0; i < count; i++) {
            int side = sides[i];
            for (int slot = side * ROW; slot < side * ROW + ROW; slot++) {
                ItemStack filter = filters.getItem(slot);
                if (filter.isEmpty())
                    continue;
                filtered[side] = true;
                if (item.is(filter.getItem()))
                    matches[side]++;
            }
        }
        return CandidateOrder.sorter(sides, count, filtered, matches, random);
    }

    /** 4.2.2 opened the GUI on any use of the pipe. */
    @Override
    public MenuProvider getMenuProvider(TilePipe pipe) {
        return new SimpleMenuProvider(
                (containerId, inventory, player) -> new ContainerSorter(containerId, inventory, pipe, this),
                TransportRegistry.moduleName(type));
    }

    @Override
    public void save(ValueOutput output) {
        filters.writeToNBT(output, "filters");
    }

    @Override
    public void load(ValueInput input) {
        filters.readFromNBT(input, "filters");
    }
}
