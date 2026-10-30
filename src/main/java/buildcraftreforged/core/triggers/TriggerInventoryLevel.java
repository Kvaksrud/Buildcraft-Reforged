/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: the IInventory slot walk reads the item handler capability instead; an empty
 * parameter counts as none; BlockEntity and Direction in place of TileEntity and ForgeDirection.
 */
package buildcraftreforged.core.triggers;

import buildcraftreforged.api.gates.ActionManager;
import buildcraftreforged.api.gates.ITriggerParameter;
import java.util.Locale;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

public class TriggerInventoryLevel extends BCTrigger {

    public enum TriggerType {

        BELOW_25, BELOW_50, BELOW_75
    };
    public TriggerType type;

    public TriggerInventoryLevel(TriggerType type) {
        super("buildcraft.inventorylevel." + type.name().toLowerCase(Locale.ENGLISH));
        this.type = type;

        // Legacy migration code
        ActionManager.triggers.put("buildcraft.filteredBuffer." + type.name().toLowerCase(Locale.ENGLISH), this);
    }

    @Override
    public boolean hasParameter() {
        return true;
    }

    @Override
    public boolean requiresParameter() {
        return true;
    }

    @Override
    public String getDescription() {
        switch (type) {
            case BELOW_25:
                return "Contains < 25%";
            case BELOW_50:
                return "Contains < 50%";
            default:
                return "Contains < 75%";
        }
    }

    @Override
    public boolean isTriggerActive(@Nullable Direction side, BlockEntity tile, @Nullable ITriggerParameter parameter) {
        // A parameter is required. 4.2.2 crashed on a parameter without a stack; that counts as none here.
        if (parameter == null || parameter.getItemStack().isEmpty())
            return false;

        ResourceHandler<ItemResource> inventory = getCapability(Capabilities.Item.BLOCK, tile, side);
        if (inventory != null) {
            ItemStack searchStack = parameter.getItemStack();
            ItemResource searched = ItemResource.of(searchStack);

            int stackSpace = 0;
            int foundItems = 0;
            for (int slot = 0; slot < inventory.size(); slot++) {
                if (inventory.isValid(slot, searched)) {
                    ItemResource stackInSlot = inventory.getResource(slot);
                    if (stackInSlot.isEmpty() || stackInSlot.equals(searched)) {
                        stackSpace++;
                        foundItems += stackInSlot.isEmpty() ? 0 : inventory.getAmountAsInt(slot);
                    }
                }
            }

            if (stackSpace > 0) {
                float percentage = (float) foundItems / ((float) stackSpace * (float) searchStack.getMaxStackSize());

                switch (type) {
                    case BELOW_25:
                        return percentage < 0.25f;
                    case BELOW_50:
                        return percentage < 0.5f;
                    default:
                        return percentage < 0.75f;
                }
            }

        }

        return false;
    }

    @Override
    public int getIconIndex() {
        switch (type) {
            case BELOW_25:
                return ActionTriggerIconProvider.Trigger_Inventory_Below25;
            case BELOW_50:
                return ActionTriggerIconProvider.Trigger_Inventory_Below50;
            default:
                return ActionTriggerIconProvider.Trigger_Inventory_Below75;
        }
    }
}
