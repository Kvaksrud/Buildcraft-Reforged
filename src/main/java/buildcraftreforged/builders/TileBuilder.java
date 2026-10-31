/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: power in FE (MJ x 10); IInventory becomes Container over the same 28 slots;
 * the blueprint comes from the server's library (BuildCraftBuilders.getBptRootIndex) by the item's BptItemData id; the
 * robot is no longer an entity but state of this tile (core/EntityRobot), and doWork hands it the next block, which
 * 4.2.2 had commented out so that its builder built nothing (docs/BUGFIXES.md); blocks are cleared and placed as the
 * owner's fake player; building materials also come from the inventories next to the builder; the path lasers and
 * the robot are render data sent with the network payload; NBT becomes ValueInput/ValueOutput with the same tag names;
 * invalidate becomes setRemoved; the GUI is a MenuProvider; the status panel (plan section 10) is new.
 */
package buildcraftreforged.builders;

import buildcraftreforged.api.core.LaserKind;
import buildcraftreforged.api.core.Position;
import buildcraftreforged.api.gates.IAction;
import buildcraftreforged.api.power.IPowerReceptor;
import buildcraftreforged.api.power.PowerHandler;
import buildcraftreforged.api.power.PowerHandler.PowerReceiver;
import buildcraftreforged.api.power.PowerHandler.Type;
import buildcraftreforged.builders.gui.ContainerBuilder;
import buildcraftreforged.core.BlockIndex;
import buildcraftreforged.core.Box;
import buildcraftreforged.core.EntityLaser;
import buildcraftreforged.core.EntityPowerLaser;
import buildcraftreforged.core.EntityRobot;
import buildcraftreforged.core.IBuilderInventory;
import buildcraftreforged.core.IMachine;
import buildcraftreforged.core.TileBuildCraft;
import buildcraftreforged.core.blueprints.BptBase;
import buildcraftreforged.core.blueprints.BptBlueprint;
import buildcraftreforged.core.blueprints.BptBuilderBase;
import buildcraftreforged.core.blueprints.BptBuilderBlueprint;
import buildcraftreforged.core.blueprints.BptBuilderTemplate;
import buildcraftreforged.core.blueprints.BptContext;
import buildcraftreforged.core.blueprints.BptRootIndex;
import buildcraftreforged.core.blueprints.BptSlot;
import buildcraftreforged.core.inventory.SimpleInventory;
import buildcraftreforged.core.status.FlowMeter;
import buildcraftreforged.core.status.IStatusProvider;
import buildcraftreforged.core.status.MachineStatus;
import buildcraftreforged.core.status.StatusReason;
import buildcraftreforged.core.status.StatusReport;
import buildcraftreforged.core.utils.BuildCraftPlayer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

