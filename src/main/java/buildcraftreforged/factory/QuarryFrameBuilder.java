/*
 * Buildcraft Reforged, 2026. Not a 4.2.2 class: the parts of 4.2.2's core/blueprints/BptBlueprint,
 * BptBuilderBlueprint, BptSlot and core/EntityRobot that TileQuarry used to build its frame, ported as one small class
 * so the quarry does not wait for the builders port (#78). Each method keeps the 4.2.2 method's name and control flow;
 * what the quarry's blueprint never contains (other blocks, item requirements, rotation, post-processing, creative
 * mode) is left out. The robot is no longer an entity: it keeps 4.2.2's queue and timing, but does not fly about or
 * draw its laser (docs/PORTING-NOTES.md, "Quarry (#67)").
 */
package buildcraftreforged.factory;

import buildcraftreforged.core.Box;
import buildcraftreforged.core.utils.BlockUtil;
import buildcraftreforged.core.utils.BuildCraftPlayer;
import java.util.LinkedList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;

public class QuarryFrameBuilder {

    /** BptSlot.Mode. */
    public enum Mode {
        ClearIfInvalid, Build
    }

    /** A BptSlot of the quarry's blueprint: a frame block, or air (4.2.2 block id 0). */
    public static class Slot {
        public final int x, y, z;
        public final boolean frame;
        public final Mode mode;

        Slot(int x, int y, int z, boolean frame, Mode mode) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.frame = frame;
            this.mode = mode;
        }

        /** BptBlock.isValid: the world holds the slot's block (any frame state counts; there is no meta any more). */
        public boolean isValid(Level world) {
            BlockState state = world.getBlockState(new BlockPos(x, y, z));
            return frame ? state.is(FactoryRegistry.frameBlock.get()) : state.isAir();
        }

        /**
         * BptBlock.buildBlock, as the owner's fake player: a frame is placed (EntityPlaceEvent), air replaces what is
         * there (BreakBlockEvent first, no drops, as 4.2.2's setBlock with id 0). False when the owner was refused.
         */
        boolean buildBlock(ServerLevel world, FakePlayer owner) {
            BlockPos pos = new BlockPos(x, y, z);
            if (frame)
                return BuildCraftPlayer.placeBlock(owner, world, pos, FactoryRegistry.frameBlock.get().getConnectedState(world, pos));
            return clear(world, owner);
        }

