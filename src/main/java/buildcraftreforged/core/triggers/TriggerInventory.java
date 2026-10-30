/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: the IInventory slot walk reads the item handler capability instead; legacy
 * id gone; BlockEntity and Direction in place of TileEntity and ForgeDirection.
 */
package buildcraftreforged.core.triggers;

import buildcraftreforged.api.gates.ITriggerParameter;
import buildcraftreforged.api.inventory.ISpecialInventory;
import java.util.Locale;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

public class TriggerInventory extends BCTrigger {

    public enum State {

        Empty, Contains, Space, Full
    };
    public State state;

    public TriggerInventory(State state) {
        super("buildcraft.inventory." + state.name().toLowerCase(Locale.ENGLISH));

        this.state = state;
    }

    @Override
    public boolean hasParameter() {
        if (state == State.Contains || state == State.Space)
            return true;
        else
            return false;
    }

    @Override
    public String getDescription() {
        switch (state) {
            case Empty:
                return "Inventory Empty";
            case Contains:
                return "Items in Inventory";
            case Space:
                return "Space in Inventory";
            default:
                return "Inventory Full";
        }
    }

    @Override
    public boolean isTriggerActive(@Nullable Direction side, BlockEntity tile, @Nullable ITriggerParameter parameter) {
        ItemStack searchedStack = ItemStack.EMPTY;

        if (parameter != null) {
            searchedStack = parameter.getItemStack();
        }

        if (tile instanceof ISpecialInventory) {
            ISpecialInventory specialInventory = (ISpecialInventory) tile;
            ItemStack[] itemStacks;
            switch (state) {
                case Contains:
                    itemStacks = specialInventory.extractItem(false, side, 1);
                    return itemStacks != null && itemStacks.length > 0 && itemStacks[0] != null && !itemStacks[0].isEmpty() && (searchedStack.isEmpty() || ItemStack.isSameItemSameComponents(itemStacks[0], searchedStack));
                case Empty:
                    itemStacks = specialInventory.extractItem(false, side, 1);
                    return itemStacks == null || itemStacks.length == 0 || itemStacks[0] == null || itemStacks[0].isEmpty();
                case Full:
                    break;
                case Space:
                    if (searchedStack.isEmpty())
                        break;
                    int added = specialInventory.addItem(searchedStack, false, side);
                    return added > 0;
            }
        }

        ResourceHandler<ItemResource> inventory = getCapability(Capabilities.Item.BLOCK, tile, side);
        if (inventory != null) {
            ItemResource searched = ItemResource.of(searchedStack);
            boolean hasSlots = false;
            boolean foundItems = false;
            boolean foundSpace = false;

            // 4.2.2 InventoryIterator walk: one index per slot the side exposes.
            for (int slot = 0; slot < inventory.size(); slot++) {
                hasSlots = true;
                ItemResource stack = inventory.getResource(slot);
                int stackSize = inventory.getAmountAsInt(slot);

                // StackHelper.canStacksMerge: same item and components, false when either side is missing.
                boolean canMerge = !stack.isEmpty() && !searched.isEmpty() && stack.equals(searched);
                foundItems |= !stack.isEmpty() && (searched.isEmpty() || canMerge);
                foundSpace |= (stack.isEmpty() || (canMerge && stackSize < inventory.getCapacityAsInt(slot, stack)))
                        && (searched.isEmpty() || inventory.isValid(slot, searched));
            }

            if(!hasSlots)
                return false;

            switch (state) {
                case Empty:
                    return !foundItems;
                case Contains:
                    return foundItems;
                case Space:
                    return foundSpace;
                default:
                    return !foundSpace;
            }
        }

        return false;
    }

    @Override
    public int getIconIndex() {
        switch (state) {
            case Empty:
                return ActionTriggerIconProvider.Trigger_Inventory_Empty;
            case Contains:
                return ActionTriggerIconProvider.Trigger_Inventory_Contains;
            case Space:
                return ActionTriggerIconProvider.Trigger_Inventory_Space;
            default:
                return ActionTriggerIconProvider.Trigger_Inventory_Full;
        }
    }
}
