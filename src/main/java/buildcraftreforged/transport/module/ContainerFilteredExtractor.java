package buildcraftreforged.transport.module;

import buildcraftreforged.api.transport.IPipeTile.PipeType;
import buildcraftreforged.core.gui.BuildCraftContainer;
import buildcraftreforged.core.gui.slots.SlotPhantom;
import buildcraftreforged.core.network.IGuiReturnHandler;
import buildcraftreforged.core.network.InvalidPacketException;
import buildcraftreforged.core.network.PacketData;
import buildcraftreforged.transport.PipeMaterial;
import buildcraftreforged.transport.TilePipe;
import buildcraftreforged.transport.TransportRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The Filtered Extractor's menu (4.2.2 {@code ContainerEmeraldPipe}): nine phantom filter slots over the player
 * inventory, and the mode button's state as a data slot. The button sends the new mode back with
 * {@code PacketGuiReturn}; {@link #getGuiReturnHandler()} applies it while the module is still on the pipe.
 */
public class ContainerFilteredExtractor extends BuildCraftContainer {

    public final TilePipe pipe;
    public final ModuleFilteredExtractor module;
    private final GuiReturn guiReturn = new GuiReturn();

    /** Client side: the pipe position comes with the open-menu packet; the module is a local copy. */
    public ContainerFilteredExtractor(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, clientPipe(playerInventory, extraData.readBlockPos()),
                new ModuleFilteredExtractor());
    }

    public ContainerFilteredExtractor(int containerId, Inventory playerInventory, TilePipe pipe,
            ModuleFilteredExtractor module) {
        super(TransportRegistry.filteredExtractorMenu.get(), containerId, module.filters.getContainerSize());
        this.pipe = pipe;
        this.module = module;

        for (int i = 0; i < FilterCursor.SIZE; i++) {
            addSlot(new SlotPhantom(module.filters, i, 8 + i * 18, 18));
        }

        for (int l = 0; l < 3; l++) {
            for (int k1 = 0; k1 < 9; k1++) {
                addSlot(new Slot(playerInventory, k1 + l * 9 + 9, 8 + k1 * 18, 50 + l * 18));
            }
        }

        for (int i1 = 0; i1 < 9; i1++) {
            addSlot(new Slot(playerInventory, i1, 8 + i1 * 18, 108));
        }

        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return module.getMode().ordinal();
            }

            @Override
            public void set(int value) {
                module.setMode(value == ModuleFilteredExtractor.Mode.NONBLOCKING.ordinal()
                        ? ModuleFilteredExtractor.Mode.NONBLOCKING : ModuleFilteredExtractor.Mode.BLOCKING);
            }
        });
    }

    static TilePipe clientPipe(Inventory playerInventory, BlockPos pos) {
        if (playerInventory.player.level().getBlockEntity(pos) instanceof TilePipe pipe)
            return pipe;
        // The pipe went away before the menu arrived; the menu closes on the next stillValid check.
        return new TilePipe(pos, TransportRegistry.getPipe(PipeType.ITEM, PipeMaterial.WOOD).defaultBlockState());
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

    @Override
    public IGuiReturnHandler getGuiReturnHandler() {
        return guiReturn;
    }

    /** The mode button's return: the pipe's position and level, the module's mode. */
    private final class GuiReturn implements IGuiReturnHandler {
        @Override
        public @Nullable Level getLevel() {
            return pipe.getLevel();
        }

        @Override
        public BlockPos getBlockPos() {
            return pipe.getBlockPos();
        }

        @Override
        public boolean isRemoved() {
            return pipe.isRemoved() || pipe.getModule() != module;
        }

        @Override
        public void writeGuiData(RegistryFriendlyByteBuf data) {
            data.writeByte(module.getMode().ordinal());
        }

        @Override
        public void readGuiData(RegistryFriendlyByteBuf data, @Nullable Player player) throws InvalidPacketException {
            int mode = PacketData.readByte(data, 0, ModuleFilteredExtractor.Mode.values().length - 1);
            module.setMode(ModuleFilteredExtractor.Mode.values()[mode]);
            pipe.setChanged();
        }
    }
}
