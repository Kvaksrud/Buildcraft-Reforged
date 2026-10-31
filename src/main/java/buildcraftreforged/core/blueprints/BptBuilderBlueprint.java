/**
 * Copyright (c) SpaceToad, 2011-2012
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: slots hold block states (blockId 0 is air, isOpaqueCube is isSolidRender);
 * item ids and damage become the item and its components; neededItems is a sorted list; the creative check reads the
 * server's game type; useRequirements stays inside the inventory, skips the blueprint slot, puts an emptied stack's
 * crafting remainder back and returns what it used; postProcessing passes over slots whose block was not built;
 * slots outside the world's height and blocks that cannot be changed are passed over (BuildCraft#1652, #1789);
 * missingMaterials (status panel) is new.
 */

package buildcraftreforged.core.blueprints;

import buildcraftreforged.core.IBuilderInventory;
import buildcraftreforged.core.blueprints.BptSlot.Mode;
import buildcraftreforged.core.utils.BCLog;
import buildcraftreforged.core.utils.BlockUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public class BptBuilderBlueprint extends BptBuilderBase {

    LinkedList<BptSlot> clearList = new LinkedList<BptSlot>();
    LinkedList<BptSlot> primaryList = new LinkedList<BptSlot>();
    LinkedList<BptSlot> secondaryList = new LinkedList<BptSlot>();

    LinkedList<BptSlot> postProcessingList = new LinkedList<BptSlot>();

    /**
     * 4.2.2's TreeSet ordered by stack size, then item id, then damage, all descending; stacks the comparator called
     * equal were dropped. The order is kept, nothing is dropped.
     */
    public List<ItemStack> neededItems = new ArrayList<ItemStack>();

    private static final Comparator<ItemStack> NEEDED_ORDER = (o1, o2) -> {
        if (o1.getCount() > o2.getCount())
            return -1;
        else if (o1.getCount() < o2.getCount())
            return 1;

        int id1 = BuiltInRegistries.ITEM.getId(o1.getItem());
        int id2 = BuiltInRegistries.ITEM.getId(o2.getItem());

        if (id1 > id2)
            return -1;
        else if (id1 < id2)
            return 1;

        return 0;
    };

    public BptBuilderBlueprint(BptBlueprint bluePrint, Level world, int x, int y, int z) {
        super(bluePrint, world, x, y, z);

        for (int j = bluePrint.sizeY - 1; j >= 0; --j) {
            for (int i = 0; i < bluePrint.sizeX; ++i) {
                for (int k = 0; k < bluePrint.sizeZ; ++k) {
                    int xCoord = i + x - bluePrint.anchorX;
                    int yCoord = j + y - bluePrint.anchorY;
                    int zCoord = k + z - bluePrint.anchorZ;

                    // BuildCraft#1652: nothing is built or cleared outside the world's height.
                    if (world.isOutsideBuildHeight(yCoord)) {
                        continue;
                    }

                    BptSlot slot = bluePrint.getSlot(i, j, k);

                    if (slot != null) {
                        slot = slot.clone();
                    } else {
                        slot = new BptSlot();
                    }

                    slot.x = xCoord;
                    slot.y = yCoord;
                    slot.z = zCoord;

                    slot.mode = Mode.ClearIfInvalid;

                    clearList.add(slot);

                }
            }
        }

        for (int j = 0; j < bluePrint.sizeY; ++j) {
            for (int i = 0; i < bluePrint.sizeX; ++i) {
                for (int k = 0; k < bluePrint.sizeZ; ++k) {
                    int xCoord = i + x - bluePrint.anchorX;
                    int yCoord = j + y - bluePrint.anchorY;
                    int zCoord = k + z - bluePrint.anchorZ;

                    // BuildCraft#1652: nothing is built or cleared outside the world's height.
                    if (world.isOutsideBuildHeight(yCoord)) {
                        continue;
                    }

                    BptSlot slot = bluePrint.getSlot(i, j, k);

                    if (slot != null) {
                        slot = slot.clone();
                    } else {
                        slot = new BptSlot();
                    }

                    slot.x = xCoord;
                    slot.y = yCoord;
                    slot.z = zCoord;

                    slot.mode = Mode.Build;

                    if (!slot.state.isAir() && slot.state.isSolidRender()) {
                        primaryList.add(slot);
                    } else {
                        secondaryList.add(slot);
                    }

                    if (!slot.state.isAir()) {
                        postProcessingList.add(slot.clone());
                    }
                }
            }
        }

        recomputeNeededItems();
    }

    private void checkDone() {
        recomputeNeededItems();

        if (clearList.size() == 0 && primaryList.size() == 0 && secondaryList.size() == 0) {
            done = true;
        } else {
            done = false;
        }
    }

    @Override
    public @Nullable BptSlot getNextBlock(Level world, IBuilderInventory inv) {
        missingMaterials = false;

        if (clearList.size() != 0) {
            BptSlot slot = internalGetNextBlock(world, inv, clearList);
            checkDone();

            if (slot != null)
                return slot;
            else
                return null;
        }

        if (primaryList.size() != 0) {
            BptSlot slot = internalGetNextBlock(world, inv, primaryList);
            checkDone();

            if (slot != null)
                return slot;
            else
                return null;
        }

        if (secondaryList.size() != 0) {
            BptSlot slot = internalGetNextBlock(world, inv, secondaryList);
            checkDone();

            if (slot != null)
                return slot;
            else
                return null;
        }

        checkDone();

        return null;
    }

    public @Nullable BptSlot internalGetNextBlock(Level world, IBuilderInventory inv, LinkedList<BptSlot> list) {
        LinkedList<BptSlot> failSlots = new LinkedList<BptSlot>();

        BptSlot result = null;

        while (list.size() > 0) {
            BptSlot slot = list.removeFirst();

            // BuildCraft#1789: a block the builder cannot change (bedrock, lava, oil) is passed over; 4.2.2's robot
            // waited at it for ever.
            if (!BlockUtil.canChangeBlock(world, slot.x, slot.y, slot.z)) {
                continue;
            }

            boolean getNext = false;

            try {
                getNext = !slot.isValid(context) && !slot.ignoreBuilding();
            } catch (Throwable t) {
                // Defensive code against errors in implementers
                BCLog.logger.error("BptBuilderBlueprint.internalGetBlock", t);
                getNext = false;
            }

            if (getNext)
                if (slot.mode == Mode.ClearIfInvalid) {
                    if (!BlockUtil.isSoftBlock(world, slot.x, slot.y, slot.z)) {
                        result = slot;
                        break;
                    }
                } else if (isCreative(world)) {
                    // In creative, we don't use blocks given in the builder

                    result = slot;

                    break;
                } else if (checkRequirements(inv, slot)) {
                    slot.usedItems = useRequirements(inv, slot);

                    result = slot;
                    break;
                } else {
                    failSlots.add(slot);
                }
        }

        list.addAll(failSlots);

        if (result == null && !failSlots.isEmpty()) {
            missingMaterials = true;
        }

        return result;
    }

    /** 4.2.2 read world.getWorldInfo().getGameType(), the world's game type, not a player's. */
    public static boolean isCreative(Level world) {
        return world instanceof ServerLevel serverLevel
                && serverLevel.getServer().getWorldData().getGameType() == GameType.CREATIVE;
    }

    /**
     * 4.2.2 matched item id, and damage unless the item was damageable; the damage of a non-damageable item is one of
     * its components now, so those must all match.
     */
    private static boolean matches(ItemStack reqStk, ItemStack invStk) {
        if (invStk.isEmpty() || reqStk.getItem() != invStk.getItem())
            return false;

        return invStk.isDamageableItem() || ItemStack.isSameItemSameComponents(reqStk, invStk);
    }

    public boolean checkRequirements(IBuilderInventory inv, BptSlot slot) {
        if (slot.state.isAir())
            return true;

        LinkedList<ItemStack> tmpReq = new LinkedList<ItemStack>();
        LinkedList<ItemStack> tmpInv = new LinkedList<ItemStack>();

        try {
            for (ItemStack stk : slot.getRequirements(context))
                if (stk != null && !stk.isEmpty()) {
                    tmpReq.add(stk.copy());
                }
        } catch (Throwable t) {
            // Defensive code against errors in implementers
            BCLog.logger.error("BptBuilderBlueprint.checkRequirements", t);
        }

        int size = inv.getContainerSize();
        for (int i = 0; i < size; ++i) {
            if (!inv.isBuildingMaterial(i)) {
                continue;
            }

            if (!inv.getItem(i).isEmpty()) {
                tmpInv.add(inv.getItem(i).copy());
            }
        }

        for (ItemStack reqStk : tmpReq) {
            for (ItemStack invStk : tmpInv) {
                if (matches(reqStk, invStk)) {

                    try {

                        slot.useItem(context, reqStk, invStk);
                    } catch (Throwable t) {
                        // Defensive code against errors in implementers
                        BCLog.logger.error("BptBuilderBlueprint.checkRequirements", t);
                    }

                    if (reqStk.getCount() == 0) {
                        break;
                    }
                }
            }

            if (reqStk.getCount() != 0)
                return false;
        }

        return true;
    }

    /**
     * Takes the slot's requirements out of inv and returns the stacks used (what useItem returned each time). 4.2.2
     * returned nothing, ran its loop one slot past the end and did not skip the blueprint slot (docs/BUGFIXES.md).
     */
    public LinkedList<ItemStack> useRequirements(IBuilderInventory inv, BptSlot slot) {
        LinkedList<ItemStack> used = new LinkedList<ItemStack>();

        if (slot.state.isAir())
            return used;

        LinkedList<ItemStack> tmpReq = new LinkedList<ItemStack>();

        try {
            for (ItemStack stk : slot.getRequirements(context))
                if (stk != null && !stk.isEmpty()) {
                    tmpReq.add(stk.copy());
                }
        } catch (Throwable t) {
            // Defensive code against errors in implementers
            BCLog.logger.error("BptBuilderBlueprint.useRequirements", t);

        }

        ListIterator<ItemStack> itr = tmpReq.listIterator();

        while (itr.hasNext()) {
            ItemStack reqStk = itr.next();
            int size = inv.getContainerSize();
            for (int i = 0; i < size; ++i) {
                if (!inv.isBuildingMaterial(i)) {
                    continue;
                }

                ItemStack invStk = inv.getItem(i);

                if (matches(reqStk, invStk)) {
                    // 4.2.2's useItem turned an emptied bucket into the empty bucket itself.
                    ItemStackTemplate template = invStk.getItem().getCraftingRemainder(invStk);
                    ItemStack remainder = template == null ? ItemStack.EMPTY : template.create();

                    try {
                        used.add(slot.useItem(context, reqStk, invStk));
                    } catch (Throwable t) {
                        // Defensive code against errors in implementers
                        BCLog.logger.error("BptBuilderBlueprint.useRequirements", t);
                    }

                    if (invStk.isEmpty()) {
                        inv.setItem(i, remainder);
                    } else {
                        inv.setItem(i, invStk);
                    }

                    if (reqStk.getCount() == 0) {
                        break;
                    }
                }
            }

            if (reqStk.getCount() != 0)
                return used;
        }

        return used;
    }

    public void recomputeNeededItems() {
        neededItems.clear();

        List<ItemStack> computeStacks = new ArrayList<ItemStack>();

        for (BptSlot slot : primaryList) {

            LinkedList<ItemStack> stacks = new LinkedList<ItemStack>();

            try {
                stacks = slot.getRequirements(context);
            } catch (Throwable t) {
                // Defensive code against errors in implementers
                BCLog.logger.error("BptBuilderBlueprint.recomputeIfNeeded", t);
            }

            for (ItemStack stack : stacks) {
                add(computeStacks, stack);
            }
        }

        for (BptSlot slot : secondaryList) {
            LinkedList<ItemStack> stacks = new LinkedList<ItemStack>();

            try {
                stacks = slot.getRequirements(context);
            } catch (Throwable t) {
                // Defensive code against errors in implementers
                BCLog.logger.error("BptBuilderBlueprint.recomputeIfNeeded", t);
            }

            for (ItemStack stack : stacks) {
                add(computeStacks, stack);
            }
        }

        for (ItemStack stack : computeStacks)
            if (stack.isDamageableItem()) {
                neededItems.add(new ItemStack(stack.getItem()));
            } else {
                neededItems.add(stack);
            }

        neededItems.sort(NEEDED_ORDER);
    }

    /** 4.2.2's computeStacks map, keyed by item and damage: stacks of the same item and components add up. */
    private static void add(List<ItemStack> computeStacks, @Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        for (ItemStack existing : computeStacks) {
            if (ItemStack.isSameItemSameComponents(existing, stack)) {
                existing.grow(stack.getCount());
                return;
            }
        }

        computeStacks.add(stack.copy());
    }

    /**
     * New: a slot whose block is not in the world (its placement was refused or skipped) is passed over, so a pipe's
     * post-processing never touches a block the builder did not place.
     */
    @Override
    public void postProcessing(Level world) {
        for (BptSlot s : postProcessingList) {
            if (world.getBlockState(s.pos()).getBlock() != s.state.getBlock())
                continue;

            try {
                s.postProcessing(context);
            } catch (Throwable t) {
                // Defensive code against errors in implementers
                BCLog.logger.error("BptBuilderBlueprint.postProcessing", t);
            }
        }
    }

}
