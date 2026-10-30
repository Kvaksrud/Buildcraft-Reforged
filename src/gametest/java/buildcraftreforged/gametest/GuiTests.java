package buildcraftreforged.gametest;

import buildcraftreforged.core.gui.BuildCraftContainer;
import buildcraftreforged.core.gui.slots.SlotPhantom;
import buildcraftreforged.core.gui.slots.SlotUntouchable;
import buildcraftreforged.core.network.InvalidPacketException;
import buildcraftreforged.core.network.PacketData;
import buildcraftreforged.core.network.PacketHandler;
import buildcraftreforged.gametest.gui.ContainerSampleGui;
import buildcraftreforged.gametest.gui.SampleGui;
import buildcraftreforged.gametest.gui.TileSampleGui;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** #25: the GUI base's menu logic, and the sample block's menu end to end on a server. */
final class GuiTests {
    private static final BlockPos POS = new BlockPos(1, 1, 1);

    private static final int PHANTOM = 0;
    private static final int UNTOUCHABLE = 1;
    private static final int MACHINE = 2;
    private static final int INVENTORY = 3;
    private static final int HOTBAR = INVENTORY + 27;

    private GuiTests() {
    }

    /**
     * 4.2.2's phantom-slot clicks and shift-click rules on a menu laid out as its GUIs are: a phantom slot, an
     * untouchable slot and a machine slot, then the 27 inventory slots and the 9 hotbar slots.
     */
    static void menuSlots(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        // 4.2.2 inventories hold 64 per slot; vanilla's SimpleContainer now allows 99.
        SimpleContainer machine = new SimpleContainer(3) {
            @Override
            public int getMaxStackSize() {
                return 64;
            }
        };
        SimpleContainer inv = new SimpleContainer(36);
        TestMenu menu = new TestMenu(machine, inv);

        // Held stack, left click: the phantom slot copies the whole stack and the player keeps it.
        menu.setCarried(new ItemStack(Items.DIAMOND, 10));
        menu.clicked(PHANTOM, 0, ContainerInput.PICKUP, player);
        count(helper, machine, 0, 10, "left click with held stack");
        helper.assertValueEqual(menu.getCarried().getCount(), 10, "held stack after phantom click");

        // Held stack of the same item, right click: one more.
        menu.clicked(PHANTOM, 1, ContainerInput.PICKUP, player);
        count(helper, machine, 0, 11, "right click with the same item");

        // Empty hand: left -1, right +1, shift left halves, shift right doubles, capped at 64.
        menu.setCarried(ItemStack.EMPTY);
        menu.clicked(PHANTOM, 0, ContainerInput.PICKUP, player);
        count(helper, machine, 0, 10, "empty-hand left click");
        menu.clicked(PHANTOM, 1, ContainerInput.PICKUP, player);
        count(helper, machine, 0, 11, "empty-hand right click");
        menu.clicked(PHANTOM, 0, ContainerInput.QUICK_MOVE, player);
        count(helper, machine, 0, 6, "shift left click");
        menu.clicked(PHANTOM, 1, ContainerInput.QUICK_MOVE, player);
        count(helper, machine, 0, 12, "shift right click");
        machine.setItem(0, new ItemStack(Items.DIAMOND, 40));
        menu.clicked(PHANTOM, 1, ContainerInput.QUICK_MOVE, player);
        count(helper, machine, 0, 64, "doubling past the stack limit");
        helper.assertTrue(menu.getCarried().isEmpty(), "phantom slot handed out items");

        // A different held item replaces the phantom item; one right click puts one.
        menu.setCarried(new ItemStack(Items.STICK, 7));
        menu.clicked(PHANTOM, 1, ContainerInput.PICKUP, player);
        helper.assertTrue(machine.getItem(0).is(Items.STICK), "different item did not replace the phantom item");
        count(helper, machine, 0, 1, "right click with a different item");

        // Middle click clears it; a count of zero empties it.
        menu.clicked(PHANTOM, 2, ContainerInput.CLONE, player);
        helper.assertTrue(machine.getItem(0).isEmpty(), "middle click did not clear the phantom slot");
        machine.setItem(0, new ItemStack(Items.DIAMOND, 1));
        menu.setCarried(ItemStack.EMPTY);
        menu.clicked(PHANTOM, 0, ContainerInput.PICKUP, player);
        helper.assertTrue(machine.getItem(0).isEmpty(), "count 0 left an item");

        // Untouchable: no clicks change it.
        machine.setItem(1, new ItemStack(Items.DIAMOND, 3));
        menu.setCarried(new ItemStack(Items.STICK, 7));
        menu.clicked(UNTOUCHABLE, 0, ContainerInput.PICKUP, player);
        menu.clicked(UNTOUCHABLE, 2, ContainerInput.CLONE, player);
        helper.assertTrue(machine.getItem(1).is(Items.DIAMOND), "untouchable slot changed");
        count(helper, machine, 1, 3, "untouchable slot");
        menu.setCarried(ItemStack.EMPTY);

        // Shift-click from the inventory goes to the machine slot, never the phantom slot.
        inv.setItem(9, new ItemStack(Items.COAL, 20));
        menu.quickMoveStack(player, INVENTORY);
        count(helper, machine, 2, 20, "shift-click into the machine");
        helper.assertTrue(inv.getItem(9).isEmpty(), "shift-click left items behind");
        helper.assertTrue(machine.getItem(0).isEmpty(), "shift-click filled the phantom slot");

        // Shift-click from the machine merges into the inventory first.
        inv.setItem(14, new ItemStack(Items.COAL, 50));
        menu.quickMoveStack(player, MACHINE);
        count(helper, inv, 14, 64, "merge into an existing stack");
        count(helper, inv, 9, 6, "rest into the first empty inventory slot");

        // With the machine full, the inventory and the hotbar trade places.
        machine.setItem(2, new ItemStack(Items.STONE, 64));
        inv.setItem(9, new ItemStack(Items.COAL, 20));
        inv.setItem(14, ItemStack.EMPTY);
        menu.quickMoveStack(player, INVENTORY);
        count(helper, inv, 0, 20, "inventory to hotbar");
        menu.quickMoveStack(player, HOTBAR);
        count(helper, inv, 9, 20, "hotbar to inventory");
        helper.succeed();
    }

