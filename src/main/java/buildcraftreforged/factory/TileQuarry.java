/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: power in FE (MJ x 10); the frame is built by QuarryFrameBuilder (the quarry's
 * part of BptBuilderBlueprint and EntityRobot) and the arm is render data, not an entity; blocks are cleared, placed
 * and dug as the owner's fake player, and a refused block stops the quarry (blocked) instead of being skipped; what is
 * dug goes to a MachineOutput, and the quarry waits while it holds items (blocked output, nothing thrown out);
 * ForgeChunkManager becomes a NeoForge TicketController, used only with quarry.loadsChunks; the world bottom is the
 * level's minimum Y; @TileNetworkData becomes writePacketPayload; NBT becomes ValueInput/ValueOutput and the legacy
 * xSize/ySize/zSize save format is dropped; IBuilderInventory (an empty inventory) is gone; a finished quarry stops
 * taking power; the status panel (plan section 10) is new.
 */
package buildcraftreforged.factory;

import buildcraftreforged.api.core.IAreaProvider;
import buildcraftreforged.api.core.LaserKind;
import buildcraftreforged.api.gates.IAction;
import buildcraftreforged.api.power.IPowerReceptor;
import buildcraftreforged.api.power.PowerHandler;
import buildcraftreforged.api.power.PowerHandler.PowerReceiver;
import buildcraftreforged.core.Box;
import buildcraftreforged.core.BuildCraftConfiguration;
import buildcraftreforged.core.CoreConstants;
import buildcraftreforged.core.DefaultAreaProvider;
import buildcraftreforged.core.IMachine;
import buildcraftreforged.core.TileBuildCraft;
import buildcraftreforged.core.status.FlowMeter;
import buildcraftreforged.core.status.IStatusProvider;
import buildcraftreforged.core.status.MachineOutput;
import buildcraftreforged.core.status.MachineStatus;
import buildcraftreforged.core.status.StatusReason;
import buildcraftreforged.core.status.StatusReport;
import buildcraftreforged.core.utils.BlockUtil;
import buildcraftreforged.core.utils.BuildCraftPlayer;
import buildcraftreforged.core.utils.Utils;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;
import org.jspecify.annotations.Nullable;

public class TileQuarry extends TileBuildCraft implements IMachine, IPowerReceptor, IStatusProvider {

    public Box box = new Box();
    public boolean inProcess = false;
    public int targetX, targetY, targetZ;
    public double headPosX, headPosY, headPosZ;
    public double speed = 0.03;
    public boolean builderDone = false;
    public QuarryFrameBuilder.@Nullable Robot builder;
    @Nullable QuarryFrameBuilder bluePrintBuilder;
    public @Nullable EntityMechanicalArm arm;
    public PowerHandler powerHandler;
    boolean isDigging = false;
    /** 4.2.2 15000 MJ, in FE. */
    public static final int MAX_ENERGY = 150000;
    /** ForgeChunkManager's default maximumChunksPerTicket, 4.2.2's getMaxChunkListDepth for the size check. */
    private static final int MAX_CHUNK_LIST_DEPTH = 25;

    /**
     * Not in 4.2.2: the owner's fake player was refused a block (a claim or protection), while building the frame or
     * digging. The quarry stops there and asks again (status panel Reason, Machine trigger blocked, red light).
     */
    private boolean isBlocked;
    /** Not in 4.2.2: the status panel's Flow and Power lines. */
    public final FlowMeter flowMeter = new FlowMeter(() -> level == null ? 0 : level.getGameTime());
    /** What the quarry dug and nothing took yet (#63); it digs on only when this is empty. */
    public final MachineOutput output = new MachineOutput(flowMeter);

    public TileQuarry(BlockPos pos, BlockState state) {
        super(FactoryRegistry.quarryTile.get(), pos, state);
        powerHandler = new PowerHandler(this, PowerHandler.Type.MACHINE);
        initPowerProvider();
    }

    private void initPowerProvider() {
        float m = TileMiningWell.miningMultiplier();
        long mj = (long) (250 * m);
        powerHandler.configure((long) (500 * m), (long) (1000 * m), mj, (long) (MAX_ENERGY * m));
        // 4.2.2's static PERDITION, new PerditionCalculator(2 * miningMultiplier) MJ.
        powerHandler.setPerdition(new PowerHandler.PerditionCalculator(20 * m));
    }

