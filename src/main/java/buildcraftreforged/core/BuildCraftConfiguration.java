/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: a ModConfigSpec pair (common and client) instead of a Forge Configuration;
 * holds the option reads of buildcraft/BuildCraftCore, BuildCraftEnergy, BuildCraftFactory, BuildCraftBuilders and
 * BuildCraftTransport; id sections, the version stamp and the dropped options removed (docs/PORTING-NOTES.md).
 */

package buildcraftreforged.core;

import buildcraftreforged.core.utils.ConfigUtils;
import java.util.List;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The 4.2.2 options under their 4.2.2 names. 4.2.2 kept them all in {@code general}; here the dotted name is the TOML
 * path, so {@code mining.cost.multipler} is key {@code multipler} in table {@code [mining.cost]}. Field names are the
 * 4.2.2 static fields each option was read into; call {@code .get()} where 4.2.2 used the field.
 */
public class BuildCraftConfiguration {

    public static final ModConfigSpec COMMON_SPEC;
    public static final ModConfigSpec CLIENT_SPEC;

    // BuildCraftCore
    public static final ModConfigSpec.BooleanValue dropBrokenBlocks;
    public static final ModConfigSpec.IntValue itemLifespan;
    public static final ModConfigSpec.IntValue updateFactor;
    public static final ModConfigSpec.IntValue longUpdateFactor;
    public static final ModConfigSpec.BooleanValue modifyWorld;
    public static final ModConfigSpec.BooleanValue consumeWaterSources;
    public static final ModConfigSpec.BooleanValue colorBlindMode;

    // BuildCraftEnergy
    public static final ModConfigSpec.BooleanValue canOilBurn;
    public static final ModConfigSpec.DoubleValue oilWellScalar;
    public static final ModConfigSpec.DoubleValue fuelOilMultiplier;
    public static final ModConfigSpec.DoubleValue fuelFuelMultiplier;
    public static final ModConfigSpec.BooleanValue spawnOilSprings;
    public static final ModConfigSpec.BooleanValue netherIgnition;

    // BuildCraftFactory
    public static final ModConfigSpec.BooleanValue allowMining;
    public static final ModConfigSpec.BooleanValue quarryOneTimeUse;
    public static final ModConfigSpec.DoubleValue miningMultiplier;
    public static final ModConfigSpec.IntValue miningDepth;
    public static final ModConfigSpec.ConfigValue<String> pumpDimensionList;
    public static final ModConfigSpec.BooleanValue quarryLoadsChunks;

    // BuildCraftBuilders
    public static final ModConfigSpec.BooleanValue fillerDestroy;
    public static final ModConfigSpec.IntValue fillerLifespanTough;
    public static final ModConfigSpec.IntValue fillerLifespanNormal;

    // BuildCraftTransport
    public static final ModConfigSpec.DoubleValue pipeDurability;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> excludedItemBlocks;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> excludedFluidBlocks;

    // Engines (PLAN.md section 9)
    public static final ModConfigSpec.BooleanValue engineExplode;
    public static final ModConfigSpec.IntValue engineOverheatTimerSeconds;

