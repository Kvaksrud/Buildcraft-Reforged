package buildcraftreforged.transport;

import buildcraftreforged.api.transport.IPipeTile.PipeType;
import buildcraftreforged.core.CreativeTabBuildCraft;
import buildcraftreforged.core.ItemBlockBuildCraft;
import buildcraftreforged.core.ItemBuildCraft;
import buildcraftreforged.core.ModuleRegisters;
import buildcraftreforged.core.RegistryNames;
import buildcraftreforged.core.TileBuildCraft;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Registry entries of the transport module, the registration half of 4.2.2's BuildCraftTransport. Add entries here as the module
 * is ported, following the naming rules in {@link buildcraftreforged.core.CoreRegistry}.
 *
 * <p>Pipes are registered from {@link #PIPE_DEFINITIONS}, one block and one item per row, all sharing the
 * {@link #pipeTile} block entity type: three kinds of each of the ten materials (docs/TRANSPORT.md 2.1).
 */
public final class TransportRegistry {
    public static final ModuleRegisters REGISTERS = new ModuleRegisters();

    /** The pipe table: kinds × materials, in creative tab order. */
    public static final List<PipeDefinition> PIPE_DEFINITIONS = table(List.of(PipeMaterial.values()));

    private static final Map<PipeDefinition, DeferredBlock<BlockPipe>> PIPE_BLOCKS = new LinkedHashMap<>();
    private static final Map<PipeDefinition, DeferredItem<ItemBlockBuildCraft>> PIPE_ITEMS = new LinkedHashMap<>();

    /** 4.2.2 {@code pipeWaterproof}: the ingredient that turns an item pipe into a fluid pipe. */
    public static final DeferredItem<ItemBuildCraft> pipeWaterproof = item("pipeWaterproof");

    static {
        for (PipeDefinition definition : PIPE_DEFINITIONS) {
            registerPipe(definition);
        }
    }

    /** 4.2.2 registered TileGenericPipe as one tile class for every pipe; here one type is valid for every pipe block. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TilePipe>> pipeTile = REGISTERS.blockEntityTypes
            .register("pipe", () -> new BlockEntityType<>(TilePipe::new,
                    PIPE_BLOCKS.values().stream().map(DeferredBlock::get).collect(Collectors.toUnmodifiableSet())));

    private TransportRegistry() {
    }

    private static List<PipeDefinition> table(List<PipeMaterial> materials) {
        List<PipeDefinition> definitions = new ArrayList<>();
        for (PipeType kind : List.of(PipeType.ITEM, PipeType.FLUID, PipeType.POWER)) {
            for (PipeMaterial material : materials) {
                definitions.add(new PipeDefinition(kind, material));
            }
        }
        return Collections.unmodifiableList(definitions);
    }

    private static DeferredItem<ItemBuildCraft> item(String unlocalizedName) {
        DeferredItem<ItemBuildCraft> item = REGISTERS.items.registerItem(RegistryNames.of(unlocalizedName),
                ItemBuildCraft::new, properties -> properties.overrideDescription("item." + unlocalizedName));
        CreativeTabBuildCraft.MACHINES.add(item);
        return item;
    }

    private static void registerPipe(PipeDefinition definition) {
        String name = definition.registryName();
        String translationKey = "item." + definition.unlocalizedName();
        BiFunction<PipeMaterial, BlockBehaviour.Properties, BlockPipe> factory = switch (definition.kind()) {
        case ITEM -> BlockPipeItems::new;
        case FLUID -> BlockPipeFluids::new;
        case POWER -> BlockPipePower::new;
        case STRUCTURE -> throw new IllegalArgumentException(definition.toString());
        };

        DeferredBlock<BlockPipe> block = REGISTERS.blocks.registerBlock(name,
                properties -> factory.apply(definition.material(), properties),
                properties -> BlockPipe.pipeProperties(properties).overrideDescription(translationKey));
        DeferredItem<ItemBlockBuildCraft> item = REGISTERS.items.registerItem(name,
                properties -> new ItemBlockBuildCraft(block.get(), properties),
                properties -> properties.overrideDescription(translationKey));
        CreativeTabBuildCraft.MACHINES.add(item);
        PIPE_BLOCKS.put(definition, block);
        PIPE_ITEMS.put(definition, item);
    }

    /** The registered pipe block for a table row. */
    public static BlockPipe getPipe(PipeType kind, PipeMaterial material) {
        DeferredBlock<BlockPipe> block = PIPE_BLOCKS.get(new PipeDefinition(kind, material));
        if (block == null)
            throw new IllegalArgumentException("No " + kind + " pipe of " + material + " is registered");
        return block.get();
    }

    public static DeferredItem<ItemBlockBuildCraft> getPipeItem(PipeDefinition definition) {
        return PIPE_ITEMS.get(definition);
    }

    public static void register(IEventBus modBus) {
        REGISTERS.register(modBus);
        modBus.addListener(RegisterCapabilitiesEvent.class, TransportRegistry::registerCapabilities);
    }

    /** docs/TRANSPORT.md 6.2: item pipes take pushes through the item capability ({@code TilePipe.getItemHandler}). */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        TileBuildCraft.registerItemHandler(event, pipeTile.get());
        // docs/TRANSPORT.md 6.2, 7.4: power pipes take pushes through the energy capability.
        event.registerBlockEntity(Capabilities.Energy.BLOCK, pipeTile.get(), TilePipe::getEnergyHandler);
    }
}
