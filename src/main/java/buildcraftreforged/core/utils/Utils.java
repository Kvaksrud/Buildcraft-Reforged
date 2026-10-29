/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: ForgeDirection becomes Direction, World becomes Level, TileEntity becomes
 * BlockEntity, IInventory becomes Container, NBT becomes ValueInput/ValueOutput and item NBT becomes data components;
 * the methods whose dependencies are not ported yet are listed below with the issue that brings them back.
 */
package buildcraftreforged.core.utils;

import buildcraftreforged.api.core.IAreaProvider;
import buildcraftreforged.api.core.Position;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/*
 * Not ported yet, waiting for the classes they call:
 * - addToRandomInventoryAround: ITransactor/Transactor (#23) and TileEngine (#36).
 * - addToRandomPipeAround, checkPipesConnections: IPipeTile (#34, #48).
 * - checkLegacyPipesConnections: IFramePipeConnection (#67).
 * - createLaser, createLaserBox: EntityBlock lasers (#29).
 * - preDestroyBlock: TileBuildCraft (#21), IDropControlInventory and InvUtils (#23).
 * Dropped: getItemData, the stack's NBT compound; item data lives in data components (docs/PLAN.md section 6).
 * Dropped: handleBufferedDescription; description data goes through vanilla getUpdateTag now (#20).
 */
public class Utils {

    public static final Random RANDOM = new Random();
    private static final List<Direction> directions = new ArrayList<Direction>(Arrays.asList(Direction.values()));

    public static Direction get2dOrientation(Position pos1, Position pos2) {
        double Dx = pos1.x - pos2.x;
        double Dz = pos1.z - pos2.z;
        double angle = Math.atan2(Dz, Dx) / Math.PI * 180 + 180;

        if (angle < 45 || angle > 315) {
            return Direction.EAST;
        } else if (angle < 135) {
            return Direction.SOUTH;
        } else if (angle < 225) {
            return Direction.WEST;
        } else {
            return Direction.NORTH;
        }
    }

    public static Direction get3dOrientation(Position pos1, Position pos2) {
        double Dx = pos1.x - pos2.x;
        double Dy = pos1.y - pos2.y;
        double angle = Math.atan2(Dy, Dx) / Math.PI * 180 + 180;

        if (angle > 45 && angle < 135) {
            return Direction.UP;
        } else if (angle > 225 && angle < 315) {
            return Direction.DOWN;
        } else {
            return get2dOrientation(pos1, pos2);
        }
    }

    public static @Nullable BlockEntity getTile(Level world, Position pos, Direction step) {
        Position tmp = new Position(pos);
        tmp.orientation = step;
        tmp.moveForwards(1.0);

        return world.getBlockEntity(new BlockPos((int) tmp.x, (int) tmp.y, (int) tmp.z));
    }

    /**
     * Ensures that the given inventory is the full inventory, i.e. takes double
     * chests into account.
     *
     * @param inv
     * @return Modified inventory if double chest, unmodified otherwise.
     */
    public static Container getInventory(Container inv) {
        if (inv instanceof ChestBlockEntity chest && chest.getLevel() != null && chest.getBlockState().getBlock() instanceof ChestBlock block) {
            Container combined = ChestBlock.getContainer(block, chest.getBlockState(), chest.getLevel(), chest.getBlockPos(), true);

            if (combined != null) {
                return combined;
            }
            return inv;
        }
        return inv;
    }

    public static @Nullable IAreaProvider getNearbyAreaProvider(Level world, int i, int j, int k) {
        BlockEntity a1 = world.getBlockEntity(new BlockPos(i + 1, j, k));
        BlockEntity a2 = world.getBlockEntity(new BlockPos(i - 1, j, k));
        BlockEntity a3 = world.getBlockEntity(new BlockPos(i, j, k + 1));
        BlockEntity a4 = world.getBlockEntity(new BlockPos(i, j, k - 1));
        BlockEntity a5 = world.getBlockEntity(new BlockPos(i, j + 1, k));
        BlockEntity a6 = world.getBlockEntity(new BlockPos(i, j - 1, k));

        if (a1 instanceof IAreaProvider) {
            return (IAreaProvider) a1;
        }

        if (a2 instanceof IAreaProvider) {
            return (IAreaProvider) a2;
        }

        if (a3 instanceof IAreaProvider) {
            return (IAreaProvider) a3;
        }

        if (a4 instanceof IAreaProvider) {
            return (IAreaProvider) a4;
        }

        if (a5 instanceof IAreaProvider) {
            return (IAreaProvider) a5;
        }

        if (a6 instanceof IAreaProvider) {
            return (IAreaProvider) a6;
        }

        return null;
    }

    /** Adds a lore line. {@code tag} was the NBT name of the 4.2.2 lore string; lore lines have no names now. */
    public static void addItemToolTip(ItemStack stack, String tag, String msg) {
        stack.update(DataComponents.LORE, ItemLore.EMPTY, lore -> lore.withLineAdded(Component.literal(msg)));
    }

    public static void writeInvToNBT(Container inv, String tag, ValueOutput data) {
        ValueOutput.TypedOutputList<ItemStackWithSlot> list = data.list(tag, ItemStackWithSlot.CODEC);
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            ItemStack stack = inv.getItem(slot);
            if (!stack.isEmpty()) {
                list.add(new ItemStackWithSlot(slot, stack));
            }
        }
    }

    public static void readInvFromNBT(Container inv, String tag, ValueInput data) {
        for (ItemStackWithSlot itemTag : data.listOrEmpty(tag, ItemStackWithSlot.CODEC)) {
            int slot = itemTag.slot();
            if (slot >= 0 && slot < inv.getContainerSize()) {
                inv.setItem(slot, itemTag.stack());
            }
        }
    }

    public static void readStacksFromNBT(ValueInput nbt, String name, ItemStack[] stacks) {
        List<ItemStack> nbttaglist = nbt.listOrEmpty(name, ItemStack.OPTIONAL_CODEC).stream().toList();

        for (int i = 0; i < stacks.length; ++i) {
            if (i < nbttaglist.size()) {
                stacks[i] = nbttaglist.get(i);
            } else {
                stacks[i] = ItemStack.EMPTY;
            }
        }
    }

    public static void writeStacksToNBT(ValueOutput nbt, String name, ItemStack[] stacks) {
        ValueOutput.TypedOutputList<ItemStack> nbttaglist = nbt.list(name, ItemStack.OPTIONAL_CODEC);

        for (int i = 0; i < stacks.length; ++i) {
            nbttaglist.add(stacks[i] == null ? ItemStack.EMPTY : stacks[i]);
        }
    }

    /** ItemStack.EMPTY where 4.2.2 returned null. */
    public static ItemStack consumeItem(ItemStack stack) {
        if (stack.getCount() == 1) {
            ItemStackTemplate remainder = stack.getItem().getCraftingRemainder(stack);
            if (remainder != null) {
                return remainder.create();
            } else {
                return ItemStack.EMPTY;
            }
        } else {
            stack.split(1);

            return stack;
        }
    }

    public static <T> T[] concat(T[] first, T[] second) {
        T[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    public static int[] concat(int[] first, int[] second) {
        int[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    public static float[] concat(float[] first, float[] second) {
        float[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    public static int[] createSlotArray(int first, int count) {
        int[] slots = new int[count];
        for (int k = first; k < first + count; k++) {
            slots[k - first] = k;
        }
        return slots;
    }
}