    static {
        ModConfigSpec.Builder mainConfiguration = new ModConfigSpec.Builder();

        // BuildCraftCore.loadConfiguration
        dropBrokenBlocks = mainConfiguration
                .comment("set to false to prevent fillers from dropping blocks.")
                .define("dropBrokenBlocks", true);

        itemLifespan = mainConfiguration
                .comment("the lifespan in ticks of items dropped on the ground by pipes and machines, vanilla = 6000, default = 6000")
                .defineInRange("itemLifespan", 6000, 100, Integer.MAX_VALUE);

        updateFactor = mainConfiguration
                .comment("increasing this number will decrease network update frequency, useful for overloaded servers")
                .defineInRange("network.updateFactor", 10, 1, Integer.MAX_VALUE);

        longUpdateFactor = mainConfiguration
                .comment("delay between full client sync packets, increasing it saves bandwidth, decreasing makes for better client syncronization.")
                .defineInRange("network.stateRefreshPeriod", 40, 1, Integer.MAX_VALUE);

        modifyWorld = mainConfiguration
                .comment("set to false if BuildCraft should not generate custom blocks (e.g. oil)")
                .define("modifyWorld", true);

        consumeWaterSources = mainConfiguration
                .comment("set to true if the Pump should consume water")
                .define("consumeWater", false);

        // BuildCraftEnergy.preInit
        canOilBurn = mainConfiguration
                .comment("Can oil burn?")
                .define("burnOil", true);

        oilWellScalar = mainConfiguration
                .comment("Probability of oil well generation")
                .defineInRange("oilWellGenerationRate", 1.0, 0.0, Double.MAX_VALUE);

        fuelOilMultiplier = mainConfiguration
                .comment("adjust energy value of Oil in Combustion Engines")
                .defineInRange("fuel.oil.combustion", 1.0, 0.0, Double.MAX_VALUE);

        fuelFuelMultiplier = mainConfiguration
                .comment("adjust energy value of Fuel in Combustion Engines")
                .defineInRange("fuel.fuel.combustion", 1.0, 0.0, Double.MAX_VALUE);

        spawnOilSprings = mainConfiguration
                .comment("set to false to stop oil springs generating at the bottom of large oil wells")
                .define("oilSprings", true);

        netherIgnition = mainConfiguration
                .comment("set to false to stop oil and fuel catching fire in the Nether")
                .define("fluids.netherIgnition", true);

        // BuildCraftFactory.initialize
        ConfigUtils genCat = new ConfigUtils(mainConfiguration);

        allowMining = genCat.get("mining.enabled", true, "disables the recipes for automated mining machines");
        quarryOneTimeUse = genCat.get("quarry.one.time.use", false, "Quarry cannot be picked back up after placement");
        miningMultiplier = genCat.get("mining.cost.multipler", 1F, 1F, 10F, "cost multiplier for mining operations, range (1.0 - 10.0)\nhigh values may render engines incapable of powering machines directly");
        miningDepth = genCat.get("mining.depth", 2, 256, 256, "how far below the machine can mining machines dig, range (2 - 256), default 256");

        pumpDimensionList = mainConfiguration
                .comment("Allows admins to whitelist or blacklist pumping of specific fluids in specific dimensions.\n"
                        + "Eg. \"-/minecraft:the_nether/minecraft:lava\" will disable lava in the nether. \"-/*/minecraft:lava\" will disable lava in any dimension. \"+/minecraft:overworld/*\" will enable any fluid in the overworld.\n"
                        + "Entries are comma seperated, banned fluids have precedence over allowed ones.\n"
                        + "Default is \"" + DefaultProps.PUMP_DIMENSION_LIST + "\" - the second redundant entry is there to show the format.")
                .define("pumping.controlList", DefaultProps.PUMP_DIMENSION_LIST);

        quarryLoadsChunks = genCat.get("quarry.loadsChunks", false, "set to true to let the quarry force-load its own chunk; otherwise it pauses while its chunks are unloaded");

        // BuildCraftBuilders.preInit
        fillerDestroy = mainConfiguration
                .comment("If true, Filler will destroy blocks instead of breaking them.")
                .define("filler.destroy", DefaultProps.FILLER_DESTROY);

        fillerLifespanTough = mainConfiguration
                .comment("Lifespan in ticks of items dropped by the filler from 'tough' blocks (those that can't be broken by hand)")
                .defineInRange("filler.lifespan.tough", DefaultProps.FILLER_LIFESPAN_TOUGH, 0, Integer.MAX_VALUE);

        fillerLifespanNormal = mainConfiguration
                .comment("Lifespan in ticks of items dropped by the filler from non-tough blocks (those that can be broken by hand)")
                .defineInRange("filler.lifespan.other", DefaultProps.FILLER_LIFESPAN_NORMAL, 0, Integer.MAX_VALUE);

        // BuildCraftTransport.preInit
        pipeDurability = mainConfiguration
                .comment("How long a pipe will take to break")
                .defineInRange("pipes.durability", DefaultProps.PIPES_DURABILITY, 0.0, Double.MAX_VALUE);

        excludedItemBlocks = mainConfiguration
                .comment("blocks wooden pipes will not extract items from, as block ids (e.g. \"minecraft:furnace\")")
                .defineListAllowEmpty("woodenPipe.item.exclusion", List.of(), () -> "", o -> o instanceof String);

        excludedFluidBlocks = mainConfiguration
                .comment("blocks wooden pipes will not extract fluids from, as block ids (e.g. \"minecraft:cauldron\")")
                .defineListAllowEmpty("woodenPipe.liquid.exclusion", List.of(), () -> "", o -> o instanceof String);

        // New in Buildcraft Reforged (PLAN.md section 9)
        mainConfiguration.comment("Engines").push("engines");
        engineExplode = mainConfiguration
                .comment("If true, an engine explodes with the 4.2.2 explosion range when its overheat timer runs out.\n"
                        + "If false, it shuts down and stays off until hit with a wrench.")
                .define("explode", true);
        engineOverheatTimerSeconds = mainConfiguration
                .comment("Seconds an engine keeps running in its red heat stage before it explodes or shuts down.\n"
                        + "Cooling below the red stage resets the timer.")
                .defineInRange("overheatTimerSeconds", 300, 1, 86400);
        mainConfiguration.pop();

        // New in Buildcraft Reforged (PLAN.md section 8); filled in by P3
        mainConfiguration.comment("Pipes and transport").push("transport");
        mainConfiguration.pop();

        COMMON_SPEC = mainConfiguration.build();

        ModConfigSpec.Builder clientConfiguration = new ModConfigSpec.Builder();

        colorBlindMode = clientConfiguration
                .comment("Set to true to enable alternate textures")
                .define("client.colorblindmode", false);

        CLIENT_SPEC = clientConfiguration.build();
    }

    private BuildCraftConfiguration() {
    }

    // FML 12 has no COMMON type; LOCAL is its successor (loaded on each side at startup, not synced), so the file name
    // is given to keep "common" in it.
    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.LOCAL, COMMON_SPEC, DefaultProps.MOD + "-common.toml");
        container.registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC);
    }
}
