package buildcraftreforged.transport.module;

import buildcraftreforged.core.gui.BuildCraftContainer;
import buildcraftreforged.core.gui.GuiBuildCraft;
import buildcraftreforged.core.gui.slots.SlotPhantom;
import buildcraftreforged.core.gui.tooltips.ToolTip;
import buildcraftreforged.core.gui.tooltips.ToolTipLine;
import buildcraftreforged.core.gui.widgets.ButtonWidget;
import buildcraftreforged.core.network.IGuiReturnHandler;
import buildcraftreforged.core.network.InvalidPacketException;
import buildcraftreforged.core.network.PacketData;
import buildcraftreforged.core.network.PacketGuiReturn;
import buildcraftreforged.core.utils.EnumColor;
import buildcraftreforged.transport.TilePipe;
import buildcraftreforged.transport.TransportRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The Filtered Colour Tag's menu (4.2.2 {@code ContainerEmzuliPipe}): four phantom filter slots, each with a paint
 * button, over the player inventory. The paint colours reach the client as data slots (4.2.2 progress bars); a click
 * on a paint button sends the slot and its new colour back with {@code PacketGuiReturn}.
 */
public class ContainerFilteredColourTag extends BuildCraftContainer {

    private static final int[][] FILTER_SLOTS = {{25, 21}, {134, 21}, {25, 49}, {134, 49}};
    private static final int[][] PAINT_BUTTONS = {{51, 19}, {104, 19}, {51, 47}, {104, 47}};

    public final TilePipe pipe;
    public final ModuleFilteredColourTag module;
    public final PaintWidget[] paintWidgets = new PaintWidget[ModuleFilteredColourTag.FILTERS];
    private final GuiReturn guiReturn = new GuiReturn();

    /** Client side: the pipe position comes with the open-menu packet; the module is a local copy. */
    public ContainerFilteredColourTag(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory,
                ContainerFilteredExtractor.clientPipe(playerInventory, extraData.readBlockPos()),
                new ModuleFilteredColourTag());
    }

    public ContainerFilteredColourTag(int containerId, Inventory playerInventory, TilePipe pipe,
            ModuleFilteredColourTag module) {
        super(TransportRegistry.filteredColourTagMenu.get(), containerId, module.filters.getContainerSize());
        this.pipe = pipe;
        this.module = module;

        for (int slot = 0; slot < ModuleFilteredColourTag.FILTERS; slot++) {
            addWidget(paintWidgets[slot] = new PaintWidget(slot, PAINT_BUTTONS[slot][0], PAINT_BUTTONS[slot][1]));
        }
        for (int slot = 0; slot < ModuleFilteredColourTag.FILTERS; slot++) {
            addSlot(new SlotPhantom(module.filters, slot, FILTER_SLOTS[slot][0], FILTER_SLOTS[slot][1]));
        }

        for (int l = 0; l < 3; l++) {
            for (int k1 = 0; k1 < 9; k1++) {
                addSlot(new Slot(playerInventory, k1 + l * 9 + 9, 8 + k1 * 18, 84 + l * 18));
            }
        }

        for (int i1 = 0; i1 < 9; i1++) {
            addSlot(new Slot(playerInventory, i1, 8 + i1 * 18, 142));
        }

        for (int slot = 0; slot < ModuleFilteredColourTag.FILTERS; slot++) {
            int index = slot;
            addDataSlot(new DataSlot() {
                @Override
                public int get() {
                    return module.slotColors[index];
                }

                @Override
                public void set(int value) {
                    module.slotColors[index] = (byte) value;
                }
            });
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

    @Override
    public IGuiReturnHandler getGuiReturnHandler() {
        return guiReturn;
    }

    /** The paint colour shown on filter {@code slot}, null for none. */
    public @Nullable EnumColor paint(int slot) {
        int colour = module.slotColors[slot];
        return colour == 0 ? null : EnumColor.fromId(colour - 1);
    }

    /**
     * A paint button (4.2.2 {@code PaintWidget}): left click the next colour, right click the previous one, middle click
     * none, in the order none, black … white. The screen draws the colour icon over it.
     */
    public final class PaintWidget extends ButtonWidget {

        private final int slot;

        PaintWidget(int slot, int x, int y) {
            super(x, y, 176, 0, 20, 20);
            this.slot = slot;
        }

        @Override
        public void draw(GuiBuildCraft<?> gui, int guiX, int guiY, int mouseX, int mouseY) {
            super.draw(gui, guiX, guiY, mouseX, mouseY);
            if (paint(slot) == null)
                gui.drawTexturedModalRect(guiX + x + 2, guiY + y + 2, u, v + h + h, 16, 16);
        }

        /** Client side. */
        @Override
        public void onRelease(int mouseButton) {
            int choices = EnumColor.VALUES.length + 1;
            int colour = module.slotColors[slot];
            colour = switch (mouseButton) {
            case 0 -> (colour + 1) % choices;
            case 1 -> (colour + choices - 1) % choices;
            default -> 0;
            };
            module.slotColors[slot] = (byte) colour;
            guiReturn.pending = slot;
            new PacketGuiReturn(containerId, guiReturn).sendPacket();
        }

        @Override
        public ToolTip getToolTip() {
            ToolTip tip = new ToolTip(500);
            EnumColor colour = paint(slot);
            tip.add(new ToolTipLine(colour != null
                    ? Component.translatable("gui.pipes.emzuli.paint", colour.getLocalizedName()).getString()
                    : Component.translatable("gui.pipes.emzuli.nopaint").getString()));
            return tip;
        }
    }

    /** A paint button's return: the pipe's position and level, then the filter slot and its colour (0 = none). */
    private final class GuiReturn implements IGuiReturnHandler {
        int pending;

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
            data.writeByte(pending);
            data.writeByte(module.slotColors[pending]);
        }

        @Override
        public void readGuiData(RegistryFriendlyByteBuf data, @Nullable Player player) throws InvalidPacketException {
            int slot = PacketData.readByte(data, 0, ModuleFilteredColourTag.FILTERS - 1);
            module.slotColors[slot] = (byte) PacketData.readByte(data, 0, EnumColor.VALUES.length);
            pipe.setChanged();
        }
    }
}
