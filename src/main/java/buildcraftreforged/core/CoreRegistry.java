/**
 * Copyright (c) 2011-2014, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: the registration half of buildcraft/BuildCraftCore.java, on DeferredRegister;
 * numeric ids, Configuration and LanguageRegistry names removed; triggers and actions are entries of the gates
 * registries, and the Machine trigger gains its blocked state.
 */

package buildcraftreforged.core;

import buildcraftreforged.api.gates.ActionManager;
import buildcraftreforged.api.gates.IAction;
import buildcraftreforged.api.gates.ITrigger;
import buildcraftreforged.core.triggers.ActionMachineControl;
import buildcraftreforged.core.triggers.ActionMachineControl.Mode;
import buildcraftreforged.core.triggers.ActionRedstoneOutput;
import buildcraftreforged.core.triggers.BCAction;
import buildcraftreforged.core.triggers.BCTrigger;
import buildcraftreforged.core.triggers.DefaultActionProvider;
import buildcraftreforged.core.triggers.DefaultTriggerProvider;
import buildcraftreforged.core.triggers.TriggerFluidContainer;
import buildcraftreforged.core.triggers.TriggerInventory;
import buildcraftreforged.core.triggers.TriggerInventoryLevel;
import buildcraftreforged.core.triggers.TriggerMachine;
import java.util.function.Supplier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.NewRegistryEvent;

/**
 * Registry entries of the core module, the registration half of 4.2.2's BuildCraftCore. Field names are the 4.2.2
 * field names; registry names are the 4.2.2 unlocalised names in snake case; the translation key stays the 4.2.2 one.
 */
public final class CoreRegistry {
    public static final ModuleRegisters REGISTERS = new ModuleRegisters();

    public static final DeferredItem<ItemBuildCraft> woodenGearItem = gear("woodenGearItem");
    public static final DeferredItem<ItemBuildCraft> stoneGearItem = gear("stoneGearItem");
    public static final DeferredItem<ItemBuildCraft> ironGearItem = gear("ironGearItem");
    public static final DeferredItem<ItemBuildCraft> goldGearItem = gear("goldGearItem");
    public static final DeferredItem<ItemBuildCraft> diamondGearItem = gear("diamondGearItem");
    public static final DeferredItem<ItemWrench> wrenchItem = wrench("wrenchItem");

    public static final DeferredHolder<ITrigger, BCTrigger> triggerMachineActive =
            trigger("triggerMachineActive", () -> new TriggerMachine(TriggerMachine.State.Active));
    public static final DeferredHolder<ITrigger, BCTrigger> triggerMachineInactive =
            trigger("triggerMachineInactive", () -> new TriggerMachine(TriggerMachine.State.Inactive));
    public static final DeferredHolder<ITrigger, BCTrigger> triggerEmptyInventory =
            trigger("triggerEmptyInventory", () -> new TriggerInventory(TriggerInventory.State.Empty));
    public static final DeferredHolder<ITrigger, BCTrigger> triggerContainsInventory =
            trigger("triggerContainsInventory", () -> new TriggerInventory(TriggerInventory.State.Contains));
    public static final DeferredHolder<ITrigger, BCTrigger> triggerSpaceInventory =
            trigger("triggerSpaceInventory", () -> new TriggerInventory(TriggerInventory.State.Space));
    public static final DeferredHolder<ITrigger, BCTrigger> triggerFullInventory =
            trigger("triggerFullInventory", () -> new TriggerInventory(TriggerInventory.State.Full));
    public static final DeferredHolder<ITrigger, BCTrigger> triggerEmptyFluid =
            trigger("triggerEmptyFluid", () -> new TriggerFluidContainer(TriggerFluidContainer.State.Empty));
    public static final DeferredHolder<ITrigger, BCTrigger> triggerContainsFluid =
            trigger("triggerContainsFluid", () -> new TriggerFluidContainer(TriggerFluidContainer.State.Contains));
    public static final DeferredHolder<ITrigger, BCTrigger> triggerSpaceFluid =
            trigger("triggerSpaceFluid", () -> new TriggerFluidContainer(TriggerFluidContainer.State.Space));
    public static final DeferredHolder<ITrigger, BCTrigger> triggerFullFluid =
            trigger("triggerFullFluid", () -> new TriggerFluidContainer(TriggerFluidContainer.State.Full));
    // SEAM(#60): triggerRedstoneActive / triggerRedstoneInactive (transport TriggerRedstoneInput) are
    // registered here in 4.2.2.

