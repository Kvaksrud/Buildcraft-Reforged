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
import buildcraftreforged.core.ModuleRegisters;
import buildcraftreforged.core.RegistryNames;
import buildcraftreforged.core.TileBuildCraft;
import buildcraftreforged.silicon.gui.ContainerAssemblyTable;
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

    /** The JSON assembly recipes (AssemblyTableRecipe), type and serializer {@code buildcraftreforged:assembly}. */
    public static final DeferredHolder<RecipeType<?>, RecipeType<AssemblyTableRecipe>> assemblyRecipeType =
            REGISTERS.recipeTypes.register("assembly", id -> RecipeType.<AssemblyTableRecipe>simple(id));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<AssemblyTableRecipe>> assemblyRecipeSerializer =
            REGISTERS.recipeSerializers.register("assembly", () -> AssemblyTableRecipe.SERIALIZER);

    static {
        CreativeTabBuildCraft.MACHINES.add(laserBlockItem);
        CreativeTabBuildCraft.MACHINES.add(assemblyTableBlockItem);
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
    }
}
