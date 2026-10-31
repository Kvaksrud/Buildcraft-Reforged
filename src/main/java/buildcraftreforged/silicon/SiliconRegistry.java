/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: the registration half of buildcraft/BuildCraftSilicon.java, on
 * DeferredRegister; numeric ids, Configuration and LanguageRegistry names removed.
 */
package buildcraftreforged.silicon;

import buildcraftreforged.api.power.PowerEnergyHandler;
import buildcraftreforged.core.CreativeTabBuildCraft;
import buildcraftreforged.core.ItemBlockBuildCraft;
import buildcraftreforged.core.ItemRedstoneChipset;
import buildcraftreforged.core.ModuleRegisters;
import buildcraftreforged.core.RegistryNames;
import buildcraftreforged.core.TileBuildCraft;
import buildcraftreforged.silicon.gui.ContainerAdvancedCraftingTable;
import buildcraftreforged.silicon.gui.ContainerAssemblyTable;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Registry entries of the silicon module, the registration half of 4.2.2's BuildCraftSilicon. Add entries here as the module
 * is ported, following the naming rules in {@link buildcraftreforged.core.CoreRegistry}.
 */
public final class SiliconRegistry {
    public static final ModuleRegisters REGISTERS = new ModuleRegisters();

    /**
     * 4.2.2 BlockLaser: Material.iron (metal, needs a pickaxe to drop, as 1.6.4's setRequiresTool), setHardness(10F)
     * (destroy time and explosion resistance 10), not an opaque cube.
     */
    public static final DeferredBlock<BlockLaser> laserBlock = REGISTERS.blocks.registerBlock(
            RegistryNames.of("laserBlock"), BlockLaser::new,
            properties -> properties.overrideDescription("tile.laserBlock").mapColor(MapColor.METAL).sound(SoundType.METAL)
                    .strength(10F).requiresCorrectToolForDrops().noOcclusion());
    public static final DeferredItem<ItemBlockBuildCraft> laserBlockItem = REGISTERS.items.registerItem(
            RegistryNames.of("laserBlock"), properties -> new ItemBlockBuildCraft(laserBlock.get(), properties),
            properties -> properties.overrideDescription("tile.laserBlock"));

    /** 4.2.2 registered TileLaser as "net.minecraft.src.buildcraft.factory.TileLaser". */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileLaser>> laserTile =
            REGISTERS.blockEntityTypes.register("laser", () -> new BlockEntityType<>(TileLaser::new, laserBlock.get()));

    /**
     * 4.2.2 BlockLaserTable meta 0 with ItemLaserTable ("tile.assemblyTableBlock"): Material.iron, setHardness(10F), not
     * an opaque cube, as the laser.
     */
    public static final DeferredBlock<BlockLaserTable> assemblyTableBlock = REGISTERS.blocks.registerBlock(
            RegistryNames.of("assemblyTableBlock"), properties -> new BlockLaserTable(properties, TileAssemblyTable::new),
            properties -> properties.overrideDescription("tile.assemblyTableBlock").mapColor(MapColor.METAL)
                    .sound(SoundType.METAL).strength(10F).requiresCorrectToolForDrops().noOcclusion());
    public static final DeferredItem<ItemBlockBuildCraft> assemblyTableBlockItem = REGISTERS.items.registerItem(
            RegistryNames.of("assemblyTableBlock"), properties -> new ItemBlockBuildCraft(assemblyTableBlock.get(), properties),
            properties -> properties.overrideDescription("tile.assemblyTableBlock"));

    /** 4.2.2 registered TileAssemblyTable as "net.minecraft.src.buildcraft.factory.TileAssemblyTable". */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileAssemblyTable>> assemblyTableTile =
            REGISTERS.blockEntityTypes.register("assembly_table",
                    () -> new BlockEntityType<>(TileAssemblyTable::new, assemblyTableBlock.get()));

    /** 4.2.2 GuiHandler GUI id 0. */
    public static final DeferredHolder<MenuType<?>, MenuType<ContainerAssemblyTable>> assemblyTableMenu =
            REGISTERS.menus.register(RegistryNames.of("assemblyTableBlock"),
                    () -> IMenuTypeExtension.create(ContainerAssemblyTable::new));

