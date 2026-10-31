package buildcraftreforged.transport.module;

import buildcraftreforged.api.gates.IAction;
import buildcraftreforged.core.inventory.SimpleInventory;
import buildcraftreforged.core.utils.EnumColor;
import buildcraftreforged.transport.IPipeGateTarget;
import buildcraftreforged.transport.PipeModuleType;
import buildcraftreforged.transport.TilePipe;
import buildcraftreforged.transport.TransportRegistry;
import buildcraftreforged.transport.graph.PipeNetwork;
import buildcraftreforged.transport.route.ItemRouting;
import buildcraftreforged.transport.route.PipeWalk;
import buildcraftreforged.transport.triggers.ActionExtractionPreset;
import java.util.BitSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.function.IntPredicate;
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
 * The Filtered Colour Tag (docs/TRANSPORT.md 8.10; 4.2.2 {@code PipeItemsEmzuli}, {@code GuiEmzuliPipe},
 * {@code ContainerEmzuliPipe}): an {@link ModuleExtractor Extractor} with four ghost filters, each with a paint colour
 * (or none) and an active flag. Only gate actions set the flags: after every gate update each active Extraction Preset
 * (red, blue, green, yellow) switches on filter 0, 1, 2 or 3 and the rest are off, so without a gate nothing is pulled.
 * Each attempt moves the cursor to the next filter that is set and active, pulls its item and starts the walk with the
 * filter's colour, which a Colour Sorter further on reads. Items only.
 */
public class ModuleFilteredColourTag extends ModuleExtractor {

    public static final int FILTERS = 4;

    public final SimpleInventory filters = new SimpleInventory(FILTERS, "Filters", 1);
    /** 4.2.2 {@code slotColors}: 0 for no paint, else the {@code EnumColor} ordinal + 1. */
    public final byte[] slotColors = new byte[FILTERS];
    /** Set by the gate on every update; not saved, as in 4.2.2. */
    private final BitSet activeFlags = new BitSet(FILTERS);
    private int currentFilter;

    ModuleFilteredColourTag() {
        super(PipeModuleType.FILTERED_COLOUR_TAG);
    }

    public int getCurrentFilter() {
        return currentFilter;
    }

    public boolean isActive(int slot) {
        return activeFlags.get(slot);
    }

    /**
     * 4.2.2 {@code incrementFilter}: one step on, then on past filters that are empty or inactive, at most
     * {@link #FILTERS} more steps. Level-free, so it is unit-tested.
     */
    public static int nextFilter(int current, IntPredicate usable) {
        int next = current + 1;
        for (int count = 0; !usable.test(Math.floorMod(next, FILTERS)) && count < FILTERS; count++) {
            next++;
        }
        return next;
    }

    private boolean usable(int slot) {
        return !filters.getItem(slot).isEmpty() && activeFlags.get(slot);
    }

    /** The walk colour of slot {@code slot}'s items, {@link PipeWalk#NO_COLOUR} for no paint. */
    public int walkColour(int slot) {
        return slotColors[slot] == 0 ? PipeWalk.NO_COLOUR : slotColors[slot] - 1;
    }

    /** 4.2.2 {@code setActivePreset}: red, blue, green and yellow are filters 0 to 3. */
    public void setActivePresets(Iterable<EnumColor> presets) {
        activeFlags.clear();
        for (EnumColor preset : presets) {
            switch (preset) {
            case RED -> activeFlags.set(0);
            case BLUE -> activeFlags.set(1);
            case GREEN -> activeFlags.set(2);
            case YELLOW -> activeFlags.set(3);
            default -> {
            }
            }
        }
    }

    /** One attempt (4.2.2 {@code checkExtract} and {@code makeItem}), made only when the pipe can move an item. */
    @Override
    protected void extractItems(ServerLevel level, PipeNetwork network, int node, Direction facing,
            ResourceHandler<ItemResource> source) {
        if (activeFlags.isEmpty())
            return;
        // 4.2.2 tried only with energy; stepping the cursor every tick would skip filters between items.
        if (ItemRouting.ROUTER.available(network.nodes[node], level.getGameTime(), ItemRouting.settings())
                < ItemRouting.ITEM_COST)
            return;
        currentFilter = nextFilter(currentFilter, this::usable);
        int slot = Math.floorMod(currentFilter, FILTERS);
        if (!usable(slot))
            return;
        ItemStack filter = filters.getItem(slot);
        ItemRouting.extract(level, network, node, facing, source, resource -> resource.is(filter.getItem()),
                walkColour(slot));
    }

    @Override
    public IPipeGateTarget gateTarget(TilePipe pipe) {
        return new IPipeGateTarget() {
            @Override
            public void addActions(LinkedList<IAction> list) {
                list.add(TransportRegistry.actionExtractionPresetRed.get());
                list.add(TransportRegistry.actionExtractionPresetBlue.get());
                list.add(TransportRegistry.actionExtractionPresetGreen.get());
                list.add(TransportRegistry.actionExtractionPresetYellow.get());
            }

            @Override
            public void actionsActivated(Map<IAction, Boolean> actions) {
                LinkedList<EnumColor> active = new LinkedList<>();
                for (Map.Entry<IAction, Boolean> action : actions.entrySet()) {
                    if (action.getKey() instanceof ActionExtractionPreset preset
                            && Boolean.TRUE.equals(action.getValue()))
                        active.add(preset.color);
                }
                setActivePresets(active);
            }
        };
    }

    @Override
    public MenuProvider getMenuProvider(TilePipe pipe) {
        return new SimpleMenuProvider(
                (containerId, inventory, player) -> new ContainerFilteredColourTag(containerId, inventory, pipe, this),
                TransportRegistry.moduleName(type));
    }

    @Override
    public void save(ValueOutput output) {
        super.save(output);
        filters.writeToNBT(output, "filters");
        output.putInt("currentFilter", currentFilter);
        for (int slot = 0; slot < FILTERS; slot++) {
            output.putByte("slotColors[" + slot + "]", slotColors[slot]);
        }
    }

    @Override
    public void load(ValueInput input) {
        super.load(input);
        filters.readFromNBT(input, "filters");
        currentFilter = input.getIntOr("currentFilter", 0);
        for (int slot = 0; slot < FILTERS; slot++) {
            int colour = input.getByteOr("slotColors[" + slot + "]", (byte) 0);
            slotColors[slot] = (byte) (colour >= 0 && colour <= EnumColor.VALUES.length ? colour : 0);
        }
    }
}
