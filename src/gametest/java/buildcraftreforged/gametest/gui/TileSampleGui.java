package buildcraftreforged.gametest.gui;

import buildcraftreforged.core.TileBuildCraft;
import buildcraftreforged.core.network.IGuiReturnHandler;
import buildcraftreforged.core.network.InvalidPacketException;
import buildcraftreforged.core.network.PacketData;
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
 * step (sent back by {@code PacketGuiReturn}); the phantom slot holds a filter-style ghost item.
 */
public class TileSampleGui extends TileBuildCraft implements MenuProvider, IGuiReturnHandler {
    public static final int MAX_STEP = 16;

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