    /** The quarry.loadsChunks option (plan section 19.3); its default until the common config is loaded. */
    static boolean loadsChunks() {
        return BuildCraftConfiguration.COMMON_SPEC.isLoaded() ? BuildCraftConfiguration.quarryLoadsChunks.get()
                : BuildCraftConfiguration.quarryLoadsChunks.getDefault();
    }

    public void createUtilsIfNeeded() {
        if (bluePrintBuilder == null) {

            if (!box.isInitialized()) {
                // 4.2.2 also ran setBoundaries on the client, where it found no chunk ticket; the client waits for the
                // box to arrive with the description packet instead.
                if (level == null || level.isClientSide())
                    return;
                setBoundaries(loadDefaultBoundaries);
            }

            initializeBluePrintBuilder();
        }

        if (builderDone) {

            box.deleteLasers();

            if (arm == null) {
                createArm();
            }

            if (findTarget(false)) {
                isDigging = true;
                if (box != null && ((headPosX < box.xMin || headPosX > box.xMax) || (headPosZ < box.zMin || headPosZ > box.zMax))) {
                    setHead(box.xMin + 1, worldPosition.getY() + 2, box.zMin + 1);
                }
            }

        } else {

            box.createLasers(level, LaserKind.Stripes);
            isDigging = true;
        }
    }

    private boolean loadDefaultBoundaries = false;
    private boolean movingHorizontally;
    private boolean movingVertically;
    private double headTrajectory;
    public boolean isAlive;
    /** The player who placed the quarry, for the chat messages; not saved, as in 4.2.2. */
    public @Nullable Player placedBy;

    private void createArm() {
        QuarryFrameBuilder bluePrint = bluePrintBuilder;
        new EntityMechanicalArm(level, box.xMin + CoreConstants.PIPE_MAX_POS,
                worldPosition.getY() + bluePrint.sizeY - 1 + CoreConstants.PIPE_MIN_POS, box.zMin + CoreConstants.PIPE_MAX_POS,
                bluePrint.sizeX - 2 + CoreConstants.PIPE_MIN_POS * 2, bluePrint.sizeZ - 2 + CoreConstants.PIPE_MIN_POS * 2, this);
    }

    // Callback from the arm once it's created
    public void setArm(EntityMechanicalArm arm) {
        this.arm = arm;
    }

    @Override
    public void updateEntity() {
        // 4.2.2 let a client quarry that was not alive run the server logic too; the client only follows the packets.
        if (level == null || level.isClientSide()) {
            super.updateEntity();
            // The arm is client render data: draw it as soon as the synced state says the frame is done.
            if (isAlive && builderDone && arm == null && box.isInitialized())
                createUtilsIfNeeded();
            return;
        }
        if (!isAlive) {
            super.updateEntity();
            return;
        }
        super.updateEntity();

        // New (#63): offer what was dug to the neighbours every tick.
        output.push(level, worldPosition);

        // 4.2.2's EntityRobot ticked as an entity of its own; it now runs here, as the owner.
        if (builder != null) {
            FakePlayer owner = BuildCraftPlayer.get(this);
            isBlocked = owner == null || !builder.update((ServerLevel) level, owner);
        }

        if (inProcess) {
            long energyToUse = 20 + powerHandler.getEnergyStored() / 500;

            long energy = powerHandler.useEnergy(energyToUse, energyToUse, true);

            if (energy > 0) {
                flowMeter.energyIn(energy);
                moveHead(0.1 + energy / 2000F);
            }
        }

        if (inProcess) {
            sendNetworkUpdate();
        }
        if (inProcess || !isDigging)
            return;

        createUtilsIfNeeded();

        if (bluePrintBuilder != null) {

            boolean wasDone = builderDone;
            builderDone = bluePrintBuilder.done;
            // builderDone is not saved: after a load the frame is found complete again here, and the client has to
            // hear it to draw the arm, even while the head stands still.
            if (builderDone != wasDone)
                sendNetworkUpdate();
            if (!builderDone) {

                buildFrame();
                return;

            } else {

                if (builder != null && builder.done()) {

                    box.deleteLasers();
                    builder = null;
                }
            }
        }

        if (builder == null) {
            dig();
        }

    }

