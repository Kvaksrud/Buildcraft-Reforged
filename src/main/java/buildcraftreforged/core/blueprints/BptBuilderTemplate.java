/**
 * Copyright (c) SpaceToad, 2011-2012
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: a slot is empty when its state is air (4.2.2 blockId 0); a filled slot counts
 * as built when the world holds any block that is not soft there (4.2.2 wanted block id 1, stone: docs/BUGFIXES.md);
 * decrStackSize becomes removeItem; slots outside the world's height and blocks that cannot be changed are passed over
 * (BuildCraft#1652, #1789); missingMaterials (status panel) is new.
 */

package buildcraftreforged.core.blueprints;

import buildcraftreforged.core.IBuilderInventory;
import buildcraftreforged.core.blueprints.BptSlot.Mode;
import buildcraftreforged.core.utils.BlockUtil;
import java.util.LinkedList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public class BptBuilderTemplate extends BptBuilderBase {

    LinkedList<BptSlot> clearList = new LinkedList<BptSlot>();
    LinkedList<BptSlot> buildList = new LinkedList<BptSlot>();

    public BptBuilderTemplate(BptBase bluePrint, Level world, int x, int y, int z) {
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

                    if (slot == null || slot.state.isAir()) {
                        slot = new BptSlot();
                        slot.x = xCoord;
                        slot.y = yCoord;
                        slot.z = zCoord;

                        slot.mode = Mode.ClearIfInvalid;

                        clearList.add(slot);
                    }
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

                    if (!slot.state.isAir()) {
                        buildList.add(slot);
                    }
                }
            }
        }
    }

    private void checkDone() {
        if (clearList.size() == 0 && buildList.size() == 0) {
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

        if (buildList.size() != 0) {
            BptSlot slot = internalGetNextBlock(world, inv, buildList);
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
        BptSlot result = null;

        while (list.size() > 0) {
            BptSlot slot = list.getFirst();

            // BuildCraft#1789: a block the builder cannot change (bedrock, lava, oil) is passed over; 4.2.2's robot
            // waited at it for ever.
            if (!BlockUtil.canChangeBlock(world, slot.x, slot.y, slot.z)) {
                list.removeFirst();
                continue;
            }

            // Note from CJ: I have no idea what this code is supposed to do, so I'm not touching it.
            if (isBuilt(world, slot)) {
                list.removeFirst();
            } else if (slot.mode == Mode.ClearIfInvalid) {
                result = slot;
                list.removeFirst();
                break;
            } else {
                int size = inv.getContainerSize();
                for (int i = 0; i < size; ++i) {
                    if (!inv.isBuildingMaterial(i)) {
                        continue;
                    }

                    ItemStack stack = inv.removeItem(i, 1);

                    if (stack != null && stack.getCount() > 0) {
                        result = slot.clone();
                        result.stackToUse = stack;
                        list.removeFirst();
                        break;
                    }
                }

                if (result == null) {
                    missingMaterials = true;
                }

                break;
            }
        }

        return result;
    }

    /**
     * 4.2.2 compared the slot's block id with the world's: air for an empty slot, block id 1 (stone, the mask) for a
     * filled one, so a filled spot was built again unless it held stone. A filled spot now counts when the world has a
     * block there that is not soft.
     */
    private static boolean isBuilt(Level world, BptSlot slot) {
        if (slot.state.isAir())
            return world.getBlockState(slot.pos()).isAir();

        return !BlockUtil.isSoftBlock(world, slot.x, slot.y, slot.z);
    }
}
