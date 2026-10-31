/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: the blueprint library half of buildcraft/BuildCraftBuilders.java
 * (registration is BuildersRegistry). The root index lives under the server's world folder (BlueprintPaths, plan
 * section 19.2) and belongs to one server; player indexes are keyed by UUID; getBptItemStack sets the BptItemData
 * component where 4.2.2 set the damage and the "BptName" tag. IBuilderHook is not ported (no 4.2.2 module used it).
 */

package buildcraftreforged.builders;

import buildcraftreforged.core.blueprints.BlueprintPaths;
import buildcraftreforged.core.blueprints.BptPlayerIndex;
import buildcraftreforged.core.blueprints.BptRootIndex;
import buildcraftreforged.core.utils.BCLog;
import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The server's blueprint library: one {@link BptRootIndex} in {@link BlueprintPaths#serverLibrary}, made on first use
 * and dropped when the server stops (EventHandlerBuilders). Server side only; the client's own library (#80) is
 * {@link BlueprintPaths#clientLibrary}.
 */
public final class BuildCraftBuilders {

    public static final int MAX_BLUEPRINTS_NAME_SIZE = 14;

    private static @Nullable BptRootIndex rootBptIndex;
    private static @Nullable MinecraftServer rootServer;
    private static final HashMap<UUID, BptPlayerIndex> playerLibrary = new HashMap<UUID, BptPlayerIndex>();

    private BuildCraftBuilders() {
    }

    public static synchronized @Nullable BptPlayerIndex getPlayerIndex(MinecraftServer server, UUID player) {
        BptRootIndex rootIndex = getBptRootIndex(server);

        if (rootIndex == null)
            return null;

        if (!playerLibrary.containsKey(player)) {
            try {
                playerLibrary.put(player, BptPlayerIndex.forPlayer(player, rootIndex));
            } catch (IOException e) {
                BCLog.logger.error("Could not open the blueprint list of " + player, e);
            }
        }

        return playerLibrary.get(player);
    }

    public static synchronized @Nullable BptRootIndex getBptRootIndex(MinecraftServer server) {
        if (rootBptIndex == null || rootServer != server) {
            clearLibrary();
            try {
                BptRootIndex index = new BptRootIndex(BlueprintPaths.serverLibrary(server), BlueprintPaths.ROOT_INDEX,
                        server.registryAccess());
                index.loadIndex();
                index.importNewFiles();
                rootBptIndex = index;
                rootServer = server;
            } catch (IOException e) {
                BCLog.logger.error("Could not open the blueprint library", e);
            }
        }

        return rootBptIndex;
    }

    /** Forgets the library; the next getBptRootIndex reads it again. Called when the server stops. */
    public static synchronized void clearLibrary() {
        rootBptIndex = null;
        rootServer = null;
        playerLibrary.clear();
    }

    /**
     * A Blueprint or Template item holding library entry {@code id}. 4.2.2 took the item id and set the damage; a
     * {@code id} of 0 or less gives a clean item, as damage 0 did.
     */
    public static ItemStack getBptItemStack(Item item, int id, @Nullable String name, @Nullable String author) {
        ItemStack stack = new ItemStack(item, 1);
        if (id > 0) {
            stack.set(BuildersRegistry.bptData.get(), new BptItemData(id, clip(name), clip(author)));
        }
        return stack;
    }

    private static String clip(@Nullable String s) {
        if (s == null)
            return "";
        return s.length() > BptItemData.MAX_STRING_LENGTH ? s.substring(0, BptItemData.MAX_STRING_LENGTH) : s;
    }
}
