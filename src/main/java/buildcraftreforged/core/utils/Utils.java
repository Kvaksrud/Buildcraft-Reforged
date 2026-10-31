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
import buildcraftreforged.api.core.LaserKind;
import buildcraftreforged.api.core.Position;
import buildcraftreforged.api.transport.IPipeTile;
import buildcraftreforged.api.transport.IPipeTile.PipeType;
import buildcraftreforged.core.EntityBlock;
import buildcraftreforged.core.IFramePipeConnection;
import buildcraftreforged.core.inventory.ITransactor;
import buildcraftreforged.core.inventory.Transactor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.jspecify.annotations.Nullable;

/*
 * Not ported yet, waiting for the classes they call:
 * - addToRandomInventoryAround: ported (#72); its TileEngine exclusion waits for #36.
 * - addToRandomPipeAround: ported (#66).
 * - checkPipesConnections: IPipeTile (#34, #48).
 * - checkLegacyPipesConnections: ported (#67).
 * - preDestroyBlock: TileBuildCraft (#21), IDropControlInventory and InvUtils (#23).
 * Dropped: getItemData, the stack's NBT compound; item data lives in data components (docs/PLAN.md section 6).
 * Dropped: handleBufferedDescription; description data goes through vanilla getUpdateTag now (#20).
 */
public class Utils {

    public static final Random RANDOM = new Random();
    private static final List<Direction> directions = new ArrayList<Direction>(Arrays.asList(Direction.values()));

    /**
     * Puts stack into one inventory next to the given position, picked at random among those that take some of it.
     * Returns the number of items added. New: a neighbour that is not a Container (another mod's block) is reached
     * through its item capability, as #23's TransactorHandler does.
     */
    public static int addToRandomInventoryAround(Level world, int x, int y, int z, ItemStack stack) {
        Collections.shuffle(directions);
        for (Direction orientation : directions) {
            Position pos = new Position(x, y, z, orientation);
            pos.moveForwards(1.0);

            BlockPos blockPos = new BlockPos((int) pos.x, (int) pos.y, (int) pos.z);
            // 1.6.4's getBlockTileEntity did not load chunks; 26.x's getBlockEntity would.
            if (!world.isLoaded(blockPos))
                continue;
            BlockEntity tileInventory = world.getBlockEntity(blockPos);
            ITransactor transactor = tileInventory == null ? null : Transactor.getTransactorFor(tileInventory);
            if (transactor == null && tileInventory != null) {
                var handler = world.getCapability(Capabilities.Item.BLOCK, blockPos, orientation.getOpposite());
                if (handler != null)
                    transactor = Transactor.getTransactorFor(handler);
            }
            // SEAM(#36): 4.2.2 also skipped a TileEngine (`!(tileInventory instanceof TileEngine)`).
            if (transactor != null && transactor.add(stack, orientation.getOpposite(), false).getCount() > 0) {
                return transactor.add(stack, orientation.getOpposite(), true).getCount();
            }
        }
        return 0;

    }

    /**
     * Look around the tile given in parameter in all 6 position, tries to add
     * the items to a random pipe entry around. Will make sure that the location
     * from which the items are coming from (identified by the from parameter)
     * isn't used again so that entities doesn't go backwards. Returns true if
     * successful, false otherwise.
     *
     * <p>Ported in #66: 4.2.2's ForgeDirection.UNKNOWN is a null from. The pipe routes the stack at once
     * (docs/TRANSPORT.md 6.2); the return value is the number of items it took.
     */
    public static int addToRandomPipeAround(Level world, int x, int y, int z, @Nullable Direction from, ItemStack stack) {
        List<IPipeTile> possiblePipes = new ArrayList<IPipeTile>();
        List<Direction> pipeDirections = new ArrayList<Direction>();

        for (Direction side : Direction.values()) {
            if (from != null && from.getOpposite() == side)
                continue;

            Position pos = new Position(x, y, z, side);

            pos.moveForwards(1.0);

            BlockPos blockPos = new BlockPos((int) pos.x, (int) pos.y, (int) pos.z);
            // 1.6.4's getBlockTileEntity did not load chunks; 26.x's getBlockEntity would.
            if (!world.isLoaded(blockPos))
                continue;
            BlockEntity tile = world.getBlockEntity(blockPos);

            if (tile instanceof IPipeTile) {
                IPipeTile pipe = (IPipeTile) tile;
                if (pipe.getPipeType() != PipeType.ITEM)
                    continue;
                if (!pipe.isPipeConnected(side.getOpposite()))
                    continue;

                possiblePipes.add(pipe);
                pipeDirections.add(side.getOpposite());
            }
        }

        if (possiblePipes.size() > 0) {
            int choice = RANDOM.nextInt(possiblePipes.size());

            IPipeTile pipeEntry = possiblePipes.get(choice);

            return pipeEntry.injectItem(stack, true, pipeDirections.get(choice));
        }
        return 0;
    }

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

