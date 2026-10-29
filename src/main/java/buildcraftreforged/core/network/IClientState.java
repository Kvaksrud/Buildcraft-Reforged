/*
 * Ported from buildcraft/core/network/IClientState.java (BuildCraft 4.2.2, MMPL; the original has no header).
 * Modified by Buildcraft Reforged, 2026: DataOutputStream/DataInputStream replaced by RegistryFriendlyByteBuf.
 */

package buildcraftreforged.core.network;

import net.minecraft.network.RegistryFriendlyByteBuf;

/**
 * Implemented by classes representing serializable client state
 *
 * @author Krapht
 */
public interface IClientState {
    /**
     * Serializes the state to the stream
     */
    void writeData(RegistryFriendlyByteBuf data);

    /**
     * Deserializes the state from the stream
     */
    void readData(RegistryFriendlyByteBuf data);
}
