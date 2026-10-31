/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: IInventory becomes Container; stacks are written with the ItemStack codec
 * through the level's registries; the id mapping is gone with IBptContext's (#76).
 */

package buildcraftreforged.api.blueprints;

import java.util.LinkedList;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Inventory contents kept in a slot's {@code cpt} under {@code inv}: one compound per inventory slot, empty where the
 * slot is empty (4.2.2's layout). Ported from 4.2.2 {@code buildcraft.api.blueprints.BptBlockUtils}.
 */
public class BptBlockUtils {

    public static void requestInventoryContents(BptSlotInfo slot, IBptContext context, LinkedList<ItemStack> requirements) {
        ItemStack[] stacks = getItemStacks(slot, context);

        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                requirements.add(stack);
            }
        }
    }

    public static void initializeInventoryContents(BptSlotInfo slot, IBptContext context, Container inventory) {
        ItemStack[] stacks = new ItemStack[inventory.getContainerSize()];

        for (int i = 0; i < inventory.getContainerSize(); ++i) {
            stacks[i] = inventory.getItem(i);
        }

        setItemStacks(slot, context, stacks);
    }

    public static void buildInventoryContents(BptSlotInfo slot, IBptContext context, Container inventory) {
        ItemStack[] stacks = getItemStacks(slot, context);

        for (int i = 0; i < stacks.length && i < inventory.getContainerSize(); ++i) {
            inventory.setItem(i, stacks[i]);
        }
    }

    /** An entry that cannot be read (an item this game does not have) counts as empty. */
    public static ItemStack[] getItemStacks(BptSlotInfo slot, IBptContext context) {
        ListTag list = slot.cpt.getListOrEmpty("inv");

        if (list.isEmpty())
            return new ItemStack[0];

        RegistryOps<Tag> ops = registries(context).createSerializationContext(NbtOps.INSTANCE);
        ItemStack stacks[] = new ItemStack[list.size()];

        for (int i = 0; i < list.size(); ++i) {
            stacks[i] = ItemStack.OPTIONAL_CODEC.parse(ops, list.get(i)).result().orElse(ItemStack.EMPTY);
        }

        return stacks;
    }

    public static void setItemStacks(BptSlotInfo slot, IBptContext context, ItemStack[] stacks) {
        ListTag nbttaglist = new ListTag();
        RegistryOps<Tag> ops = registries(context).createSerializationContext(NbtOps.INSTANCE);

        for (int i = 0; i < stacks.length; ++i) {
            ItemStack stack = stacks[i];

            if (stack != null && !stack.isEmpty()) {
                nbttaglist.add(ItemStack.CODEC.encodeStart(ops, stack).getOrThrow());
            } else {
                nbttaglist.add(new CompoundTag());
            }
        }

        slot.cpt.put("inv", nbttaglist);
    }

    private static HolderLookup.Provider registries(IBptContext context) {
        return context.world().registryAccess();
    }

}
