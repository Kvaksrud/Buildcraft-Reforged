/**
 * Copyright (c) 2011-2014, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: the registration half of buildcraft/BuildCraftEnergy.java, on
 * DeferredRegister; numeric ids, Configuration and LanguageRegistry names removed. Oil and fuel are a FluidType (BCFluid),
 * a source and a flowing Fluid, a LiquidBlock and a bucket each; FluidContainerRegistry and BucketHandler are replaced by
 * the bucket's fluid capability and vanilla's BucketPickup. 4.2.2's engineBlock with metadata 0-2 becomes one BlockEngine,
 * ItemEngine and block entity type per engine, registered through engine().
 */
package buildcraftreforged.energy;

import buildcraftreforged.api.fuels.FuelDataMaps;
import buildcraftreforged.api.fuels.IronEngineFuel;
import buildcraftreforged.api.power.PowerEnergyHandler;
import buildcraftreforged.core.BuildCraftConfiguration;
import buildcraftreforged.core.CreativeTabBuildCraft;
import buildcraftreforged.core.ModuleRegisters;
import buildcraftreforged.core.RegistryNames;
import buildcraftreforged.core.TileBuildCraft;
import buildcraftreforged.core.fluids.BCFluid;
import buildcraftreforged.energy.gui.ContainerEngine;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidInteractionRegistry;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.transfer.fluid.BucketResourceHandler;

/**
 * Registry entries of the energy module, the registration half of 4.2.2's BuildCraftEnergy. Add entries here as the module
 * is ported, following the naming rules in {@link buildcraftreforged.core.CoreRegistry}.
 */
public final class EnergyRegistry {
    public static final ModuleRegisters REGISTERS = new ModuleRegisters();

    /** Every engine's block entity type, for the capabilities. Declared before any engine() field. */
    private static final List<DeferredHolder<BlockEntityType<?>, ? extends BlockEntityType<? extends TileEngine>>> ENGINE_TILES =
            new ArrayList<>();

    /** Every engine's block entity type, for the engine renderer (EnergyProxyClient). */
    public static List<DeferredHolder<BlockEntityType<?>, ? extends BlockEntityType<? extends TileEngine>>> engineTiles() {
        return Collections.unmodifiableList(ENGINE_TILES);
    }

    // Engines (#37, #38, #44) go here, below ENGINE_TILES.
    public static final EngineEntry<TileEngineWood> engineWood = engine("engineWood", "engine_wood", TileEngineWood::new);
    public static final EngineEntry<TileEngineStone> engineStone = engine("engineStone", "engine_stone", TileEngineStone::new);

    /** 4.2.2 GuiIds.ENGINE_STONE: the Stirling engine's ContainerEngine. */
    public static final DeferredHolder<MenuType<?>, MenuType<ContainerEngine>> engineStoneMenu = REGISTERS.menus.register(
            "engine_stone", () -> IMenuTypeExtension.create((containerId, inventory, extraData) -> new ContainerEngine(
                    EnergyRegistry.engineStoneMenu.get(), containerId, inventory,
                    ContainerEngine.clientTile(inventory, extraData.readBlockPos(), EnergyRegistry.engineStone))));

    // Oil and fuel (4.2.2 BuildCraftEnergy.preInit). fluidOil and fluidFuel are the source fluids; 4.2.2's
    // buildcraftFluidOil/buildcraftFluidFuel (the BCFluid) are the fluid types.
    public static final DeferredHolder<FluidType, BCFluid> buildcraftFluidOil = REGISTERS.fluidTypes.register("oil",
            () -> new BCFluid("oil", fluidTypeProperties().density(800).viscosity(1500)));
    public static final DeferredHolder<FluidType, BCFluid> buildcraftFluidFuel = REGISTERS.fluidTypes.register("fuel",
            () -> new BCFluid("fuel", fluidTypeProperties()));

