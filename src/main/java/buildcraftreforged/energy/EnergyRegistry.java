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
 * the bucket's fluid capability and vanilla's BucketPickup.
 */
package buildcraftreforged.energy;

import buildcraftreforged.core.BuildCraftConfiguration;
import buildcraftreforged.core.CreativeTabBuildCraft;
import buildcraftreforged.core.ModuleRegisters;
import buildcraftreforged.core.RegistryNames;
import buildcraftreforged.core.fluids.BCFluid;
import com.mojang.serialization.MapCodec;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
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

    private static DeferredItem<ItemBucketBuildcraft> bucket(String unlocalizedName, Supplier<? extends Fluid> fluid) {
        DeferredItem<ItemBucketBuildcraft> item = REGISTERS.items.registerItem(RegistryNames.of(unlocalizedName),
                properties -> new ItemBucketBuildcraft(fluid.get(), properties),
                properties -> properties.overrideDescription("item." + unlocalizedName));
        CreativeTabBuildCraft.MACHINES.add(item);
        return item;
    }

    public static void register(IEventBus modBus) {
        REGISTERS.register(modBus);
        modBus.addListener(RegisterCapabilitiesEvent.class, EnergyRegistry::registerCapabilities);
        modBus.addListener(FMLCommonSetupEvent.class, EnergyRegistry::commonSetup);
    }

    /** 4.2.2 FluidContainerRegistry.registerFluidContainer for the two buckets. */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
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
