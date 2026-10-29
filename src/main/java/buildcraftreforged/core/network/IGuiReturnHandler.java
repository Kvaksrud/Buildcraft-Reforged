/*
 * Ported from buildcraft/core/network/IGuiReturnHandler.java (BuildCraft 4.2.2, MMPL; the original has no header).
 * Modified by Buildcraft Reforged, 2026: streams replaced by RegistryFriendlyByteBuf; getWorld() is getLevel() and the
 * position and removal state come from BlockEntity, so a block entity implements this interface with no extra code
 * beyond the two data methods; readGuiData throws InvalidPacketException for out-of-range values.
 */

package buildcraftreforged.core.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * A block entity whose GUI sends its settings back to the server with {@link PacketGuiReturn}. The menu open on the
 * server names its handler through {@code BuildCraftContainer.getGuiReturnHandler()}.
 *
 * @author CovertJaguar <railcraft.wikispaces.com>
 */
public interface IGuiReturnHandler {
    @Nullable Level getLevel();

    BlockPos getBlockPos();

    boolean isRemoved();

    /** Client side: writes the settings the GUI shows. */
    void writeGuiData(RegistryFriendlyByteBuf data);

    /**
     * Server side, after the packet handler has checked the sender, the open menu and the distance. Read every value
     * through {@link PacketData}'s bounded reads (or check it and throw), and change nothing until everything has been
     * read: a throw drops the whole packet.
     */
    void readGuiData(RegistryFriendlyByteBuf data, @Nullable Player player) throws InvalidPacketException;
}
