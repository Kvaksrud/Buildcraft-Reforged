package buildcraftreforged.gametest.gui;

import buildcraftreforged.core.gui.BuildCraftContainer;
import buildcraftreforged.core.gui.slots.SlotPhantom;
import buildcraftreforged.core.network.IGuiReturnHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;

/** The Stirling engine's layout (4.2.2 {@code ContainerEngine}) with a phantom slot in place of the fuel slot. */
public class ContainerSampleGui extends BuildCraftContainer {
    public final TileSampleGui tile;
    public final CounterWidget counterWidget;

    /** Client side: the block position comes with the open-menu packet. */
    public ContainerSampleGui(int containerId, Inventory inventoryplayer, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventoryplayer, clientTile(inventoryplayer, extraData.readBlockPos()));
    }

    public ContainerSampleGui(int containerId, Inventory inventoryplayer, TileSampleGui tile) {
        super(SampleGui.menuType, containerId, tile.inventory.getContainerSize());
        this.tile = tile;

        addSlot(new SlotPhantom(tile.inventory, 0, 80, 41));

        for (int i = 0; i < 3; i++) {
            for (int k = 0; k < 9; k++) {
                addSlot(new Slot(inventoryplayer, k + i * 9 + 9, 8 + k * 18, 84 + i * 18));
            }
        }

        for (int j = 0; j < 9; j++) {
            addSlot(new Slot(inventoryplayer, j, 8 + j * 18, 142));
        }

        counterWidget = new CounterWidget(tile, 80, 24, 176, 0, 14, 14);
        addWidget(counterWidget);
    }

    private static TileSampleGui clientTile(Inventory inventoryplayer, BlockPos pos) {
        if (inventoryplayer.player.level().getBlockEntity(pos) instanceof TileSampleGui tile) {
            return tile;
        }
        // The block went away before the menu arrived; the menu closes on the next stillValid check.
        return new TileSampleGui(pos, SampleGui.block.defaultBlockState());
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(tile, player);
    }

    @Override
    public IGuiReturnHandler getGuiReturnHandler() {
        return tile;
    }
}
