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

import buildcraftreforged.core.CreativeTabBuildCraft;
import buildcraftreforged.core.ItemBlockBuildCraft;
import buildcraftreforged.core.ModuleRegisters;
import buildcraftreforged.core.RegistryNames;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
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

    public static void register(IEventBus modBus) {
        REGISTERS.register(modBus);
    }
}
