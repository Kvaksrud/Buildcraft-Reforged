/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: the boolean "active" becomes a State with the new Blocked state (plan section
 * 8.3); BlockEntity and Direction in place of TileEntity and ForgeDirection.
 */
package buildcraftreforged.core.triggers;

import buildcraftreforged.api.gates.ITriggerParameter;
import buildcraftreforged.core.IMachine;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

public class TriggerMachine extends BCTrigger {

    /** 4.2.2 had a boolean: true for Active ("scheduled"), false for Inactive ("done"). Blocked is new. */
    public enum State {

        Active, Inactive, Blocked
    };
    public State state;

    public TriggerMachine(State state) {
        super("buildcraft.work." + switch (state) {
            case Active -> "scheduled";
            case Inactive -> "done";
            case Blocked -> "blocked";
        });

        this.state = state;
    }

    @Override
    public String getDescription() {
        switch (state) {
            case Active:
                return "Work Scheduled";
            case Inactive:
                return "Work Done";
            default:
                return "Output Blocked";
        }
    }

    @Override
    public boolean isTriggerActive(@Nullable Direction side, BlockEntity tile, @Nullable ITriggerParameter parameter) {
        if (tile instanceof IMachine) {
            IMachine machine = (IMachine) tile;

            switch (state) {
                case Active:
                    return machine.isActive();
                case Inactive:
                    // A blocked machine has work left, so it is not "done".
                    return !machine.isActive() && !machine.isBlocked();
                default:
                    return machine.isBlocked();
            }
        }

        return false;
    }

    @Override
    public int getIconIndex() {
        switch (state) {
            case Active:
                return ActionTriggerIconProvider.Trigger_Machine_Active;
            case Inactive:
                return ActionTriggerIconProvider.Trigger_Machine_Inactive;
            default:
                return ActionTriggerIconProvider.Trigger_Machine_Blocked;
        }
    }
}
