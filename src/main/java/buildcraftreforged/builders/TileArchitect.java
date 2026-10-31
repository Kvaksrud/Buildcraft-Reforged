/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: IInventory becomes Container over the same two slots; the blueprint goes to
 * the server's library (BuildCraftBuilders.getBptRootIndex) and the item gets the BptItemData component, with the
 * owner as the author; the template's setBlockId is setMask; @TileNetworkData/handleDescriptionPacket become
 * writePacketPayload/readPacketPayload; NBT becomes ValueInput/ValueOutput with the same tag names; invalidate becomes
 * setRemoved; the GUI is a MenuProvider, and the ARCHITECT_NAME packet a PacketGuiReturn carrying the typed character;
 * the status panel (plan section 10) is new.
 */
package buildcraftreforged.builders;

import buildcraftreforged.api.core.IAreaProvider;
import buildcraftreforged.api.core.LaserKind;
import buildcraftreforged.builders.gui.ContainerTemplate;
import buildcraftreforged.core.Box;
import buildcraftreforged.core.TileBuildCraft;
import buildcraftreforged.core.blueprints.BptBase;
import buildcraftreforged.core.blueprints.BptBlueprint;
import buildcraftreforged.core.blueprints.BptContext;
import buildcraftreforged.core.blueprints.BptRootIndex;
import buildcraftreforged.core.blueprints.BptTemplate;
import buildcraftreforged.core.network.IGuiReturnHandler;
import buildcraftreforged.core.network.InvalidPacketException;
import buildcraftreforged.core.status.IStatusProvider;
import buildcraftreforged.core.status.MachineStatus;
import buildcraftreforged.core.utils.Utils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class TileArchitect extends TileBuildCraft implements Container, IGuiReturnHandler, MenuProvider,
        IStatusProvider {

    /** 4.2.2 counted to 200 ticks before writing the blueprint. */
    public static final int COMPUTING_TIME = 200;

    public Box box = new Box();
    private NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
    private boolean isComputing = false;
    public int computingTime = 0;
    public String name = "";
    // Use that field to avoid creating several times the same template if
    // they're the same!
    private int lastBptId = 0;

    public TileArchitect(BlockPos pos, BlockState state) {
        super(BuildersRegistry.architectTile.get(), pos, state);
    }

    @Override
    public void updateEntity() {
        super.updateEntity();

        if (level != null && !level.isClientSide() && isComputing) {
            if (computingTime < COMPUTING_TIME) {
                computingTime++;
            } else {
                createBpt();
            }
        }
    }

    @Override
    public void initialize() {
        super.initialize();

        if (level == null)
            return;

        if (!level.isClientSide() && !box.isInitialized()) {
            IAreaProvider a = Utils.getNearbyAreaProvider(level, worldPosition.getX(), worldPosition.getY(),
                    worldPosition.getZ());

            if (a != null) {
                box.initialize(a);
                a.removeFromWorld();
                setChanged();
            }
        }

        // 4.2.2 created the lasers on the server only (!isRenderWorld) and on the client when the box arrived; the box
        // is render data here, so the client is the side that needs it.
        if (box.isInitialized()) {
            box.createLasers(level, LaserKind.Stripes);
        }

        sendNetworkUpdate();
    }

    public void createBpt() {
        if (level == null || !box.isInitialized() || !items.get(1).isEmpty())
            return;
        // 4.2.2 read items[0] unchecked; computing only starts with a blueprint or template there.
        if (!(items.get(0).getItem() instanceof ItemBptBase))
            return;

        BptBase result;
        BptContext context = null;

        if (items.get(0).getItem() instanceof ItemBptTemplate) {
            result = createBptTemplate();
            context = new BptContext(level, null, box);
        } else {
            result = createBptBlueprint();
            context = new BptContext(level, (BptBlueprint) result, box);
        }

        if (!name.equals("")) {
            result.setName(name);
        }

        // Not in 4.2.2: the item and the file carry the author (plan section 6), the player who placed the table.
        if (getOwnerProfile() != null) {
            result.author = getOwner();
        }

        result.anchorX = worldPosition.getX() - box.xMin;
        result.anchorY = worldPosition.getY() - box.yMin;
        result.anchorZ = worldPosition.getZ() - box.zMin;

        Direction o = getBlockState().hasProperty(BlockArchitect.FACING)
                ? getBlockState().getValue(BlockArchitect.FACING).getOpposite() : Direction.EAST;

        if (o == Direction.EAST) {
            // Do nothing
        } else if (o == Direction.SOUTH) {
            result.rotateLeft(context);
            result.rotateLeft(context);
            result.rotateLeft(context);
        } else if (o == Direction.WEST) {
            result.rotateLeft(context);
            result.rotateLeft(context);
        } else if (o == Direction.NORTH) {
            result.rotateLeft(context);
        }

        if (!(level instanceof ServerLevel serverLevel))
            return;
        BptRootIndex index = BuildCraftBuilders.getBptRootIndex(serverLevel.getServer());
        if (index == null)
            return;

        ItemStack stack;
        BptBase last = lastBptId > 0 ? index.getBluePrint(lastBptId) : null;
        if (last != null && result.equals(last)) {
            result = last;
            stack = BuildCraftBuilders.getBptItemStack(items.get(0).getItem(), lastBptId, result.getName(), result.author);
        } else {
            int bptId = index.storeBluePrint(result);
            stack = BuildCraftBuilders.getBptItemStack(items.get(0).getItem(), bptId, result.getName(), result.author);
            lastBptId = bptId;
        }

        setItem(1, stack);
        setItem(0, ItemStack.EMPTY);
    }

    public BptBase createBptTemplate() {
        int mask1 = 1;
        int mask0 = 0;

        if (level.hasNeighborSignal(worldPosition)) {
            mask1 = 0;
            mask0 = 1;
        }

        BptTemplate result = new BptTemplate(box.sizeX(), box.sizeY(), box.sizeZ());

        for (int x = box.xMin; x <= box.xMax; ++x) {
            for (int y = box.yMin; y <= box.yMax; ++y) {
                for (int z = box.zMin; z <= box.zMax; ++z) {
                    if (!level.isEmptyBlock(new BlockPos(x, y, z))) {
                        result.setMask(x - box.xMin, y - box.yMin, z - box.zMin, mask1);
                    } else {
                        result.setMask(x - box.xMin, y - box.yMin, z - box.zMin, mask0);
                    }
                }
            }
        }

        return result;
    }

    private BptBase createBptBlueprint() {
        BptBlueprint result = new BptBlueprint(box.sizeX(), box.sizeY(), box.sizeZ());

        BptContext context = new BptContext(level, result, box);

        for (int x = box.xMin; x <= box.xMax; ++x) {
            for (int y = box.yMin; y <= box.yMax; ++y) {
                for (int z = box.zMin; z <= box.zMax; ++z) {
                    result.readFromWorld(context, this, x, y, z);
                }
            }
        }

        return result;
    }

    public void handleClientInput(char c) {
        if (c == 8) {
            if (name.length() > 0) {
                name = name.substring(0, name.length() - 1);
            }
        } else if (Character.isLetterOrDigit(c) || c == ' ') {
            if (name.length() < BuildCraftBuilders.MAX_BLUEPRINTS_NAME_SIZE) {
                name += c;
            }
        }
        setChanged();
        sendNetworkUpdate();
    }

    /* CONTAINER */
    @Override
    public int getContainerSize() {
        return 2;
    }

    @Override
    public boolean isEmpty() {
        return items.get(0).isEmpty() && items.get(1).isEmpty();
    }

    @Override
    public ItemStack getItem(int i) {
        return items.get(i);
    }

    @Override
    public ItemStack removeItem(int i, int j) {
        ItemStack result = ContainerHelper.removeItem(items, i, j);

        initializeComputing();
        setChanged();

        return result;
    }

    @Override
    public void setItem(int i, ItemStack itemstack) {
        items.set(i, itemstack);

        initializeComputing();
        setChanged();
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    public String getInvName() {
        return "Template";
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    /** 4.2.2 isItemValidForSlot returned false: pipes and hoppers put nothing in; the GUI's slots ignore this. */
    @Override
    public boolean canPlaceItem(int i, ItemStack itemstack) {
        return false;
    }

    @Override
    public boolean stillValid(Player entityplayer) {
        return Container.stillValidBlockEntity(this, entityplayer);
    }

    @Override
    protected void loadAdditional(ValueInput nbttagcompound) {
        super.loadAdditional(nbttagcompound);

        lastBptId = nbttagcompound.getIntOr("lastTemplateId", 0);
        computingTime = nbttagcompound.getIntOr("computingTime", 0);
        isComputing = nbttagcompound.getBooleanOr("isComputing", false);

        nbttagcompound.child("box").ifPresent(box::initialize);

        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(nbttagcompound, items);

        name = nbttagcompound.getStringOr("name", "");
        if (name.length() > BuildCraftBuilders.MAX_BLUEPRINTS_NAME_SIZE)
            name = name.substring(0, BuildCraftBuilders.MAX_BLUEPRINTS_NAME_SIZE);
    }

    @Override
    protected void saveAdditional(ValueOutput nbttagcompound) {
        super.saveAdditional(nbttagcompound);

        nbttagcompound.putInt("lastTemplateId", lastBptId);
        nbttagcompound.putInt("computingTime", computingTime);
        nbttagcompound.putBoolean("isComputing", isComputing);

        if (box.isInitialized()) {
            box.writeToNBT(nbttagcompound.child("box"));
        }

        ContainerHelper.saveAllItems(nbttagcompound, items);
        nbttagcompound.putString("name", name);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        destroy();
    }

    @Override
    public void destroy() {
        if (box.isInitialized()) {
            box.deleteLasers();
        }
    }

    private void initializeComputing() {
        if (!box.isInitialized())
            return;
        else if (!isComputing) {
            if (items.get(0).getItem() instanceof ItemBptBase && items.get(1).isEmpty()) {
                isComputing = true;
                computingTime = 0;
            } else {
                isComputing = false;
                computingTime = 0;
            }
        } else {
            if (!(items.get(0).getItem() instanceof ItemBptBase)) {
                isComputing = false;
                computingTime = 0;
            }
        }
    }

    public int getComputingProgressScaled(int i) {
        return (computingTime * i) / COMPUTING_TIME;
    }

    @Override
    public void writePacketPayload(RegistryFriendlyByteBuf data) {
        box.writeToStream(data);
        data.writeUtf(name, BuildCraftBuilders.MAX_BLUEPRINTS_NAME_SIZE);
    }

    @Override
    public void readPacketPayload(RegistryFriendlyByteBuf data) {
        boolean initialized = box.isInitialized();

        box.readFromStream(data);
        name = data.readUtf(BuildCraftBuilders.MAX_BLUEPRINTS_NAME_SIZE);

        if (!initialized && box.isInitialized() && level != null) {
            box.createLasers(level, LaserKind.Stripes);
        }
    }

    /** The character goes as extra data of the PacketGuiReturn (GuiTemplate.keyTyped); the tile itself sends nothing. */
    @Override
    public void writeGuiData(RegistryFriendlyByteBuf data) {
    }

    /** 4.2.2 PacketHandlerBuilders.onArchitectName. Characters handleClientInput does not take are ignored, as in 4.2.2. */
    @Override
    public void readGuiData(RegistryFriendlyByteBuf data, @Nullable Player player) throws InvalidPacketException {
        if (data.readableBytes() != 2)
            throw new InvalidPacketException("Architect name packet of " + data.readableBytes() + " bytes");
        handleClientInput(data.readChar());
    }

    /* MENU: 4.2.2's GuiHandler, GuiIds.ARCHITECT_TABLE. */
    @Override
    public Component getDisplayName() {
        return Component.translatable("tile.architectBlock");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ContainerTemplate(containerId, playerInventory, this);
    }

    // Not in 4.2.2.

    public Box getBox() {
        return box;
    }

    public boolean isComputing() {
        return isComputing;
    }

    // Status panel (plan section 10), not in 4.2.2.

    @Override
    public MachineStatus getStatusState() {
        if (!box.isInitialized())
            return MachineStatus.NO_WORK_AREA;
        if (isComputing)
            return MachineStatus.WORKING;
        if (!items.get(1).isEmpty())
            return MachineStatus.DONE;
        return MachineStatus.IDLE;
    }
}
