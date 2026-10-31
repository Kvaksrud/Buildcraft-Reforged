/*
 * Ported from buildcraft/builders/TileBlueprintLibrary.java (BuildCraft 4.2.2, MMPL; the original has no header).
 * Modified by Buildcraft Reforged, 2026: IInventory becomes Container over the same four slots; the owner is the
 * placer's UUID (#27) and the list is that player's BptPlayerIndex in the server's library
 * (BuildCraftBuilders.getPlayerIndex, plan section 19.2); items carry the BptItemData component where 4.2.2 used the
 * damage; @TileNetworkData becomes writePacketPayload/readPacketPayload; NBT becomes ValueInput/ValueOutput with the
 * same tag names; the GUI is a MenuProvider and PacketHandlerBuilders' LIBRARY_ACTION and LIBRARY_SELECT become a
 * PacketGuiReturn read by readGuiData, which checks every value and the sender. Bug fixes (docs/BUGFIXES.md): the
 * previous page goes back (4.2.2 moved one entry forward), taking an item out mid-way resets the progress (4.2.2 threw
 * a NullPointerException at 100), a copy is an item of the entry's kind, and only the owner may lock or delete.
 */

package buildcraftreforged.builders;

import buildcraftreforged.builders.gui.ContainerBlueprintLibrary;
import buildcraftreforged.core.TileBuildCraft;
import buildcraftreforged.core.blueprints.BptBase;
import buildcraftreforged.core.blueprints.BptPlayerIndex;
import buildcraftreforged.core.blueprints.BptRootIndex;
import buildcraftreforged.core.blueprints.BptTemplate;
import buildcraftreforged.core.network.IGuiReturnHandler;
import buildcraftreforged.core.network.InvalidPacketException;
import buildcraftreforged.core.network.PacketData;
import buildcraftreforged.core.utils.BCLog;
import buildcraftreforged.core.utils.Utils;
import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Util;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class TileBlueprintLibrary extends TileBuildCraft implements Container, IGuiReturnHandler, MenuProvider {
    public static final int COMMAND_NEXT = 1, COMMAND_PREV = 2, COMMAND_LOCK_UPDATE = 3, COMMAND_DELETE = 4;
    /** 4.2.2's LIBRARY_SELECT packet, now one more command followed by the row. */
    public static final int COMMAND_SELECT = 5;

    /** 4.2.2 BuildCraftBuilders.LIBRARY_PAGE_SIZE. */
    public static final int LIBRARY_PAGE_SIZE = 12;
    /** Longest name sent to the client; the GUI shows MAX_BLUEPRINTS_NAME_SIZE of it, as 4.2.2. */
    public static final int MAX_SENT_NAME_LENGTH = 64;

    public ItemStack[] stack = new ItemStack[4];

    public int progressIn = 0;
    public int progressOut = 0;

    private ArrayList<BptBase> currentPage = new ArrayList<BptBase>();

    public String[] currentNames = new String[LIBRARY_PAGE_SIZE];
    public int selected = -1;

    public boolean locked = false;

    /** Client copy of the owner's UUID, so the GUI shows the owner's buttons. Null when no player placed the block. */
    public @Nullable UUID clientOwnerId = null;

    public TileBlueprintLibrary(BlockPos pos, BlockState state) {
        super(BuildersRegistry.libraryTile.get(), pos, state);
        Arrays.fill(stack, ItemStack.EMPTY);
        for (int i = 0; i < currentNames.length; i++) {
            currentNames[i] = "";
        }
    }

    @Override
    public void initialize() {
        super.initialize();
        if (level != null && !level.isClientSide()) {
            setCurrentPage(getNextPage(null));
        }
    }

    /**
     * 4.2.2 BuildersProxy.getOwner: the player whose list this library shows. A library placed without a player shares
     * one list, as 4.2.2's owner "" did.
     */
    public UUID getOwnerId() {
        GameProfile profile = getOwnerProfile();
        return profile == null ? Util.NIL_UUID : profile.id();
    }

    /** 4.2.2 compared usernames with owner; now the UUIDs. */
    public boolean isOwner(@Nullable Player player) {
        UUID ownerId;
        if (level != null && level.isClientSide()) {
            ownerId = clientOwnerId;
        } else {
            GameProfile profile = getOwnerProfile();
            ownerId = profile == null ? null : profile.id();
        }
        return player != null && ownerId != null && ownerId.equals(player.getUUID());
    }

    /** BlockBlueprintLibrary.onBlockActivated: a locked library opens for its owner only. */
    public boolean canOpen(Player player) {
        return !locked || isOwner(player);
    }

    private @Nullable BptPlayerIndex getPlayerIndex() {
        MinecraftServer server = level == null ? null : level.getServer();
        return server == null ? null : BuildCraftBuilders.getPlayerIndex(server, getOwnerId());
    }

    private @Nullable BptRootIndex getRootIndex() {
        MinecraftServer server = level == null ? null : level.getServer();
        return server == null ? null : BuildCraftBuilders.getBptRootIndex(server);
    }

    public ArrayList<BptBase> getNextPage(@Nullable String after) {
        ArrayList<BptBase> result = new ArrayList<BptBase>();

        BptPlayerIndex index = getPlayerIndex();
        BptRootIndex rootIndex = getRootIndex();

        if (index == null || rootIndex == null)
            return result;

        String it = after;

        while (result.size() < LIBRARY_PAGE_SIZE) {
            it = index.nextBpt(it);

            if (it == null) {
                break;
            }

            // Null for a file that is gone or cannot be read, 1.6.4 files included (BptBase.LEGACY_MESSAGE).
            BptBase bpt = rootIndex.getBluePrint(it);

            if (bpt != null) {
                result.add(bpt);
            }
        }

        return result;
    }

    public ArrayList<BptBase> getPrevPage(@Nullable String before) {
        ArrayList<BptBase> result = new ArrayList<BptBase>();

        BptPlayerIndex index = getPlayerIndex();
        BptRootIndex rootIndex = getRootIndex();

        if (index == null || rootIndex == null)
            return result;

        String it = before;

        while (result.size() < LIBRARY_PAGE_SIZE) {
            it = index.prevBpt(it);

            if (it == null) {
                break;
            }

            BptBase bpt = rootIndex.getBluePrint(it);

            if (bpt != null) {
                result.add(bpt);
            }
        }

        return result;
    }

    public void updateCurrentNames() {
        currentNames = new String[LIBRARY_PAGE_SIZE];
        for (int i = 0; i < currentPage.size(); i++) {
            currentNames[i] = currentPage.get(i).getName();
        }
        for (int i = currentPage.size(); i < currentNames.length; i++) {
            currentNames[i] = "";
        }
        sendNetworkUpdate();
    }

    public ArrayList<BptBase> getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(ArrayList<BptBase> newPage) {
        currentPage = newPage;
        selected = -1;
        updateCurrentNames();
    }

    /**
     * 4.2.2 called getNextPage for both buttons, so "previous" moved the page one entry forward. Previous now shows the
     * entries before the first one, or the first page when fewer than a page are left.
     */
    public void setCurrentPage(boolean nextPage) {
        if (currentPage.size() > 0) {
            if (nextPage) {
                setCurrentPage(getNextPage(fileName(currentPage.get(currentPage.size() - 1))));
            } else {
                ArrayList<BptBase> prev = getPrevPage(fileName(currentPage.get(0)));
                if (prev.size() < LIBRARY_PAGE_SIZE) {
                    setCurrentPage(getNextPage(null));
                } else {
                    Collections.reverse(prev);
                    setCurrentPage(prev);
                }
            }
        } else {
            setCurrentPage(getNextPage(null));
        }
    }

    private static @Nullable String fileName(BptBase bpt) {
        return bpt.file == null ? null : bpt.file.getFileName().toString();
    }

    public void deleteSelectedBpt() {
        BptPlayerIndex index = getPlayerIndex();
        if (index != null && selected > -1 && selected < currentPage.size()
                && fileName(currentPage.get(selected)) != null) {
            index.deleteBluePrint(fileName(currentPage.get(selected)));
            if (currentPage.size() > 0) {
                currentPage = getNextPage(index.prevBpt(fileName(currentPage.get(0))));
            } else {
                currentPage = getNextPage(null);
            }
            selected = -1;
            updateCurrentNames();
        }
    }

    /** Shows the current page again from its first entry, after an entry was added. */
    private void refreshCurrentPage() {
        BptPlayerIndex index = getPlayerIndex();
        if (index != null && currentPage.size() > 0) {
            setCurrentPage(getNextPage(index.prevBpt(fileName(currentPage.get(0)))));
        } else {
            setCurrentPage(getNextPage(null));
        }
    }

    @Override
    protected void loadAdditional(ValueInput nbttagcompound) {
        super.loadAdditional(nbttagcompound);

        // 4.2.2's "owner" (a username) is TileBuildCraft's owner and ownerId now.
        locked = nbttagcompound.getBooleanOr("locked", false);

        Utils.readStacksFromNBT(nbttagcompound, "stack", stack);
        // 4.2.2 did not save the progress either; an item left in an input slot starts again.
        progressIn = isBpt(stack[0]) ? 1 : 0;
        progressOut = isBpt(stack[2]) ? 1 : 0;
    }

    @Override
    protected void saveAdditional(ValueOutput nbttagcompound) {
        super.saveAdditional(nbttagcompound);

        nbttagcompound.putBoolean("locked", locked);

        Utils.writeStacksToNBT(nbttagcompound, "stack", stack);
    }

    /* CONTAINER */
    @Override
    public int getContainerSize() {
        return 4;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack s : stack) {
            if (!s.isEmpty())
                return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int i) {
        return stack[i];
    }

    @Override
    public ItemStack removeItem(int i, int j) {
        if (stack[i].isEmpty())
            return ItemStack.EMPTY;

        ItemStack res = stack[i].split(j);

        if (stack[i].isEmpty()) {
            stack[i] = ItemStack.EMPTY;
            // 4.2.2 kept the progress, and at 100 read the item that was gone (NullPointerException).
            resetProgress(i);
        }
        setChanged();

        return res;
    }

    @Override
    public void setItem(int i, ItemStack itemstack) {
        stack[i] = itemstack;

        if (i == 0) {
            if (isBpt(stack[0])) {
                progressIn = 1;
            } else {
                progressIn = 0;
            }
        }

        if (i == 2) {
            if (isBpt(stack[2])) {
                progressOut = 1;
            } else {
                progressOut = 0;
            }
        }
        setChanged();
    }

    private void resetProgress(int i) {
        if (i == 0)
            progressIn = 0;
        if (i == 2)
            progressOut = 0;
    }

    private static boolean isBpt(ItemStack itemstack) {
        return itemstack.getItem() instanceof ItemBptBase;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack toReturn = stack[slot];
        stack[slot] = ItemStack.EMPTY;
        resetProgress(slot);
        return toReturn;
    }

    @Override
    public void clearContent() {
        Arrays.fill(stack, ItemStack.EMPTY);
        progressIn = 0;
        progressOut = 0;
    }

    public String getInvName() {
        return "";
    }

    /** 4.2.2 getInventoryStackLimit; blueprint and template items stack to 1 anyway. */
    @Override
    public int getMaxStackSize() {
        return 64;
    }

    /** 4.2.2 isItemValidForSlot returned false: pipes and hoppers put nothing in; the GUI's slots ignore this. */
    @Override
    public boolean canPlaceItem(int i, ItemStack itemstack) {
        return false;
    }

    /** 4.2.2 checked the lock only when the GUI opened; a GUI left open by another player now closes on locking. */
    @Override
    public boolean stillValid(Player entityplayer) {
        return Container.stillValidBlockEntity(this, entityplayer) && canOpen(entityplayer);
    }

    @Override
    public void updateEntity() {
        super.updateEntity();
        if (level == null || level.isClientSide())
            return;

        if (progressIn > 0 && progressIn < 100) {
            progressIn++;
        }

        if (progressOut > 0 && progressOut < 100) {
            progressOut++;
        }

        if (progressIn == 100 && stack[1].isEmpty()) {
            setItem(1, stack[0]);
            setItem(0, ItemStack.EMPTY);
            int id = ItemBptBase.getBptId(stack[1]);
            BptRootIndex rootIndex = getRootIndex();
            BptBase bpt = id > 0 && rootIndex != null ? rootIndex.getBluePrint(id) : null;

            if (bpt != null && bpt.file != null) {
                BptPlayerIndex index = getPlayerIndex();

                try {
                    if (index != null) {
                        index.addBlueprint(bpt.file);
                        // 4.2.2: setCurrentPage(true); setCurrentPage(false);
                        refreshCurrentPage();
                    }
                } catch (IOException e) {
                    BCLog.logger.error("Could not save the blueprint list of " + getOwnerId(), e);
                }
            }
        }

        if (progressOut == 100 && stack[3].isEmpty()) {
            ItemStack blank = stack[2];
            if (selected > -1 && selected < currentPage.size()) {
                BptBase bpt = currentPage.get(selected);
                setItem(3, BuildCraftBuilders.getBptItemStack(itemFor(bpt), bpt.position, bpt.getName(), bpt.author));
            } else {
                setItem(3, BuildCraftBuilders.getBptItemStack(blank.getItem(), 0, null, null));
            }
            setItem(2, ItemStack.EMPTY);
        }
    }

    /** 4.2.2 kept the blank's item id, so a template could hold a blueprint; the copy is of the entry's kind. */
    private static Item itemFor(BptBase bpt) {
        return bpt instanceof BptTemplate ? BuildersRegistry.templateItem.get() : BuildersRegistry.blueprintItem.get();
    }

    @Override
    public void writePacketPayload(RegistryFriendlyByteBuf data) {
        for (String name : currentNames) {
            data.writeUtf(name.length() > MAX_SENT_NAME_LENGTH ? name.substring(0, MAX_SENT_NAME_LENGTH) : name,
                    MAX_SENT_NAME_LENGTH);
        }
        data.writeVarInt(selected);
        data.writeBoolean(locked);
        GameProfile profile = getOwnerProfile();
        data.writeNullable(profile == null ? null : profile.id(), UUIDUtil.STREAM_CODEC);
    }

    @Override
    public void readPacketPayload(RegistryFriendlyByteBuf data) {
        for (int i = 0; i < LIBRARY_PAGE_SIZE; i++) {
            currentNames[i] = data.readUtf(MAX_SENT_NAME_LENGTH);
        }
        selected = data.readVarInt();
        locked = data.readBoolean();
        clientOwnerId = data.readNullable(UUIDUtil.STREAM_CODEC);
    }

    /** The command goes as extra data of the PacketGuiReturn (GuiBlueprintLibrary.sendCommand); the tile sends nothing. */
    @Override
    public void writeGuiData(RegistryFriendlyByteBuf data) {
    }

    /**
     * 4.2.2 PacketHandlerBuilders.onLibraryAction and onLibrarySelect, which took any command for any library. The
     * sender must be allowed into the library (PacketHandler has already checked the open menu and reach), lock and
     * delete are the owner's, and a row is 0 to 11 (4.2.2 took any number; an empty row is still ignored).
     */
    @Override
    public void readGuiData(RegistryFriendlyByteBuf data, @Nullable Player player) throws InvalidPacketException {
        int command = PacketData.readVarInt(data, COMMAND_NEXT, COMMAND_SELECT);
        int row = command == COMMAND_SELECT ? PacketData.readVarInt(data, 0, LIBRARY_PAGE_SIZE - 1) : -1;
        if (data.isReadable())
            throw new InvalidPacketException("Library packet has " + data.readableBytes() + " bytes too many");

        if (player == null || !canOpen(player))
            throw new InvalidPacketException("library at " + worldPosition.toShortString() + " is locked");
        if ((command == COMMAND_LOCK_UPDATE || command == COMMAND_DELETE) && !isOwner(player))
            throw new InvalidPacketException("only the owner may lock or delete in the library at "
                    + worldPosition.toShortString());

        switch (command) {
            case COMMAND_DELETE:
                deleteSelectedBpt();
                break;
            case COMMAND_LOCK_UPDATE:
                locked = !locked;
                setChanged();
                sendNetworkUpdate();
                break;
            case COMMAND_NEXT:
                setCurrentPage(true);
                break;
            case COMMAND_PREV:
                setCurrentPage(false);
                break;
            case COMMAND_SELECT:
                if (row < getCurrentPage().size()) {
                    selected = row;
                }
                sendNetworkUpdate();
                break;
            default:
                break;
        }
    }

    /* MENU: 4.2.2's GuiHandler, GuiIds.BLUEPRINT_LIBRARY. */
    @Override
    public Component getDisplayName() {
        return Component.translatable("tile.libraryBlock");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ContainerBlueprintLibrary(containerId, playerInventory, this);
    }
}