public class TileBuilder extends TileBuildCraft implements IBuilderInventory, IPowerReceptor, IMachine, MenuProvider,
        IStatusProvider, EntityRobot.IRobotOwner {

    /** 4.2.2 used 25 MJ per block, in FE. */
    public static final int POWER_USAGE = 250;
    /** GuiBuilder shows 7 x 4 needed stacks; the payload carries no more. */
    public static final int MAX_NEEDED_SHOWN = 28;

    private final SimpleInventory items = new SimpleInventory(28, "Builder", 64);
    private @Nullable BptBuilderBase bluePrintBuilder;
    public Box box = new Box();
    private PowerHandler powerHandler;
    private @Nullable LinkedList<BlockIndex> path;
    private @Nullable LinkedList<EntityLaser> pathLasers;
    private @Nullable EntityRobot builderRobot;

    private class PathIterator {

        public Iterator<BlockIndex> currentIterator;
        public double cx, cy, cz;
        public float ix, iy, iz;
        public BlockIndex to;
        public double lastDistance;
        @Nullable
        AABB oldBoundingBox = null;
        Direction o;

        public PathIterator(BlockIndex from, Iterator<BlockIndex> it) {
            this.to = it.next();

            currentIterator = it;

            double dx = to.x - from.x;
            double dy = to.y - from.y;
            double dz = to.z - from.z;

            double size = Math.sqrt(dx * dx + dy * dy + dz * dz);

            cx = dx / size / 10;
            cy = dy / size / 10;
            cz = dz / size / 10;

            ix = from.x;
            iy = from.y;
            iz = from.z;

            lastDistance = (ix - to.x) * (ix - to.x) + (iy - to.y) * (iy - to.y) + (iz - to.z) * (iz - to.z);

            if (Math.abs(dx) > Math.abs(dz)) {
                if (dx > 0) {
                    o = Direction.EAST;
                } else {
                    o = Direction.WEST;
                }
            } else {
                if (dz > 0) {
                    o = Direction.SOUTH;
                } else {
                    o = Direction.NORTH;
                }
            }
        }

        /**
         * Return false when reached the end of the iteration
         */
        public @Nullable BptBuilderBase next() {
            while (true) {
                BptBuilderBase bpt;

                int newX = Math.round(ix);
                int newY = Math.round(iy);
                int newZ = Math.round(iz);

                bpt = instanciateBluePrint(newX, newY, newZ, o);

                if (bpt == null)
                    return null;

                AABB boundingBox = bpt.getBoundingBox();

                if (oldBoundingBox == null || !collision(oldBoundingBox, boundingBox)) {

                    oldBoundingBox = boundingBox;

                    if (bpt != null)
                        return bpt;
                }

                ix += cx;
                iy += cy;
                iz += cz;

                double distance = (ix - to.x) * (ix - to.x) + (iy - to.y) * (iy - to.y) + (iz - to.z) * (iz - to.z);

                if (distance > lastDistance)
                    return null;
                else {
                    lastDistance = distance;
                }
            }
        }

        public @Nullable PathIterator iterate() {
            if (currentIterator.hasNext()) {
                PathIterator next = new PathIterator(to, currentIterator);
                next.oldBoundingBox = oldBoundingBox;

                return next;
            } else
                return null;
        }

        public boolean collision(AABB left, AABB right) {
            if (left.maxX < right.minX || left.minX > right.maxX)
                return false;
            if (left.maxY < right.minY || left.minY > right.maxY)
                return false;
            if (left.maxZ < right.minZ || left.minZ > right.maxZ)
                return false;
            return true;
        }
    }

    private @Nullable PathIterator currentPathIterator;
    private boolean done = true;

    /** Not in 4.2.2: the last step found blocks to build but not their items (status panel). */
    private boolean missingMaterials;
    /** Not in 4.2.2: the needed stacks GuiBuilder shows, as the client last received them. */
    private List<ItemStack> clientNeededItems = new ArrayList<>();
    private List<ItemStack> sentNeededItems = new ArrayList<>();
    /** Not in 4.2.2: something the client draws changed (box, path, robot, needed items). */
    private boolean networkDirty;
    /** Not in 4.2.2: the status panel's Power line. */
    public final FlowMeter flowMeter = new FlowMeter(() -> level == null ? 0 : level.getGameTime());

    public TileBuilder(BlockPos pos, BlockState state) {
        super(BuildersRegistry.builderTile.get(), pos, state);
        items.addListener(this);

        powerHandler = new PowerHandler(this, Type.MACHINE);
        powerHandler.configure(POWER_USAGE, POWER_USAGE, POWER_USAGE, POWER_USAGE);
    }

    @Override
    public void initialize() {
        super.initialize();

        if (level == null || level.isClientSide())
            return;

        int xCoord = worldPosition.getX(), yCoord = worldPosition.getY(), zCoord = worldPosition.getZ();

        for (int x = xCoord - 1; x <= xCoord + 1; ++x) {
            for (int y = yCoord - 1; y <= yCoord + 1; ++y) {
                for (int z = zCoord - 1; z <= zCoord + 1; ++z) {
                    BlockEntity tile = level.getBlockEntity(new BlockPos(x, y, z));

                    if (tile instanceof TilePathMarker) {
                        path = ((TilePathMarker) tile).getPath();

                        for (BlockIndex b : path) {
                            BlockPos markerPos = new BlockPos(b.x, b.y, b.z);
                            level.removeBlock(markerPos, false);

                            Block.popResource(level, markerPos, new ItemStack(BuildersRegistry.pathMarkerBlock.get()));
                        }

                        break;
                    }
                }
            }
        }

        if (path != null && pathLasers == null) {
            path.getFirst().x = xCoord;
            path.getFirst().y = yCoord;
            path.getFirst().z = zCoord;

            createLasersForPath();
        }

        iterateBpt();
        networkDirty = true;
    }

    public void createLasersForPath() {
        pathLasers = new LinkedList<EntityLaser>();
        BlockIndex previous = null;

        for (BlockIndex b : path) {
            if (previous != null) {

                EntityPowerLaser laser = new EntityPowerLaser(level, new Position(previous.x + 0.5, previous.y + 0.5, previous.z + 0.5), new Position(
                        b.x + 0.5, b.y + 0.5, b.z + 0.5));

                laser.setTexture(0);
                laser.show();
                pathLasers.add(laser);
            }

            previous = b;
        }
    }

    public @Nullable BptBuilderBase instanciateBluePrint(int x, int y, int z, Direction o) {
        if (!(level instanceof ServerLevel serverLevel))
            return null;
        BptRootIndex index = BuildCraftBuilders.getBptRootIndex(serverLevel.getServer());
        int id = ItemBptBase.getBptId(items.getItem(0));
        BptBase bpt = index == null || id <= 0 ? null : index.getBluePrint(id);

        if (bpt == null)
            return null;

        bpt = bpt.clone();

        BptContext context = new BptContext(level, null, bpt.getBoxForPos(x, y, z));

        if (o == Direction.EAST) {
            // Do nothing
        } else if (o == Direction.SOUTH) {
            bpt.rotateLeft(context);
        } else if (o == Direction.WEST) {
            bpt.rotateLeft(context);
            bpt.rotateLeft(context);
        } else if (o == Direction.NORTH) {
            bpt.rotateLeft(context);
            bpt.rotateLeft(context);
            bpt.rotateLeft(context);
        }

        if (items.getItem(0).getItem() instanceof ItemBptTemplate)
            return new BptBuilderTemplate(bpt, level, x, y, z);
        else if (items.getItem(0).getItem() instanceof ItemBptBluePrint && bpt instanceof BptBlueprint blueprint)
            return new BptBuilderBlueprint(blueprint, level, x, y, z);
        else
            return null;
    }

    @Override
    public void doWork(PowerHandler workProvider) {
        if (level == null || level.isClientSide())
            return;

        if (done)
            return;

        if (builderRobot != null && !builderRobot.readyToBuild())
            return;

        if (powerHandler.useEnergy(POWER_USAGE, POWER_USAGE, true) < POWER_USAGE)
            return;

        flowMeter.energyIn(POWER_USAGE);

        iterateBpt();

        if (bluePrintBuilder != null && !bluePrintBuilder.done) {
            if (!box.isInitialized()) {
                box.initialize(bluePrintBuilder);
                networkDirty = true;
            }

            if (builderRobot == null) {
                builderRobot = new EntityRobot(level, box);
                networkDirty = true;
            }

            box.createLasers(level, LaserKind.Stripes);

            // Commented out in 4.2.2, which therefore built nothing.
            builderRobot.scheduleContruction(bluePrintBuilder.getNextBlock(level, getBuildingInventory()),
                    bluePrintBuilder.getContext());
            missingMaterials = bluePrintBuilder.missingMaterials;
            setChanged();
        }
    }

    public void iterateBpt() {
        if (!(items.getItem(0).getItem() instanceof ItemBptBase)) {

            if (bluePrintBuilder != null) {
                bluePrintBuilder = null;
            }

            if (builderRobot != null) {
                builderRobot.setDead(this);
                builderRobot = null;
            }

            if (box.isInitialized()) {
                box.deleteLasers();
                box.reset();
            }

            if (currentPathIterator != null) {
                currentPathIterator = null;
            }

            missingMaterials = false;
            networkDirty = true;

            return;
        }

        if (bluePrintBuilder == null || bluePrintBuilder.done) {
            if (path != null && path.size() > 1) {
                if (currentPathIterator == null) {
                    Iterator<BlockIndex> it = path.iterator();
                    BlockIndex start = it.next();
                    currentPathIterator = new PathIterator(start, it);
                }

                if (bluePrintBuilder != null && builderRobot != null) {
                    // Commented out in 4.2.2: the finished blueprint's post-processing.
                    builderRobot.markEndOfBlueprint(bluePrintBuilder);
                }

                bluePrintBuilder = currentPathIterator.next();

                if (bluePrintBuilder != null) {
                    box.deleteLasers();
                    box.reset();

                    /*
                     box.preInit(bluePrintBuilder);
                     box.createLasers(worldObj, LaserKind.Stripes);
                     */
                }

                if (builderRobot != null) {
                    builderRobot.setBox(box);
                }

                if (bluePrintBuilder == null) {
                    currentPathIterator = currentPathIterator.iterate();
                }

                if (currentPathIterator == null) {
                    done = true;
                }
                networkDirty = true;
            } else {
                if (bluePrintBuilder != null && bluePrintBuilder.done) {
                    if (builderRobot != null) {
                        // Commented out in 4.2.2: the finished blueprint's post-processing.
                        builderRobot.markEndOfBlueprint(bluePrintBuilder);
                    }

                    done = true;
                    bluePrintBuilder = null;
                    missingMaterials = false;
                } else {
                    bluePrintBuilder = instanciateBluePrint(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                            getFacing().getOpposite());

                    if (bluePrintBuilder != null) {
                        box.initialize(bluePrintBuilder);
                        box.createLasers(level, LaserKind.Stripes);
                    }
                }
                networkDirty = true;
            }
        }
    }

    /** 4.2.2 read the front from the metadata; 0 (down, never placed by a player) turned nothing, as west does. */
    private Direction getFacing() {
        BlockState state = getBlockState();
        return state.hasProperty(BlockBuilder.FACING) ? state.getValue(BlockBuilder.FACING) : Direction.WEST;
    }

    /* CONTAINER */
    @Override
    public int getContainerSize() {
        return items.getContainerSize();
    }

    @Override
    public boolean isEmpty() {
        return items.isEmpty();
    }

    @Override
    public ItemStack getItem(int i) {
        return items.getItem(i);
    }

    @Override
    public ItemStack removeItem(int i, int j) {
        ItemStack result = items.removeItem(i, j);

        if (i == 0) {
            iterateBpt();
        }

        return result;
    }

    @Override
    public void setItem(int i, ItemStack itemstack) {
        items.setItem(i, itemstack);

        if (i == 0) {
            iterateBpt();
            done = false;
        }
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return items.removeItemNoUpdate(slot);
    }

    @Override
    public void clearContent() {
        items.clearContent();
    }

    public String getInvName() {
        return "Builder";
    }

    @Override
    public int getMaxStackSize() {
        return 64;
    }

    @Override
    public boolean canPlaceItem(int i, ItemStack itemstack) {
        return true;
    }

    @Override
    public boolean stillValid(Player entityplayer) {
        return Container.stillValidBlockEntity(this, entityplayer);
    }

    @Override
    protected void loadAdditional(ValueInput nbttagcompound) {
        super.loadAdditional(nbttagcompound);

        items.readFromNBT(nbttagcompound);

        nbttagcompound.child("box").ifPresent(box::initialize);

        path = null;
        nbttagcompound.childrenList("path").ifPresent(list -> {
            path = new LinkedList<BlockIndex>();

            for (ValueInput c : list) {
                path.add(new BlockIndex(c));
            }
        });

        done = nbttagcompound.getBooleanOr("done", false);

    }

    @Override
    protected void saveAdditional(ValueOutput nbttagcompound) {
        super.saveAdditional(nbttagcompound);

        items.writeToNBT(nbttagcompound);

        if (box.isInitialized()) {
            box.writeToNBT(nbttagcompound.child("box"));
        }

        if (path != null) {
            ValueOutput.ValueOutputList list = nbttagcompound.childrenList("path");

            for (BlockIndex i : path) {
                i.writeTo(list.addChild());
            }
        }

        nbttagcompound.putBoolean("done", done);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        destroy();
    }

    /** The robot's queued items go back into the inventory before it is dropped (not in 4.2.2, which lost them). */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (builderRobot != null && level != null && !level.isClientSide()) {
            builderRobot.setDead(this);
            builderRobot = null;
        }
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    public void destroy() {
        if (box.isInitialized()) {
            box.deleteLasers();
        }

        if (builderRobot != null) {
            builderRobot.setDead(null);
            builderRobot = null;
        }

        cleanPathLasers();
    }

    @Override
    public PowerReceiver getPowerReceiver(@Nullable Direction side) {
        return powerHandler.getPowerReceiver();
    }

    /**
     * 4.2.2's @TileNetworkData box, then what its entities carried: the path, the robot (position, destination, motion
     * and laser) and the needed items GuiBuilder shows.
     */
    @Override
    public void writePacketPayload(RegistryFriendlyByteBuf data) {
        box.writeToStream(data);

        data.writeBoolean(path != null);
        if (path != null) {
            data.writeVarInt(path.size());
            for (BlockIndex b : path) {
                data.writeInt(b.x);
                data.writeInt(b.y);
                data.writeInt(b.z);
            }
        }

        data.writeBoolean(builderRobot != null);
        if (builderRobot != null) {
            builderRobot.writeData(data);
        }

        Collection<ItemStack> needs = getNeededItems();
        List<ItemStack> shown = new ArrayList<>();
        if (needs != null) {
            for (ItemStack stack : needs) {
                if (shown.size() >= MAX_NEEDED_SHOWN)
                    break;
                shown.add(stack.copy());
            }
        }
        data.writeVarInt(shown.size());
        for (ItemStack stack : shown) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(data, stack.copyWithCount(1));
            data.writeVarInt(stack.getCount());
        }
        sentNeededItems = shown;
    }

    @Override
    public void readPacketPayload(RegistryFriendlyByteBuf data) {
        Box old = new Box();
        old.initialize(box);
        boolean initialized = box.isInitialized();

        box.readFromStream(data);

        if (initialized && (!box.isInitialized() || box.xMin != old.xMin || box.yMin != old.yMin || box.zMin != old.zMin
                || box.xMax != old.xMax || box.yMax != old.yMax || box.zMax != old.zMax)) {
            box.deleteLasers();
        }

        if (box.isInitialized() && level != null) {
            box.createLasers(level, LaserKind.Stripes);
        }

        if (data.readBoolean()) {
            int size = Math.min(data.readVarInt(), 4096);
            LinkedList<BlockIndex> newPath = new LinkedList<>();
            for (int i = 0; i < size; i++) {
                newPath.add(new BlockIndex(data.readInt(), data.readInt(), data.readInt()));
            }
            if (path == null || !path.equals(newPath)) {
                cleanPathLasers();
                path = newPath;
                createLasersForPath();
            }
        } else if (path != null) {
            cleanPathLasers();
            path = null;
        }

        if (data.readBoolean()) {
            if (builderRobot == null) {
                builderRobot = new EntityRobot(level, new Box());
            }
            builderRobot.readData(data);
        } else if (builderRobot != null) {
            builderRobot.setDead(null);
            builderRobot = null;
        }

        int count = Math.min(data.readVarInt(), MAX_NEEDED_SHOWN);
        List<ItemStack> needs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ItemStack stack = ItemStack.OPTIONAL_STREAM_CODEC.decode(data);
            needs.add(stack.copyWithCount(Math.max(1, data.readVarInt())));
        }
        clientNeededItems = needs;
    }

    @Override
    public void updateEntity() {

        super.updateEntity();

        if (level == null)
            return;

        if (level.isClientSide()) {
            if (builderRobot != null) {
                builderRobot.onUpdate(null);
            }
            return;
        }

        if ((bluePrintBuilder == null || bluePrintBuilder.done) && box.isInitialized() && (builderRobot == null || builderRobot.done())) {

            box.deleteLasers();
            box.reset();

            sendNetworkUpdate();

            return;
        }

        if (!box.isInitialized() && bluePrintBuilder == null && builderRobot != null) {
            builderRobot.setDead(this);
            builderRobot = null;
            networkDirty = true;
        }

        if (builderRobot != null) {
            builderRobot.onUpdate(this);
        }

        // Not in 4.2.2: what the client draws, at most every other tick.
        if ((networkDirty || (builderRobot != null && builderRobot.needsUpdate) || !sameStacks(sentNeededItems, getNeededItems()))
                && level.getGameTime() % 2 == 0) {
            networkDirty = false;
            sendNetworkUpdate();
        }
    }

    private static boolean sameStacks(List<ItemStack> sent, @Nullable Collection<ItemStack> needs) {
        if (needs == null)
            return sent.isEmpty();
        Iterator<ItemStack> it = needs.iterator();
        for (ItemStack stack : sent) {
            if (!it.hasNext())
                return false;
            ItemStack other = it.next();
            if (!ItemStack.isSameItemSameComponents(stack, other) || stack.getCount() != other.getCount())
                return false;
        }
        return !it.hasNext() || sent.size() >= MAX_NEEDED_SHOWN;
    }

    @Override
    public boolean isActive() {
        return !done;
    }

    @Override
    public boolean manageFluids() {
        return false;
    }

    @Override
    public boolean manageSolids() {
        return true;
    }

    public void cleanPathLasers() {
        if (pathLasers != null) {
            for (EntityLaser laser : pathLasers) {
                laser.setDead();
            }

            pathLasers = null;
        }
    }

    public boolean isBuildingBlueprint() {
        return getItem(0).getItem() instanceof ItemBptBluePrint;
    }

    /** The stacks still needed, most first; on the client the ones the server last sent. */
    public @Nullable Collection<ItemStack> getNeededItems() {
        if (level != null && level.isClientSide())
            return clientNeededItems;
        if (bluePrintBuilder instanceof BptBuilderBlueprint)
            return ((BptBuilderBlueprint) bluePrintBuilder).neededItems;
        else
            return null;
    }

    @Override
    public boolean isBuildingMaterial(int i) {
        return i != 0;
    }

    @Override
    public boolean allowAction(IAction action) {
        return false;
    }

    /* MENU: 4.2.2's GuiHandler, GuiIds.BUILDER. */
    @Override
    public Component getDisplayName() {
        return Component.translatable("tile.builderBlock");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ContainerBuilder(containerId, playerInventory, this);
    }

    /* ROBOT: what core/EntityRobot needs to build as the owner. Not in 4.2.2. */

    @Override
    public @Nullable Player getRobotPlayer() {
        return BuildCraftPlayer.get(this);
    }

    /** Back into the building slots; what does not fit is dropped on the builder. */
    @Override
    public void giveBack(ItemStack stack) {
        if (stack.isEmpty() || level == null)
            return;

        for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
            for (int i = 1; i < items.getContainerSize() && !stack.isEmpty(); i++) {
                ItemStack slot = items.getItem(i);
                if (pass == 0 && !slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, stack)) {
                    int move = Math.min(stack.getCount(), Math.min(getMaxStackSize(), slot.getMaxStackSize()) - slot.getCount());
                    if (move > 0) {
                        slot.grow(move);
                        stack.shrink(move);
                        items.setItem(i, slot);
                    }
                } else if (pass == 1 && slot.isEmpty()) {
                    items.setItem(i, stack.split(Math.min(stack.getCount(), stack.getMaxStackSize())));
                }
            }
        }

        if (!stack.isEmpty()) {
            Block.popResource(level, worldPosition.above(), stack);
        }
    }

    /**
     * A built BuildCraft machine has no owner (#76 strips it from the capture): it gets the builder's, as if the
     * builder's owner had placed it.
     */
    @Override
    public void onBuilt(BptSlot slot) {
        if (level == null || getOwnerProfile() == null)
            return;
        if (level.getBlockEntity(slot.pos()) instanceof TileBuildCraft tile && tile.getOwnerProfile() == null) {
            Player player = getRobotPlayer();
            if (player != null) {
                tile.onBlockPlacedBy(player, ItemStack.EMPTY);
                tile.setChanged();
            }
        }
    }

    /**
     * Not in 4.2.2, which took materials from its own slots only: the building slots, then each neighbouring
     * inventory (below, above, north, south, west, east) as a hopper sees it (a double chest whole). A sided
     * inventory gives only what it lets out on the side facing the builder; another builder only its building slots.
     */
    public IBuilderInventory getBuildingInventory() {
        List<Container> containers = new ArrayList<>();
        List<Integer> slots = new ArrayList<>();
        List<Direction> faces = new ArrayList<>();

        for (int i = 1; i < getContainerSize(); i++) {
            containers.add(this);
            slots.add(i);
            faces.add(null);
        }

        if (level != null) {
            for (Direction dir : Direction.values()) {
                Container container = HopperBlockEntity.getContainerAt(level, worldPosition.relative(dir));
                if (container == null || container == this)
                    continue;
                Direction face = dir.getOpposite();
                if (container instanceof WorldlyContainer sided) {
                    for (int slot : sided.getSlotsForFace(face)) {
                        containers.add(container);
                        slots.add(slot);
                        faces.add(face);
                    }
                } else {
                    for (int slot = 0; slot < container.getContainerSize(); slot++) {
                        if (container instanceof IBuilderInventory builder && !builder.isBuildingMaterial(slot))
                            continue;
                        containers.add(container);
                        slots.add(slot);
                        faces.add(face);
                    }
                }
            }
        }

        return new BuildingInventory(containers, slots, faces);
    }

    /** The slots getBuildingInventory found, as one inventory of building materials. */
    private static final class BuildingInventory implements IBuilderInventory {
        private final List<Container> containers;
        private final List<Integer> slots;
        private final List<@Nullable Direction> faces;

        BuildingInventory(List<Container> containers, List<Integer> slots, List<@Nullable Direction> faces) {
            this.containers = containers;
            this.slots = slots;
            this.faces = faces;
        }

        @Override
        public boolean isBuildingMaterial(int i) {
            Direction face = faces.get(i);
            if (face != null && containers.get(i) instanceof WorldlyContainer sided) {
                ItemStack stack = getItem(i);
                return stack.isEmpty() || sided.canTakeItemThroughFace(slots.get(i), stack, face);
            }
            return true;
        }

        @Override
        public int getContainerSize() {
            return slots.size();
        }

        @Override
        public boolean isEmpty() {
            for (int i = 0; i < slots.size(); i++) {
                if (!getItem(i).isEmpty())
                    return false;
            }
            return true;
        }

        @Override
        public ItemStack getItem(int i) {
            return containers.get(i).getItem(slots.get(i));
        }

        @Override
        public ItemStack removeItem(int i, int count) {
            ItemStack result = containers.get(i).removeItem(slots.get(i), count);
            containers.get(i).setChanged();
            return result;
        }

        @Override
        public ItemStack removeItemNoUpdate(int i) {
            return containers.get(i).removeItemNoUpdate(slots.get(i));
        }

        @Override
        public void setItem(int i, ItemStack stack) {
            containers.get(i).setItem(slots.get(i), stack);
            containers.get(i).setChanged();
        }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(Player player) {
            return false;
        }

        @Override
        public void clearContent() {
        }
    }

    // Not in 4.2.2.

    public Box getBox() {
        return box;
    }

    public boolean isDone() {
        return done;
    }

    public PowerHandler getPowerHandler() {
        return powerHandler;
    }

    public @Nullable EntityRobot getRobot() {
        return builderRobot;
    }

    public @Nullable List<EntityLaser> getPathLasers() {
        return pathLasers;
    }

    public @Nullable BptBuilderBase getBluePrintBuilder() {
        return bluePrintBuilder;
    }

    // Status panel (plan section 10), not in 4.2.2.

    /** Waiting for building materials: the last step found blocks to build and not their items. */
    @Override
    public boolean isBlocked() {
        return missingMaterials && !done;
    }

    @Override
    public MachineStatus getStatusState() {
        if (!(getItem(0).getItem() instanceof ItemBptBase))
            return MachineStatus.IDLE;
        if (done && (builderRobot == null || builderRobot.done()))
            return MachineStatus.DONE;
        if (isBlocked())
            return MachineStatus.BLOCKED;
        if (powerHandler.getEnergyStored() < POWER_USAGE && flowMeter.energyInPerSecond() == 0)
            return MachineStatus.NO_POWER;
        return MachineStatus.WORKING;
    }

    @Override
    public StatusReason getStatusReason() {
        return isBlocked() ? StatusReason.MISSING_MATERIALS : StatusReason.NONE;
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
