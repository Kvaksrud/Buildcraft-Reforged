package buildcraftreforged.core.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import buildcraftreforged.core.gui.slots.SlotPhantom;
import buildcraftreforged.core.gui.slots.SlotUntouchable;
import buildcraftreforged.core.gui.widgets.Widget;
import buildcraftreforged.core.network.PacketData;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * #25: the parts of the GUI base that need no item stacks (stacks need a loaded world: the phantom-slot and shift-click
 * checks are the {@code gui_menu_slots} GameTest).
 */
class BuildCraftContainerTest {
    private static final int PHANTOM = 0;
    private static final int MACHINE = 2;

    private TestMenu menu;

    @BeforeEach
    void setUp() {
        menu = new TestMenu(new SimpleContainer(3), new SimpleContainer(36));
    }

    @Test
    void phantomSlotsTakeNoDrag() {
        assertFalse(menu.canDragTo(menu.getSlot(PHANTOM)));
        assertTrue(menu.canDragTo(menu.getSlot(MACHINE)));
    }

    @Test
    void widgetDataReachesTheWidgetOnTheOtherSide() {
        TestMenu client = new TestMenu(new SimpleContainer(3), new SimpleContainer(36));
        byte[] data = PacketData.write(RegistryAccess.EMPTY, buf -> buf.writeVarInt(1234));
        RegistryFriendlyByteBuf buf = PacketData.wrap(data, RegistryAccess.EMPTY);
        client.handleWidgetClientData(0, buf);
        assertEquals(1234, client.widget.value);
        // An unknown widget index is logged and ignored, not thrown.
        client.handleWidgetClientData(5, PacketData.wrap(data, RegistryAccess.EMPTY));
    }

    @Test
    void legacyMouseButtons() {
        assertEquals(0, GuiBuildCraft.toLegacyButton(InputConstants.MOUSE_BUTTON_LEFT));
        assertEquals(1, GuiBuildCraft.toLegacyButton(InputConstants.MOUSE_BUTTON_RIGHT));
        assertEquals(2, GuiBuildCraft.toLegacyButton(InputConstants.MOUSE_BUTTON_MIDDLE));
    }

    private static final class TestMenu extends BuildCraftContainer {
        final ValueWidget widget = new ValueWidget();

        TestMenu(SimpleContainer machine, SimpleContainer player) {
            super(null, 1, machine.getContainerSize());
            addSlot(new SlotPhantom(machine, 0, 0, 0));
            addSlot(new SlotUntouchable(machine, 1, 0, 0));
            addSlot(new Slot(machine, 2, 0, 0));
            for (int i = 0; i < 27; i++) {
                addSlot(new Slot(player, i + 9, 0, 0));
            }
            for (int i = 0; i < 9; i++) {
                addSlot(new Slot(player, i, 0, 0));
            }
            addWidget(widget);
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }

    private static final class ValueWidget extends Widget {
        int value;

        ValueWidget() {
            super(0, 0, 0, 0, 1, 1);
        }

        @Override
        public void handleClientPacketData(RegistryFriendlyByteBuf data) {
            value = data.readVarInt();
        }
    }
}
