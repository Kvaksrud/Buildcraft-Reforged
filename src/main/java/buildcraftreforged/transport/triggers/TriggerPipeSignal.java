/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: the pipe is the pipe block entity; the legacy id is gone (registry entry).
 */
package buildcraftreforged.transport.triggers;

import buildcraftreforged.api.gates.ITriggerParameter;
import buildcraftreforged.api.transport.IPipe;
import buildcraftreforged.core.triggers.ActionTriggerIconProvider;
import buildcraftreforged.core.triggers.BCTrigger;
import buildcraftreforged.transport.ITriggerPipe;
import buildcraftreforged.transport.TilePipe;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

public class TriggerPipeSignal extends BCTrigger implements ITriggerPipe {

    boolean active;
    IPipe.WireColor color;

    public TriggerPipeSignal(boolean active, IPipe.WireColor color) {
        super("buildcraft.pipe.wire.input." + color.name().toLowerCase(Locale.ENGLISH) + (active ? ".active" : ".inactive"));

        this.active = active;
        this.color = color;
    }

    @Override
    public boolean hasParameter() {
        return false;
    }

    @Override
    public String getDescription() {
        if (active) {
            switch (color) {
                case Red:
                    return "Red Pipe Signal On";
                case Blue:
                    return "Blue Pipe Signal On";
                case Green:
                    return "Green Pipe Signal On";
                case Yellow:
                    return "Yellow Pipe Signal On";
            }
        } else {
            switch (color) {
                case Red:
                    return "Red Pipe Signal Off";
                case Blue:
                    return "Blue Pipe Signal Off";
                case Green:
                    return "Green Pipe Signal Off";
                case Yellow:
                    return "Yellow Pipe Signal Off";
            }
        }

        return "";
    }

    @Override
    public boolean isTriggerActive(TilePipe pipe, @Nullable ITriggerParameter parameter) {
        if (active)
            return pipe.gateHolder.signalStrength[color.ordinal()] > 0;
        else
            return pipe.gateHolder.signalStrength[color.ordinal()] == 0;
    }

    @Override
    public int getIconIndex() {
        if (active) {
            switch (color) {
                case Red:
                    return ActionTriggerIconProvider.Trigger_PipeSignal_Red_Active;
                case Blue:
                    return ActionTriggerIconProvider.Trigger_PipeSignal_Blue_Active;
                case Green:
                    return ActionTriggerIconProvider.Trigger_PipeSignal_Green_Active;
                case Yellow:
                    return ActionTriggerIconProvider.Trigger_PipeSignal_Yellow_Active;
            }
        } else {
            switch (color) {
                case Red:
                    return ActionTriggerIconProvider.Trigger_PipeSignal_Red_Inactive;
                case Blue:
                    return ActionTriggerIconProvider.Trigger_PipeSignal_Blue_Inactive;
                case Green:
                    return ActionTriggerIconProvider.Trigger_PipeSignal_Green_Inactive;
                case Yellow:
                    return ActionTriggerIconProvider.Trigger_PipeSignal_Yellow_Inactive;
            }
        }
        return -1;
    }
}
