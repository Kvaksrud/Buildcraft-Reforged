/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: TileEntity becomes BlockEntity, ticked by BlockBuildCraft's BlockEntityTicker;
 * NBT becomes ValueInput/ValueOutput; invalidate becomes setRemoved; Utils.preDestroyBlock becomes
 * preRemoveSideEffects; the active-machine light goes through the auxiliary light manager; the reflection packet
 * wrappers become writePacketPayload/readPacketPayload, sent as the vanilla update tag and as state 0 of a
 * PacketTileState. The inventory drop uses InvUtils (#23); getItemHandler and registerItemHandler expose the item
 * capability per side. An IStatusProvider keeps its block's StatusPanel.BLOCKED property (the red light) current.
 * Placement records the placer's UUID next to the owner name, so machines act as the owner's fake player (#27).
 */
package buildcraftreforged.core;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.api.power.IPowerReceptor;
import buildcraftreforged.api.power.PowerHandler;
import buildcraftreforged.core.inventory.InvUtils;
import buildcraftreforged.core.network.IClientState;
import buildcraftreforged.core.network.ISyncedTile;
import buildcraftreforged.core.network.PacketData;
import buildcraftreforged.core.network.PacketTileState;
import buildcraftreforged.core.status.IStatusProvider;
import buildcraftreforged.core.status.StatusPanel;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Codec;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.world.AuxiliaryLightManager;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

public abstract class TileBuildCraft extends BlockEntity implements ISyncedTile {

    /** State id of writePacketPayload in a PacketTileState; tiles with more states use other ids. */
    public static final byte NETWORK_STATE = 0;

    private boolean init = false;
    private String owner = "[BuildCraft]";
    /** The placer's UUID, recorded with owner; null when no player placed the block. */
    private @Nullable UUID ownerId = null;
    private final IClientState networkState = new IClientState() {
        @Override
        public void writeData(RegistryFriendlyByteBuf data) {
            writePacketPayload(data);
        }

        @Override
        public void readData(RegistryFriendlyByteBuf data) {
            readPacketPayload(data);
        }
    };

    public TileBuildCraft(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public String getOwner() {
        return owner;
    }

    /**
     * The game profile of the player who placed this block (UUID and name), or null when no player did, or the block
     * was placed before #27. BuildCraftPlayer.get makes the fake player machines break and place blocks as.
     */
    public @Nullable GameProfile getOwnerProfile() {
        return ownerId == null ? null : new GameProfile(ownerId, owner);
    }

    /**
     * 1.6.4's TileEntity.canUpdate: false stops BlockBuildCraft's ticker calling updateEntity, as 4.2.2's
     * TileAssemblyTable does on the client.
     */
    public boolean canUpdate() {
        return true;
    }

    /** Called every tick on both sides by BlockBuildCraft's ticker, as 1.6.4 called updateEntity. */
    public void updateEntity() {
        if (!init && !isRemoved()) {
            initialize();
            init = true;
        }

        if (this instanceof IMachine) {
            updateMachineLight();
        }

        if (this instanceof IPowerReceptor receptor) {
            PowerHandler.PowerReceiver receiver = receptor.getPowerReceiver(null);
            if (receiver != null)
                receiver.update();
        }

        if (this instanceof IStatusProvider) {
            StatusPanel.updateBlockedLight(this);
        }
    }

    /**
     * 4.2.2's BlockBuildCraft.getLightValue added 8 while an IMachine was active. The light engine may not read block
     * entities any more, so the active state is copied into the chunk's auxiliary light manager, which
     * BlockBuildCraft.getLightEmission reads, and the block is relit when it changes.
     */
    private void updateMachineLight() {
        if (level == null)
            return;
        AuxiliaryLightManager lightManager = level.getAuxLightManager(worldPosition);
        int light = ((IMachine) this).isActive() ? 8 : 0;
        if (lightManager != null && lightManager.getLightAt(worldPosition) != light) {
            lightManager.setLightAt(worldPosition, light);
            level.getLightEngine().checkBlock(worldPosition);
        }
    }

    /**
     * 4.2.2 Utils.preDestroyBlock, which BlockBuildCraft.breakBlock called: drop the inventory unless an
     * IDropControlInventory says not to, then destroy(). Server side only, as the drop was.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (this instanceof Container inventory && level != null) {
            if (!(this instanceof IDropControlInventory) || ((IDropControlInventory) this).doDrop()) {
                InvUtils.dropItems(level, inventory, pos.getX(), pos.getY(), pos.getZ());
                InvUtils.wipeInventory(inventory);
            }
        }

        if (level != null) {
            AuxiliaryLightManager lightManager = level.getAuxLightManager(pos);
            if (lightManager != null)
                lightManager.removeLightAt(pos);
        }

        destroy();
    }

    @Override
    public void setRemoved() {
        init = false;
        super.setRemoved();
    }

    /**
     * 4.2.2 called Utils.handleBufferedDescription(this) here, to apply a description packet that arrived before the
     * tile existed. Vanilla now creates the block entity before it applies the update tag, so nothing is buffered.
     */
    public void initialize() {
    }

    public void onBlockPlacedBy(@Nullable LivingEntity entity, ItemStack stack) {
        if (entity instanceof Player) {
            owner = ((Player) entity).getGameProfile().name();
            ownerId = ((Player) entity).getGameProfile().id();
        }
    }

    /**
     * The item capability (Capabilities.Item.BLOCK) on side: InvUtils.getItemHandler, which follows 4.2.2's
     * ISidedInventory rules for a tile that is a WorldlyContainer and shows every slot for one that is a plain
     * Container. Null for a tile without an inventory. Override to expose something else.
     */
    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable Direction side) {
        return InvUtils.getItemHandler(this, side);
    }

    /**
     * Registers getItemHandler as the item capability of type. A module's registry class calls this from its
     * RegisterCapabilitiesEvent listener for each tile type that holds items (4.2.2: implements IInventory).
     */
    public static void registerItemHandler(RegisterCapabilitiesEvent event, BlockEntityType<? extends TileBuildCraft> type) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, type, TileBuildCraft::getItemHandler);
    }

    /** Called by preRemoveSideEffects (4.2.2 Utils.preDestroyBlock) before the block entity goes. */
    public void destroy() {
    }

    // Network sync. 4.2.2 sent every @TileNetworkData field of the tile, by reflection, both as the description packet
    // and as the update packet. Subclasses now write those fields themselves, in the same order.

    /** Writes the fields 4.2.2 marked @TileNetworkData. Server side. */
    public void writePacketPayload(RegistryFriendlyByteBuf data) {
    }

    /** Reads what writePacketPayload wrote. Client side. */
    public void readPacketPayload(RegistryFriendlyByteBuf data) {
    }

    /** Called on the client after readPacketPayload, as in 4.2.2. */
    public void postPacketHandling() {
    }

    /**
     * 4.2.2 sent to players within NETWORK_UPDATE_RANGE; #20 sends to the players tracking the chunk. Only players whose
     * connection negotiated our channel get it: a client without the mod, or a GameTest mock player, would otherwise
     * make NeoForge throw.
     */
    public void sendNetworkUpdate() {
        if (level instanceof ServerLevel serverLevel) {
            PacketTileState packet = new PacketTileState(worldPosition, level.registryAccess());
            packet.addStateForSerialization(NETWORK_STATE, networkState);
            for (ServerPlayer player : serverLevel.getChunkSource().chunkMap.getPlayers(ChunkPos.containing(worldPosition), false)) {
                if (player.connection.hasChannel(PacketTileState.TYPE))
                    PacketDistributor.sendToPlayer(player, packet);
            }
        }
    }

    @Override
    public @Nullable IClientState getStateInstance(byte stateId) {
        return stateId == NETWORK_STATE ? networkState : null;
    }

    @Override
    public void afterStateUpdated(byte stateId) {
        if (stateId == NETWORK_STATE)
            postPacketHandling();
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** The description packet: the same bytes as an update, as 4.2.2's getDescriptionPacket sent. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putByteArray("networkData", PacketData.write(registryAccess(registries), this::writePacketPayload));
        return tag;
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        handleDescription(input);
    }

    @Override
    public void onDataPacket(Connection net, ValueInput input) {
        handleDescription(input);
    }

    private void handleDescription(ValueInput input) {
        Optional<ByteBuffer> bytes = input.read("networkData", Codec.BYTE_BUFFER);
        if (bytes.isEmpty())
            return;
        byte[] array = new byte[bytes.get().remaining()];
        bytes.get().get(array);
        RegistryFriendlyByteBuf data = PacketData.wrap(array, registryAccess(input.lookup()));
        try {
            readPacketPayload(data);
            postPacketHandling();
        } catch (RuntimeException e) {
            BuildcraftReforged.LOGGER.warn("Could not apply description data at {}", worldPosition, e);
        } finally {
            data.release();
        }
    }

    private RegistryAccess registryAccess(HolderLookup.Provider registries) {
        if (registries instanceof RegistryAccess registryAccess)
            return registryAccess;
        return level != null ? level.registryAccess() : RegistryAccess.EMPTY;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("owner", owner);
        output.storeNullable("ownerId", UUIDUtil.CODEC, ownerId);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        owner = input.getStringOr("owner", owner);
        ownerId = input.read("ownerId", UUIDUtil.CODEC).orElse(ownerId);
    }

    public boolean isInvNameLocalized() {
        // TODO Auto-generated method stub
        return false;
    }

    public @Nullable Level getWorld() {
        return level;
    }
}