    @Override
    public void doWork(PowerHandler workProvider) {
    }

    protected void buildFrame() {
        float m = TileMiningWell.miningMultiplier();
        long mj = (long) (250 * m);
        powerHandler.configure((long) (500 * m), (long) (1000 * m), mj, (long) (MAX_ENERGY * m));
        if (powerHandler.useEnergy(mj, mj, true) != mj)
            return;
        flowMeter.energyIn(mj);

        if (builder == null) {
            builder = new QuarryFrameBuilder.Robot();
        }

        if (builder.readyToBuild()) {
            builder.scheduleContruction(bluePrintBuilder.getNextBlock(level));
        }
    }

    protected void dig() {
        float m = TileMiningWell.miningMultiplier();
        powerHandler.configure((long) (1000 * m), (long) (5000 * m), TileMiningWell.MINING_ENERGY_COST_PER_BLOCK,
                (long) (MAX_ENERGY * m));

        // New (#63): nothing new is dug while the output still holds items.
        if (output.hasPending())
            return;

        // New: the head stays at a block the owner may not break; ask again once a second instead of skipping it.
        if (isBlocked) {
            if (level.getGameTime() % 20 != 0)
                return;
            positionReached();
            if (isBlocked)
                return;
        }

        long mj = (long) (TileMiningWell.MINING_ENERGY_COST_PER_BLOCK * m);
        if (powerHandler.useEnergy(mj, mj, true) != mj)
            return;
        flowMeter.energyIn(mj);

        if (!findTarget(true)) {

            // I believe the issue is box going null becuase of bad chunkloader positioning
            if (arm != null && box != null) {
                setTarget(box.xMin + 1, worldPosition.getY() + 2, box.zMin + 1);
            }

            isDigging = false;
            // New (plan section 19.3): a finished quarry stops drawing power.
            powerHandler.configure(0, 0, 0, (long) (MAX_ENERGY * m));
        }

        inProcess = true;
        movingHorizontally = true;
        movingVertically = true;
        double[] head = getHead();
        int[] target = getTarget();
        headTrajectory = Math.atan2(target[2] - head[2], target[0] - head[0]);
    }

    private final LinkedList<int[]> visitList = new LinkedList<int[]>();

    public boolean findTarget(boolean doSet) {
        if (level == null || level.isClientSide())
            return false;

        boolean columnVisitListIsUpdated = false;

        if (visitList.isEmpty()) {
            createColumnVisitList();
            columnVisitListIsUpdated = true;
        }

        if (!doSet)
            return !visitList.isEmpty();

        if (visitList.isEmpty())
            return false;

        int[] nextTarget = visitList.removeFirst();

        if (!columnVisitListIsUpdated) { // nextTarget may not be accurate, at least search the target column for changes
            for (int y = nextTarget[1] + 1; y < worldPosition.getY() + 3; y++) {
                BlockState state = level.getBlockState(new BlockPos(nextTarget[0], y, nextTarget[2]));
                if (BlockUtil.isAnObstructingBlock(state, level, nextTarget[0], y, nextTarget[2])
                        || !BlockUtil.isSoftBlock(state, level, nextTarget[0], y, nextTarget[2])) {
                    createColumnVisitList();
                    columnVisitListIsUpdated = true;
                    nextTarget = null;
                    break;
                }
            }
        }
        if (columnVisitListIsUpdated && nextTarget == null && !visitList.isEmpty()) {
            nextTarget = visitList.removeFirst();
        } else if (columnVisitListIsUpdated && nextTarget == null) {
            return false;
        }

        setTarget(nextTarget[0], nextTarget[1] + 1, nextTarget[2]);

        return true;
    }

