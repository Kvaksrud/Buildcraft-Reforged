package buildcraftreforged.transport.module;

import buildcraftreforged.transport.PipeModuleType;
import buildcraftreforged.transport.TilePipe;
import buildcraftreforged.transport.TransportRegistry;
import buildcraftreforged.transport.graph.PipeNetwork;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * A pipe module (docs/TRANSPORT.md 8.1): the object a pipe holds in place of 4.2.2's per-material pipe classes. One per
 * pipe; it has a {@link #type}, saved settings and the hooks below, all with do-nothing defaults. The pipe calls them on
 * the server; the client holds a module of the right type with default settings (only the type is synced).
 *
 * <p>Modules need no power (PLAN 8.2). A module is fitted by using its item on a pipe without one and removed by
 * sneak-using a wrench, which hands back the item with the settings in {@link TransportRegistry#moduleSettings} (Q18).
 *
 * <p>Hooks the later modules add here: a candidate filter for the walk (Sorter, Colour Sorter, #56/#57), colour tagging
 * on entry (#57) and gate actions (#60).
 */
public class PipeModule {

    public final PipeModuleType type;

    protected PipeModule(PipeModuleType type) {
        this.type = type;
    }

    /** A new module of {@code type} with default settings. Types without behaviour yet are plain data. */
    public static PipeModule create(PipeModuleType type) {
        return switch (type) {
        case EXTRACTOR -> new ModuleExtractor(type);
        case FILTERED_EXTRACTOR -> new ModuleFilteredExtractor();
        case DIRECTOR -> new ModuleDirector();
        default -> new PipeModule(type);
        };
    }

    /** Server: the module was fitted to {@code pipe}. */
    public void onInstalled(TilePipe pipe) {
    }

    /** Server: the module is about to leave {@code pipe} (removed, or the pipe was broken). */
    public void onRemoved(TilePipe pipe) {
    }

    /**
     * Server: the pipe recomputed its connections, which it does on the tick after a neighbour block changed, a
     * neighbour's capability was invalidated, the pipe loaded, or a plug or module changed.
     */
    public void onNeighbourChange(TilePipe pipe) {
    }

    /** Whether the routing walk may leave the pipe by {@code side} (docs/TRANSPORT.md 4.2 step 4). Read live. */
    public boolean allowsOutput(TilePipe pipe, Direction side) {
        return true;
    }

    /**
     * Server: one transfer attempt of a source module ({@link PipeModuleType#isSource()}), called once per tick by
     * {@code PipeSources} for the node {@code node} of {@code network}, which is {@code pipe}.
     */
    public void tickSource(ServerLevel level, PipeNetwork network, int node, TilePipe pipe) {
    }

    /** Server: a wrench was used on the pipe without sneaking. Returns whether the module did something. */
    public boolean onWrench(TilePipe pipe, Player player) {
        return false;
    }

    /** The GUI opened by using the pipe with an empty hand, or null for none. */
    public @Nullable MenuProvider getMenuProvider(TilePipe pipe) {
        return null;
    }

    /** Writes the settings; the pipe stores them under {@code module_settings}, the item in its data component. */
    public void save(ValueOutput output) {
    }

    /** Reads what {@link #save} wrote; missing values keep their defaults. */
    public void load(ValueInput input) {
    }

    /** The module's item, carrying the settings, or empty for a type without an item. */
    public ItemStack toItem(HolderLookup.Provider registries) {
        Item item = TransportRegistry.getModuleItem(type);
        if (item == null)
            return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item);
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        save(output);
        CompoundTag settings = output.buildResult();
        if (!settings.isEmpty())
            stack.set(TransportRegistry.moduleSettings.get(), CustomData.of(settings));
        return stack;
    }

    /** A module of {@code type} with the settings carried by {@code stack}, if any. */
    public static PipeModule fromItem(PipeModuleType type, ItemStack stack, HolderLookup.Provider registries) {
        PipeModule module = create(type);
        CustomData settings = stack.get(TransportRegistry.moduleSettings.get());
        if (settings != null)
            module.load(TagValueInput.create(ProblemReporter.DISCARDING, registries, settings.copyTag()));
        return module;
    }
}
