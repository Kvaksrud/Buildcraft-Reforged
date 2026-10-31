/**
 * Copyright (c) SpaceToad, 2011-2012
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: no longer an Entity, as EntityLaser. The robot is state of the builder that
 * made it: TileBuilder calls onUpdate every tick on both sides and sends position, destination, motion and laser with
 * its network payload (4.2.2's spawn data and entity tracking); the server picks destinations and builds, the client
 * only moves along. Blocks are cleared and placed as the builder's owner (IRobotOwner, plan section 6): a refused or
 * unchangeable target is dropped from the queue and its items go back to the builder, where 4.2.2 cleared and placed
 * without asking and waited for ever at an unbreakable block. A template target is placed through the item's use
 * (4.2.2 threw "NOT IMPLEMENTED" there). The laser's power is FE.
 */

package buildcraftreforged.core;

import buildcraftreforged.api.core.Position;
import buildcraftreforged.core.blueprints.BptBuilderBase;
import buildcraftreforged.core.blueprints.BptContext;
import buildcraftreforged.core.blueprints.BptSlot;
import buildcraftreforged.core.blueprints.BptSlot.Mode;
import buildcraftreforged.core.utils.BCLog;
import buildcraftreforged.core.utils.BlockUtil;
import buildcraftreforged.core.utils.BuildCraftPlayer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

public class EntityRobot {

    /** Not in 4.2.2: what the robot needs from the builder it works for. */
    public interface IRobotOwner {

        /** The owner's fake player, as which blocks are cleared and placed. */
        @Nullable
        Player getRobotPlayer();

        /** Items the robot could not use: a target it was refused, or what an item's use left over. */
        void giveBack(ItemStack stack);

        /** A blueprint slot was built. */
        void onBuilt(BptSlot slot);
    }

    private Box box;
    private int destX, destY, destZ;
    public double posX, posY, posZ;
    public double motionX, motionY, motionZ;

    EntityEnergyLaser laser;

    public LinkedList<Action> targets = new LinkedList<Action>();
    public static int MAX_TARGETS = 20;
    public int wait = 0;
    /** Not in 4.2.2: position, destination or laser changed since the builder last sent them. */
    public boolean needsUpdate = true;
    private final @Nullable Level worldObj;

    private class Action {

        public Action(BptSlot slot, BptContext context) {
            this.slot = slot;
            this.context = context;
        }

        public Action(BptBuilderBase builder) {
            this.builder = builder;
        }

        @Nullable
        BptSlot slot;
        @Nullable
        BptBuilderBase builder;
        @Nullable
        BptContext context;
    }

    public EntityRobot(@Nullable Level world, Box box) {
        this.worldObj = world;
        this.box = box;
        init();
    }

    protected void init() {

        destX = (int) box.centerX();
        destY = (int) box.centerY();
        destZ = (int) box.centerZ();

        motionX = 0;
        motionY = 0;
        motionZ = 0;

        posX = destX;
        posY = destY;
        posZ = destZ;

        laser = new EntityEnergyLaser(worldObj, new Position(posX, posY, posZ), new Position(posX, posY, posZ));
    }

    /** 4.2.2's writeSpawnData and the tracked position and motion. */
    public void writeData(RegistryFriendlyByteBuf data) {
        box.writeToStream(data);
        data.writeInt(destX);
        data.writeInt(destY);
        data.writeInt(destZ);
        data.writeDouble(posX);
        data.writeDouble(posY);
        data.writeDouble(posZ);
        data.writeDouble(motionX);
        data.writeDouble(motionY);
        data.writeDouble(motionZ);
        laser.writeData(data);
        needsUpdate = false;
    }

    /** 4.2.2's readSpawnData, for the robot the client already has. */
    public void readData(RegistryFriendlyByteBuf data) {
        box.readFromStream(data);
        destX = data.readInt();
        destY = data.readInt();
        destZ = data.readInt();
        posX = data.readDouble();
        posY = data.readDouble();
        posZ = data.readDouble();
        motionX = data.readDouble();
        motionY = data.readDouble();
        motionZ = data.readDouble();
        laser.readData(data);
    }

