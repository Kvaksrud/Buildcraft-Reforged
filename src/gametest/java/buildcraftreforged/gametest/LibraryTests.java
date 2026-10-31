package buildcraftreforged.gametest;

import buildcraftreforged.builders.BptItemData;
import buildcraftreforged.builders.BuildCraftBuilders;
import buildcraftreforged.builders.BuildersRegistry;
import buildcraftreforged.builders.ItemBptBase;
import buildcraftreforged.builders.TileBlueprintLibrary;
import buildcraftreforged.builders.gui.ContainerBlueprintLibrary;
import buildcraftreforged.core.blueprints.BptBase;
import buildcraftreforged.core.blueprints.BptBlueprint;
import buildcraftreforged.core.blueprints.BptPlayerIndex;
import buildcraftreforged.core.blueprints.BptRootIndex;
import buildcraftreforged.core.blueprints.BptTemplate;
import buildcraftreforged.core.network.InvalidPacketException;
import buildcraftreforged.core.network.PacketData;
import buildcraftreforged.core.network.PacketHandler;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * #80 blueprint library: the owner's list in the server's library, storing an item's blueprint into it, copying a
 * selected entry onto a blank item, deleting, paging, and refusing other players' and malformed requests. Other tests
 * share the server's library, so entries are looked up by name, never by position.
 */
final class LibraryTests {
    static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();
    static final int MAX_TICKS = 400;

    private static final BlockPos LIBRARY = new BlockPos(1, 2, 1);

    static {
        TESTS.put("library_store_copy_delete", LibraryTests::storeCopyDelete);
        TESTS.put("library_paging", LibraryTests::paging);
        TESTS.put("library_refuses_forged_requests", LibraryTests::refusesForgedRequests);
    }

    private LibraryTests() {
    }

