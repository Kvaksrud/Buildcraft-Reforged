package buildcraftreforged.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.core.gui.BuildCraftContainer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Server-side checks on {@link PacketGuiReturn}: what passes, and that every malformed packet is logged and dropped. */
class GuiReturnValidationTest {
    private static final int CONTAINER_ID = 5;
    private static final BlockPos POS = new BlockPos(10, 70, -4);

    private TestHandler handler;
    private TestMenu menu;
    private TestSender sender;
    private LogCapture log;

    @BeforeEach
    void setUp() {
        handler = new TestHandler();
        menu = new TestMenu(CONTAINER_ID, handler);
        sender = new TestSender(menu);
        log = LogCapture.begin();
    }

    @AfterEach
    void tearDown() {
        log.end();
    }

    private static PacketGuiReturn packet(int containerId, BlockPos pos, Consumer<RegistryFriendlyByteBuf> writer) {
        return new PacketGuiReturn(containerId, pos, PacketData.write(RegistryAccess.EMPTY, writer));
    }

    private static PacketGuiReturn validPacket() {
        return packet(CONTAINER_ID, POS, data -> {
            data.writeVarInt(2);
            data.writeUtf("box");
        });
    }

    private void assertDropped(PacketGuiReturn packet, String reasonPart) {
        assertFalse(PacketHandler.processGuiReturn(packet, sender));
        assertEquals(-1, handler.mode, "handler state changed by a dropped packet");
        assertEquals(1, log.messages.size(), "expected one warning, got " + log.messages);
        assertTrue(log.messages.getFirst().contains("Dropped gui_return packet from tester"), log.messages.getFirst());
        assertTrue(log.messages.getFirst().contains(reasonPart), log.messages.getFirst());
    }

    @Test
    void validPacketIsApplied() {
        assertTrue(PacketHandler.processGuiReturn(validPacket(), sender));
        assertEquals(2, handler.mode);
        assertEquals("box", handler.pattern);
        assertTrue(log.messages.isEmpty(), log.messages.toString());
    }

    @Test
    void packetFromRealHandlerRoundTrips() {
        TestHandler client = new TestHandler();
        client.mode = 3;
        client.pattern = "clear";
        PacketGuiReturn sent = packet(CONTAINER_ID, POS, client::writeGuiData);
        assertTrue(PacketHandler.processGuiReturn(sent, sender));
        assertEquals(3, handler.mode);
        assertEquals("clear", handler.pattern);
    }

    @Test
    void wrongContainerIdIsDropped() {
        assertDropped(packet(CONTAINER_ID + 1, POS, data -> data.writeVarInt(2)), "is not the open menu");
    }

    @Test
    void playerInventoryContainerIsDropped() {
        assertDropped(packet(0, POS, data -> data.writeVarInt(2)), "is not the open menu");
    }

    @Test
    void noOpenMenuIsDropped() {
        sender.openMenu = null;
        assertDropped(validPacket(), "is not the open menu");
    }

    @Test
    void menuWithoutReturnHandlerIsDropped() {
        menu.handler = null;
        assertDropped(validPacket(), "takes no GUI data");
    }

    @Test
    void otherBlockPositionIsDropped() {
        assertDropped(packet(CONTAINER_ID, POS.above(), data -> data.writeVarInt(2)), "open menu belongs to");
    }

    @Test
    void removedBlockIsDropped() {
        handler.removed = true;
        assertDropped(validPacket(), "is gone");
    }

    @Test
    void invalidMenuIsDropped() {
        menu.valid = false;
        assertDropped(validPacket(), "no longer valid");
    }

    @Test
    void outOfReachIsDropped() {
        sender.inReach = false;
        assertDropped(validPacket(), "out of reach");
    }

    @Test
    void valueOutOfRangeIsDropped() {
        assertDropped(packet(CONTAINER_ID, POS, data -> {
            data.writeVarInt(TestHandler.MODES);
            data.writeUtf("box");
        }), "outside [0, 3]");
    }