    /** 4.2.2's onUpdate. The client moves the robot along its motion and leaves the rest to the server. */
    public void onUpdate(@Nullable IRobotOwner owner) {
        if (worldObj == null || worldObj.isClientSide() || owner == null) {
            if (!reachedDesination()) {
                setPosition(posX + motionX, posY + motionY, posZ + motionZ);
            }
            return;
        }

        move();
        build(owner);
        updateLaser();
    }

    private void setPosition(double x, double y, double z) {
        posX = x;
        posY = y;
        posZ = z;
    }

    protected void move() {

        setPosition(posX + motionX, posY + motionY, posZ + motionZ);

        if (reachedDesination()) {

            BlockIndex newDesination = getNewDestination();
            if (newDesination != null) {
                setDestination(newDesination.x, newDesination.y, newDesination.z);
            }

        }

    }

    protected @Nullable BlockIndex getNewDestination() {

        Box movementBoundary = new Box();
        movementBoundary.initialize(box);
        movementBoundary.expand(1);

        Box moveArea = new Box();
        moveArea.initialize(destX, destY, destZ, 1);

        List<BlockIndex> potentialDestinations = new ArrayList<BlockIndex>();
        for (BlockIndex blockIndex : moveArea.getBlocksInArea()) {

            if (BlockUtil.isSoftBlock(worldObj, blockIndex.x, blockIndex.y, blockIndex.z) && movementBoundary.contains(blockIndex)) {
                potentialDestinations.add(blockIndex);
            }
        }

        if (!potentialDestinations.isEmpty()) {

            int i = worldObj.getRandom().nextInt(potentialDestinations.size());
            return potentialDestinations.get(i);
        }

        return null;
    }

    /** 4.2.2 scaled the speed with the laser's MJ average; the average is FE now (MJ x 10). */
    protected void setDestination(int x, int y, int z) {

        destX = x;
        destY = y;
        destZ = z;

        double speed = laser.getPowerAverage() / 10 / 2 + 1;

        motionX = (destX - posX) / 75 * speed;
        motionY = (destY - posY) / 75 * speed;
        motionZ = (destZ - posZ) / 75 * speed;
        needsUpdate = true;
    }

    protected boolean reachedDesination() {
        double dx = posX - destX;
        double dy = posY - destY;
        double dz = posZ - destZ;

        if (Math.sqrt(dx * dx + dy * dy + dz * dz) <= .2)
            return true;

        return false;
    }

    protected void build(IRobotOwner owner) {

        updateWait();

        if (targets.size() > 0) {

            Action a = targets.getFirst();
            if (a.slot != null) {

                BptSlot target = a.slot;
                if (wait <= 0) {

                    if (!(worldObj instanceof ServerLevel world)) {
                        targets.pop();
                        return;
                    }

                    Player player = owner.getRobotPlayer();

                    if (player == null || !BlockUtil.canChangeBlock(worldObj, target.x, target.y, target.z)) {
                        // 4.2.2 left an unbreakable target (bedrock) at the head of the queue for ever.
                        refuse(owner, target);
                    } else if (target.mode == Mode.ClearIfInvalid) {

                        if (!target.isValid(a.context)) {
                            clear(world, player, target.pos());
                        }

                    } else if (target.stackToUse != null) {
                        placeStack(owner, world, player, target);
                    } else {

                        try {
                            buildSlot(owner, world, player, target, a.context);
                        } catch (Throwable t) {
                            // Defensive code against errors in implementers
                            BCLog.logger.error("EntityRobot.update", t);
                        }
                    }

                    targets.pop();
                    needsUpdate = true;
                }

            } else if (a.builder != null) {
                a.builder.postProcessing(worldObj);
                targets.pop();
            }
        }
    }

    /** 4.2.2 setBlockToAir, without drops. False when the owner may not break the block. */
    private static boolean clear(ServerLevel world, Player player, BlockPos pos) {
        if (!BuildCraftPlayer.canBreak(player, world, pos))
            return false;
        world.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        return true;
    }

