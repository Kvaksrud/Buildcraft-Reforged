/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: on AbstractContainerMenu; ICrafting crafters are the players that opened the
 * menu (PlayerContainerEvent); widget packets are PacketGuiWidget payloads; getGuiReturnHandler() added so the server
 * can check a PacketGuiReturn against the open menu. slotClick is clicked (the 1.6.4 modifier is the ContainerInput
 * id, which keeps the 1.6.4 numbers), transferStackInSlot is quickMoveStack, null stacks are ItemStack.EMPTY, and the
 * held stack is the menu's carried stack. canDragTo keeps drag-splitting out of phantom slots, which 1.6.4 had no
 * drag path into.
 */

package buildcraftreforged.core.gui;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.core.gui.widgets.Widget;
import buildcraftreforged.core.network.IGuiReturnHandler;
import buildcraftreforged.core.network.PacketGuiWidget;
import buildcraftreforged.core.gui.slots.IPhantomSlot;
import buildcraftreforged.core.gui.slots.SlotBase;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

@EventBusSubscriber(modid = BuildcraftReforged.MOD_ID)
public abstract class BuildCraftContainer extends AbstractContainerMenu {

    private final List<Widget> widgets = new ArrayList<>();
    private final List<Player> crafters = new ArrayList<>();
    private final int inventorySize;

    public BuildCraftContainer(@Nullable MenuType<?> menuType, int containerId, int inventorySize) {
        super(menuType, containerId);
        this.inventorySize = inventorySize;
    }

    public List<Widget> getWidgets() {
        return widgets;
    }

    @Override
    public Slot addSlot(Slot slot) {
        return super.addSlot(slot);
    }

    public void addWidget(Widget widget) {
        widget.addToContainer(this);
        widgets.add(widget);
    }

    /**
     * The block entity whose GUI this is, if it accepts {@code PacketGuiReturn}. A return packet is applied only when
     * the sender has this menu open and this handler sits at the packet's position.
     */
    public @Nullable IGuiReturnHandler getGuiReturnHandler() {
        return null;
    }

