package buildcraftreforged.gametest.gui;

import buildcraftreforged.core.TileBuildCraft;
import buildcraftreforged.core.network.IGuiReturnHandler;
import buildcraftreforged.core.network.InvalidPacketException;
import buildcraftreforged.core.network.PacketData;
import buildcraftreforged.core.status.FlowMeter;
import buildcraftreforged.core.status.HeatStage;
import buildcraftreforged.core.status.IStatusProvider;
import buildcraftreforged.core.status.MachineStatus;
import buildcraftreforged.core.status.StatusReason;
import buildcraftreforged.core.status.StatusReport;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Counts up by {@link #step} once a second on the server. The GUI shows the count (synced by a widget) and changes the
 * step (sent back by {@code PacketGuiReturn}); the phantom slot holds a filter-style ghost item. #26: it also fills
 * all six lines of the status panel, made up from the counter.
 */
public class TileSampleGui extends TileBuildCraft implements MenuProvider, IGuiReturnHandler, IStatusProvider {
    public static final int MAX_STEP = 16;
    public static final long CAPACITY = 10000;

    public final FlowMeter flowMeter = new FlowMeter(() -> level == null ? 0 : level.getGameTime());

    public final SimpleContainer inventory = new SimpleContainer(1);
    public int counter;
    public int step = 1;

    public TileSampleGui(BlockPos pos, BlockState state) {
        super(SampleGui.tileType, pos, state);
    }

    @Override
    public void updateEntity() {
        super.updateEntity();
        if (level != null && !level.isClientSide() && level.getGameTime() % 20 == 0) {
            counter += step;
        }
        if (level != null && !level.isClientSide()) {
            // Made-up traffic for the panel: one item and 25 mB in and out each tick, 2 FE/t drawn.
            flowMeter.itemsIn(1);
            flowMeter.itemsOut(1);
            flowMeter.fluidIn(25);
            flowMeter.fluidOut(25);
            flowMeter.energyIn(2);
        }
    }

    @Override
    public MachineStatus getStatusState() {
        return counter % 10 < 5 ? MachineStatus.WORKING : MachineStatus.BLOCKED;
    }

    @Override
    public StatusReason getStatusReason() {
        return getStatusState() == MachineStatus.BLOCKED ? StatusReason.OUTPUT_FULL : StatusReason.NONE;
    }

    @Override
    public FlowMeter getStatusFlow() {
        return flowMeter;
    }

    @Override
    public StatusReport.Power getStatusPower() {
        return StatusReport.Power.drawn(flowMeter, Math.floorMod(counter * 100L, CAPACITY), CAPACITY);
    }

    @Override
    public StatusReport.Heat getStatusHeat() {
        return new StatusReport.Heat(HeatStage.values()[Math.floorMod(counter, HeatStage.values().length)],
                Math.max(StatusReport.Heat.NO_COUNTDOWN, 300 - counter));
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Sample GUI");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ContainerSampleGui(containerId, playerInventory, this);
    }

    @Override
    public void writeGuiData(RegistryFriendlyByteBuf data) {
        data.writeVarInt(step);
    }

    @Override
    public void readGuiData(RegistryFriendlyByteBuf data, @Nullable Player player) throws InvalidPacketException {
        step = PacketData.readVarInt(data, 1, MAX_STEP);
    }
}