    /** 4.2.2: setBlockToAir, then stackToUse.onItemUse; the item is used on the target itself, as the filler does. */
    private static void placeStack(IRobotOwner owner, ServerLevel world, Player player, BptSlot target) {
        BlockPos pos = target.pos();
        ItemStack stack = target.stackToUse;

        if (!world.getBlockState(pos).isAir() && !clear(world, player, pos)) {
            owner.giveBack(stack);
            return;
        }

        BuildCraftPlayer.useItemOn(player, stack, pos, Direction.UP);

        if (!stack.isEmpty()) {
            owner.giveBack(stack);
        }
    }

    /**
     * BptSlot.buildBlock as the owner: a block in the way must be breakable by the owner, and the placement goes
     * through EntityPlaceEvent. A refused slot gives its items back.
     */
    private static void buildSlot(IRobotOwner owner, ServerLevel world, Player player, BptSlot target,
            @Nullable BptContext context) {
        BlockPos pos = target.pos();

        if (target.state.isAir()) {
            clear(world, player, pos);
            return;
        }

        if (!world.getBlockState(pos).isAir() && !BuildCraftPlayer.canBreak(player, world, pos)) {
            refuse(owner, target);
            return;
        }

        if (BuildCraftPlayer.placeAs(player, world, () -> target.buildBlock(context))) {
            owner.onBuilt(target);
        } else {
            refuse(owner, target);
        }
    }

    private static void refuse(IRobotOwner owner, BptSlot target) {
        for (ItemStack stack : target.usedItems) {
            owner.giveBack(stack);
        }
        target.usedItems.clear();

        if (target.stackToUse != null) {
            owner.giveBack(target.stackToUse);
            target.stackToUse = null;
        }
    }

    public void updateWait() {

        if (targets.size() > 0)
            if (wait == 0) {
                wait = MAX_TARGETS - targets.size() + 2;
            } else {
                wait--;
            }
    }

    private void updateLaser() {

        if (laser == null)
            return;

        if (targets.size() > 0) {

            Action a = targets.getFirst();
            BptSlot target = a.slot;

            if (target != null) {

                Position head = laser.getHead();
                Position tail = laser.getTail();
                if (!laser.isVisible() || head.x != posX || head.y != posY || head.z != posZ
                        || tail.x != target.x + 0.5 || tail.y != target.y + 0.5 || tail.z != target.z + 0.5) {
                    laser.setPositions(new Position(posX, posY, posZ), new Position(target.x + 0.5, target.y + 0.5, target.z + 0.5));
                    laser.show();
                    needsUpdate = true;
                }
            }
        } else if (laser.isVisible()) {
            laser.hide();
            needsUpdate = true;
        }

        laser.pushPower(Math.round(((float) targets.size()) / ((float) MAX_TARGETS) * 40F));
    }

    public void scheduleContruction(@Nullable BptSlot slot, BptContext context) {

        if (slot != null) {
            targets.add(new Action(slot, context));

        }
    }

    public void markEndOfBlueprint(BptBuilderBase builder) {
        targets.add(new Action(builder));
    }

    public boolean readyToBuild() {
        return targets.size() < MAX_TARGETS;
    }

    public boolean done() {
        return targets.isEmpty();
    }

    public void setBox(Box box) {

        this.box = box;
        setDestination((int) box.centerX(), (int) box.centerY(), (int) box.centerZ());
    }

    public Box getBox() {
        return box;
    }

    public EntityEnergyLaser getLaser() {
        return laser;
    }

    /**
     * 4.2.2 killed the laser entity with the robot. Items the robot still had queued go back to the builder (4.2.2
     * lost them with the entity).
     */
    public void setDead(@Nullable IRobotOwner owner) {

        if (laser != null) {
            laser.setDead();
        }

        if (owner != null) {
            for (Action a : targets) {
                if (a.slot != null) {
                    refuse(owner, a.slot);
                }
            }
        }
        targets.clear();
    }

}
