/*
 * Ported from BuildCraft 4.2.2, buildcraft/api/tools/IToolWrench.java (no header in the original).
 * Modified by Buildcraft Reforged, 2026: EntityPlayer becomes Player, the x, y, z triple becomes a BlockPos, and the
 * hand holding the wrench is passed because a player has two.
 */
package buildcraftreforged.api.tools;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

/***
 * Implement this interface on subclasses of Item to have that item work as a wrench for buildcraft. Items in the
 * {@code c:tools/wrench} tag work as a wrench too, without implementing it (see buildcraftreforged.core.WrenchHelper).
 */
public interface IToolWrench {

    /***
     * Called to ensure that the wrench can be used. To get the ItemStack that is used, check player.getItemInHand(hand)
     *
     * @param player
     *            - The player doing the wrenching
     * @param hand
     *            - The hand holding the wrench
     * @param pos
     *            - The coordinates for the block being wrenched
     *
     * @return true if wrenching is allowed, false if not
     */
    public boolean canWrench(Player player, InteractionHand hand, BlockPos pos);

    /***
     * Callback after the wrench has been used. This can be used to decrease durability or for other purposes. To get the ItemStack that was used, check
     * player.getItemInHand(hand)
     *
     * @param player
     *            - The player doing the wrenching
     * @param hand
     *            - The hand holding the wrench
     * @param pos
     *            - The coordinates of the block being wrenched
     */
    public void wrenchUsed(Player player, InteractionHand hand, BlockPos pos);
}
