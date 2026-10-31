/**
 * Copyright (c) 2011-2014, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: the registration half of buildcraft/BuildCraftFactory.java, on
 * DeferredRegister; numeric ids, Configuration and LanguageRegistry names removed; the fluid handlers are block
 * capabilities.
 */
package buildcraftreforged.factory;

import buildcraftreforged.api.power.PowerEnergyHandler;
import buildcraftreforged.core.BlockBuildCraft;
import buildcraftreforged.core.CreativeTabBuildCraft;
import buildcraftreforged.core.ItemBlockBuildCraft;
import buildcraftreforged.core.ModuleRegisters;
import buildcraftreforged.core.RegistryNames;
import buildcraftreforged.factory.gui.ContainerRefinery;
import java.util.function.Supplier;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Registry entries of the factory module, the registration half of 4.2.2's BuildCraftFactory. Add entries here as the module
 * is ported, following the naming rules in {@link buildcraftreforged.core.CoreRegistry}.
 */
public final class FactoryRegistry {
    public static final ModuleRegisters REGISTERS = new ModuleRegisters();

    /**
     * BlockTank's constructor: Material.glass (glass sound, the hat instrument, no occlusion, not a redstone conductor,
     * no suffocation, no spawns), setHardness(0.5F).
     */
    public static final DeferredBlock<BlockTank> tankBlock = REGISTERS.blocks.registerBlock(RegistryNames.of("tankBlock"),
            BlockTank::new, properties -> properties.overrideDescription("tile.tankBlock").instrument(NoteBlockInstrument.HAT)
                    .strength(0.5F).sound(SoundType.GLASS).noOcclusion().isValidSpawn((state, world, pos, type) -> false)
                    .isRedstoneConductor((state, world, pos) -> false).isSuffocating((state, world, pos) -> false));
    public static final DeferredItem<ItemBlockBuildCraft> tankBlockItem = blockItem("tankBlock", tankBlock);

    /** 4.2.2 registered TileTank as "net.minecraft.src.buildcraft.factory.TileTank". */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileTank>> tankTile =
            REGISTERS.blockEntityTypes.register("tank", () -> new BlockEntityType<>(TileTank::new, tankBlock.get()));

    /** BlockRefinery's constructor: Material.iron (metal sound and colour, pickaxe), setHardness(5F); not opaque. */
    public static final DeferredBlock<BlockRefinery> refineryBlock = REGISTERS.blocks.registerBlock(
            RegistryNames.of("refineryBlock"), BlockRefinery::new,
            properties -> BlockBuildCraft.defaultProperties(properties.overrideDescription("tile.refineryBlock")
                    .mapColor(MapColor.METAL).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));
    public static final DeferredItem<ItemBlockBuildCraft> refineryBlockItem = blockItem("refineryBlock", refineryBlock);

    /** 4.2.2 registered TileRefinery as "net.minecraft.src.buildcraft.factory.Refinery". */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileRefinery>> refineryTile =
            REGISTERS.blockEntityTypes.register("refinery", () -> new BlockEntityType<>(TileRefinery::new, refineryBlock.get()));

    /** 4.2.2 GuiIds.REFINERY. */
    public static final DeferredHolder<MenuType<?>, MenuType<ContainerRefinery>> refineryMenu =
            REGISTERS.menus.register("refinery", () -> IMenuTypeExtension.create(ContainerRefinery::new));

    /** The JSON refinery recipes (RefineryRecipe), type and serializer {@code buildcraftreforged:refinery}. */
    public static final DeferredHolder<RecipeType<?>, RecipeType<RefineryRecipe>> refineryRecipeType =
            REGISTERS.recipeTypes.register("refinery", id -> RecipeType.<RefineryRecipe>simple(id));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<RefineryRecipe>> refineryRecipeSerializer =
            REGISTERS.recipeSerializers.register("refinery", () -> RefineryRecipe.SERIALIZER);

    private FactoryRegistry() {
    }

    private static DeferredItem<ItemBlockBuildCraft> blockItem(String unlocalizedName, Supplier<? extends Block> block) {
        DeferredItem<ItemBlockBuildCraft> item = REGISTERS.items.registerItem(RegistryNames.of(unlocalizedName),
                properties -> new ItemBlockBuildCraft(block.get(), properties),
                properties -> properties.overrideDescription("tile." + unlocalizedName));
        CreativeTabBuildCraft.MACHINES.add(item);
        return item;
    }

    /** 4.2.2's machines implemented IFluidHandler; now each exposes a fluid capability. */
    static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, tankTile.get(), (tile, side) -> tile);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, refineryTile.get(), (tile, side) -> tile);
        PowerEnergyHandler.registerEnergyHandler(event, refineryTile.get());
    }

    public static void register(IEventBus modBus) {
        REGISTERS.register(modBus);
        modBus.addListener(FactoryRegistry::registerCapabilities);
    }
}
