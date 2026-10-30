/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: TileEntity becomes BlockEntity; NBT becomes ValueInput/ValueOutput with the
 * same tag names; the @TileNetworkData fields are written by writePacketPayload in declaration order; the laser and
 * signal EntityBlocks are render data drawn by RenderMarker instead of spawned entities; block ids become BlockState
 * checks; destroy runs from preRemoveSideEffects (block removal) instead of invalidate, which also fires on chunk
 * unload now.
 */

package buildcraftreforged.builders;

import buildcraftreforged.api.core.IAreaProvider;
import buildcraftreforged.api.core.LaserKind;
import buildcraftreforged.api.core.Position;
import buildcraftreforged.core.EntityBlock;
import buildcraftreforged.core.TileBuildCraft;
import buildcraftreforged.core.utils.Utils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class TileMarker extends TileBuildCraft implements IAreaProvider {

    private static int maxSize = 64;

    public static class TileWrapper {

        public int x, y, z;

        public TileWrapper() {
            x = Integer.MAX_VALUE;
            y = Integer.MAX_VALUE;
            z = Integer.MAX_VALUE;
        }

        public TileWrapper(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        private @Nullable TileMarker marker;

        public boolean isSet() {
            return x != Integer.MAX_VALUE;
        }

        public @Nullable TileMarker getMarker(@Nullable Level world) {
            if (!isSet() || world == null)
                return null;

            // A cached marker that was removed is looked up again, so a replaced block entity is found.
            if (marker == null || marker.isRemoved()) {
                BlockEntity tile = world.getBlockEntity(new BlockPos(x, y, z));
                marker = tile instanceof TileMarker ? (TileMarker) tile : null;
            }

            return marker;
        }

        public void reset() {
            x = Integer.MAX_VALUE;
            y = Integer.MAX_VALUE;
            z = Integer.MAX_VALUE;
        }

        void write(RegistryFriendlyByteBuf data) {
            data.writeInt(x);
            data.writeInt(y);
            data.writeInt(z);
        }

        static TileWrapper read(RegistryFriendlyByteBuf data) {
            return new TileWrapper(data.readInt(), data.readInt(), data.readInt());
        }
    }

    public static class Origin {

        public boolean isSet() {
            return vectO.isSet();
        }

        public TileWrapper vectO = new TileWrapper();
        public TileWrapper[] vect = { new TileWrapper(), new TileWrapper(), new TileWrapper() };
        public int xMin, yMin, zMin, xMax, yMax, zMax;
    }

    public Origin origin = new Origin();

    private EntityBlock @Nullable [] lasers;
    private EntityBlock @Nullable [] signals;
    public boolean showSignals = false;

    public TileMarker(BlockPos pos, BlockState state) {
        this(BuildersRegistry.markerTile.get(), pos, state);
    }

    protected TileMarker(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public int xCoord() {
        return worldPosition.getX();
    }

    public int yCoord() {
        return worldPosition.getY();
    }

    public int zCoord() {
        return worldPosition.getZ();
    }

    public void updateSignals() {
        if (level != null && !level.isClientSide()) {
            showSignals = level.hasNeighborSignal(worldPosition);
            sendNetworkUpdate();
        }
    }

    private void switchSignals() {
        if (signals != null) {
            for (EntityBlock b : signals) {
                if (b != null) {
                    b.setDead();
                }
            }
            signals = null;
        }
        if (showSignals && level != null) {
            int xCoord = xCoord(), yCoord = yCoord(), zCoord = zCoord();
            signals = new EntityBlock[6];
            if (!origin.isSet() || !origin.vect[0].isSet()) {
                signals[0] = Utils.createLaser(level, new Position(xCoord, yCoord, zCoord), new Position(xCoord + maxSize - 1, yCoord, zCoord),
                        LaserKind.Blue);
                signals[1] = Utils.createLaser(level, new Position(xCoord - maxSize + 1, yCoord, zCoord), new Position(xCoord, yCoord, zCoord),
                        LaserKind.Blue);
            }

            if (!origin.isSet() || !origin.vect[1].isSet()) {
                signals[2] = Utils.createLaser(level, new Position(xCoord, yCoord, zCoord), new Position(xCoord, yCoord + maxSize - 1, zCoord),
                        LaserKind.Blue);
                signals[3] = Utils.createLaser(level, new Position(xCoord, yCoord - maxSize + 1, zCoord), new Position(xCoord, yCoord, zCoord),
                        LaserKind.Blue);
            }

            if (!origin.isSet() || !origin.vect[2].isSet()) {
                signals[4] = Utils.createLaser(level, new Position(xCoord, yCoord, zCoord), new Position(xCoord, yCoord, zCoord + maxSize - 1),
                        LaserKind.Blue);
                signals[5] = Utils.createLaser(level, new Position(xCoord, yCoord, zCoord - maxSize + 1), new Position(xCoord, yCoord, zCoord),
                        LaserKind.Blue);
            }
        }
    }

    private @Nullable Position initVectO;
    private @Nullable Position @Nullable [] initVect;

    @Override
    public void initialize() {
        super.initialize();

        updateSignals();

        if (initVectO != null && level != null) {
            origin = new Origin();

            origin.vectO = new TileWrapper((int) initVectO.x, (int) initVectO.y, (int) initVectO.z);

            for (int i = 0; i < 3; ++i) {
                if (initVect[i] != null) {
                    BlockEntity tile = level.getBlockEntity(new BlockPos((int) initVect[i].x, (int) initVect[i].y, (int) initVect[i].z));
                    linkTo(tile instanceof TileMarker ? (TileMarker) tile : null, i);
                }
            }

            // 4.2.2 kept initVectO, so a second initialize (after invalidate and validate) linked again.
            initVectO = null;
            initVect = null;
        }
    }

    public void tryConnection() {
        if (level == null || level.isClientSide())
            return;

        for (int j = 0; j < 3; ++j) {
            if (!origin.isSet() || !origin.vect[j].isSet()) {
                setVect(j);
            }
        }

        sendNetworkUpdate();
    }

    void setVect(int n) {
        Block markerId = BuildersRegistry.markerBlock.get();

        int[] coords = new int[3];

        coords[0] = xCoord();
        coords[1] = yCoord();
        coords[2] = zCoord();

        if (!origin.isSet() || !origin.vect[n].isSet()) {
            for (int j = 1; j < maxSize; ++j) {
                coords[n] += j;

                BlockPos pos = new BlockPos(coords[0], coords[1], coords[2]);

                if (level.getBlockState(pos).is(markerId)) {
                    TileMarker marker = level.getBlockEntity(pos) instanceof TileMarker tile ? tile : null;

                    if (linkTo(marker, n)) {
                        break;
                    }
                }

                coords[n] -= j;
                coords[n] -= j;

                pos = new BlockPos(coords[0], coords[1], coords[2]);

                if (level.getBlockState(pos).is(markerId)) {
                    TileMarker marker = level.getBlockEntity(pos) instanceof TileMarker tile ? tile : null;

                    if (linkTo(marker, n)) {
                        break;
                    }
                }

                coords[n] += j;
            }
        }
    }

    private boolean linkTo(@Nullable TileMarker marker, int n) {
        if (marker == null)
            return false;

        if (origin.isSet() && marker.origin.isSet())
            return false;

        if (!origin.isSet() && !marker.origin.isSet()) {
            origin = new Origin();
            marker.origin = origin;
            origin.vectO = new TileWrapper(xCoord(), yCoord(), zCoord());
            origin.vect[n] = new TileWrapper(marker.xCoord(), marker.yCoord(), marker.zCoord());
        } else if (!origin.isSet()) {
            origin = marker.origin;
            origin.vect[n] = new TileWrapper(xCoord(), yCoord(), zCoord());
        } else {
            marker.origin = origin;
            origin.vect[n] = new TileWrapper(marker.xCoord(), marker.yCoord(), marker.zCoord());
        }

        TileMarker markerOrigin = origin.vectO.getMarker(level);
        if (markerOrigin != null) {
            markerOrigin.createLasers();
        }
        updateSignals();
        marker.updateSignals();

        return true;
    }

    private void createLasers() {
        if (lasers != null) {
            for (EntityBlock entity : lasers) {
                if (entity != null) {
                    entity.setDead();
                }
            }
        }

        lasers = new EntityBlock[12];
        Origin o = origin;

        if (!origin.vect[0].isSet()) {
            o.xMin = origin.vectO.x;
            o.xMax = origin.vectO.x;
        } else if (origin.vect[0].x < xCoord()) {
            o.xMin = origin.vect[0].x;
            o.xMax = xCoord();
        } else {
            o.xMin = xCoord();
            o.xMax = origin.vect[0].x;
        }

        if (!origin.vect[1].isSet()) {
            o.yMin = origin.vectO.y;
            o.yMax = origin.vectO.y;
        } else if (origin.vect[1].y < yCoord()) {
            o.yMin = origin.vect[1].y;
            o.yMax = yCoord();
        } else {
            o.yMin = yCoord();
            o.yMax = origin.vect[1].y;
        }

        if (!origin.vect[2].isSet()) {
            o.zMin = origin.vectO.z;
            o.zMax = origin.vectO.z;
        } else if (origin.vect[2].z < zCoord()) {
            o.zMin = origin.vect[2].z;
            o.zMax = zCoord();
        } else {
            o.zMin = zCoord();
            o.zMax = origin.vect[2].z;
        }

        lasers = Utils.createLaserBox(level, o.xMin, o.yMin, o.zMin, o.xMax, o.yMax, o.zMax, LaserKind.Red);
    }

    /** The red box lasers and the blue signal lasers, for RenderMarker. Either may be null. */
    public EntityBlock @Nullable [] getLasers() {
        return lasers;
    }

    public EntityBlock @Nullable [] getSignals() {
        return signals;
    }

    @Override
    public int xMin() {
        if (origin.isSet())
            return origin.xMin;
        return xCoord();
    }

    @Override
    public int yMin() {
        if (origin.isSet())
            return origin.yMin;
        return yCoord();
    }

    @Override
    public int zMin() {
        if (origin.isSet())
            return origin.zMin;
        return zCoord();
    }

    @Override
    public int xMax() {
        if (origin.isSet())
            return origin.xMax;
        return xCoord();
    }

    @Override
    public int yMax() {
        if (origin.isSet())
            return origin.yMax;
        return yCoord();
    }

    @Override
    public int zMax() {
        if (origin.isSet())
            return origin.zMax;
        return zCoord();
    }

    @Override
    public void destroy() {
        TileMarker markerOrigin = null;

        if (origin.isSet()) {
            markerOrigin = origin.vectO.getMarker(level);

            Origin o = origin;

            if (markerOrigin != null && markerOrigin.lasers != null) {
                for (EntityBlock entity : markerOrigin.lasers) {
                    if (entity != null) {
                        entity.setDead();
                    }
                }
                markerOrigin.lasers = null;
            }

            for (TileWrapper m : o.vect) {
                TileMarker mark = m.getMarker(level);

                if (mark != null) {
                    if (mark.lasers != null) {
                        for (EntityBlock entity : mark.lasers) {
                            if (entity != null) {
                                entity.setDead();
                            }
                        }
                        mark.lasers = null;
                    }

                    if (mark != this) {
                        mark.origin = new Origin();
                    }
                }
            }

            if (markerOrigin != this && markerOrigin != null) {
                markerOrigin.origin = new Origin();
            }

            for (TileWrapper wrapper : o.vect) {
                TileMarker mark = wrapper.getMarker(level);

                if (mark != null) {
                    mark.updateSignals();
                }
            }
            if (markerOrigin != null) {
                markerOrigin.updateSignals();
            }
        }

        if (signals != null) {
            for (EntityBlock block : signals) {
                if (block != null) {
                    block.setDead();
                }
            }
        }

        signals = null;

        if (level != null && !level.isClientSide() && markerOrigin != null && markerOrigin != this) {
            markerOrigin.sendNetworkUpdate();
        }
    }

    @Override
    public void removeFromWorld() {
        if (!origin.isSet() || level == null)
            return;

        Origin o = origin;

        for (TileWrapper m : o.vect.clone()) {
            if (m.isSet()) {
                BlockPos pos = new BlockPos(m.x, m.y, m.z);
                level.removeBlock(pos, false);

                Block.popResource(level, pos, new ItemStack(BuildersRegistry.markerBlock.get()));
            }
        }

        BlockPos pos = new BlockPos(o.vectO.x, o.vectO.y, o.vectO.z);
        level.removeBlock(pos, false);

        Block.popResource(level, pos, new ItemStack(BuildersRegistry.markerBlock.get()));
    }

    @Override
    protected void loadAdditional(ValueInput nbttagcompound) {
        super.loadAdditional(nbttagcompound);

        nbttagcompound.child("vectO").ifPresent(vectO -> {
            initVectO = new Position(vectO);
            initVect = new Position[3];

            for (int i = 0; i < 3; ++i) {
                int n = i;
                nbttagcompound.child("vect" + i).ifPresent(vect -> initVect[n] = new Position(vect));
            }
        });
    }

    @Override
    protected void saveAdditional(ValueOutput nbttagcompound) {
        super.saveAdditional(nbttagcompound);

        if (origin.isSet() && origin.vectO.getMarker(level) == this) {
            new Position(this).writeToNBT(nbttagcompound.child("vectO"));

            for (int i = 0; i < 3; ++i) {
                if (origin.vect[i].isSet()) {
                    new Position(origin.vect[i].x, origin.vect[i].y, origin.vect[i].z).writeToNBT(nbttagcompound.child("vect" + i));
                }
            }

        } else if (initVectO != null) {
            // Not linked yet after loading (saved before the first tick): keep what was loaded.
            initVectO.writeToNBT(nbttagcompound.child("vectO"));

            for (int i = 0; i < 3; ++i) {
                if (initVect[i] != null) {
                    initVect[i].writeToNBT(nbttagcompound.child("vect" + i));
                }
            }
        }
    }

    @Override
    public void writePacketPayload(RegistryFriendlyByteBuf data) {
        super.writePacketPayload(data);

        origin.vectO.write(data);
        for (TileWrapper w : origin.vect) {
            w.write(data);
        }
        data.writeInt(origin.xMin);
        data.writeInt(origin.yMin);
        data.writeInt(origin.zMin);
        data.writeInt(origin.xMax);
        data.writeInt(origin.yMax);
        data.writeInt(origin.zMax);
        data.writeBoolean(showSignals);
    }

    @Override
    public void readPacketPayload(RegistryFriendlyByteBuf data) {
        super.readPacketPayload(data);

        Origin o = new Origin();
        o.vectO = TileWrapper.read(data);
        for (int i = 0; i < 3; ++i) {
            o.vect[i] = TileWrapper.read(data);
        }
        o.xMin = data.readInt();
        o.yMin = data.readInt();
        o.zMin = data.readInt();
        o.xMax = data.readInt();
        o.yMax = data.readInt();
        o.zMax = data.readInt();
        origin = o;
        showSignals = data.readBoolean();
    }

    @Override
    public void postPacketHandling() {
        super.postPacketHandling();

        switchSignals();

        if (origin.vectO.isSet()) {
            TileMarker markerOrigin = origin.vectO.getMarker(level);
            if (markerOrigin != null) {
                markerOrigin.updateSignals();
            }

            for (TileWrapper w : origin.vect) {
                TileMarker m = w.getMarker(level);

                if (m != null) {
                    m.updateSignals();
                }
            }
        }

        createLasers();
    }

}
