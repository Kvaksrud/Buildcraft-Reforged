package buildcraftreforged.gametest;

import buildcraftreforged.core.network.PacketData;
import buildcraftreforged.core.network.PacketHandler;
import buildcraftreforged.core.status.ContainerStatus;
import buildcraftreforged.core.status.MachineStatus;
import buildcraftreforged.core.status.PacketStatusWatch;
import buildcraftreforged.core.status.StatusPanel;
import buildcraftreforged.core.status.StatusReason;
import buildcraftreforged.core.status.StatusReport;
import buildcraftreforged.core.status.StatusWidget;
import buildcraftreforged.gametest.gui.ContainerSampleGui;
import buildcraftreforged.gametest.gui.SampleGui;
import buildcraftreforged.gametest.gui.TileSampleGui;
import buildcraftreforged.gametest.status.SampleStatus;
import buildcraftreforged.gametest.status.TileSampleStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** #26: the status panel's six lines, sync only while open, the panel of a block without a GUI and the red light. */
final class StatusTests {
    private static final BlockPos POS = new BlockPos(1, 1, 1);

    private StatusTests() {
    }

    private static ServerPlayer playerNextTo(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.snapTo(Vec3.atBottomCenterOf(helper.absolutePos(POS).above()));
        return player;
    }

    /**
     * The sample block shows all six lines, and a report goes to the player only while the ledger is open: nothing
     * before the watch packet, the first report at once, nothing again within the interval or when unchanged, and
     * nothing after the ledger closes.
     */
    static void syncWhileOpen(GameTestHelper helper) {
        helper.setBlock(POS, SampleGui.block);
        TileSampleGui tile = helper.getBlockEntity(POS, TileSampleGui.class);
        ServerPlayer player = playerNextTo(helper);
        // A mock player has no mod channel: open the menu on the server side only (see GuiTests.sampleMenu).
        if (!(tile.createMenu(1, player.getInventory(), player) instanceof ContainerSampleGui menu)) {
            throw helper.assertionException("the sample tile made no sample menu");
        }
        player.containerMenu = menu;
        StatusWidget widget = menu.statusWidget;
        PacketHandler.Sender sender = PacketHandler.Sender.of(player);
        RegistryAccess registryAccess = helper.getLevel().registryAccess();

        helper.runAfterDelay(25, () -> {
            helper.assertTrue(widget.pollData(player) == null, "report sent before the ledger opened");

            // Refused: another container id, and a menu without a status panel.
            helper.assertFalse(PacketStatusWatch.process(new PacketStatusWatch(menu.containerId + 1, true), sender),
                    "watch for another container accepted");
            player.containerMenu = player.inventoryMenu;
            helper.assertFalse(PacketStatusWatch.process(new PacketStatusWatch(0, true), sender),
                    "watch for the player inventory accepted");
            player.containerMenu = menu;
            helper.assertFalse(widget.isWatching(player), "refused watch packet started the panel");

            // The ledger opens: the first report goes out at once and carries all six lines.
            helper.assertTrue(PacketStatusWatch.process(new PacketStatusWatch(menu.containerId, true), sender),
                    "watch packet refused");
            tile.counter = 5; // the sample is blocked for counters 5 to 9, which fills the Reason line
            byte[] first = widget.pollData(player);
            helper.assertTrue(first != null, "no report after the ledger opened");
            ContainerSampleGui client = new ContainerSampleGui(menu.containerId, player.getInventory(), tile);
            client.handleWidgetClientData(menu.getWidgets().indexOf(widget), PacketData.wrap(first, registryAccess));
            StatusReport report = client.statusWidget.report;
            helper.assertTrue(report != null, "client read no report");
            helper.assertValueEqual(report.state(), MachineStatus.BLOCKED, "State line");
            helper.assertValueEqual(report.reason(), StatusReason.OUTPUT_FULL, "Reason line");
            // One item, 25 mB and 2 FE a tick over the last second (19 or 20 ticks, by where this tick stands).
            helper.assertTrue(report.flow() != null && report.flow().itemsIn() >= 19 && report.flow().itemsIn() <= 20
                    && report.flow().fluidIn() >= 475 && report.flow().fluidIn() <= 500, "Flow line: " + report.flow());
            helper.assertTrue(report.power() != null && report.power().rate() >= 1.9F && report.power().rate() <= 2F
                    && report.power().capacity() == TileSampleGui.CAPACITY, "Power line: " + report.power());
            helper.assertTrue(report.heat() != null && report.heat().hasCountdown(), "Heat line: " + report.heat());
            helper.assertTrue(report.owner() != null && !report.owner().isEmpty(), "Owner line");

            // Within the interval: nothing, even though the report changed.
            tile.counter = 0;
            helper.assertTrue(widget.pollData(player) == null, "report sent again within the interval");

            helper.runAfterDelay(StatusWidget.SEND_INTERVAL, () -> {
                byte[] second = widget.pollData(player);
                helper.assertTrue(second != null, "changed report not sent after the interval");
                client.handleWidgetClientData(menu.getWidgets().indexOf(widget), PacketData.wrap(second, registryAccess));
                helper.assertValueEqual(client.statusWidget.report.state(), MachineStatus.WORKING, "State after change");

                // The ledger closes: no more data.
                helper.assertTrue(PacketStatusWatch.process(new PacketStatusWatch(menu.containerId, false), sender),
                        "close packet refused");
                tile.counter = 5;
                helper.runAfterDelay(StatusWidget.SEND_INTERVAL, () -> {
                    helper.assertTrue(widget.pollData(player) == null, "report sent after the ledger closed");
                    player.containerMenu = player.inventoryMenu;
                    helper.succeed();
                });
            });
        });
    }

    /**
     * A block without a GUI: its status menu is watched from the moment it opens, belongs to the block, and closes
     * with it. The red light follows the State line.
     */
    static void panelWithoutGui(GameTestHelper helper) {
        helper.setBlock(POS, SampleStatus.block);
        TileSampleStatus tile = helper.getBlockEntity(POS, TileSampleStatus.class);
        ServerPlayer player = playerNextTo(helper);

        // StatusPanel.open sends the open-screen packet a mock player cannot take; open the same menu by hand.
        ContainerStatus menu = new ContainerStatus(1, tile);
        player.containerMenu = menu;
        menu.addCraftingToCrafters(player);
        helper.assertTrue(menu.statusWidget.isWatching(player), "status-only panel not watched on open");
        helper.assertTrue(menu.stillValid(player), "status menu not valid next to its block");
        byte[] data = menu.statusWidget.pollData(player);
        helper.assertTrue(data != null, "status-only panel sent no report");

        tile.forced = MachineStatus.BLOCKED;
        helper.runAfterDelay(2, () -> {
            helper.assertBlockProperty(POS, StatusPanel.BLOCKED, true);
            tile.forced = MachineStatus.WORKING;
            helper.runAfterDelay(2, () -> {
                helper.assertBlockProperty(POS, StatusPanel.BLOCKED, false);
                // The block entity survives the property change.
                helper.assertTrue(helper.getBlockEntity(POS, TileSampleStatus.class) == tile, "block entity replaced");

                menu.removeCraftingFromCrafters(player);
                helper.destroyBlock(POS);
                helper.assertFalse(menu.stillValid(player), "status menu valid after its block went");
                player.containerMenu = player.inventoryMenu;
                helper.succeed();
            });
        });
    }
}
