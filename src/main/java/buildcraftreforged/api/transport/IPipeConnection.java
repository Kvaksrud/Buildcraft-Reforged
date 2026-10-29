/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: Direction in place of ForgeDirection.
 */
package buildcraftreforged.api.transport;

import buildcraftreforged.api.transport.IPipeTile.PipeType;
import net.minecraft.core.Direction;

/**
 * Lets a block entity override whether pipes connect to it. Ported from 4.2.2
 * {@code buildcraft.api.transport.IPipeConnection}. Expected to survive plan section 8 unchanged: the pipe graph of 8.3
 * still decides connections per side and per pipe kind.
 */
public interface IPipeConnection {

    enum ConnectOverride {

        CONNECT, DISCONNECT, DEFAULT
    };

    /**
     * Allows you to override pipe connection logic.
     *
     * @param type
     * @param with
     * @return CONNECT to force a connection, DISCONNECT to force no connection,
     * and DEFAULT to let the pipe decide.
     */
    public ConnectOverride overridePipeConnection(PipeType type, Direction with);
}
