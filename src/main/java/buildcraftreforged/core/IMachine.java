/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: isBlocked added for the Machine trigger's "blocked" state (plan section 8.3).
 */

package buildcraftreforged.core;

import buildcraftreforged.api.gates.IAction;

public interface IMachine {

    public boolean isActive();

    public boolean manageFluids();

    public boolean manageSolids();

    public boolean allowAction(IAction action);

    /**
     * True while the machine has work but cannot place its output (plan section 8.3, "Output blocked"). New in Buildcraft
     * Reforged; 4.2.2 spilled the output instead. Read by the Machine trigger's blocked state.
     */
    public default boolean isBlocked() {
        return false;
    }

}
