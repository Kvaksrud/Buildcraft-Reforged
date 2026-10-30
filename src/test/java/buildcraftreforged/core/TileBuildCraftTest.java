package buildcraftreforged.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import buildcraftreforged.api.power.IPowerReceptor;
import buildcraftreforged.api.power.PowerHandler;
import buildcraftreforged.api.gates.IAction;
import buildcraftreforged.core.network.PacketTileState;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.junit.jupiter.api.Test;

/**
 * The sample block entity of #21: ticks through BlockBuildCraft's ticker, saves and loads through TileBuildCraft, and
 * syncs its network payload through #20's PacketTileState and the description tag. Block entity types cannot be registered once the game has loaded, so the sample borrows
 * the furnace's type and state; nothing here reads them.
 */
class TileBuildCraftTest {
    private static final BlockPos POS = new BlockPos(10, 64, -3);
    private static final BlockState STATE = Blocks.FURNACE.defaultBlockState();

    static class SampleTile extends TileBuildCraft implements IMachine, IDropControlInventory {
        int initializeCalls;
        int updateCalls;
        int destroyCalls;
        int postPacketCalls;
        boolean canUpdate = true;
        boolean active;
        int energy;

        SampleTile() {
            super(BlockEntityTypes.FURNACE, POS, STATE);
        }

        @Override
        public boolean canUpdate() {
            return canUpdate;
        }

        @Override
        public void initialize() {
            super.initialize();
            initializeCalls++;
        }

        @Override
        public void updateEntity() {
            super.updateEntity();
            updateCalls++;
        }

        @Override
        public void destroy() {
            destroyCalls++;
        }

        @Override
        public boolean isActive() {
            return active;
        }

        @Override
        public boolean manageFluids() {
            return false;
        }

        @Override
        public boolean manageSolids() {
            return false;
        }

        @Override
        public boolean allowAction(IAction action) {
            return false;
        }

        @Override
        public boolean doDrop() {
            return false;
        }

        @Override
        public void writePacketPayload(RegistryFriendlyByteBuf data) {
            data.writeBoolean(active);
            data.writeVarInt(energy);
        }

        @Override
        public void readPacketPayload(RegistryFriendlyByteBuf data) {
            active = data.readBoolean();
            energy = data.readVarInt();
        }

        @Override
        public void postPacketHandling() {
            postPacketCalls++;
        }

        @Override
        protected void saveAdditional(ValueOutput output) {
            super.saveAdditional(output);
            output.putInt("energy", energy);
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            energy = input.getIntOr("energy", 0);
        }
    }

    private static void tick(TileBuildCraft tile) {
        BlockBuildCraft.updateEntity(null, tile.getBlockPos(), tile.getBlockState(), tile);
    }

    @Test
    void ticksAndInitializesOnTheFirstTick() {
        SampleTile tile = new SampleTile();
        tick(tile);
        tick(tile);
        tick(tile);
        assertEquals(1, tile.initializeCalls);
        assertEquals(3, tile.updateCalls);
    }

    static class PowerTile extends TileBuildCraft implements IPowerReceptor {
        final PowerHandler powerHandler = new PowerHandler(this, PowerHandler.Type.MACHINE);
        int nullSideLookups;

        PowerTile() {
            super(BlockEntityTypes.FURNACE, POS, STATE);
        }

        @Override
        public PowerHandler.PowerReceiver getPowerReceiver(Direction side) {
            if (side == null)
                nullSideLookups++;
            return powerHandler.getPowerReceiver();
        }

        @Override
        public void doWork(PowerHandler workProvider) {
        }

        @Override
        public Level getWorld() {
            return level;
        }
    }

    @Test
    void updatesThePowerReceiverEveryTick() {
        // 4.2.2 TileBuildCraft.updateEntity: receptor.getPowerReceiver(null).update().
        PowerTile tile = new PowerTile();
        tick(tile);
        tick(tile);
        assertEquals(2, tile.nullSideLookups);
    }

