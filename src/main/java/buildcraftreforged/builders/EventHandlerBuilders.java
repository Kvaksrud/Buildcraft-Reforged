/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: WorldEvent becomes LevelEvent on the game bus; the effective-side check
 * becomes a ServerLevel check; BuildCraftBuilders.ServerStop's clearAvailableMarkersList moves here as
 * ServerStoppingEvent.
 */

package buildcraftreforged.builders;

import buildcraftreforged.BuildcraftReforged;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@EventBusSubscriber(modid = BuildcraftReforged.MOD_ID)
public class EventHandlerBuilders {

    @SubscribeEvent
    public static void handleWorldLoad(LevelEvent.Load event) {
        // Temporary solution
        // Please remove the world Load event when world Unload event gets implimented
        if (event.getLevel() instanceof ServerLevel level) {
            TilePathMarker.clearAvailableMarkersList(level);
        }
    }

    @SubscribeEvent
    public static void handleWorldUnload(LevelEvent.Unload event) {
        // When a world unloads clean from the list of available markers the ones
        // that were on the unloaded world
        if (event.getLevel() instanceof ServerLevel level) {
            TilePathMarker.clearAvailableMarkersList(level);
        }
    }

    @SubscribeEvent
    public static void serverStop(ServerStoppingEvent event) {
        TilePathMarker.clearAvailableMarkersList();
        BuildCraftBuilders.clearLibrary();
    }

}