    /**
     * 4.2.2 BlockLaserTable meta 1 with ItemLaserTable ("tile.assemblyWorkbenchBlock"), the Advanced Crafting Table:
     * as the assembly table.
     */
    public static final DeferredBlock<BlockLaserTable> advancedCraftingTableBlock = REGISTERS.blocks.registerBlock(
            RegistryNames.of("assemblyWorkbenchBlock"),
            properties -> new BlockLaserTable(properties, TileAdvancedCraftingTable::new),
            properties -> properties.overrideDescription("tile.assemblyWorkbenchBlock").mapColor(MapColor.METAL)
                    .sound(SoundType.METAL).strength(10F).requiresCorrectToolForDrops().noOcclusion());
    public static final DeferredItem<ItemBlockBuildCraft> advancedCraftingTableBlockItem = REGISTERS.items.registerItem(
            RegistryNames.of("assemblyWorkbenchBlock"),
            properties -> new ItemBlockBuildCraft(advancedCraftingTableBlock.get(), properties),
            properties -> properties.overrideDescription("tile.assemblyWorkbenchBlock"));

    /** 4.2.2 registered TileAdvancedCraftingTable as "net.minecraft.src.buildcraft.factory.TileAssemblyAdvancedWorkbench". */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileAdvancedCraftingTable>> advancedCraftingTableTile =
            REGISTERS.blockEntityTypes.register("advanced_crafting_table",
                    () -> new BlockEntityType<>(TileAdvancedCraftingTable::new, advancedCraftingTableBlock.get()));

    /** 4.2.2 GuiHandler GUI id 0 on block metadata 1. */
    public static final DeferredHolder<MenuType<?>, MenuType<ContainerAdvancedCraftingTable>> advancedCraftingTableMenu =
            REGISTERS.menus.register(RegistryNames.of("assemblyWorkbenchBlock"),
                    () -> IMenuTypeExtension.create(ContainerAdvancedCraftingTable::new));

    /** The JSON assembly recipes (AssemblyTableRecipe), type and serializer {@code buildcraftreforged:assembly}. */
    public static final DeferredHolder<RecipeType<?>, RecipeType<AssemblyTableRecipe>> assemblyRecipeType =
            REGISTERS.recipeTypes.register("assembly", id -> RecipeType.<AssemblyTableRecipe>simple(id));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<AssemblyTableRecipe>> assemblyRecipeSerializer =
            REGISTERS.recipeSerializers.register("assembly", () -> AssemblyTableRecipe.SERIALIZER);

    /**
     * 4.2.2 ItemRedstoneChipset ("item.redstoneChipset", damage 0 to 4): one item per chipset, by 4.2.2 damage value
     * (Redstone, Iron, Golden, Diamond, Pulsating).
     */
    public static final List<DeferredItem<ItemRedstoneChipset>> redstoneChipset = IntStream.range(0, 5)
            .mapToObj(chipset -> REGISTERS.items.registerItem(ItemRedstoneChipset.registryName(chipset),
                    properties -> new ItemRedstoneChipset(chipset, properties),
                    properties -> properties.overrideDescription(ItemRedstoneChipset.translationKey(chipset))))
            .toList();

    static {
        CreativeTabBuildCraft.MACHINES.add(laserBlockItem);
        CreativeTabBuildCraft.MACHINES.add(assemblyTableBlockItem);
        CreativeTabBuildCraft.MACHINES.add(advancedCraftingTableBlockItem);
        redstoneChipset.forEach(CreativeTabBuildCraft.MACHINES::add);
    }

    /** The chipset with 4.2.2 damage value chipset: 0 Redstone, 1 Iron, 2 Golden, 3 Diamond, 4 Pulsating. */
    public static ItemRedstoneChipset getChipset(int chipset) {
        return redstoneChipset.get(chipset).get();
    }

    private SiliconRegistry() {
    }

    public static void register(IEventBus modBus) {
        REGISTERS.register(modBus);
        modBus.addListener(SiliconRegistry::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        PowerEnergyHandler.registerEnergyHandler(event, laserTile.get());
        TileBuildCraft.registerItemHandler(event, assemblyTableTile.get());
        TileBuildCraft.registerItemHandler(event, advancedCraftingTableTile.get());
    }
}