    @Test
    void canUpdateFalseStopsTheTick() {
        SampleTile tile = new SampleTile();
        tile.canUpdate = false;
        tick(tile);
        assertEquals(0, tile.updateCalls);
        assertEquals(0, tile.initializeCalls);
    }

    @Test
    void removedTileInitializesAgainWhenRestored() {
        SampleTile tile = new SampleTile();
        tick(tile);
        tile.setRemoved();
        tick(tile);
        assertEquals(1, tile.initializeCalls, "a removed tile does not initialize");
        tile.clearRemoved();
        tick(tile);
        assertEquals(2, tile.initializeCalls);
    }

    @Test
    void savesAndLoadsWithThe422TagNames() {
        SampleTile tile = new SampleTile();
        tile.energy = 42;
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, RegistryAccess.EMPTY);
        tile.saveCustomOnly(output);
        CompoundTag tag = output.buildResult();
        assertEquals("[BuildCraft]", tag.getStringOr("owner", ""));
        assertEquals(42, tag.getIntOr("energy", 0));

        tag.putString("owner", "Patrick");
        SampleTile loaded = new SampleTile();
        loaded.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, tag));
        assertEquals("Patrick", loaded.getOwner());
        assertEquals(42, loaded.energy);
    }

    @Test
    void missingOwnerKeepsTheDefault() {
        SampleTile tile = new SampleTile();
        tile.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, new CompoundTag()));
        assertEquals("[BuildCraft]", tile.getOwner());
    }

    @Test
    void placedByNoPlayerKeepsTheDefaultOwner() {
        SampleTile tile = new SampleTile();
        tile.onBlockPlacedBy(null, ItemStack.EMPTY);
        assertEquals("[BuildCraft]", tile.getOwner());
    }

    @Test
    void networkPayloadRoundTrips() {
        SampleTile server = new SampleTile();
        server.active = true;
        server.energy = 1234;
        RegistryFriendlyByteBuf data = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        server.writePacketPayload(data);

        SampleTile client = new SampleTile();
        client.readPacketPayload(data);
        assertEquals(0, data.readableBytes());
        assertTrue(client.active);
        assertEquals(1234, client.energy);
    }

    @Test
    void tileStatePacketSyncs() {
        SampleTile server = new SampleTile();
        server.active = true;
        server.energy = 77;
        PacketTileState packet = new PacketTileState(POS, RegistryAccess.EMPTY);
        packet.addStateForSerialization(TileBuildCraft.NETWORK_STATE, server.getStateInstance(TileBuildCraft.NETWORK_STATE));

        ByteBuf buf = Unpooled.buffer();
        PacketTileState.STREAM_CODEC.encode(buf, packet);
        PacketTileState received = PacketTileState.STREAM_CODEC.decode(buf);

        SampleTile client = new SampleTile();
        received.applyStates(client, RegistryAccess.EMPTY);
        assertTrue(client.active);
        assertEquals(77, client.energy);
        assertEquals(1, client.postPacketCalls);
    }

    @Test
    void unknownStateIdHasNoInstance() {
        assertNull(new SampleTile().getStateInstance((byte) 5));
    }

    @Test
    void descriptionCarriesOnlyThePayload() {
        SampleTile server = new SampleTile();
        server.active = true;
        server.energy = 9;
        CompoundTag tag = server.getUpdateTag(RegistryAccess.EMPTY);
        assertFalse(tag.contains("owner"), "the description is the network payload, not the saved data");

        SampleTile client = new SampleTile();
        client.handleUpdateTag(TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, tag));
        assertTrue(client.active);
        assertEquals(9, client.energy);
        assertEquals(1, client.postPacketCalls);
    }

    @Test
    void removalCallsDestroy() {
        SampleTile tile = new SampleTile();
        tile.preRemoveSideEffects(POS, STATE);
        assertEquals(1, tile.destroyCalls);
        assertFalse(tile.isRemoved());
    }
}