    @Test
    void negativeValueIsDropped() {
        assertDropped(packet(CONTAINER_ID, POS, data -> {
            data.writeVarInt(-1);
            data.writeUtf("box");
        }), "outside [0, 3]");
    }

    @Test
    void tooLongStringIsDropped() {
        assertDropped(packet(CONTAINER_ID, POS, data -> {
            data.writeVarInt(1);
            data.writeUtf("x".repeat(TestHandler.MAX_PATTERN_LENGTH + 1));
        }), "malformed string");
    }

    @Test
    void truncatedDataIsDropped() {
        assertDropped(packet(CONTAINER_ID, POS, data -> {}), "malformed");
    }

    @Test
    void garbageDataIsDropped() {
        assertDropped(packet(CONTAINER_ID, POS, data -> data.writeBytes(new byte[] {-1, -1, -1, -1, -1, -1})),
                "malformed");
    }

    /** A filler-like block entity: a mode in [0, 3] and a short pattern name. */
    static final class TestHandler implements IGuiReturnHandler {
        static final int MODES = 4;
        static final int MAX_PATTERN_LENGTH = 32;

        int mode = -1;
        String pattern = "";
        boolean removed;

        @Override
        public @Nullable Level getLevel() {
            return null;
        }

        @Override
        public BlockPos getBlockPos() {
            return POS;
        }

        @Override
        public boolean isRemoved() {
            return removed;
        }

        @Override
        public void writeGuiData(RegistryFriendlyByteBuf data) {
            data.writeVarInt(mode);
            data.writeUtf(pattern);
        }

        @Override
        public void readGuiData(RegistryFriendlyByteBuf data, @Nullable Player player) throws InvalidPacketException {
            int newMode = PacketData.readVarInt(data, 0, MODES - 1);
            String newPattern = PacketData.readUtf(data, MAX_PATTERN_LENGTH);
            mode = newMode;
            pattern = newPattern;
        }
    }

    static final class TestMenu extends BuildCraftContainer {
        @Nullable IGuiReturnHandler handler;
        boolean valid = true;

        TestMenu(int containerId, IGuiReturnHandler handler) {
            super(null, containerId, 0);
            this.handler = handler;
        }

        @Override
        public @Nullable IGuiReturnHandler getGuiReturnHandler() {
            return handler;
        }

        @Override
        public ItemStack quickMoveStack(Player player, int slotIndex) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return valid;
        }
    }

    static final class TestSender implements PacketHandler.Sender {
        @Nullable AbstractContainerMenu openMenu;
        boolean inReach = true;

        TestSender(AbstractContainerMenu openMenu) {
            this.openMenu = openMenu;
        }

        @Override
        public String name() {
            return "tester";
        }

        @Override
        public @Nullable Player player() {
            return null;
        }

        @Override
        public @Nullable AbstractContainerMenu openMenu() {
            return openMenu;
        }

        @Override
        public @Nullable Level level() {
            return null;
        }

        @Override
        public RegistryAccess registryAccess() {
            return RegistryAccess.EMPTY;
        }

        @Override
        public boolean menuStillValid(AbstractContainerMenu menu) {
            return menu.stillValid(null);
        }

        @Override
        public boolean canReach(BlockPos pos) {
            return inReach;
        }
    }

    /** Collects what the mod logger writes at WARN while a test runs. */
    static final class LogCapture extends AbstractAppender {
        final List<String> messages = new ArrayList<>();
        private final Logger logger;

        private LogCapture(Logger logger) {
            super("GuiReturnValidationTest", null, null, true, Property.EMPTY_ARRAY);
            this.logger = logger;
        }

        static LogCapture begin() {
            Logger logger = (Logger) LogManager.getLogger(BuildcraftReforged.LOGGER.getName());
            LogCapture capture = new LogCapture(logger);
            capture.start();
            logger.addAppender(capture);
            return capture;
        }

        void end() {
            logger.removeAppender(this);
            stop();
        }

        @Override
        public void append(LogEvent event) {
            if (event.getLoggerName().equals(logger.getName())) {
                messages.add(event.getMessage().getFormattedMessage());
            }
        }
    }
}
