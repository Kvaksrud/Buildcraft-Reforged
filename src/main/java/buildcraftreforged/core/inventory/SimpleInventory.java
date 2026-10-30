/**
 * Copyright (c) Krapht, 2011
 *
 * "LogisticsPipes" is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: IInventory becomes Container (getSizeInventory → getContainerSize,
 * getStackInSlot → getItem, decrStackSize → removeItem, getStackInSlotOnClosing → removeItemNoUpdate,
 * setInventorySlotContents → setItem, getInventoryStackLimit → getMaxStackSize, onInventoryChanged → setChanged,
 * isUseableByPlayer → stillValid, isItemValidForSlot → canPlaceItem); null stacks become ItemStack.EMPTY; listeners
 * are block entities; NBT becomes ValueInput/ValueOutput with the same "items" list of "Slot"-tagged stacks.
 */
package buildcraftreforged.core.inventory;

import buildcraftreforged.core.utils.INBTTagable;
import java.util.Arrays;
import java.util.LinkedList;
import net.minecraft.world.Container;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class SimpleInventory implements Container, INBTTagable {

    private final ItemStack[] _contents;
    private final String _name;
    private final int _stackLimit;
    private final LinkedList<BlockEntity> _listener = new LinkedList<BlockEntity>();

    public SimpleInventory(int size, String name, int stackLimit) {
        _contents = new ItemStack[size];
        Arrays.fill(_contents, ItemStack.EMPTY);
        _name = name;
        _stackLimit = stackLimit;
    }

    @Override
    public int getContainerSize() {
        return _contents.length;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : _contents) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slotId) {
        return _contents[slotId];
    }

    @Override
    public ItemStack removeItem(int slotId, int count) {
        if (slotId < _contents.length && !_contents[slotId].isEmpty()) {
            if (_contents[slotId].getCount() > count) {
                ItemStack result = _contents[slotId].split(count);
                setChanged();
                return result;
            }
            ItemStack stack = _contents[slotId];
            setItem(slotId, ItemStack.EMPTY);
            return stack;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void setItem(int slotId, ItemStack itemstack) {
        if (slotId >= _contents.length) {
            return;
        }
        _contents[slotId] = itemstack;

        if (!itemstack.isEmpty() && itemstack.getCount() > this.getMaxStackSize()) {
            itemstack.setCount(this.getMaxStackSize());
        }
        setChanged();
    }

    public String getInvName() {
        return _name;
    }

    @Override
    public int getMaxStackSize() {
        return _stackLimit;
    }

    @Override
    public void setChanged() {
        for (BlockEntity handler : _listener) {
            handler.setChanged();
        }
    }

    @Override
    public boolean stillValid(Player entityplayer) {
        return true;
    }

    @Override
    public void readFromNBT(ValueInput data) {
        readFromNBT(data, "items");
    }

    /** 4.2.2 also read an int "index" written by older versions; nothing writes it now. */
    public void readFromNBT(ValueInput data, String tag) {
        for (ItemStackWithSlot slot : data.listOrEmpty(tag, ItemStackWithSlot.CODEC)) {
            int index = slot.slot();
            if (index >= 0 && index < _contents.length) {
                setItem(index, slot.stack());
            }
        }
    }

    @Override
    public void writeToNBT(ValueOutput data) {
        writeToNBT(data, "items");
    }

    public void writeToNBT(ValueOutput data, String tag) {
        ValueOutput.TypedOutputList<ItemStackWithSlot> slots = data.list(tag, ItemStackWithSlot.CODEC);
        for (int index = 0; index < _contents.length; ++index) {
            if (!_contents[index].isEmpty()) {
                slots.add(new ItemStackWithSlot(index, _contents[index]));
            }
        }
    }

    public void addListener(BlockEntity listner) {
        _listener.add(listner);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slotId) {
        if (this._contents[slotId].isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack stackToTake = this._contents[slotId];
        setItem(slotId, ItemStack.EMPTY);
        return stackToTake;
    }

    public ItemStack[] getItemStacks() {
        return _contents;
    }

    @Override
    public boolean canPlaceItem(int i, ItemStack itemstack) {
        return true;
    }

    /** Clearable, new in Container; 4.2.2 code used InvUtils.wipeInventory. */
    @Override
    public void clearContent() {
        InvUtils.wipeInventory(this);
    }
}
