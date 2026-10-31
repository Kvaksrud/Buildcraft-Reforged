/*
 * Ported from buildcraft/transport/triggers/ActionEnergyPulser.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: the legacy id is gone (registry entry).
 */
package buildcraftreforged.transport.triggers;

import buildcraftreforged.core.triggers.ActionTriggerIconProvider;
import buildcraftreforged.core.triggers.BCAction;

public class ActionEnergyPulser extends BCAction {

    public ActionEnergyPulser() {
        super("buildcraft.pulser.constant");
    }

    @Override
    public int getIconIndex() {
        return ActionTriggerIconProvider.Trigger_Machine_Active;
    }

    @Override
    public String getDescription() {
        return "Energy Pulser";
    }

}
