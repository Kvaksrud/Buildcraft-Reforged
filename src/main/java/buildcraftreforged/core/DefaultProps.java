/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: block and item ids, legacy trigger and action ids, biome ids, texture paths,
 * the Forge dependency strings, the network channel name and the default language removed; registries, resource locations and the
 * payload registrar replace them. The pump dimension list default uses namespaced ids (docs/PORTING-NOTES.md).
 */

package buildcraftreforged.core;

import buildcraftreforged.BuildcraftReforged;

public class DefaultProps {

    public static final String MOD = BuildcraftReforged.MOD_ID;

    public static int WORLD_HEIGHT = 256;
    public static int NETWORK_UPDATE_RANGE = 128;
    public static int PIPE_CONTENTS_RENDER_DIST = 24;

    public static String PUMP_DIMENSION_LIST = "+/*/*,+/minecraft:the_nether/minecraft:lava";

    public static boolean CURRENT_CONTINUOUS = false;
    public static double PIPES_DURABILITY = 0.25D;
    public static boolean FILLER_DESTROY = false;

    public static final int FILLER_LIFESPAN_TOUGH = 20;
    public static final int FILLER_LIFESPAN_NORMAL = 6000;
}