    /**
     * Make the column visit list: called once per layer
     */
    private void createColumnVisitList() {
        visitList.clear();

        Level world = level;
        int yCoord = worldPosition.getY();
        int sizeX = bluePrintBuilder.sizeX, sizeZ = bluePrintBuilder.sizeZ;
        // 1.6.4's heights started at 0 and "height > 0" meant a column with a surface; the world now starts at getMinY().
        int bottom = world.getMinY();

        Integer[][] columnHeights = new Integer[sizeX - 2][sizeZ - 2];
        boolean[][] blockedColumns = new boolean[sizeX - 2][sizeZ - 2];
        for (int searchY = yCoord + 3; searchY >= bottom + 1 && searchY >= yCoord - TileMiningWell.miningDepth(); --searchY) {
            int startX, endX, incX;
            // floorMod: 1.6.4 had no negative Y, and Java's % keeps the sign, which would break the zig-zag below 0.
            int parityY = Math.floorMod(searchY, 2);

            if (parityY == 0) {
                startX = 0;
                endX = sizeX - 2;
                incX = 1;
            } else {
                startX = sizeX - 3;
                endX = -1;
                incX = -1;
            }

            for (int searchX = startX; searchX != endX; searchX += incX) {
                int startZ, endZ, incZ;

                if (searchX % 2 == parityY) {
                    startZ = 0;
                    endZ = sizeZ - 2;
                    incZ = 1;
                } else {
                    startZ = sizeZ - 3;
                    endZ = -1;
                    incZ = -1;
                }

                for (int searchZ = startZ; searchZ != endZ; searchZ += incZ) {
                    if (!blockedColumns[searchX][searchZ]) {
                        Integer height = columnHeights[searchX][searchZ];
                        int bx = box.xMin + searchX + 1, by = searchY, bz = box.zMin + searchZ + 1;

                        if (height == null)
                            columnHeights[searchX][searchZ] = height = world.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz);

                        if (height > bottom && height < by && world.dimension() != Level.NETHER) {
                            continue;
                        }

                        BlockState state = world.getBlockState(new BlockPos(bx, by, bz));

                        if (!BlockUtil.canChangeBlock(state, world, bx, by, bz)) {
                            blockedColumns[searchX][searchZ] = true;
                        } else if (!BlockUtil.isSoftBlock(state, world, bx, by, bz)) {
                            visitList.add(new int[] { bx, by, bz });
                        }
                        if (height == bottom && !state.isAir()) {
                            columnHeights[searchX][searchZ] = by;
                        }

                        // Stop at two planes - generally any obstructions will have been found and will force a recompute prior to this
                        if (visitList.size() > sizeZ * sizeX * 2)
                            return;
                    }
                }
            }
        }
    }

    @Override
    protected void loadAdditional(ValueInput nbttagcompound) {
        super.loadAdditional(nbttagcompound);

        powerHandler.readFromNBT(nbttagcompound);
        initPowerProvider();

        ValueInput boxTag = nbttagcompound.child("box").orElse(null);
        if (boxTag != null) {
            box.initialize(boxTag);

            loadDefaultBoundaries = false;
        } else {
            // 4.2.2 also read the pre-box xSize/ySize/zSize format here; worlds from 1.6.4 do not load in this port.
            loadDefaultBoundaries = true;
        }

        targetX = nbttagcompound.getIntOr("targetX", 0);
        targetY = nbttagcompound.getIntOr("targetY", 0);
        targetZ = nbttagcompound.getIntOr("targetZ", 0);
        headPosX = nbttagcompound.getDoubleOr("headPosX", 0);
        headPosY = nbttagcompound.getDoubleOr("headPosY", 0);
        headPosZ = nbttagcompound.getDoubleOr("headPosZ", 0);
        output.load(nbttagcompound);
    }

    @Override
    protected void saveAdditional(ValueOutput nbttagcompound) {
        super.saveAdditional(nbttagcompound);

        powerHandler.writeToNBT(nbttagcompound);

        nbttagcompound.putInt("targetX", targetX);
        nbttagcompound.putInt("targetY", targetY);
        nbttagcompound.putInt("targetZ", targetZ);
        nbttagcompound.putDouble("headPosX", headPosX);
        nbttagcompound.putDouble("headPosY", headPosY);
        nbttagcompound.putDouble("headPosZ", headPosZ);

        if (box.isInitialized()) {
            box.writeToNBT(nbttagcompound.child("box"));
        }
        output.save(nbttagcompound);
    }

    public void positionReached() {
        inProcess = false;

        if (!(level instanceof ServerLevel world))
            return;

        int i = targetX;
        int j = targetY - 1;
        int k = targetZ;
        BlockPos pos = new BlockPos(i, j, k);

        if (isQuarriableBlock(i, j, k)) {

            // New: the owner's fake player must be allowed to break the block (#27). A refusal stops the quarry here.
            FakePlayer owner = BuildCraftPlayer.get(this);
            if (owner == null || !BuildCraftPlayer.canBreak(owner, world, pos)) {
                isBlocked = true;
                return;
            }

            // Share this with mining well!

            List<ItemStack> stacks = BlockUtil.getItemStackFromBlock(world, i, j, k, owner);

            if (stacks != null) {
                for (ItemStack s : stacks) {
                    if (s != null && !s.isEmpty()) {
                        mineStack(s);
                    }
                }
            }

            world.levelEvent(2001, pos, Block.getId(world.getBlockState(pos)));
            world.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        isBlocked = false;

        // Collect any lost items laying around
        double[] head = getHead();
        AABB axis = new AABB(head[0] - 2, head[1] - 2, head[2] - 2, head[0] + 3, head[1] + 3, head[2] + 3);
        List<ItemEntity> result = world.getEntitiesOfClass(ItemEntity.class, axis);
        for (ItemEntity entity : result) {
            if (!entity.isAlive()) {
                continue;
            }

            ItemStack mineable = entity.getItem();
            if (mineable.isEmpty()) {
                continue;
            }
            entity.discard();
            mineStack(mineable.copy());
        }
    }

    /**
     * 4.2.2 tried a nearby chest, then a pipe, and threw the rest out. #63: the output buffer offers it to every
     * neighbour that takes items, keeps what nobody takes and blocks the quarry until it is gone.
     */
    private void mineStack(ItemStack stack) {
        output.add(stack);
    }

    private boolean isQuarriableBlock(int bx, int by, int bz) {
        BlockState state = level.getBlockState(new BlockPos(bx, by, bz));
        return BlockUtil.canChangeBlock(state, level, bx, by, bz) && !BlockUtil.isSoftBlock(state, level, bx, by, bz);
    }

    /**
     * 4.2.2 BlockQuarry.breakBlock (the frame, now removed at once) and invalidate (the chunk ticket); the pending
     * output drops as an inventory's contents would (#63).
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null && !level.isClientSide()) {
            if (box.isInitialized() && Integer.MAX_VALUE != box.xMax) {
                BlockQuarry.removeFrame(level, box);
            }
            releaseChunks();
            output.dropAll(level, pos);
        }
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        destroy();
    }

    @Override
    public void destroy() {

        if (arm != null) {
            arm.setDead();
        }

        builder = null;

        box.deleteLasers();
        arm = null;
    }

    @Override
    public boolean isActive() {
        return isDigging;
    }

    private void setBoundaries(boolean useDefault) {
        int xCoord = worldPosition.getX(), yCoord = worldPosition.getY(), zCoord = worldPosition.getZ();

        IAreaProvider a = null;

        if (!useDefault) {
            a = Utils.getNearbyAreaProvider(level, xCoord, yCoord, zCoord);
        }

        if (a == null) {
            a = new DefaultAreaProvider(xCoord, yCoord, zCoord, xCoord + 10, yCoord + 4, zCoord + 10);

            useDefault = true;
        }

        int xSize = a.xMax() - a.xMin() + 1;
        int zSize = a.zMax() - a.zMin() + 1;

        if (xSize < 3 || zSize < 3 || ((xSize * zSize) >> 8) >= MAX_CHUNK_LIST_DEPTH) {
            if (placedBy != null) {
                placedBy.sendSystemMessage(Component.literal(String.format(
                        "Quarry size is outside of chunkloading bounds or too small %d %d (%d)", xSize, zSize,
                        MAX_CHUNK_LIST_DEPTH)));
            }
            a = new DefaultAreaProvider(xCoord, yCoord, zCoord, xCoord + 10, yCoord + 4, zCoord + 10);

            useDefault = true;
        }

        xSize = a.xMax() - a.xMin() + 1;
        int ySize = a.yMax() - a.yMin() + 1;
        zSize = a.zMax() - a.zMin() + 1;

        box.initialize(a);

        if (ySize < 5) {
            ySize = 5;
            box.yMax = box.yMin + ySize - 1;
        }

        if (useDefault) {
            int xMin, zMin;

            Direction o = getBlockState().getValue(BlockQuarry.FACING).getOpposite();

            switch (o) {
                case EAST:
                    xMin = xCoord + 1;
                    zMin = zCoord - 4 - 1;
                    break;
                case WEST:
                    xMin = xCoord - 9 - 2;
                    zMin = zCoord - 4 - 1;
                    break;
                case SOUTH:
                    xMin = xCoord - 4 - 1;
                    zMin = zCoord + 1;
                    break;
                case NORTH:
                default:
                    xMin = xCoord - 4 - 1;
                    zMin = zCoord - 9 - 2;
                    break;
            }

            box.initialize(xMin, yCoord, zMin, xMin + xSize - 1, yCoord + ySize - 1, zMin + zSize - 1);
        }

        a.removeFromWorld();
        forceChunkLoading();
    }

    private void initializeBluePrintBuilder() {
        bluePrintBuilder = new QuarryFrameBuilder(box, box.xMin, worldPosition.getY(), box.zMin);
    }

    @Override
    public void writePacketPayload(RegistryFriendlyByteBuf data) {
        box.writeToStream(data);
        data.writeBoolean(inProcess);
        data.writeInt(targetX);
        data.writeInt(targetY);
        data.writeInt(targetZ);
        data.writeDouble(headPosX);
        data.writeDouble(headPosY);
        data.writeDouble(headPosZ);
        data.writeDouble(speed);
        data.writeBoolean(builderDone);
        data.writeBoolean(isAlive);
    }

    @Override
    public void readPacketPayload(RegistryFriendlyByteBuf data) {
        box.readFromStream(data);
        inProcess = data.readBoolean();
        targetX = data.readInt();
        targetY = data.readInt();
        targetZ = data.readInt();
        headPosX = data.readDouble();
        headPosY = data.readDouble();
        headPosZ = data.readDouble();
        speed = data.readDouble();
        builderDone = data.readBoolean();
        isAlive = data.readBoolean();
    }

    @Override
    public void postPacketHandling() {
        super.postPacketHandling();

        if (isAlive) {
            createUtilsIfNeeded();
        } else {
            box.deleteLasers();
            box.reset();
            return;
        }
        if (arm != null) {
            arm.setHead(headPosX, headPosY, headPosZ);
            arm.updatePosition();
        }
    }

    @Override
    public void initialize() {
        super.initialize();

        if (level != null && !level.isClientSide()) {
            if (!box.initialized) {
                setBoundaries(false);
            } else {
                // 4.2.2's ticket callback did this when the world loaded; NeoForge keeps the tickets itself.
                forceChunkLoading();
            }
        }

        createUtilsIfNeeded();

        sendNetworkUpdate();
    }

    public void reinitalize() {
        builderDone = false;
        initializeBluePrintBuilder();
        isDigging = true;
    }

    @Override
    public PowerReceiver getPowerReceiver(@Nullable Direction side) {
        return powerHandler.getPowerReceiver();
    }

    @Override
    public boolean manageFluids() {
        return false;
    }

    @Override
    public boolean manageSolids() {
        return true;
    }

    @Override
    public boolean allowAction(IAction action) {
        return false;
    }

    public void moveHead(double instantSpeed) {
        int[] target = getTarget();
        double[] head = getHead();

        if (movingHorizontally) {
            if (Math.abs(target[0] - head[0]) < instantSpeed * 2 && Math.abs(target[2] - head[2]) < instantSpeed * 2) {
                head[0] = target[0];
                head[2] = target[2];

                movingHorizontally = false;

                if (!movingVertically) {
                    positionReached();
                    head[1] = target[1];
                }
            } else {
                head[0] += Math.cos(headTrajectory) * instantSpeed;
                head[2] += Math.sin(headTrajectory) * instantSpeed;
            }
            setHead(head[0], head[1], head[2]);
        }

        if (movingVertically) {
            if (Math.abs(target[1] - head[1]) < instantSpeed * 2) {
                head[1] = target[1];

                movingVertically = false;
                if (!movingHorizontally) {
                    positionReached();
                    head[0] = target[0];
                    head[2] = target[2];
                }
            } else {
                if (target[1] > head[1]) {
                    head[1] += instantSpeed;
                } else {
                    head[1] -= instantSpeed;
                }
            }
            setHead(head[0], head[1], head[2]);
        }

        updatePosition();
    }

    private void updatePosition() {
        if (arm != null && level != null && level.isClientSide()) {
            arm.setHead(headPosX, headPosY, headPosZ);
            arm.updatePosition();
        }
    }

    private void setHead(double x, double y, double z) {
        this.headPosX = x;
        this.headPosY = y;
        this.headPosZ = z;
    }

    private double[] getHead() {
        return new double[] { headPosX, headPosY, headPosZ };
    }

    private int[] getTarget() {
        return new int[] { targetX, targetY, targetZ };
    }

    private void setTarget(int x, int y, int z) {
        this.targetX = x;
        this.targetY = y;
        this.targetZ = z;
    }

    /** The quarry's own chunk and every chunk of its box, as 4.2.2's forceChunkLoading forced them. */
    private Set<ChunkPos> getChunks() {
        Set<ChunkPos> chunks = new LinkedHashSet<>();
        chunks.add(ChunkPos.containing(worldPosition));

        for (int chunkX = box.xMin >> 4; chunkX <= box.xMax >> 4; chunkX++) {
            for (int chunkZ = box.zMin >> 4; chunkZ <= box.zMax >> 4; chunkZ++) {
                chunks.add(new ChunkPos(chunkX, chunkZ));
            }
        }
        return chunks;
    }

    /**
     * 4.2.2 forced the chunks unconditionally, through a ticket that could run out. Now only with quarry.loadsChunks
     * (plan section 19.3, default off); without it the quarry is alive anyway and pauses while its chunks are unloaded.
     */
    public void forceChunkLoading() {
        isAlive = true;

        if (loadsChunks() && level instanceof ServerLevel world) {
            Set<ChunkPos> chunks = getChunks();
            for (ChunkPos chunk : chunks) {
                FactoryRegistry.quarryChunkLoader.forceChunk(world, worldPosition, chunk.x(), chunk.z(), true, false);
            }
            if (placedBy != null) {
                placedBy.sendSystemMessage(Component.literal(String.format(
                        "[BUILDCRAFT] The quarry at %d %d %d will keep %d chunks loaded", worldPosition.getX(),
                        worldPosition.getY(), worldPosition.getZ(), chunks.size())));
            }
        }
        sendNetworkUpdate();
    }

    /** 4.2.2 invalidate's ForgeChunkManager.releaseTicket. */
    private void releaseChunks() {
        if (level instanceof ServerLevel world && box.isInitialized()) {
            for (ChunkPos chunk : getChunks()) {
                FactoryRegistry.quarryChunkLoader.forceChunk(world, worldPosition, chunk.x(), chunk.z(), false, false);
            }
        }
    }

    /**
     * 4.2.2 BuildCraftFactory.QuarryChunkloadCallback: when the world loads, keep only the tickets whose owner is still
     * a quarry, and none at all while quarry.loadsChunks is off.
     */
    static void validateTickets(ServerLevel world, TicketHelper ticketHelper) {
        for (BlockPos owner : new ArrayList<>(ticketHelper.getBlockTickets().keySet())) {
            if (!loadsChunks() || !world.getBlockState(owner).is(FactoryRegistry.quarryBlock.get())) {
                ticketHelper.removeAllTickets(owner);
            }
        }
    }

    // Status panel (plan section 10), not in 4.2.2.

    /** The frame is built and there is nothing left to dig: "Done" (plan section 19.3). */
    public boolean isDone() {
        return builderDone && !isDigging && !inProcess;
    }

    @Override
    public boolean isBlocked() {
        return isBlocked || output.isBlocked();
    }

    @Override
    public MachineStatus getStatusState() {
        if (isBlocked())
            return MachineStatus.BLOCKED;
        if (isDone())
            return MachineStatus.DONE;
        if (!isDigging)
            return MachineStatus.IDLE;
        if (!inProcess && powerHandler.getEnergyStored() < powerHandler.getActivationEnergy())
            return MachineStatus.NO_POWER;
        return MachineStatus.WORKING;
    }

    @Override
    public StatusReason getStatusReason() {
        return isBlocked ? StatusReason.PROTECTED_BLOCK : output.getReason();
    }

    @Override
    public FlowMeter getStatusFlow() {
        return flowMeter;
    }

    @Override
    public StatusReport.Power getStatusPower() {
        return StatusReport.Power.drawn(flowMeter, powerHandler.getEnergyStored(), powerHandler.getMaxEnergyStored());
    }
}