    /**
     * Returns the laser as EntityBlock render data. 4.2.2 spawned it as an entity; the caller now keeps it and its
     * BlockEntityRenderer draws it. CoreProxyClient.newEntityBlock's texture choice is done here, on both sides.
     */
    public static @Nullable EntityBlock createLaser(Level world, Position p1, Position p2, LaserKind kind) {
        if (p1.equals(p2)) {
            return null;
        }

        double iSize = p2.x - p1.x;
        double jSize = p2.y - p1.y;
        double kSize = p2.z - p1.z;

        double i = p1.x;
        double j = p1.y;
        double k = p1.z;

        if (iSize != 0) {
            i += 0.5;
            j += 0.45;
            k += 0.45;

            jSize = 0.10;
            kSize = 0.10;
        } else if (jSize != 0) {
            i += 0.45;
            j += 0.5;
            k += 0.45;

            iSize = 0.10;
            kSize = 0.10;
        } else if (kSize != 0) {
            i += 0.45;
            j += 0.45;
            k += 0.5;

            iSize = 0.10;
            jSize = 0.10;
        }

        EntityBlock block = new EntityBlock(world, i, j, k, iSize, jSize, kSize);
        switch (kind) {
            case Blue:
                block.texture = EntityBlock.blueLaserTexture;
                break;

            case Red:
                block.texture = EntityBlock.redLaserTexture;
                break;

            case Stripes:
                block.texture = EntityBlock.stripesLaserTexture;
                break;
        }
        block.setBrightness(210);

        return block;
    }

    public static @Nullable EntityBlock[] createLaserBox(Level world, double xMin, double yMin, double zMin, double xMax, double yMax, double zMax, LaserKind kind) {
        EntityBlock lasers[] = new EntityBlock[12];
        Position[] p = new Position[8];

        p[0] = new Position(xMin, yMin, zMin);
        p[1] = new Position(xMax, yMin, zMin);
        p[2] = new Position(xMin, yMax, zMin);
        p[3] = new Position(xMax, yMax, zMin);
        p[4] = new Position(xMin, yMin, zMax);
        p[5] = new Position(xMax, yMin, zMax);
        p[6] = new Position(xMin, yMax, zMax);
        p[7] = new Position(xMax, yMax, zMax);

        lasers[0] = Utils.createLaser(world, p[0], p[1], kind);
        lasers[1] = Utils.createLaser(world, p[0], p[2], kind);
        lasers[2] = Utils.createLaser(world, p[2], p[3], kind);
        lasers[3] = Utils.createLaser(world, p[1], p[3], kind);
        lasers[4] = Utils.createLaser(world, p[4], p[5], kind);
        lasers[5] = Utils.createLaser(world, p[4], p[6], kind);
        lasers[6] = Utils.createLaser(world, p[5], p[7], kind);
        lasers[7] = Utils.createLaser(world, p[6], p[7], kind);
        lasers[8] = Utils.createLaser(world, p[0], p[4], kind);
        lasers[9] = Utils.createLaser(world, p[1], p[5], kind);
        lasers[10] = Utils.createLaser(world, p[2], p[6], kind);
        lasers[11] = Utils.createLaser(world, p[3], p[7], kind);

        return lasers;
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

    public static boolean checkLegacyPipesConnections(BlockGetter blockAccess, int x1, int y1, int z1, int x2, int y2, int z2) {

        Block b1 = blockAccess.getBlockState(new BlockPos(x1, y1, z1)).getBlock();
        Block b2 = blockAccess.getBlockState(new BlockPos(x2, y2, z2)).getBlock();

        if (!(b1 instanceof IFramePipeConnection) && !(b2 instanceof IFramePipeConnection)) {
            return false;
        }

        if (b1 instanceof IFramePipeConnection && !((IFramePipeConnection) b1).isPipeConnected(blockAccess, x1, y1, z1, x2, y2, z2)) {
            return false;
        }

        if (b2 instanceof IFramePipeConnection && !((IFramePipeConnection) b2).isPipeConnected(blockAccess, x2, y2, z2, x1, y1, z1)) {
            return false;
        }

        return true;

    }
}