    public void sendWidgetDataToClient(Widget widget, Player player, byte[] data) {
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer,
                    new PacketGuiWidget(containerId, widgets.indexOf(widget), data));
        }
    }

    public void handleWidgetClientData(int widgetId, RegistryFriendlyByteBuf data) {
        if (widgetId < 0 || widgetId >= widgets.size()) {
            BuildcraftReforged.LOGGER.warn("Widget packet for widget {} of a menu with {} widgets", widgetId,
                    widgets.size());
            return;
        }
        widgets.get(widgetId).handleClientPacketData(data);
    }

    public void addCraftingToCrafters(Player player) {
        crafters.add(player);
        for (Widget widget : widgets) {
            widget.initWidget(player);
        }
    }

    public void removeCraftingFromCrafters(Player player) {
        crafters.remove(player);
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        for (Widget widget : widgets) {
            for (Player player : crafters) {
                widget.updateWidget(player);
            }
        }
    }

    @Override
    public void clicked(int slotNum, int mouseButton, ContainerInput modifier, Player player) {
        Slot slot = slotNum < 0 || slotNum >= slots.size() ? null : slots.get(slotNum);
        if (slot instanceof IPhantomSlot && modifier != ContainerInput.QUICK_CRAFT) {
            slotClickPhantom(slot, mouseButton, modifier, player);
            return;
        }
        super.clicked(slotNum, mouseButton, modifier, player);
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return !(slot instanceof IPhantomSlot) && super.canDragTo(slot);
    }

    private ItemStack slotClickPhantom(Slot slot, int mouseButton, ContainerInput modifier, Player player) {
        ItemStack stack = ItemStack.EMPTY;

        if (mouseButton == 2) {
            if (((IPhantomSlot) slot).canAdjust()) {
                slot.set(ItemStack.EMPTY);
            }
        } else if (mouseButton == 0 || mouseButton == 1) {
            slot.setChanged();
            ItemStack stackSlot = slot.getItem();
            ItemStack stackHeld = getCarried();

            if (!stackSlot.isEmpty()) {
                stack = stackSlot.copy();
            }

            if (stackSlot.isEmpty()) {
                if (!stackHeld.isEmpty() && slot.mayPlace(stackHeld)) {
                    fillPhantomSlot(slot, stackHeld, mouseButton, modifier);
                }
            } else if (stackHeld.isEmpty()) {
                adjustPhantomSlot(slot, mouseButton, modifier);
                slot.onTake(player, getCarried());
            } else if (slot.mayPlace(stackHeld)) {
                if (canStacksMerge(stackSlot, stackHeld)) {
                    adjustPhantomSlot(slot, mouseButton, modifier);
                } else {
                    fillPhantomSlot(slot, stackHeld, mouseButton, modifier);
                }
            }
        }
        return stack;
    }

    protected void adjustPhantomSlot(Slot slot, int mouseButton, ContainerInput modifier) {
        if (!((IPhantomSlot) slot).canAdjust()) {
            return;
        }
        ItemStack stackSlot = slot.getItem();
        int stackSize;
        if (modifier == ContainerInput.QUICK_MOVE) {
            stackSize = mouseButton == 0 ? (stackSlot.getCount() + 1) / 2 : stackSlot.getCount() * 2;
        } else {
            stackSize = mouseButton == 0 ? stackSlot.getCount() - 1 : stackSlot.getCount() + 1;
        }

        if (stackSize > slot.getMaxStackSize()) {
            stackSize = slot.getMaxStackSize();
        }

        stackSlot.setCount(stackSize);

        if (stackSlot.getCount() <= 0) {
            slot.set(ItemStack.EMPTY);
        }
    }

    protected void fillPhantomSlot(Slot slot, ItemStack stackHeld, int mouseButton, ContainerInput modifier) {
        if (!((IPhantomSlot) slot).canAdjust()) {
            return;
        }
        int stackSize = mouseButton == 0 ? stackHeld.getCount() : 1;
        if (stackSize > slot.getMaxStackSize()) {
            stackSize = slot.getMaxStackSize();
        }
        ItemStack phantomStack = stackHeld.copy();
        phantomStack.setCount(stackSize);

        slot.set(phantomStack);
    }

    protected boolean shiftItemStack(ItemStack stackToShift, int start, int end) {
        boolean changed = false;
        if (stackToShift.isStackable()) {
            for (int slotIndex = start; stackToShift.getCount() > 0 && slotIndex < end; slotIndex++) {
                Slot slot = slots.get(slotIndex);
                ItemStack stackInSlot = slot.getItem();
                if (!stackInSlot.isEmpty() && canStacksMerge(stackInSlot, stackToShift)) {
                    int resultingStackSize = stackInSlot.getCount() + stackToShift.getCount();
                    int max = Math.min(stackToShift.getMaxStackSize(), slot.getMaxStackSize());
                    if (resultingStackSize <= max) {
                        stackToShift.setCount(0);
                        stackInSlot.setCount(resultingStackSize);
                        slot.setChanged();
                        changed = true;
                    } else if (stackInSlot.getCount() < max) {
                        stackToShift.shrink(max - stackInSlot.getCount());
                        stackInSlot.setCount(max);
                        slot.setChanged();
                        changed = true;
                    }
                }
            }
        }
        if (stackToShift.getCount() > 0) {
            for (int slotIndex = start; stackToShift.getCount() > 0 && slotIndex < end; slotIndex++) {
                Slot slot = slots.get(slotIndex);
                ItemStack stackInSlot = slot.getItem();
                if (stackInSlot.isEmpty()) {
                    int max = Math.min(stackToShift.getMaxStackSize(), slot.getMaxStackSize());
                    stackInSlot = stackToShift.copyWithCount(Math.min(stackToShift.getCount(), max));
                    stackToShift.shrink(stackInSlot.getCount());
                    slot.set(stackInSlot);
                    slot.setChanged();
                    changed = true;
                }
            }
        }
        return changed;
    }

    private boolean tryShiftItem(ItemStack stackToShift, int numSlots) {
        for (int machineIndex = 0; machineIndex < numSlots - 9 * 4; machineIndex++) {
            Slot slot = slots.get(machineIndex);
            if (slot instanceof SlotBase slotBase && !slotBase.canShift()) {
                continue;
            }
            if (slot instanceof IPhantomSlot) {
                continue;
            }
            if (!slot.mayPlace(stackToShift)) {
                continue;
            }
            if (shiftItemStack(stackToShift, machineIndex, machineIndex + 1)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack originalStack = ItemStack.EMPTY;
        Slot slot = slots.get(slotIndex);
        int numSlots = slots.size();
        if (slot != null && slot.hasItem()) {
            ItemStack stackInSlot = slot.getItem();
            originalStack = stackInSlot.copy();
            if (slotIndex >= numSlots - 9 * 4 && tryShiftItem(stackInSlot, numSlots)) {
                // NOOP
            } else if (slotIndex >= numSlots - 9 * 4 && slotIndex < numSlots - 9) {
                if (!shiftItemStack(stackInSlot, numSlots - 9, numSlots)) {
                    return ItemStack.EMPTY;
                }
            } else if (slotIndex >= numSlots - 9 && slotIndex < numSlots) {
                if (!shiftItemStack(stackInSlot, numSlots - 9 * 4, numSlots - 9)) {
                    return ItemStack.EMPTY;
                }
            } else if (!shiftItemStack(stackInSlot, numSlots - 9 * 4, numSlots)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(stackInSlot, originalStack);
            if (stackInSlot.getCount() <= 0) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (stackInSlot.getCount() == originalStack.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, stackInSlot);
        }
        return originalStack;
    }

    /** 4.2.2 {@code StackHelper.canStacksMerge}; switch to the #23 port of {@code StackHelper} when it lands. */
    private static boolean canStacksMerge(ItemStack stack1, ItemStack stack2) {
        if (stack1.isEmpty() || stack2.isEmpty()) {
            return false;
        }
        return ItemStack.isSameItemSameComponents(stack1, stack2);
    }

    public int getInventorySize() {
        return inventorySize;
    }

    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (event.getContainer() instanceof BuildCraftContainer container) {
            container.addCraftingToCrafters(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        if (event.getContainer() instanceof BuildCraftContainer container) {
            container.removeCraftingFromCrafters(event.getEntity());
        }
    }
}