    public static final DeferredHolder<ITrigger, BCTrigger> triggerInventoryBelow25 =
            trigger("triggerInventoryBelow25", () -> new TriggerInventoryLevel(TriggerInventoryLevel.TriggerType.BELOW_25));
    public static final DeferredHolder<ITrigger, BCTrigger> triggerInventoryBelow50 =
            trigger("triggerInventoryBelow50", () -> new TriggerInventoryLevel(TriggerInventoryLevel.TriggerType.BELOW_50));
    public static final DeferredHolder<ITrigger, BCTrigger> triggerInventoryBelow75 =
            trigger("triggerInventoryBelow75", () -> new TriggerInventoryLevel(TriggerInventoryLevel.TriggerType.BELOW_75));

    /** New: the Machine trigger's blocked state (plan section 8.3). */
    public static final DeferredHolder<ITrigger, BCTrigger> triggerMachineBlocked =
            trigger("triggerMachineBlocked", () -> new TriggerMachine(TriggerMachine.State.Blocked));

    public static final DeferredHolder<IAction, BCAction> actionRedstone =
            action("actionRedstone", ActionRedstoneOutput::new);
    public static final DeferredHolder<IAction, BCAction> actionOn =
            action("actionOn", () -> new ActionMachineControl(Mode.On));
    public static final DeferredHolder<IAction, BCAction> actionOff =
            action("actionOff", () -> new ActionMachineControl(Mode.Off));
    public static final DeferredHolder<IAction, BCAction> actionLoop =
            action("actionLoop", () -> new ActionMachineControl(Mode.Loop));

    static {
        CreativeTabBuildCraft.registerAll(REGISTERS.creativeModeTabs);
    }

    private CoreRegistry() {
    }

    private static DeferredItem<ItemBuildCraft> gear(String unlocalizedName) {
        DeferredItem<ItemBuildCraft> item = REGISTERS.items.registerItem(RegistryNames.of(unlocalizedName),
                ItemBuildCraft::new, properties -> properties.overrideDescription("item." + unlocalizedName));
        CreativeTabBuildCraft.MACHINES.add(item);
        return item;
    }

    private static DeferredItem<ItemWrench> wrench(String unlocalizedName) {
        DeferredItem<ItemWrench> item = REGISTERS.items.registerItem(RegistryNames.of(unlocalizedName), ItemWrench::new,
                properties -> properties.overrideDescription("item." + unlocalizedName));
        CreativeTabBuildCraft.MACHINES.add(item);
        return item;
    }

    /** Triggers and actions are named after their 4.2.2 field: triggerMachineActive -> trigger_machine_active. */
    private static DeferredHolder<ITrigger, BCTrigger> trigger(String fieldName, Supplier<BCTrigger> factory) {
        return REGISTERS.triggers.register(RegistryNames.of(fieldName), factory);
    }

    private static DeferredHolder<IAction, BCAction> action(String fieldName, Supplier<BCAction> factory) {
        return REGISTERS.actions.register(RegistryNames.of(fieldName), factory);
    }

    public static void register(IEventBus modBus) {
        REGISTERS.register(modBus);

        // The gates registries belong to the API; core creates them, as BuildCraftCore owned ActionManager's content.
        modBus.addListener(NewRegistryEvent.class, event -> {
            event.register(ActionManager.TRIGGER_REGISTRY);
            event.register(ActionManager.ACTION_REGISTRY);
        });
        // 4.2.2 BuildCraftCore.init
        ActionManager.registerTriggerProvider(new DefaultTriggerProvider());
        ActionManager.registerActionProvider(new DefaultActionProvider());
    }
}
