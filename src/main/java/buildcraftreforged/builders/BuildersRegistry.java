/**
 * Copyright (c) SpaceToad, 2011-2012 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: the registration half of buildcraft/BuildCraftBuilders.java, on
 * DeferredRegister; numeric ids, Configuration and LanguageRegistry names removed.
 */

package buildcraftreforged.builders;

import buildcraftreforged.api.filler.FillerManager;
import buildcraftreforged.api.gates.ActionManager;
import buildcraftreforged.api.power.PowerEnergyHandler;
import buildcraftreforged.builders.filler.FillerRegistry;
import buildcraftreforged.builders.filler.pattern.PatternBox;
import buildcraftreforged.builders.filler.pattern.PatternClear;
import buildcraftreforged.builders.filler.pattern.PatternFill;
import buildcraftreforged.builders.filler.pattern.PatternFlatten;
import buildcraftreforged.builders.filler.pattern.PatternHorizon;
import buildcraftreforged.builders.filler.pattern.PatternPyramid;
import buildcraftreforged.builders.filler.pattern.PatternStairs;
import buildcraftreforged.builders.gui.ContainerFiller;
import buildcraftreforged.builders.triggers.BuildersActionProvider;
import buildcraftreforged.core.BlockBuildCraft;
import buildcraftreforged.core.CreativeTabBuildCraft;
import buildcraftreforged.core.ItemBlockBuildCraft;
import buildcraftreforged.core.ModuleRegisters;
import buildcraftreforged.core.RegistryNames;
import buildcraftreforged.core.TileBuildCraft;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Registry entries of the builders module, the registration half of 4.2.2's BuildCraftBuilders, following the naming
 * rules in {@link buildcraftreforged.core.CoreRegistry}.
 */
public final class BuildersRegistry {
    public static final ModuleRegisters REGISTERS = new ModuleRegisters();

    public static final DeferredBlock<BlockMarker> markerBlock = marker("markerBlock", BlockMarker::new);
    public static final DeferredItem<ItemBlockBuildCraft> markerBlockItem = blockItem("markerBlock", markerBlock);
    public static final DeferredBlock<BlockPathMarker> pathMarkerBlock = marker("pathMarkerBlock", BlockPathMarker::new);
    public static final DeferredItem<ItemBlockBuildCraft> pathMarkerBlockItem = blockItem("pathMarkerBlock", pathMarkerBlock);

    /** 4.2.2 GameRegistry.registerTileEntity(TileMarker.class, "Marker"). */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileMarker>> markerTile =
            REGISTERS.blockEntityTypes.register("marker", () -> new BlockEntityType<>(TileMarker::new, markerBlock.get()));
    /** 4.2.2 registered TilePathMarker as "net.minecraft.src.builders.TilePathMarker". */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TilePathMarker>> pathMarkerTile =
            REGISTERS.blockEntityTypes.register("path_marker", () -> new BlockEntityType<>(TilePathMarker::new, pathMarkerBlock.get()));

    /** BlockFiller's constructor: Material.iron (metal sound and colour, pickaxe), setHardness(5F). */
    public static final DeferredBlock<BlockFiller> fillerBlock = REGISTERS.blocks.registerBlock(RegistryNames.of("fillerBlock"),
            BlockFiller::new, properties -> BlockBuildCraft.defaultProperties(properties.overrideDescription("tile.fillerBlock")
                    .mapColor(MapColor.METAL).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final DeferredItem<ItemBlockBuildCraft> fillerBlockItem = blockItem("fillerBlock", fillerBlock);

    /** 4.2.2 GameRegistry.registerTileEntity(TileFiller.class, "Filler"). */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileFiller>> fillerTile =
            REGISTERS.blockEntityTypes.register("filler", () -> new BlockEntityType<>(TileFiller::new, fillerBlock.get()));

    /** 4.2.2 GuiIds.FILLER. */
    public static final DeferredHolder<MenuType<?>, MenuType<ContainerFiller>> fillerMenu =
            REGISTERS.menus.register("filler_block", () -> IMenuTypeExtension.create(ContainerFiller::new));

    static {
        // 4.2.2 BuildCraftBuilders.init: create the filler registry and add the patterns. Each pattern's gate action
        // is registered as filler_<tag> on the way (FillerRegistry.addPattern).
        FillerManager.registry = new FillerRegistry(REGISTERS.actions);
        FillerManager.registry.addPattern(PatternFill.INSTANCE);
        FillerManager.registry.addPattern(new PatternFlatten());
        FillerManager.registry.addPattern(new PatternHorizon());
        FillerManager.registry.addPattern(new PatternClear());
        FillerManager.registry.addPattern(new PatternBox());
        FillerManager.registry.addPattern(new PatternPyramid());
        FillerManager.registry.addPattern(new PatternStairs());
    }

    private BuildersRegistry() {
    }

    /**
     * BlockMarker's constructor: Material.circuits (no collision, washed off by water and broken by pistons, as a
     * torch), setLightValue(0.5F) (light 7), no hardness (breaks instantly).
     */
    private static <B extends Block> DeferredBlock<B> marker(String unlocalizedName,
            Function<BlockBehaviour.Properties, B> factory) {
        return REGISTERS.blocks.registerBlock(RegistryNames.of(unlocalizedName), factory,
                properties -> properties.overrideDescription("tile." + unlocalizedName).noCollision().instabreak()
                        .lightLevel(state -> 7).noOcclusion().pushReaction(PushReaction.POPPED));
    }

    private static DeferredItem<ItemBlockBuildCraft> blockItem(String unlocalizedName, Supplier<? extends Block> block) {
        DeferredItem<ItemBlockBuildCraft> item = REGISTERS.items.registerItem(RegistryNames.of(unlocalizedName),
                properties -> new ItemBlockBuildCraft(block.get(), properties),
                properties -> properties.overrideDescription("tile." + unlocalizedName));
        CreativeTabBuildCraft.MACHINES.add(item);
        return item;
    }

    /** 4.2.2's TileFiller was an IInventory and an IPowerReceptor. */
    static void registerCapabilities(RegisterCapabilitiesEvent event) {
        TileBuildCraft.registerItemHandler(event, fillerTile.get());
        PowerEnergyHandler.registerEnergyHandler(event, fillerTile.get());
    }

    public static void register(IEventBus modBus) {
        REGISTERS.register(modBus);
        modBus.addListener(BuildersRegistry::registerCapabilities);
        // 4.2.2 BuildCraftBuilders.init
        ActionManager.registerActionProvider(new BuildersActionProvider());
        // 4.2.2 BuildCraftBuilders.load (and the other modules' load): blueprint schematics (#76)
        BuildersSchematics.register();
    }
}
