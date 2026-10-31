package buildcraftreforged.transport;

import buildcraftreforged.api.gates.ActionManager;
import buildcraftreforged.api.gates.IAction;
import buildcraftreforged.api.gates.ITrigger;
import buildcraftreforged.api.transport.IPipe;
import buildcraftreforged.core.triggers.BCAction;
import buildcraftreforged.core.triggers.BCTrigger;
import buildcraftreforged.core.utils.EnumColor;
import buildcraftreforged.transport.gui.ContainerGateInterface;
import buildcraftreforged.transport.triggers.ActionEnergyPulser;
import buildcraftreforged.transport.triggers.ActionExtractionPreset;
import buildcraftreforged.transport.triggers.ActionPipeColor;
import buildcraftreforged.transport.triggers.ActionPipeDirection;
import buildcraftreforged.transport.triggers.ActionPowerLimiter;
import buildcraftreforged.transport.triggers.ActionSignalOutput;
import buildcraftreforged.transport.triggers.ActionSingleEnergyPulse;
import buildcraftreforged.transport.triggers.TriggerPipeContents;
import buildcraftreforged.transport.triggers.TriggerPipeSignal;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import buildcraftreforged.api.transport.IPipeTile.PipeType;
import buildcraftreforged.core.CreativeTabBuildCraft;
import buildcraftreforged.core.ItemBlockBuildCraft;
import buildcraftreforged.core.ItemBuildCraft;
import buildcraftreforged.core.ModuleRegisters;
import buildcraftreforged.core.RegistryNames;
import buildcraftreforged.core.TileBuildCraft;
import buildcraftreforged.transport.module.ContainerFilteredColourTag;
import buildcraftreforged.transport.module.ContainerFilteredExtractor;
import buildcraftreforged.transport.module.ContainerSorter;
import buildcraftreforged.transport.module.ItemPipeModule;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import org.jspecify.annotations.Nullable;

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

    private static final Map<PipeModuleType, DeferredItem<ItemPipeModule>> MODULE_ITEMS = new EnumMap<>(PipeModuleType.class);

    /** The module items (docs/TRANSPORT.md 8.1). #56 and #57 add theirs with {@link #module}. */
    public static final DeferredItem<ItemPipeModule> extractorModule = module(PipeModuleType.EXTRACTOR, "moduleExtractor");
    public static final DeferredItem<ItemPipeModule> filteredExtractorModule =
            module(PipeModuleType.FILTERED_EXTRACTOR, "moduleFilteredExtractor");
    public static final DeferredItem<ItemPipeModule> directorModule = module(PipeModuleType.DIRECTOR, "moduleDirector");
    public static final DeferredItem<ItemPipeModule> sorterModule = module(PipeModuleType.SORTER, "moduleSorter");
    public static final DeferredItem<ItemPipeModule> pickupModule = module(PipeModuleType.PICKUP, "modulePickup");
    public static final DeferredItem<ItemPipeModule> voidModule = module(PipeModuleType.VOID, "moduleVoid");
    public static final DeferredItem<ItemPipeModule> colourTagModule = module(PipeModuleType.COLOUR_TAG, "moduleColourTag");
    public static final DeferredItem<ItemPipeModule> colourSorterModule =
            module(PipeModuleType.COLOUR_SORTER, "moduleColourSorter");
    public static final DeferredItem<ItemPipeModule> filteredColourTagModule =
            module(PipeModuleType.FILTERED_COLOUR_TAG, "moduleFilteredColourTag");
    public static final DeferredItem<ItemPipeModule> isolatorModule = module(PipeModuleType.ISOLATOR, "moduleIsolator");

    /** A module item's settings, kept when the module is taken off a pipe (Q18). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CustomData>> moduleSettings =
            REGISTERS.dataComponents.registerComponentType("module_settings",
                    builder -> builder.persistent(CustomData.CODEC).networkSynchronized(CustomData.STREAM_CODEC));

    /** 4.2.2 {@code GuiIds.PIPE_EMERALD_ITEM}. */
    public static final DeferredHolder<MenuType<?>, MenuType<ContainerFilteredExtractor>> filteredExtractorMenu =
            REGISTERS.menus.register("filtered_extractor", () -> IMenuTypeExtension.create(ContainerFilteredExtractor::new));
    /** 4.2.2 {@code GuiIds.PIPE_DIAMOND}. */
    public static final DeferredHolder<MenuType<?>, MenuType<ContainerSorter>> sorterMenu =
            REGISTERS.menus.register("sorter", () -> IMenuTypeExtension.create(ContainerSorter::new));
    /** 4.2.2 {@code GuiIds.PIPE_LOGEMERALD_ITEM}. */
    public static final DeferredHolder<MenuType<?>, MenuType<ContainerFilteredColourTag>> filteredColourTagMenu =
            REGISTERS.menus.register("filtered_colour_tag", () -> IMenuTypeExtension.create(ContainerFilteredColourTag::new));

    /** 4.2.2 registered TileGenericPipe as one tile class for every pipe; here one type is valid for every pipe block. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TilePipe>> pipeTile = REGISTERS.blockEntityTypes
            .register("pipe", () -> new BlockEntityType<>(TilePipe::new,
                    PIPE_BLOCKS.values().stream().map(DeferredBlock::get).collect(Collectors.toUnmodifiableSet())));

    // Gates and pipe wire (#60), 4.2.2 BuildCraftTransport: items, the gate's data component, the gate menu, and the
    // pipe triggers and actions, named after their 4.2.2 fields.

    public static final DeferredItem<ItemPipeWire> redPipeWire = pipeWire("redPipeWire", IPipe.WireColor.Red);
    public static final DeferredItem<ItemPipeWire> bluePipeWire = pipeWire("bluePipeWire", IPipe.WireColor.Blue);
    public static final DeferredItem<ItemPipeWire> greenPipeWire = pipeWire("greenPipeWire", IPipe.WireColor.Green);
    public static final DeferredItem<ItemPipeWire> yellowPipeWire = pipeWire("yellowPipeWire", IPipe.WireColor.Yellow);

    private static final Map<String, DeferredItem<ItemGate>> GATE_ITEMS = new LinkedHashMap<>();

    static {
        for (boolean autarchic : new boolean[] {false, true}) {
            for (Gate.GateKind kind : Gate.GateKind.values()) {
                if (kind != Gate.GateKind.None)
                    registerGate(kind, autarchic);
            }
        }
    }

    /** The programming a gate item carries (new; 4.2.2 lost it when the gate came off). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GateSettings>> gateSettings =
            REGISTERS.dataComponents.registerComponentType("gate_settings",
                    builder -> builder.persistent(GateSettings.CODEC).networkSynchronized(GateSettings.STREAM_CODEC));

    public static final DeferredHolder<MenuType<?>, MenuType<ContainerGateInterface>> gateMenu =
            REGISTERS.menus.register("gate", () -> IMenuTypeExtension.create(ContainerGateInterface::new));

    // Facades and plugs (#61), 4.2.2 BuildCraftTransport facadeItem and plugItem.

    /** 4.2.2 {@code pipeFacade}: one item; the block it shows is its {@link #facadeState} component. */
    public static final DeferredItem<ItemFacade> facadeItem = REGISTERS.items.registerItem(RegistryNames.of("pipeFacade"),
            ItemFacade::new, properties -> properties.overrideDescription("item.Facade"));
    /** 4.2.2 {@code pipePlug}. */
    public static final DeferredItem<ItemPlug> plugItem = REGISTERS.items.registerItem(RegistryNames.of("pipePlug"),
            ItemPlug::new, properties -> properties.overrideDescription("item.PipePlug"));

    static {
        CreativeTabBuildCraft.FACADES.addStacks(ItemFacade::getAllFacades);
        CreativeTabBuildCraft.MACHINES.add(plugItem);
    }

    /** The block state a facade shows (4.2.2: the "id" and "meta" NBT tags). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlockState>> facadeState =
            REGISTERS.dataComponents.registerComponentType("facade",
                    builder -> builder.persistent(BlockState.CODEC)
                            .networkSynchronized(ByteBufCodecs.idMapper(Block.BLOCK_STATE_REGISTRY)));

    /** 4.2.2 {@code FacadeRecipe}, the facade turning helper for logs. */
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<ItemFacade.FacadeRecipe>> facadeRecipeSerializer =
            REGISTERS.recipeSerializers.register("facade_rotation", () -> ItemFacade.FacadeRecipe.SERIALIZER);

    public static final DeferredHolder<ITrigger, BCTrigger> triggerPipeEmpty =
            trigger("triggerPipeEmpty", () -> new TriggerPipeContents(TriggerPipeContents.Kind.Empty));
    public static final DeferredHolder<ITrigger, BCTrigger> triggerPipeItems =
            trigger("triggerPipeItems", () -> new TriggerPipeContents(TriggerPipeContents.Kind.ContainsItems));
    public static final DeferredHolder<ITrigger, BCTrigger> triggerPipeFluids =
            trigger("triggerPipeFluids", () -> new TriggerPipeContents(TriggerPipeContents.Kind.ContainsFluids));
    public static final DeferredHolder<ITrigger, BCTrigger> triggerPipeContainsEnergy =
            trigger("triggerPipeContainsEnergy", () -> new TriggerPipeContents(TriggerPipeContents.Kind.ContainsEnergy));
    public static final DeferredHolder<ITrigger, BCTrigger> triggerPipeRequestsEnergy =
            trigger("triggerPipeRequestsEnergy", () -> new TriggerPipeContents(TriggerPipeContents.Kind.RequestsEnergy));
    public static final DeferredHolder<ITrigger, BCTrigger> triggerPipeTooMuchEnergy =
            trigger("triggerPipeTooMuchEnergy", () -> new TriggerPipeContents(TriggerPipeContents.Kind.TooMuchEnergy));
    public static final DeferredHolder<ITrigger, BCTrigger> triggerRedSignalActive =
            trigger("triggerRedSignalActive", () -> new TriggerPipeSignal(true, IPipe.WireColor.Red));
    public static final DeferredHolder<ITrigger, BCTrigger> triggerRedSignalInactive =
            trigger("triggerRedSignalInactive", () -> new TriggerPipeSignal(false, IPipe.WireColor.Red));
    public static final DeferredHolder<ITrigger, BCTrigger> triggerBlueSignalActive =
            trigger("triggerBlueSignalActive", () -> new TriggerPipeSignal(true, IPipe.WireColor.Blue));
    public static final DeferredHolder<ITrigger, BCTrigger> triggerBlueSignalInactive =
            trigger("triggerBlueSignalInactive", () -> new TriggerPipeSignal(false, IPipe.WireColor.Blue));
    public static final DeferredHolder<ITrigger, BCTrigger> triggerGreenSignalActive =
            trigger("triggerGreenSignalActive", () -> new TriggerPipeSignal(true, IPipe.WireColor.Green));
    public static final DeferredHolder<ITrigger, BCTrigger> triggerGreenSignalInactive =
            trigger("triggerGreenSignalInactive", () -> new TriggerPipeSignal(false, IPipe.WireColor.Green));
    public static final DeferredHolder<ITrigger, BCTrigger> triggerYellowSignalActive =
            trigger("triggerYellowSignalActive", () -> new TriggerPipeSignal(true, IPipe.WireColor.Yellow));
    public static final DeferredHolder<ITrigger, BCTrigger> triggerYellowSignalInactive =
            trigger("triggerYellowSignalInactive", () -> new TriggerPipeSignal(false, IPipe.WireColor.Yellow));

    public static final DeferredHolder<IAction, BCAction> actionRedSignal =
            action("actionRedSignal", () -> new ActionSignalOutput(IPipe.WireColor.Red));
    public static final DeferredHolder<IAction, BCAction> actionBlueSignal =
            action("actionBlueSignal", () -> new ActionSignalOutput(IPipe.WireColor.Blue));
    public static final DeferredHolder<IAction, BCAction> actionGreenSignal =
            action("actionGreenSignal", () -> new ActionSignalOutput(IPipe.WireColor.Green));
    public static final DeferredHolder<IAction, BCAction> actionYellowSignal =
            action("actionYellowSignal", () -> new ActionSignalOutput(IPipe.WireColor.Yellow));
    public static final DeferredHolder<IAction, BCAction> actionEnergyPulser =
            action("actionEnergyPulser", ActionEnergyPulser::new);
    public static final DeferredHolder<IAction, BCAction> actionSingleEnergyPulse =
            action("actionSingleEnergyPulse", ActionSingleEnergyPulse::new);
    /** 4.2.2 arrays, by EnumColor, Direction and PowerMode ordinal: {@code action_pipe_color_red} and so on. */
    public static final List<DeferredHolder<IAction, BCAction>> actionPipeColor = actions("actionPipeColor",
            EnumColor.VALUES, color -> color.name(), ActionPipeColor::new);
    public static final List<DeferredHolder<IAction, BCAction>> actionPipeDirection = actions("actionPipeDirection",
            Direction.values(), direction -> direction.name(), ActionPipeDirection::new);
    public static final List<DeferredHolder<IAction, BCAction>> actionPowerLimiter = actions("actionPowerLimiter",
            PowerMode.VALUES, mode -> mode.name(), ActionPowerLimiter::new);
    public static final DeferredHolder<IAction, BCAction> actionExtractionPresetRed =
            action("actionExtractionPresetRed", () -> new ActionExtractionPreset(EnumColor.RED));
    public static final DeferredHolder<IAction, BCAction> actionExtractionPresetBlue =
            action("actionExtractionPresetBlue", () -> new ActionExtractionPreset(EnumColor.BLUE));
    public static final DeferredHolder<IAction, BCAction> actionExtractionPresetGreen =
            action("actionExtractionPresetGreen", () -> new ActionExtractionPreset(EnumColor.GREEN));
    public static final DeferredHolder<IAction, BCAction> actionExtractionPresetYellow =
            action("actionExtractionPresetYellow", () -> new ActionExtractionPreset(EnumColor.YELLOW));

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

    private static DeferredItem<ItemPipeModule> module(PipeModuleType type, String unlocalizedName) {
        DeferredItem<ItemPipeModule> item = REGISTERS.items.registerItem(RegistryNames.of(unlocalizedName),
                properties -> new ItemPipeModule(type, properties),
                properties -> properties.overrideDescription("item." + unlocalizedName));
        CreativeTabBuildCraft.MACHINES.add(item);
        MODULE_ITEMS.put(type, item);
        return item;
    }

    /** The item of a module type, or null for a type that has none yet. */
    public static @Nullable Item getModuleItem(PipeModuleType type) {
        DeferredItem<ItemPipeModule> item = MODULE_ITEMS.get(type);
        return item == null ? null : item.get();
    }

    /** The module's name (its item's), also the title of the module's GUI. */
    public static Component moduleName(PipeModuleType type) {
        Item item = getModuleItem(type);
        return item == null ? Component.literal(type.getSerializedName()) : item.getName(new ItemStack(item));
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

    private static DeferredItem<ItemPipeWire> pipeWire(String unlocalizedName, IPipe.WireColor color) {
        DeferredItem<ItemPipeWire> item = REGISTERS.items.registerItem(RegistryNames.of(unlocalizedName),
                properties -> new ItemPipeWire(color, properties),
                properties -> properties.overrideDescription("item." + unlocalizedName));
        CreativeTabBuildCraft.MACHINES.add(item);
        return item;
    }

    private static void registerGate(Gate.GateKind kind, boolean autarchic) {
        DeferredItem<ItemGate> item = REGISTERS.items.registerItem(ItemGate.registryName(kind, autarchic),
                properties -> new ItemGate(kind, autarchic, properties),
                properties -> properties.overrideDescription(ItemGate.translationKey(kind, autarchic)));
        CreativeTabBuildCraft.MACHINES.add(item);
        GATE_ITEMS.put(ItemGate.registryName(kind, autarchic), item);
    }

    /** The gate item of a kind and series. */
    public static ItemGate getGateItem(Gate.GateKind kind, boolean autarchic) {
        DeferredItem<ItemGate> item = GATE_ITEMS.get(ItemGate.registryName(kind, autarchic));
        if (item == null)
            throw new IllegalArgumentException("No gate item for " + kind);
        return item.get();
    }

    public static ItemPipeWire getPipeWire(IPipe.WireColor color) {
        return switch (color) {
        case Red -> redPipeWire.get();
        case Blue -> bluePipeWire.get();
        case Green -> greenPipeWire.get();
        case Yellow -> yellowPipeWire.get();
        };
    }

    /** Triggers and actions are named after their 4.2.2 field: triggerPipeEmpty -> trigger_pipe_empty. */
    private static DeferredHolder<ITrigger, BCTrigger> trigger(String fieldName, Supplier<BCTrigger> factory) {
        return REGISTERS.triggers.register(RegistryNames.of(fieldName), factory);
    }

    private static DeferredHolder<IAction, BCAction> action(String fieldName, Supplier<BCAction> factory) {
        return REGISTERS.actions.register(RegistryNames.of(fieldName), factory);
    }

    private static <E> List<DeferredHolder<IAction, BCAction>> actions(String fieldName, E[] values,
            Function<E, String> name, Function<E, BCAction> factory) {
        List<DeferredHolder<IAction, BCAction>> holders = new ArrayList<>();
        for (E value : values) {
            holders.add(REGISTERS.actions.register(
                    RegistryNames.of(fieldName) + "_" + name.apply(value).toLowerCase(Locale.ENGLISH),
                    () -> factory.apply(value)));
        }
        return Collections.unmodifiableList(holders);
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
        // 4.2.2 BuildCraftTransport.init
        ActionManager.registerTriggerProvider(new PipeTriggerProvider());
        modBus.addListener(RegisterCapabilitiesEvent.class, TransportRegistry::registerCapabilities);
    }

    /** docs/TRANSPORT.md 6.2: item pipes take pushes through the item capability ({@code TilePipe.getItemHandler}). */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        TileBuildCraft.registerItemHandler(event, pipeTile.get());
        // docs/TRANSPORT.md 6.2, 7.4: power pipes take pushes through the energy capability.
        event.registerBlockEntity(Capabilities.Energy.BLOCK, pipeTile.get(), TilePipe::getEnergyHandler);
        // docs/TRANSPORT.md 6.2, 7.2: fluid pipes take pushes through the fluid capability.
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, pipeTile.get(), TilePipe::getFluidHandler);
    }
}