        /** EntityRobot's ClearIfInvalid case: setBlockToAir, without drops, if the owner may break the block. */
        boolean clear(ServerLevel world, FakePlayer owner) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!BuildCraftPlayer.canBreak(owner, world, pos))
                return false;
            world.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            return true;
        }
    }

    /** BptBlueprint.sizeX, sizeY, sizeZ. */
    public final int sizeX, sizeY, sizeZ;
    /** BptBuilderBase.done. */
    public boolean done;

    LinkedList<Slot> clearList = new LinkedList<Slot>();
    LinkedList<Slot> primaryList = new LinkedList<Slot>();
    LinkedList<Slot> secondaryList = new LinkedList<Slot>();

    /**
     * TileQuarry.initializeBluePrintBuilder's blueprint (frame edges, air everywhere else) and the BptBuilderBlueprint
     * constructor's lists, for a blueprint of box's size at x, y, z (the anchor is 0, 0, 0).
     */
    public QuarryFrameBuilder(Box box, int x, int y, int z) {
        sizeX = box.sizeX();
        sizeY = box.sizeY();
        sizeZ = box.sizeZ();
        boolean[][][] frame = new boolean[sizeX][sizeY][sizeZ];

        for (int it = 0; it < 2; it++) {
            for (int i = 0; i < sizeX; ++i) {
                frame[i][it * (box.sizeY() - 1)][0] = true;
                frame[i][it * (box.sizeY() - 1)][sizeZ - 1] = true;
            }

            for (int k = 0; k < sizeZ; ++k) {
                frame[0][it * (box.sizeY() - 1)][k] = true;
                frame[sizeX - 1][it * (box.sizeY() - 1)][k] = true;

            }
        }

        for (int h = 1; h < box.sizeY(); ++h) {
            frame[0][h][0] = true;
            frame[0][h][sizeZ - 1] = true;
            frame[sizeX - 1][h][0] = true;
            frame[sizeX - 1][h][sizeZ - 1] = true;
        }

        for (int j = sizeY - 1; j >= 0; --j) {
            for (int i = 0; i < sizeX; ++i) {
                for (int k = 0; k < sizeZ; ++k) {
                    clearList.add(new Slot(i + x, j + y, k + z, frame[i][j][k], Mode.ClearIfInvalid));
                }
            }
        }

        for (int j = 0; j < sizeY; ++j) {
            for (int i = 0; i < sizeX; ++i) {
                for (int k = 0; k < sizeZ; ++k) {
                    // The frame is not an opaque cube, so 4.2.2 put every slot of this blueprint in the secondary list.
                    secondaryList.add(new Slot(i + x, j + y, k + z, frame[i][j][k], Mode.Build));
                }
            }
        }
    }

    private void checkDone() {
        if (clearList.size() == 0 && primaryList.size() == 0 && secondaryList.size() == 0) {
            done = true;
        } else {
            done = false;
        }
    }

    public Slot getNextBlock(Level world) {
        if (clearList.size() != 0) {
            Slot slot = internalGetNextBlock(world, clearList);
            checkDone();

            if (slot != null)
                return slot;
            else
                return null;
        }

        if (primaryList.size() != 0) {
            Slot slot = internalGetNextBlock(world, primaryList);
            checkDone();

            if (slot != null)
                return slot;
            else
                return null;
        }

        if (secondaryList.size() != 0) {
            Slot slot = internalGetNextBlock(world, secondaryList);
            checkDone();

            if (slot != null)
                return slot;
            else
                return null;
        }

        checkDone();

        return null;
    }

    /**
     * The first invalid slot of list. A ClearIfInvalid slot that holds a soft block is passed over; in survival a Build
     * slot needs its requirements, which are none for frames and air (BptBlockFrame.addRequirements), so it is taken.
     */
    public Slot internalGetNextBlock(Level world, LinkedList<Slot> list) {
        Slot result = null;

        while (list.size() > 0) {
            Slot slot = list.removeFirst();

            boolean getNext = !slot.isValid(world);

            if (getNext)
                if (slot.mode == Mode.ClearIfInvalid) {
                    if (!BlockUtil.isSoftBlock(world, slot.x, slot.y, slot.z)) {
                        result = slot;
                        break;
                    }
                } else {
                    result = slot;
                    break;
                }
        }

        return result;
    }

    /**
     * The construction half of 4.2.2's EntityRobot: a queue of up to MAX_TARGETS slots, built one at a time with a wait
     * that shortens as the queue fills. 4.2.2 ran it as the entity's onUpdate; TileQuarry now calls {@link #update}
     * every server tick while the frame is being built.
     */
    public static class Robot {
        public LinkedList<Slot> targets = new LinkedList<Slot>();
        public static int MAX_TARGETS = 20;
        public int wait = 0;

        /**
         * EntityRobot.build. New: the slot is built as the owner's fake player; a refusal leaves it at the head of the
         * queue and returns false, so the quarry stops there (blocked) and the next update tries again.
         */
        public boolean update(ServerLevel world, FakePlayer owner) {
            updateWait();

            if (targets.size() > 0) {
                Slot target = targets.getFirst();
                // 4.2.2 waits here for ever when the block cannot be changed (bedrock, lava, oil in a frame slot).
                if (wait <= 0 && BlockUtil.canChangeBlock(world, target.x, target.y, target.z)) {
                    boolean built;
                    if (target.mode == Mode.ClearIfInvalid) {
                        built = target.isValid(world) || target.clear(world, owner);
                    } else {
                        built = target.buildBlock(world, owner);
                    }

                    if (!built)
                        return false;

                    targets.pop();
                }
            }
            return true;
        }

        public void updateWait() {
            if (targets.size() > 0)
                if (wait == 0) {
                    wait = MAX_TARGETS - targets.size() + 2;
                } else {
                    wait--;
                }
        }

        public void scheduleContruction(Slot slot) {
            if (slot != null) {
                targets.add(slot);
            }
        }

        public boolean readyToBuild() {
            return targets.size() < MAX_TARGETS;
        }

        public boolean done() {
            return targets.isEmpty();
        }
    }
}
