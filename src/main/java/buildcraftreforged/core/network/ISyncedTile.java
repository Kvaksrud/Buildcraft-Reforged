/*
 * Ported from buildcraft/core/network/ISyncedTile.java (BuildCraft 4.2.2, MMPL; the original has no header).
 * Modified by Buildcraft Reforged, 2026: unchanged apart from the package; a missing state may be returned as null.
 */

package buildcraftreforged.core.network;

import org.jspecify.annotations.Nullable;

/**
 * Implemented by block entities
 *
 * @author Krapht
 */
public interface ISyncedTile {

    /**
     * called by the PacketHandler for each state contained in a StatePacket
     *
     * @return an object that should be refreshed from the state, or null for an unknown id (logged and skipped)
     */
    @Nullable IClientState getStateInstance(byte stateId);

    /**
     * Called after a state has been updated
     */
    void afterStateUpdated(byte stateId);
}