    private static TileBlueprintLibrary placeLibrary(GameTestHelper helper, @Nullable ServerPlayer owner) {
        helper.setBlock(LIBRARY, BuildersRegistry.libraryBlock.get());
        TileBlueprintLibrary tile = helper.getBlockEntity(LIBRARY, TileBlueprintLibrary.class);
        if (owner != null)
            tile.onBlockPlacedBy(owner, new ItemStack(BuildersRegistry.libraryBlock.get()));
        return tile;
    }

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.snapTo(Vec3.atBottomCenterOf(helper.absolutePos(LIBRARY).above()));
        return player;
    }

    private static BptRootIndex root(GameTestHelper helper) {
        BptRootIndex index = BuildCraftBuilders.getBptRootIndex(helper.getLevel().getServer());
        helper.assertTrue(index != null, "no blueprint library");
        return index;
    }

    private static BptPlayerIndex playerIndex(GameTestHelper helper, ServerPlayer player) {
        BptPlayerIndex index = BuildCraftBuilders.getPlayerIndex(helper.getLevel().getServer(), player.getUUID());
        helper.assertTrue(index != null, "no blueprint list for " + player.getGameProfile().name());
        return index;
    }

    /** A 1 x 1 x 1 blueprint (stone) or template, stored in the server's library under name. */
    private static BptBase store(GameTestHelper helper, String name, boolean template) {
        BptBase bpt;
        if (template) {
            BptTemplate t = new BptTemplate(1, 1, 1);
            t.setMask(0, 0, 0, 1);
            bpt = t;
        } else {
            bpt = new BptBlueprint(1, 1, 1);
            bpt.setBlockState(0, 0, 0, Blocks.STONE.defaultBlockState());
        }
        bpt.setName(name);
        bpt.author = "Library Test";
        root(helper).storeBluePrint(bpt);
        helper.assertTrue(bpt.file != null && Files.exists(bpt.file), "blueprint " + name + " not saved");
        return bpt;
    }

    private static String fileName(BptBase bpt) {
        return bpt.file.getFileName().toString();
    }

    private static boolean listed(BptPlayerIndex index, BptBase bpt) {
        for (String it = index.nextBpt(null); it != null; it = index.nextBpt(it)) {
            if (it.equals(fileName(bpt)))
                return true;
        }
        return false;
    }

    /** The GUI's command as the server reads it from a PacketGuiReturn. */
    private static void send(GameTestHelper helper, TileBlueprintLibrary tile, @Nullable ServerPlayer player,
            Consumer<FriendlyByteBuf> writer) throws InvalidPacketException {
        RegistryFriendlyByteBuf data = PacketData.wrap(PacketData.write(helper.getLevel().registryAccess(),
                writer::accept), helper.getLevel().registryAccess());
        try {
            tile.readGuiData(data, player);
        } finally {
            data.release();
        }
    }

    private static void command(GameTestHelper helper, TileBlueprintLibrary tile, ServerPlayer player, int command,
            int row) {
        try {
            send(helper, tile, player, buf -> {
                buf.writeVarInt(command);
                if (command == TileBlueprintLibrary.COMMAND_SELECT)
                    buf.writeVarInt(row);
            });
        } catch (InvalidPacketException e) {
            throw helper.assertionException(Component.literal("command " + command + " refused: " + e.getMessage()));
        }
    }

    private static void refused(GameTestHelper helper, TileBlueprintLibrary tile, @Nullable ServerPlayer player,
            String what, Consumer<FriendlyByteBuf> writer) {
        try {
            send(helper, tile, player, writer);
        } catch (InvalidPacketException e) {
            return;
        }
        throw helper.assertionException(Component.literal(what + " was accepted"));
    }

    /** Pages forward with the GUI's ">" until the entry is on the page, and selects its row. */
    private static void select(GameTestHelper helper, TileBlueprintLibrary tile, ServerPlayer player, BptBase bpt) {
        for (int page = 0; page < 100; page++) {
            List<BptBase> current = tile.getCurrentPage();
            for (int row = 0; row < current.size(); row++) {
                if (current.get(row).file != null && current.get(row).file.equals(bpt.file)) {
                    command(helper, tile, player, TileBlueprintLibrary.COMMAND_SELECT, row);
                    helper.assertValueEqual(tile.selected, row, "selected row");
                    helper.assertValueEqual(tile.currentNames[row], bpt.getName(), "name in the selected row");
                    return;
                }
            }
            command(helper, tile, player, TileBlueprintLibrary.COMMAND_NEXT, -1);
        }
        throw helper.assertionException(Component.literal(bpt.getName() + " is not in the library's list"));
    }

    private static ContainerBlueprintLibrary openMenu(GameTestHelper helper, TileBlueprintLibrary tile, ServerPlayer player) {
        if (!(tile.createMenu(1, player.getInventory(), player) instanceof ContainerBlueprintLibrary menu)) {
            throw helper.assertionException(Component.literal("the library made no library menu"));
        }
        player.containerMenu = menu;
        return menu;
    }

    /**
     * Storing: a used blueprint item in the top input moves to the top output after 4.2.2's 100 ticks and its entry
     * joins the owner's list. Copying: with the entry selected, a clean template in the bottom input comes out as a
     * blueprint of that entry (name, author); with nothing selected, as a clean item. Deleting: the entry leaves the
     * owner's list, the file stays in the library.
     */
    static void storeCopyDelete(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        TileBlueprintLibrary tile = placeLibrary(helper, owner);
        BptPlayerIndex index = playerIndex(helper, owner);
        // Stored after the owner's list was made, so it is not on it yet.
        BptBase bpt = store(helper, "00 Library Store", false);
        helper.assertFalse(listed(index, bpt), "a new entry is on an existing list");
        ItemStack used = BuildCraftBuilders.getBptItemStack(BuildersRegistry.blueprintItem.get(), bpt.position,
                bpt.getName(), bpt.author);

        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> {
                    ContainerBlueprintLibrary menu = openMenu(helper, tile, owner);
                    helper.assertValueEqual(menu.slots.size(), 4 + 36, "menu slots");
                    tile.setItem(0, used);
                    helper.assertValueEqual(tile.progressIn, 1, "store progress");
                })
                .thenIdle(90)
                .thenExecute(() -> helper.assertTrue(tile.getItem(1).isEmpty(), "stored before 100 ticks"))
                .thenWaitUntil(() -> helper.assertFalse(tile.getItem(1).isEmpty(), "not stored yet"))
                .thenExecute(() -> {
                    helper.assertTrue(tile.getItem(0).isEmpty(), "input left: " + tile.getItem(0));
                    helper.assertValueEqual(ItemBptBase.getBptId(tile.getItem(1)), bpt.position, "stored item's entry");
                    helper.assertTrue(listed(index, bpt), "the stored entry is not on the owner's list");

                    select(helper, tile, owner, bpt);
                    tile.setItem(2, new ItemStack(BuildersRegistry.templateItem.get()));
                })
                .thenWaitUntil(() -> helper.assertFalse(tile.getItem(3).isEmpty(), "not copied yet"))
                .thenExecute(() -> {
                    ItemStack copy = tile.getItem(3);
                    helper.assertTrue(tile.getItem(2).isEmpty(), "blank left: " + tile.getItem(2));
                    // 4.2.2 kept the blank's item, so a template held a blueprint; the copy is of the entry's kind.
                    helper.assertTrue(copy.is(BuildersRegistry.blueprintItem.get()), "copy is " + copy);
                    BptItemData data = ItemBptBase.getBptData(copy);
                    helper.assertValueEqual(data, new BptItemData(bpt.position, "00 Library Store", "Library Test"),
                            "copied item data");

                    // Nothing selected after a page change: the blank comes out clean.
                    tile.removeItem(3, 1);
                    command(helper, tile, owner, TileBlueprintLibrary.COMMAND_NEXT, -1);
                    command(helper, tile, owner, TileBlueprintLibrary.COMMAND_PREV, -1);
                    helper.assertValueEqual(tile.selected, -1, "selection after paging");
                    tile.setItem(2, used.copy());
                })
                .thenWaitUntil(() -> helper.assertFalse(tile.getItem(3).isEmpty(), "not cleared yet"))
                .thenExecute(() -> {
                    ItemStack clean = tile.getItem(3);
                    helper.assertTrue(clean.is(BuildersRegistry.blueprintItem.get()) && ItemBptBase.getBptData(clean) == null,
                            "cleared item " + clean.getComponents());

                    // An input taken out half-way stops the count (4.2.2 threw at 100).
                    tile.setItem(2, new ItemStack(BuildersRegistry.blueprintItem.get()));
                    tile.removeItem(3, 1);
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    tile.removeItem(2, 1);
                    helper.assertValueEqual(tile.progressOut, 0, "copy progress without an input");

                    select(helper, tile, owner, bpt);
                    command(helper, tile, owner, TileBlueprintLibrary.COMMAND_DELETE, -1);
                    helper.assertFalse(listed(index, bpt), "deleted entry still on the owner's list");
                    helper.assertFalse(Arrays.asList(tile.currentNames).contains("00 Library Store"),
                            "deleted entry still shown");
                    helper.assertTrue(Files.exists(bpt.file), "deleting from the list removed the file");
                    helper.assertTrue(root(helper).getBluePrint(bpt.position) != null, "entry gone from the library");
                    owner.containerMenu = owner.inventoryMenu;
                })
                .thenSucceed();
    }

    /**
     * ">" shows the entries after the page, "<" the ones before (4.2.2 moved one entry forward), past the end the page
     * is empty, and ">" from an empty page starts again at the first.
     */
    static void paging(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        TileBlueprintLibrary tile = placeLibrary(helper, owner);
        BptPlayerIndex index = playerIndex(helper, owner);
        try {
            for (int i = 0; i < TileBlueprintLibrary.LIBRARY_PAGE_SIZE + 2; i++) {
                index.addBlueprint(store(helper, "01 Library Page " + (char) ('a' + i), i % 2 == 1).file);
            }
        } catch (java.io.IOException e) {
            throw helper.assertionException(Component.literal("could not add to the list: " + e));
        }

        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> {
                    List<String> first = names(tile);
                    helper.assertValueEqual(first.size(), TileBlueprintLibrary.LIBRARY_PAGE_SIZE, "first page size");
                    helper.assertValueEqual(first, names(tile.getNextPage(null)), "first page");

                    command(helper, tile, owner, TileBlueprintLibrary.COMMAND_NEXT, -1);
                    List<String> second = names(tile);
                    helper.assertFalse(second.isEmpty(), "second page empty");
                    helper.assertFalse(first.contains(second.get(0)), "second page repeats the first");

                    command(helper, tile, owner, TileBlueprintLibrary.COMMAND_PREV, -1);
                    helper.assertValueEqual(names(tile), first, "page after < from the second");

                    for (int i = 0; i < 100 && !tile.getCurrentPage().isEmpty(); i++) {
                        command(helper, tile, owner, TileBlueprintLibrary.COMMAND_NEXT, -1);
                    }
                    helper.assertTrue(tile.getCurrentPage().isEmpty(), "never past the end");
                    command(helper, tile, owner, TileBlueprintLibrary.COMMAND_NEXT, -1);
                    helper.assertValueEqual(names(tile), first, "page after > from the end");
                })
                .thenSucceed();
    }

    private static List<String> names(TileBlueprintLibrary tile) {
        return names(tile.getCurrentPage());
    }

    private static List<String> names(List<BptBase> page) {
        List<String> result = new ArrayList<>();
        for (BptBase bpt : page)
            result.add(fileName(bpt));
        return result;
    }

    /**
     * A visitor may page, select and copy in an open library, but a forged delete or lock of the owner's list is
     * refused. Once the owner locks it the visitor's open menu is no longer valid and every request is refused. Rows
     * out of range, unknown commands and trailing bytes are refused too, and nothing changes.
     */
    static void refusesForgedRequests(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        ServerPlayer visitor = player(helper);
        TileBlueprintLibrary tile = placeLibrary(helper, owner);
        BptPlayerIndex index = playerIndex(helper, owner);
        BptBase bpt = store(helper, "02 Library Owned", true);
        BlockPos pos = helper.absolutePos(LIBRARY);
        try {
            index.addBlueprint(bpt.file);
        } catch (java.io.IOException e) {
            throw helper.assertionException(Component.literal("could not add to the list: " + e));
        }

        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> {
                    ContainerBlueprintLibrary menu = openMenu(helper, tile, visitor);
                    String reason = PacketHandler.checkMenuAccess(PacketHandler.Sender.of(visitor), menu.containerId, pos);
                    helper.assertTrue(reason == null, "visitor's GUI return refused: " + reason);
                    select(helper, tile, visitor, bpt);

                    refused(helper, tile, visitor, "visitor's delete",
                            buf -> buf.writeVarInt(TileBlueprintLibrary.COMMAND_DELETE));
                    helper.assertTrue(listed(index, bpt), "visitor deleted from the owner's list");
                    refused(helper, tile, visitor, "visitor's lock",
                            buf -> buf.writeVarInt(TileBlueprintLibrary.COMMAND_LOCK_UPDATE));
                    helper.assertFalse(tile.locked, "visitor locked the library");
                    refused(helper, tile, null, "request without a player",
                            buf -> buf.writeVarInt(TileBlueprintLibrary.COMMAND_NEXT));

                    int selected = tile.selected;
                    refused(helper, tile, owner, "row 12", buf -> {
                        buf.writeVarInt(TileBlueprintLibrary.COMMAND_SELECT);
                        buf.writeVarInt(TileBlueprintLibrary.LIBRARY_PAGE_SIZE);
                    });
                    refused(helper, tile, owner, "row -1", buf -> {
                        buf.writeVarInt(TileBlueprintLibrary.COMMAND_SELECT);
                        buf.writeVarInt(-1);
                    });
                    refused(helper, tile, owner, "select without a row",
                            buf -> buf.writeVarInt(TileBlueprintLibrary.COMMAND_SELECT));
                    refused(helper, tile, owner, "command 6", buf -> buf.writeVarInt(6));
                    refused(helper, tile, owner, "trailing bytes", buf -> {
                        buf.writeVarInt(TileBlueprintLibrary.COMMAND_DELETE);
                        buf.writeByte(0);
                    });
                    helper.assertValueEqual(tile.selected, selected, "selection after refused requests");
                    helper.assertTrue(listed(index, bpt), "a refused request deleted the entry");

                    // The owner locks it: the visitor's open GUI is no longer valid, and its requests are refused.
                    command(helper, tile, owner, TileBlueprintLibrary.COMMAND_LOCK_UPDATE, -1);
                    helper.assertTrue(tile.locked, "owner could not lock");
                    helper.assertFalse(tile.canOpen(visitor), "a locked library opens for a visitor");
                    helper.assertTrue(tile.canOpen(owner), "a locked library does not open for its owner");
                    reason = PacketHandler.checkMenuAccess(PacketHandler.Sender.of(visitor), menu.containerId, pos);
                    helper.assertTrue(reason != null, "visitor's GUI return accepted in a locked library");
                    refused(helper, tile, visitor, "visitor's select in a locked library", buf -> {
                        buf.writeVarInt(TileBlueprintLibrary.COMMAND_SELECT);
                        buf.writeVarInt(0);
                    });
                    visitor.containerMenu = visitor.inventoryMenu;

                    // The owner deletes their own entry.
                    select(helper, tile, owner, bpt);
                    command(helper, tile, owner, TileBlueprintLibrary.COMMAND_DELETE, -1);
                    helper.assertFalse(listed(index, bpt), "owner could not delete");
                })
                .thenSucceed();
    }
}
