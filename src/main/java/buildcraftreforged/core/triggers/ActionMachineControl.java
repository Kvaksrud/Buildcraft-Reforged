/*
 * Modified by Buildcraft Reforged, 2026: ported from buildcraft/core/triggers/ActionMachineControl.java (no file
 * header at 4.2.2); the legacy id is gone (registry entry).
 */

package buildcraftreforged.core.triggers;

import java.util.Locale;


public class ActionMachineControl extends BCAction {

    public enum Mode {
        Unknown, On, Off, Loop
    };

    Mode mode;

    public ActionMachineControl(Mode mode) {
        super("buildcraft.machine." + mode.name().toLowerCase(Locale.ENGLISH));

        this.mode = mode;
    }

    @Override
    public String getDescription() {
        switch (mode) {
        case On:
            return "On";
        case Off:
            return "Off";
        case Loop:
            return "Loop";
        default:
            return "";
        }
    }

    @Override
    public int getIconIndex() {
        switch (mode) {
        case On:
            return ActionTriggerIconProvider.Action_MachineControl_On;
        case Off:
            return ActionTriggerIconProvider.Action_MachineControl_Off;
        case Loop:
        default:
            return ActionTriggerIconProvider.Action_MachineControl_Loop;
        }
    }
}
