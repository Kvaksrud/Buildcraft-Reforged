/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: the pipe is the pipe block entity; the legacy id is gone (registry entry);
 * icons come from ActionTriggerIconProvider, which names the same sprites.
 */
package buildcraftreforged.transport.triggers;

import buildcraftreforged.api.gates.ITriggerParameter;
import buildcraftreforged.core.triggers.ActionTriggerIconProvider;
import buildcraftreforged.core.triggers.BCTrigger;
import buildcraftreforged.transport.ITriggerPipe;
import buildcraftreforged.transport.TilePipe;
import org.jspecify.annotations.Nullable;

public class TriggerRedstoneInput extends BCTrigger implements ITriggerPipe {

    boolean active;

    public TriggerRedstoneInput(boolean active) {
        super(active ? "buildcraft.redtone.input.active" : "buildcraft.redtone.input.inactive");

        this.active = active;
    }

    @Override
    public String getDescription() {
        if (active)
            return "Redstone Signal On";
        else
            return "Redstone Signal Off";
    }

    @Override
    public boolean isTriggerActive(TilePipe pipe, @Nullable ITriggerParameter parameter) {
        if (active)
            return isBeingPowered(pipe);
        return !isBeingPowered(pipe);
    }

    private boolean isBeingPowered(TilePipe pipe) {
        return pipe.gateHolder.redstonePowered;
    }

    @Override
    public int getIconIndex() {
        if (active)
            return ActionTriggerIconProvider.Trigger_RedstoneInput_Active;
        else
            return ActionTriggerIconProvider.Trigger_RedstoneInput_Inactive;
    }
}
