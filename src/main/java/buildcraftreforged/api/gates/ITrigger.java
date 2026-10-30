/*
 * Modified by Buildcraft Reforged, 2026: ported from buildcraft/api/gates/ITrigger.java (no file header at 4.2.2);
 * numeric legacy ids give way to the trigger registry, the Icon becomes a GUI sprite id, BlockEntity and Direction
 * replace TileEntity and ForgeDirection.
 */

package buildcraftreforged.api.gates;

import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

public interface ITrigger {

    String getUniqueTag();

    /**
     * The sprite drawn for this trigger in the gate GUI, in the GUI sprite atlas ({@code textures/gui/sprites}). 4.2.2
     * returned an {@code Icon} registered in {@code registerIcons}; sprites in that folder load by themselves.
     */
    Identifier getIcon();

    /**
     * Return true if this trigger can accept parameters
     */
    boolean hasParameter();

    /**
     * Return true if this trigger requires a parameter
     */
    boolean requiresParameter();

    /**
     * Return the trigger description in the UI
     */
    String getDescription();

    /**
     * Return true if the tile given in parameter activates the trigger, given
     * the parameters. {@code side} is the side of the tile facing the gate, null for none (4.2.2 UNKNOWN).
     */
    boolean isTriggerActive(@Nullable Direction side, BlockEntity tile, @Nullable ITriggerParameter parameter);

    /**
     * Create parameters for the trigger. As for now, there is only one kind of
     * trigger parameter available so this subprogram is final.
     */
    ITriggerParameter createParameter();
}
