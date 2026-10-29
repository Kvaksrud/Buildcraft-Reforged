/*
 * Modified by Buildcraft Reforged, 2026: ported from buildcraft/core/triggers/ActionTriggerIconProvider.java (no file
 * header at 4.2.2); icons are GUI sprites (textures/gui/sprites/triggers) looked up by id instead of being registered,
 * and Trigger_Machine_Blocked is new (plan section 8.3).
 */

package buildcraftreforged.core.triggers;

import buildcraftreforged.BuildcraftReforged;
import net.minecraft.resources.Identifier;

public class ActionTriggerIconProvider {

    public static ActionTriggerIconProvider INSTANCE = new ActionTriggerIconProvider();

    public static final int Action_MachineControl_On             =  0;
    public static final int Action_MachineControl_Off            =  1;
    public static final int Action_MachineControl_Loop           =  2;

    public static final int Trigger_EngineHeat_Blue              =  3;
    public static final int Trigger_EngineHeat_Green             =  4;
    public static final int Trigger_EngineHeat_Yellow            =  5;
    public static final int Trigger_EngineHeat_Red               =  6;
    public static final int Trigger_Inventory_Empty              =  7;
    public static final int Trigger_Inventory_Contains           =  8;
    public static final int Trigger_Inventory_Space              =  9;
    public static final int Trigger_Inventory_Full               = 10;
    public static final int Trigger_FluidContainer_Empty         = 11;
    public static final int Trigger_FluidContainer_Contains      = 12;
    public static final int Trigger_FluidContainer_Space         = 13;
    public static final int Trigger_FluidContainer_Full          = 14;
    public static final int Trigger_Machine_Active               = 15;
    public static final int Trigger_Machine_Inactive             = 16;
    public static final int Trigger_PipeContents_Empty           = 17;
    public static final int Trigger_PipeContents_ContainsItems   = 18;
    public static final int Trigger_PipeContents_ContainsFluid   = 19;
    public static final int Trigger_PipeContents_ContainsEnergy  = 20;
    public static final int Trigger_PipeSignal_Red_Active        = 21;
    public static final int Trigger_PipeSignal_Blue_Active       = 22;
    public static final int Trigger_PipeSignal_Green_Active      = 23;
    public static final int Trigger_PipeSignal_Yellow_Active     = 24;
    public static final int Trigger_PipeSignal_Red_Inactive      = 25;
    public static final int Trigger_PipeSignal_Blue_Inactive     = 26;
    public static final int Trigger_PipeSignal_Green_Inactive    = 27;
    public static final int Trigger_PipeSignal_Yellow_Inactive   = 28;
    public static final int Trigger_RedstoneInput_Active         = 29;
    public static final int Trigger_RedstoneInput_Inactive       = 30;
    public static final int Trigger_PipeContents_RequestsEnergy  = 31;
    public static final int Trigger_PipeContents_TooMuchEnergy   = 32;
    public static final int Trigger_Inventory_Below25            = 33;
    public static final int Trigger_Inventory_Below50            = 34;
    public static final int Trigger_Inventory_Below75            = 35;
    /** New: the Machine trigger's blocked state (plan section 8.3). Its sprite is new art, not drawn yet. */
    public static final int Trigger_Machine_Blocked              = 36;

    public static final int MAX                                  = 37;

    private final Identifier[] icons = new Identifier[MAX];

    private ActionTriggerIconProvider() {
        // 4.2.2 registerIcons, minus the lines it had commented out (engine heat and pipe contents). Those triggers'
        // own classes name their sprites when they are ported.
        icons[Action_MachineControl_On] = sprite("action_machinecontrol_on");
        icons[Action_MachineControl_Off] = sprite("action_machinecontrol_off");
        icons[Action_MachineControl_Loop] = sprite("action_machinecontrol_loop");

        icons[Trigger_Inventory_Empty] = sprite("trigger_inventory_empty");
        icons[Trigger_Inventory_Contains] = sprite("trigger_inventory_contains");
        icons[Trigger_Inventory_Space] = sprite("trigger_inventory_space");
        icons[Trigger_Inventory_Full] = sprite("trigger_inventory_full");
        icons[Trigger_FluidContainer_Empty] = sprite("trigger_liquidcontainer_empty");
        icons[Trigger_FluidContainer_Contains] = sprite("trigger_liquidcontainer_contains");
        icons[Trigger_FluidContainer_Space] = sprite("trigger_liquidcontainer_space");
        icons[Trigger_FluidContainer_Full] = sprite("trigger_liquidcontainer_full");
        icons[Trigger_Machine_Active] = sprite("trigger_machine_active");
        icons[Trigger_Machine_Inactive] = sprite("trigger_machine_inactive");
        icons[Trigger_PipeSignal_Red_Active] = sprite("trigger_pipesignal_red_active");
        icons[Trigger_PipeSignal_Red_Inactive] = sprite("trigger_pipesignal_red_inactive");
        icons[Trigger_PipeSignal_Blue_Active] = sprite("trigger_pipesignal_blue_active");
        icons[Trigger_PipeSignal_Blue_Inactive] = sprite("trigger_pipesignal_blue_inactive");
        icons[Trigger_PipeSignal_Green_Active] = sprite("trigger_pipesignal_green_active");
        icons[Trigger_PipeSignal_Green_Inactive] = sprite("trigger_pipesignal_green_inactive");
        icons[Trigger_PipeSignal_Yellow_Active] = sprite("trigger_pipesignal_yellow_active");
        icons[Trigger_PipeSignal_Yellow_Inactive] = sprite("trigger_pipesignal_yellow_inactive");
        icons[Trigger_RedstoneInput_Active] = sprite("trigger_redstoneinput_active");
        icons[Trigger_RedstoneInput_Inactive] = sprite("trigger_redstoneinput_inactive");
        icons[Trigger_Inventory_Below25] = sprite("trigger_inventory_below25");
        icons[Trigger_Inventory_Below50] = sprite("trigger_inventory_below50");
        icons[Trigger_Inventory_Below75] = sprite("trigger_inventory_below75");
        icons[Trigger_Machine_Blocked] = sprite("trigger_machine_blocked");
    }

    private static Identifier sprite(String name) {
        return Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "triggers/" + name);
    }

    public Identifier getIcon(int iconIndex) {
        return icons[iconIndex];
    }

}