    public static final DeferredHolder<Fluid, BuildcraftFlowingFluid.Source> fluidOil = REGISTERS.fluids.register("oil",
            () -> new BuildcraftFlowingFluid.Source(oilProperties()));
    public static final DeferredHolder<Fluid, BuildcraftFlowingFluid.Flowing> fluidOilFlowing = REGISTERS.fluids.register(
            "flowing_oil", () -> new BuildcraftFlowingFluid.Flowing(oilProperties()));
    public static final DeferredHolder<Fluid, BuildcraftFlowingFluid.Source> fluidFuel = REGISTERS.fluids.register("fuel",
            () -> new BuildcraftFlowingFluid.Source(fuelProperties()));
    public static final DeferredHolder<Fluid, BuildcraftFlowingFluid.Flowing> fluidFuelFlowing = REGISTERS.fluids.register(
            "flowing_fuel", () -> new BuildcraftFlowingFluid.Flowing(fuelProperties()));

    public static final DeferredBlock<BlockBuildcraftFluid> blockOil = fluidBlock("blockOil", MapColor.COLOR_BLACK,
            properties -> new BlockBuildcraftFluid(fluidOil.get(), properties)
                    .setFlammable(BuildCraftConfiguration.canOilBurn::get).setFlammability(0));
    public static final DeferredBlock<BlockBuildcraftFluid> blockFuel = fluidBlock("blockFuel", MapColor.COLOR_YELLOW,
            properties -> new BlockBuildcraftFluid(fluidFuel.get(), properties)
                    .setFlammable(true).setFlammability(5).setParticleColor(0.7F, 0.7F, 0.0F));

    public static final DeferredItem<ItemBucketBuildcraft> bucketOil = bucket("bucketOil", fluidOil);
    public static final DeferredItem<ItemBucketBuildcraft> bucketFuel = bucket("bucketFuel", fluidFuel);

    /** The drip under oil and fuel (4.2.2 EntityDropParticleFX), coloured per fluid. */
    public static final DeferredHolder<ParticleType<?>, ParticleType<ColorParticleOption>> dropParticle =
            REGISTERS.particleTypes.register("drop", () -> new ParticleType<ColorParticleOption>(false) {
                @Override
                public MapCodec<ColorParticleOption> codec() {
                    return ColorParticleOption.codec(this);
                }

                @Override
                public StreamCodec<? super RegistryFriendlyByteBuf, ColorParticleOption> streamCodec() {
                    return ColorParticleOption.streamCodec(this);
                }
            });

    private EnergyRegistry() {
    }

