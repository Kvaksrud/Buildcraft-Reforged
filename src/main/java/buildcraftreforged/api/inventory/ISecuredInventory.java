/*
 * Modified by Buildcraft Reforged, 2026: ported from buildcraft/api/inventory/ISecuredInventory.java (no file header at
 * 4.2.2); Direction in place of ForgeDirection.
 */

package buildcraftreforged.api.inventory;

import net.minecraft.core.Direction;

/**
 * An inventory with per-player access. Ported from 4.2.2 {@code buildcraft.api.inventory.ISecuredInventory}. BuildCraft
 * 4.2.2 itself has no implementation; pipes call it on blocks from other mods.
 *
 * <p><b>Plan section 8:</b> P3 decides whether routing still calls {@link #prepareTransaction}. Owner checks in
 * Buildcraft Reforged go through the owner's fake player (plan section 6), which covers the same ground.
 */
public interface ISecuredInventory {

    /**
     * @param name
     * @return true if the user/player with the given name has access permissions on this machine.
     */
    boolean canAccess(String name);

    /**
     * Informs the inventory with whose permissions the next item or liquid transaction will be performed. It is up to the inventory to determine the effect.
     *
     * @param orientation
     *            Orientation the transaction will be performed from.
     * @param name
     *            Name of the user/player who owns the transaction.
     */
    void prepareTransaction(Direction orientation, String name);

}
