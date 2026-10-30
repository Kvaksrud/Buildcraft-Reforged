/*
 * Ported from buildcraft/builders/TilePathMarker.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: TileEntity becomes BlockEntity; NBT becomes ValueInput/ValueOutput with the
 * same tag names; the EntityPowerLaser between two linked markers is no longer spawned: the link positions are synced
 * with tryingToConnect and RenderMarker draws the laser; invalidate becomes destroy (block removal), onChunkUnload
 * becomes onChunkUnloaded; the dimension id check compares levels; handleUpdatePacket's re-render becomes the
 * BlockPathMarker ACTIVE property.
 */
package buildcraftreforged.builders;

import buildcraftreforged.core.BlockIndex;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class TilePathMarker extends TileMarker {

    public int x0, y0, z0, x1, y1, z1;
    public boolean loadLink0 = false, loadLink1 = false;
    public boolean tryingToConnect = false;
    public @Nullable TilePathMarker links[] = new TilePathMarker[2];
    /** Client side: the positions of links, from the network payload, for RenderMarker. */
    public @Nullable BlockPos linkPositions[] = new BlockPos[2];
    public static int searchSize = 64; // TODO: this should be moved to default props
    // A list with the pathMarkers that aren't fully connected
    // It only contains markers within the loaded chunks
    private static LinkedList<TilePathMarker> availableMarkers = new LinkedList<TilePathMarker>();

    public TilePathMarker(BlockPos pos, BlockState state) {
        super(BuildersRegistry.pathMarkerTile.get(), pos, state);
    }

    /** 4.2.2 tested both lasers; a link and its laser were always set together. */
    public boolean isFullyConnected() {
        return links[0] != null && links[1] != null;
    }

    public boolean isLinkedTo(TilePathMarker pathMarker) {
        return links[0] == pathMarker || links[1] == pathMarker;
    }

    public void connect(TilePathMarker marker) {
        if (links[0] == null) {
            links[0] = marker;
        } else if (links[1] == null) {
            links[1] = marker;
        }

        if (isFullyConnected()) {
            availableMarkers.remove(this);
        }
    }

    public void createLaserAndConnect(TilePathMarker pathMarker) {

        if (level == null || level.isClientSide())
            return;

        connect(pathMarker);
        pathMarker.connect(this);

        // 4.2.2 spawned an EntityPowerLaser here, which reached the clients as an entity.
        sendNetworkUpdate();
        pathMarker.sendNetworkUpdate();
    }

    // Searches the availableMarkers list for the nearest available that is within searchSize
    private @Nullable TilePathMarker findNearestAvailablePathMarker() {
        TilePathMarker nearestAvailable = null;
        double nearestDistance = 0, distance; // The initialization of nearestDistance is only to make the compiler shut up

        for (TilePathMarker t : availableMarkers) {
            if (t == this || t == this.links[0] || t == this.links[1] || t.level != this.level) {
                continue;
            }

            distance = Math.sqrt(Math.pow(this.xCoord() - t.xCoord(), 2) + Math.pow(this.yCoord() - t.yCoord(), 2) + Math.pow(this.zCoord() - t.zCoord(), 2));

            if (distance > searchSize) {
                continue;
            }

            if (nearestAvailable == null || distance < nearestDistance) {
                nearestAvailable = t;
                nearestDistance = distance;
            }
        }

        return nearestAvailable;
    }

    @Override
    public void tryConnection() {

        if (level == null || level.isClientSide() || isFullyConnected())
            return;

        tryingToConnect = !tryingToConnect; // Allow the user to stop the path marker from searching for new path markers to connect
        sendNetworkUpdate();
        updateActiveState();
    }

    @Override
    public void updateEntity() {
        super.updateEntity();

        if (level == null || level.isClientSide())
            return;

        if (tryingToConnect) {
            TilePathMarker nearestPathMarker = findNearestAvailablePathMarker();

            if (nearestPathMarker != null) {
                createLaserAndConnect(nearestPathMarker);
                tryingToConnect = false;
                sendNetworkUpdate();
                updateActiveState();
            }
        }
    }

    /** 4.2.2 re-rendered the block when tryingToConnect changed, to switch to the active texture. */
    private void updateActiveState() {
        BlockState state = getBlockState();
        if (level != null && state.hasProperty(BlockPathMarker.ACTIVE) && state.getValue(BlockPathMarker.ACTIVE) != tryingToConnect) {
            level.setBlock(worldPosition, state.setValue(BlockPathMarker.ACTIVE, tryingToConnect), 3);
        }
    }

    public LinkedList<BlockIndex> getPath() {
        TreeSet<BlockIndex> visitedPaths = new TreeSet<BlockIndex>();
        LinkedList<BlockIndex> res = new LinkedList<BlockIndex>();

        TilePathMarker nextTile = this;

        while (nextTile != null) {
            BlockIndex b = new BlockIndex(nextTile.xCoord(), nextTile.yCoord(), nextTile.zCoord());

            visitedPaths.add(b);
            res.add(b);

            if (nextTile.links[0] != null
                    && !visitedPaths.contains(new BlockIndex(nextTile.links[0].xCoord(), nextTile.links[0].yCoord(), nextTile.links[0].zCoord()))) {
                nextTile = nextTile.links[0];
            } else if (nextTile.links[1] != null
                    && !visitedPaths.contains(new BlockIndex(nextTile.links[1].xCoord(), nextTile.links[1].yCoord(), nextTile.links[1].zCoord()))) {
                nextTile = nextTile.links[1];
            } else {
                nextTile = null;
            }
        }

        return res;

    }

    @Override
    public void destroy() {
        super.destroy();

        if (links[0] != null) {
            links[0].unlink(this);
        }

        if (links[1] != null) {
            links[1].unlink(this);
        }

        availableMarkers.remove(this);
        tryingToConnect = false;
    }

    @Override
    public void initialize() {
        super.initialize();

        if (level == null)
            return;

        if (!level.isClientSide() && !isFullyConnected()) {
            availableMarkers.add(this);
        }

        if (loadLink0) {
            BlockEntity e0 = level.getBlockEntity(new BlockPos(x0, y0, z0));

            if (links[0] != e0 && links[1] != e0 && e0 instanceof TilePathMarker) {
                createLaserAndConnect((TilePathMarker) e0);
            }

            loadLink0 = false;
        }

        if (loadLink1) {
            BlockEntity e1 = level.getBlockEntity(new BlockPos(x1, y1, z1));

            if (links[0] != e1 && links[1] != e1 && e1 instanceof TilePathMarker) {
                createLaserAndConnect((TilePathMarker) e1);
            }

            loadLink1 = false;
        }
    }

    private void unlink(TilePathMarker tile) {
        if (links[0] == tile) {
            links[0] = null;
        }

        if (links[1] == tile) {
            links[1] = null;
        }

        if (!isFullyConnected() && !availableMarkers.contains(this) && level != null && !level.isClientSide()) {
            availableMarkers.add(this);
        }

        // 4.2.2 killed the laser entity, which the clients saw.
        sendNetworkUpdate();
    }

    @Override
    protected void loadAdditional(ValueInput nbttagcompound) {
        super.loadAdditional(nbttagcompound);

        if (nbttagcompound.getInt("x0").isPresent()) {
            x0 = nbttagcompound.getIntOr("x0", 0);
            y0 = nbttagcompound.getIntOr("y0", 0);
            z0 = nbttagcompound.getIntOr("z0", 0);

            loadLink0 = true;
        }

        if (nbttagcompound.getInt("x1").isPresent()) {
            x1 = nbttagcompound.getIntOr("x1", 0);
            y1 = nbttagcompound.getIntOr("y1", 0);
            z1 = nbttagcompound.getIntOr("z1", 0);

            loadLink1 = true;
        }
    }

    @Override
    protected void saveAdditional(ValueOutput nbttagcompound) {
        super.saveAdditional(nbttagcompound);

        if (links[0] != null) {
            nbttagcompound.putInt("x0", links[0].xCoord());
            nbttagcompound.putInt("y0", links[0].yCoord());
            nbttagcompound.putInt("z0", links[0].zCoord());
        } else if (loadLink0) {
            // Loaded but not ticked yet (26.x does not tick border chunks): keep the saved link.
            nbttagcompound.putInt("x0", x0);
            nbttagcompound.putInt("y0", y0);
            nbttagcompound.putInt("z0", z0);
        }

        if (links[1] != null) {
            nbttagcompound.putInt("x1", links[1].xCoord());
            nbttagcompound.putInt("y1", links[1].yCoord());
            nbttagcompound.putInt("z1", links[1].zCoord());
        } else if (loadLink1) {
            nbttagcompound.putInt("x1", x1);
            nbttagcompound.putInt("y1", y1);
            nbttagcompound.putInt("z1", z1);
        }
    }

    @Override
    public void onChunkUnloaded() {
        availableMarkers.remove(this);
    }

    public static void clearAvailableMarkersList() {
        availableMarkers.clear();
    }

    public static void clearAvailableMarkersList(Level w) {
        for (Iterator<TilePathMarker> it = availableMarkers.iterator(); it.hasNext();) {
            TilePathMarker t = it.next();
            if (t.level == null || t.level.dimension() != w.dimension()) {
                it.remove();
            }
        }
    }

    @Override
    public void writePacketPayload(RegistryFriendlyByteBuf data) {
        super.writePacketPayload(data);

        data.writeBoolean(tryingToConnect);
        for (TilePathMarker link : links) {
            data.writeBoolean(link != null);
            if (link != null) {
                data.writeBlockPos(link.getBlockPos());
            }
        }
    }

    @Override
    public void readPacketPayload(RegistryFriendlyByteBuf data) {
        super.readPacketPayload(data);

        tryingToConnect = data.readBoolean();
        for (int i = 0; i < 2; ++i) {
            linkPositions[i] = data.readBoolean() ? data.readBlockPos() : null;
        }
    }
}