    /**
     * 4.2.2 gave both fluids Material.water: entities swim, drown and are put out in them, boats float, farmland is
     * hydrated, falls are broken. Bucket sounds are the vanilla ones (1.6.4 buckets were silent).
     */
    private static FluidType.Properties fluidTypeProperties() {
        return FluidType.Properties.create().canExtinguish(true).supportsBoating(true).canHydrate(true)
                .fallDistanceModifier(0F).sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY);
    }

    /** Forge 1.6.4 BlockFluidBase: tickRate = viscosity / 200 (oil 1500, fuel the default 1000). */
    private static BaseFlowingFluid.Properties oilProperties() {
        return new BaseFlowingFluid.Properties(buildcraftFluidOil, fluidOil, fluidOilFlowing).block(blockOil).bucket(bucketOil)
                .tickRate(1500 / 200);
    }

    private static BaseFlowingFluid.Properties fuelProperties() {
        return new BaseFlowingFluid.Properties(buildcraftFluidFuel, fluidFuel, fluidFuelFlowing).block(blockFuel)
                .bucket(bucketFuel).tickRate(1000 / 200);
    }

    /** BlockBuildcraftFluid with Material.water: the properties of vanilla's water block. */
    private static DeferredBlock<BlockBuildcraftFluid> fluidBlock(String unlocalizedName, MapColor mapColor,
            Function<BlockBehaviour.Properties, BlockBuildcraftFluid> factory) {
        return REGISTERS.blocks.registerBlock(RegistryNames.of(unlocalizedName), factory,
                properties -> properties.overrideDescription("tile." + unlocalizedName).mapColor(mapColor).replaceable()
                        .noCollision().strength(100.0F).pushReaction(PushReaction.POPPED).noLootTable().liquid()
                        .sound(SoundType.EMPTY));
    }

    /** An engine's block, item and block entity type. */
    public record EngineEntry<T extends TileEngine>(DeferredBlock<BlockEngine> block, DeferredItem<ItemEngine> item,
            DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> tile) {
    }

    /**
     * Registers an engine: a BlockEngine named after unlocalizedName (tile.engineWood → engine_wood) with
     * {@link BlockEngine#engineProperties}, its ItemEngine in the machines tab, and a block entity type named tileName
     * whose tiles get the Forge Energy capability (and the item capability, for a TileEngineWithInventory). The
     * engine still needs its blockstate, models, loot table and minecraft:mineable/pickaxe entry.
     */
    public static <T extends TileEngine> EngineEntry<T> engine(String unlocalizedName, String tileName,
            BlockEntityType.BlockEntitySupplier<T> factory) {
        String name = RegistryNames.of(unlocalizedName);
        DeferredBlock<BlockEngine> block = REGISTERS.blocks.registerBlock(name, properties -> new BlockEngine(properties, factory),
                properties -> BlockEngine.engineProperties(properties).overrideDescription("tile." + unlocalizedName));
        DeferredItem<ItemEngine> item = REGISTERS.items.registerItem(name, properties -> new ItemEngine(block.get(), properties),
                properties -> properties.overrideDescription("tile." + unlocalizedName));
        DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> tile = REGISTERS.blockEntityTypes.register(tileName,
                () -> new BlockEntityType<>(factory, block.get()));
        CreativeTabBuildCraft.MACHINES.add(item);
        ENGINE_TILES.add(tile);
        return new EngineEntry<>(block, item, tile);
    }

    private static <T extends TileEngine> void registerEngineCapabilities(RegisterCapabilitiesEvent event, BlockEntityType<T> type) {
        PowerEnergyHandler.registerEnergyHandler(event, type);
        TileBuildCraft.registerItemHandler(event, type);
    }

    private static DeferredItem<ItemBucketBuildcraft> bucket(String unlocalizedName, Supplier<? extends Fluid> fluid) {
        DeferredItem<ItemBucketBuildcraft> item = REGISTERS.items.registerItem(RegistryNames.of(unlocalizedName),
                properties -> new ItemBucketBuildcraft(fluid.get(), properties),
                properties -> properties.overrideDescription("item." + unlocalizedName));
        CreativeTabBuildCraft.MACHINES.add(item);
        return item;
    }

    public static void register(IEventBus modBus) {
        REGISTERS.register(modBus);

        // Iron Engine Fuels and Coolants (#43): the values are data (FuelDataMaps); the 4.2.2 config multipliers
        // scale the oil and fuel burning times when the engine looks them up.
        modBus.addListener(FuelDataMaps::register);
        IronEngineFuel.burningTimeMultipliers.put("buildcraftreforged:oil", BuildCraftConfiguration.fuelOilMultiplier::get);
        IronEngineFuel.burningTimeMultipliers.put("buildcraftreforged:fuel", BuildCraftConfiguration.fuelFuelMultiplier::get);
        modBus.addListener(RegisterCapabilitiesEvent.class, EnergyRegistry::registerCapabilities);
        modBus.addListener(FMLCommonSetupEvent.class, EnergyRegistry::commonSetup);
    }

    /** 4.2.2 FluidContainerRegistry.registerFluidContainer for the two buckets; the engines' energy and items. */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        for (DeferredHolder<BlockEntityType<?>, ? extends BlockEntityType<? extends TileEngine>> tile : ENGINE_TILES) {
            registerEngineCapabilities(event, tile.get());
        }
        event.registerItem(Capabilities.Fluid.ITEM, (stack, access) -> new BucketResourceHandler(access), bucketOil.get(),
                bucketFuel.get());
    }

    /**
     * Material.water in 4.2.2: lava hardens next to oil and fuel as next to water. NeoForge 26.3 checks these
     * interactions when the lava's neighbour changes, not when the lava is placed.
     */
    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            for (FluidType type : new FluidType[] {buildcraftFluidOil.get(), buildcraftFluidFuel.get()}) {
                FluidInteractionRegistry.addInteraction(NeoForgeMod.LAVA_TYPE.value(), new FluidInteractionRegistry.InteractionInformation(
                        type, fluidState -> fluidState.isSource() ? Blocks.OBSIDIAN.defaultBlockState()
                                : Blocks.COBBLESTONE.defaultBlockState()));
            }
        });
    }
}
