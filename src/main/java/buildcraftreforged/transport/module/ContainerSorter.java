package buildcraftreforged.transport.module;

import buildcraftreforged.core.gui.BuildCraftContainer;
import buildcraftreforged.core.gui.slots.SlotPhantom;
import buildcraftreforged.transport.TilePipe;
import buildcraftreforged.transport.TransportRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;

/**
 * The Sorter's menu (4.2.2 {@code ContainerDiamondPipe}): six rows of nine phantom filter slots, one row per side in
 * side order, over the player inventory.
 */
public class ContainerSorter extends BuildCraftContainer {

    public final TilePipe pipe;
    public final ModuleSorter module;

    /** Client side: the pipe position comes with the open-menu packet; the module is a local copy. */
    public ContainerSorter(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory,
                ContainerFilteredExtractor.clientPipe(playerInventory, extraData.readBlockPos()), new ModuleSorter());
    }

    public ContainerSorter(int containerId, Inventory playerInventory, TilePipe pipe, ModuleSorter module) {
        super(TransportRegistry.sorterMenu.get(), containerId, module.filters.getContainerSize());
        this.pipe = pipe;
        this.module = module;

        for (int y = 0; y < 6; y++) {
            for (int x = 0; x < ModuleSorter.ROW; x++) {
                addSlot(new SlotPhantom(module.filters, x + y * ModuleSorter.ROW, 8 + x * 18, 18 + y * 18));
            }
        }

        for (int l = 0; l < 3; l++) {
            for (int k1 = 0; k1 < 9; k1++) {
                addSlot(new Slot(playerInventory, k1 + l * 9 + 9, 8 + k1 * 18, 140 + l * 18));
            }
        }

        for (int i1 = 0; i1 < 9; i1++) {
            addSlot(new Slot(playerInventory, i1, 8 + i1 * 18, 198));
        }
    }

    /** Still the module on the pipe (it is gone once removed by a wrench), and the player is near. */
    @Override
    public boolean stillValid(Player player) {
        return pipe.getModule() == module && Container.stillValidBlockEntity(pipe, player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        // The filters changed through the phantom slots; save them with the pipe.
        if (!player.level().isClientSide())
            pipe.setChanged();
    }
}