    /**
     * The sample block's menu: its counter reaches the client through the widget, and its step comes back
     * through a GUI return that passes the server's checks.
     */
    static void sampleMenu(GameTestHelper helper) {
        helper.setBlock(POS, SampleGui.block);
        BlockPos pos = helper.absolutePos(POS);
        TileSampleGui tile = helper.getBlockEntity(POS, TileSampleGui.class);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.snapTo(Vec3.atBottomCenterOf(pos.above()));

        // A mock player has no mod channel, so NeoForge refuses the open-screen packet that player.openMenu(tile, pos)
        // sends from the block. Open the tile's menu on the server side only, as openMenu does before that packet.
        if (!(tile.createMenu(1, player.getInventory(), player) instanceof ContainerSampleGui menu)) {
            throw helper.assertionException("the sample tile made no sample menu");
        }
        player.containerMenu = menu;

        helper.runAfterDelay(45, () -> {
            helper.assertTrue(tile.counter >= 2, "counter did not run: " + tile.counter);

            // Server to client: the widget's bytes, read by the same widget in a client-side copy of the menu.
            RegistryAccess registryAccess = helper.getLevel().registryAccess();
            ContainerSampleGui client = new ContainerSampleGui(menu.containerId, player.getInventory(), tile);
            int index = menu.getWidgets().indexOf(menu.counterWidget);
            client.handleWidgetClientData(index,
                    PacketData.wrap(menu.counterWidget.getData(registryAccess), registryAccess));
            helper.assertValueEqual(client.counterWidget.value, tile.counter, "counter on the client");

            // Client to server: a GUI return for this menu passes the checks, and the step is applied.
            String reason = PacketHandler.checkMenuAccess(PacketHandler.Sender.of(player), menu.containerId, pos);
            helper.assertTrue(reason == null, "GUI return refused: " + reason);
            try {
                tile.readGuiData(PacketData.wrap(PacketData.write(registryAccess, buf -> buf.writeVarInt(5)),
                        registryAccess), player);
            } catch (InvalidPacketException e) {
                throw helper.assertionException("valid step refused: " + e.getMessage());
            }
            helper.assertValueEqual(tile.step, 5, "step");
            boolean refused = false;
            try {
                tile.readGuiData(PacketData.wrap(PacketData.write(registryAccess,
                        buf -> buf.writeVarInt(TileSampleGui.MAX_STEP + 1)), registryAccess), player);
            } catch (InvalidPacketException e) {
                refused = true;
            }
            helper.assertTrue(refused && tile.step == 5, "out-of-range step accepted");

            int before = tile.counter;
            helper.runAfterDelay(21, () -> {
                helper.assertTrue(tile.counter >= before + 5, "step not applied: " + before + " -> " + tile.counter);
                player.containerMenu = player.inventoryMenu;
                helper.succeed();
            });
        });
    }

    private static void count(GameTestHelper helper, SimpleContainer container, int slot, int expected, String what) {
        helper.assertValueEqual(container.getItem(slot).getCount(), expected, what);
    }

    private static final class TestMenu extends BuildCraftContainer {
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
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }
}
