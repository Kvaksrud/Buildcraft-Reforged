/*
 * Modified by Buildcraft Reforged, 2026: ported from buildcraft/api/gates/IAction.java (no file header at 4.2.2);
 * numeric legacy ids give way to the action registry, and the Icon becomes a GUI sprite id.
 */

package buildcraftreforged.api.gates;

import net.minecraft.resources.Identifier;

public interface IAction {

    String getUniqueTag();

    /**
     * The sprite drawn for this action in the gate GUI, in the GUI sprite atlas ({@code textures/gui/sprites}). 4.2.2
     * returned an {@code Icon} registered in {@code registerIcons}; sprites in that folder load by themselves.
     */
    Identifier getIcon();

    boolean hasParameter();

    String getDescription();
}
