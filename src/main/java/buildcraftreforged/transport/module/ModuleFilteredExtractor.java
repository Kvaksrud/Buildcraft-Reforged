package buildcraftreforged.transport.module;

import buildcraftreforged.core.inventory.SimpleInventory;
import buildcraftreforged.transport.PipeModuleType;
import buildcraftreforged.transport.TilePipe;
import buildcraftreforged.transport.TransportRegistry;
import buildcraftreforged.transport.graph.PipeNetwork;
import buildcraftreforged.transport.route.ItemRouting;
import buildcraftreforged.transport.route.TransferResult;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * The Filtered Extractor (docs/TRANSPORT.md 8.3; 4.2.2 {@code PipeItemsEmerald}, {@code GuiEmeraldPipe}): an
 * {@link ModuleExtractor Extractor} with nine ghost filter slots, a round-robin cursor and a blocking / non-blocking
 * button (Q9). Each attempt pulls only the current filter's item ("same item": {@code ItemStack.isSameItem}); blocking
 * mode waits for it, non-blocking mode tries the following filters once each. All filters empty: nothing moves.
 * Items only (Q8).
 */
public class ModuleFilteredExtractor extends ModuleExtractor {

    /** 4.2.2 {@code ButtonState}; blocking is the default. */
    public enum Mode {
        BLOCKING("gui.pipes.emerald.blocking"),
        NONBLOCKING("gui.pipes.emerald.nonblocking");

        public final String label;

        Mode(String label) {
            this.label = label;
        }
    }

    public final SimpleInventory filters = new SimpleInventory(FilterCursor.SIZE, "Filters", 1);
    public final FilterCursor cursor = new FilterCursor();
    private Mode mode = Mode.BLOCKING;

    ModuleFilteredExtractor() {
        super(PipeModuleType.FILTERED_EXTRACTOR);
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    private boolean hasFilter(int slot) {
        return !filters.getItem(slot).isEmpty();
    }

    @Override
    protected void extractItems(ServerLevel level, PipeNetwork network, int node, Direction facing,
            ResourceHandler<ItemResource> source) {
        cursor.attempt(this::hasFilter, mode == Mode.BLOCKING, slot -> {
            ItemStack filter = filters.getItem(slot);
            TransferResult result = ItemRouting.extract(level, network, node, facing, source,
                    resource -> resource.is(filter.getItem()));
            if (result.movedAny())
                return FilterCursor.Outcome.MOVED;
            return result.reason() == TransferResult.Reason.NO_CAPACITY ? FilterCursor.Outcome.STOP
                    : FilterCursor.Outcome.NOT_FOUND;
        });
    }

    @Override
    public MenuProvider getMenuProvider(TilePipe pipe) {
        return new SimpleMenuProvider(
                (containerId, inventory, player) -> new ContainerFilteredExtractor(containerId, inventory, pipe, this),
                TransportRegistry.moduleName(type));
    }

    @Override
    public void save(ValueOutput output) {
        super.save(output);
        filters.writeToNBT(output, "filters");
        output.putInt("currentFilter", cursor.get());
        output.putByte("state", (byte) mode.ordinal());
    }

    @Override
    public void load(ValueInput input) {
        super.load(input);
        filters.readFromNBT(input, "filters");
        cursor.set(input.getIntOr("currentFilter", 0));
        int state = input.getByteOr("state", (byte) 0);
        mode = state == Mode.NONBLOCKING.ordinal() ? Mode.NONBLOCKING : Mode.BLOCKING;
    }
}
