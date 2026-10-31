package buildcraftreforged.gametest;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.api.recipes.AssemblyRecipe;
import buildcraftreforged.core.network.InvalidPacketException;
import buildcraftreforged.core.network.PacketData;
import buildcraftreforged.core.network.PacketHandler;
import buildcraftreforged.silicon.AssemblyRecipeManager;
import buildcraftreforged.silicon.AssemblyTableRecipe;
import buildcraftreforged.silicon.BlockLaser;
import buildcraftreforged.silicon.SiliconRegistry;
import buildcraftreforged.silicon.TileAssemblyTable;
import buildcraftreforged.silicon.TileAssemblyTable.SelectionMessage;
import buildcraftreforged.silicon.TileLaser;
import buildcraftreforged.silicon.gui.ContainerAssemblyTable;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * #72 assembly table: the JSON recipe type, crafting with laser power, the plan queue, tag inputs, output into a
 * neighbouring inventory, the selection through the GUI return, the GUI's energy numbers, saving and the client
 * payload. The recipes are test-only (gametest_data: gametest_assembly_items, 400 FE, redstone + 2 iron ingots ->
 * 2 diamonds; gametest_assembly_tag, 200 FE, 2 of c:dyes/red + redstone -> emerald), because the 4.2.2 outputs
 * (chipsets, gates, pipe wire, plugs, facades) are not registered yet.
 */
final class AssemblyTableTests {
    private static final BlockPos LASER = new BlockPos(2, 1, 2);
    private static final BlockPos TABLE = new BlockPos(2, 3, 2);

    private AssemblyTableTests() {
    }

    /** A laser under the table powers the planned recipe; it crafts after 400 FE (10 ticks at 40 FE/t) and drops it. */
    static void laserCrafting(GameTestHelper helper) {
        TileAssemblyTable table = placeTable(helper, TABLE);
        BlockPos pos = helper.absolutePos(TABLE);
        table.setItem(0, new ItemStack(Items.REDSTONE));
        table.setItem(1, new ItemStack(Items.IRON_INGOT, 2));

        AssemblyRecipe diamonds = recipe(helper, Items.DIAMOND);
        helper.assertValueEqual(table.getPotentialOutputs(), List.of(diamonds), "potential outputs");
        helper.assertFalse(table.requiresLaserEnergy(), "a table with nothing planned wants energy");

        // The GUI's click: a SelectionMessage in a GUI return for the open menu, as the server receives it.
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.snapTo(Vec3.atBottomCenterOf(pos.above()));
        if (!(table.createMenu(1, player.getInventory(), player) instanceof ContainerAssemblyTable menu)) {
            throw helper.assertionException("the table made no assembly menu");
        }
        player.containerMenu = menu;
        helper.assertValueEqual(menu.slots.size(), 12 + 36, "menu slots");
        String reason = PacketHandler.checkMenuAccess(PacketHandler.Sender.of(player), menu.containerId, pos);
        helper.assertTrue(reason == null, "GUI return refused: " + reason);
        select(helper, table, Items.DIAMOND, 2, true);
        helper.assertTrue(table.isPlanned(diamonds) && table.isAssembling(diamonds), "the clicked recipe is not current");
        helper.assertTrue(table.requiresLaserEnergy(), "a table with a planned recipe wants no energy");
        helper.assertValueEqual(table.getRequiredEnergy(), 400L, "required FE");

        TileLaser laser = placeLaser(helper);
        helper.assertValueEqual(insert(helper, 1500), 1500, "FE accepted by the laser");

        helper.runAfterDelay(5, () -> {
            helper.assertTrue(laser.getLaserTarget() == table, "the laser did not pick the table");
            long stored = table.getStoredEnergy();
            helper.assertTrue(stored > 0 && stored < 400 && stored % 40 == 0, "stored FE after 5 ticks: " + stored);
            helper.assertTrue(dropped(helper, Items.DIAMOND) == 0, "crafted before the energy was in");

            // The GUI numbers, as 16-bit data slots read back on a client copy of the menu.
            TileAssemblyTable client = new TileAssemblyTable(pos, table.getBlockState());
            for (int i = 0; i < 6; i++) {
                client.getGUINetworkData(i, (short) table.sendGUINetworkData(i));
            }
            helper.assertValueEqual(client.getStoredEnergy(), stored, "stored FE on the client");
            helper.assertValueEqual(client.getRequiredEnergy(), 400L, "required FE on the client");
            helper.assertTrue(client.getRecentEnergyAverage() > 0, "no energy rate on the client");

            helper.succeedWhen(() -> {
                helper.assertValueEqual(dropped(helper, Items.DIAMOND), 2, "diamonds dropped");
                helper.assertTrue(table.getItem(0).isEmpty() && table.getItem(1).isEmpty(), "inputs not used up");
                helper.assertTrue(table.currentRecipe == null, "a recipe stays current without its inputs");
                helper.assertTrue(table.isPlanned(diamonds), "the plan was forgotten after crafting");
                helper.assertFalse(table.requiresLaserEnergy(), "a table with nothing to do wants energy");
                helper.assertValueEqual(table.getStoredEnergy(), 0L, "energy kept after crafting");
                player.containerMenu = player.inventoryMenu;
            });
        });
    }

    /**
     * Two planned recipes run one after the other, the output goes into the chest next to the table, a tag input takes
     * exactly its count, and energy beyond five times the recipe's is not asked for.
     */
    static void queueAndTags(GameTestHelper helper) {
        TileAssemblyTable table = placeTable(helper, TABLE);
        BlockPos chestPos = TABLE.east();
        helper.setBlock(chestPos, Blocks.CHEST);
        Container chest = helper.getBlockEntity(chestPos, ChestBlockEntity.class);
        table.setItem(0, new ItemStack(Items.DYE.pick(DyeColor.RED), 3));
        table.setItem(1, new ItemStack(Items.REDSTONE, 2));
        table.setItem(2, new ItemStack(Items.IRON_INGOT, 2));

        AssemblyRecipe diamonds = recipe(helper, Items.DIAMOND);
        AssemblyRecipe emerald = recipe(helper, Items.EMERALD);
        helper.assertValueEqual(table.getPotentialOutputs().size(), 2, "potential outputs");
        table.planOutput(diamonds);
        table.planOutput(emerald);
        helper.assertTrue(table.isAssembling(diamonds), "the first planned recipe is not current");

        table.receiveLaserEnergy(1999);
        helper.assertTrue(table.requiresLaserEnergy(), "below five times the recipe's energy the table wants more");
        table.receiveLaserEnergy(1);
        helper.assertFalse(table.requiresLaserEnergy(), "at five times the recipe's energy the table still wants more");

        helper.runAfterDelay(1, () -> {
            helper.assertValueEqual(count(chest, Items.DIAMOND), 2, "diamonds in the chest");
            helper.assertTrue(table.isAssembling(emerald), "the next planned recipe did not follow");
            helper.assertValueEqual(table.getStoredEnergy(), 0L, "4.2.2 drops the extra energy after crafting");
            table.receiveLaserEnergy(200);
            helper.runAfterDelay(1, () -> {
                helper.assertValueEqual(count(chest, Items.EMERALD), 1, "emerald in the chest");
                helper.assertValueEqual(table.getItem(0).getCount(), 1, "red dye left: the tag input took its count");
                helper.assertTrue(table.getItem(1).isEmpty() && table.getItem(2).isEmpty(), "inputs left over");
                helper.assertTrue(table.currentRecipe == null, "a recipe is current with nothing to make");
                helper.assertTrue(dropped(helper, Items.DIAMOND) == 0 && dropped(helper, Items.EMERALD) == 0,
                        "output dropped next to a chest");
                helper.succeed();
            });
        });
    }

    /**
     * The JSON reads into the 4.2.2 input array; cancelling moves the current recipe on; the selection survives a
     * save and load and reaches a client through the payload; a bad GUI return is refused.
     */
    static void selectionSaveAndSync(GameTestHelper helper) {
        RegistryAccess registryAccess = helper.getLevel().registryAccess();
        AssemblyRecipe diamonds = recipe(helper, Items.DIAMOND);
        AssemblyRecipe emerald = recipe(helper, Items.EMERALD);
        helper.assertTrue(helper.getLevel().getServer().getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE,
                Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "gametest_assembly_items")))
                .filter(holder -> holder.value() instanceof AssemblyTableRecipe).isPresent(), "the JSON recipe did not load");
        helper.assertValueEqual(diamonds.energy, 400F, "energy read");
        helper.assertValueEqual(diamonds.input.length, 2, "item inputs");
        helper.assertTrue(diamonds.input[1] instanceof ItemStack stack && stack.is(Items.IRON_INGOT) && stack.getCount() == 2,
                "the second input is not 2 iron ingots");
        helper.assertValueEqual(emerald.input.length, 3, "a tag input is a tag and a count");
        helper.assertValueEqual(emerald.input[1], (Object) 2, "tag count");
        helper.assertTrue(helper.getLevel().getServer().getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE,
                Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "assembly_table_block"))).isPresent(),
                "no crafting recipe for the table");

        TileAssemblyTable table = placeTable(helper, TABLE);
        table.setItem(0, new ItemStack(Items.REDSTONE, 2));
        table.setItem(1, new ItemStack(Items.IRON_INGOT, 2));
        table.setItem(2, new ItemStack(Items.DYE.pick(DyeColor.RED), 2));
        select(helper, table, Items.DIAMOND, 1, true);
        select(helper, table, Items.EMERALD, 1, true);
        select(helper, table, Items.GOLD_INGOT, 1, true);
        helper.assertTrue(table.isAssembling(diamonds) && table.isPlanned(emerald), "selection");
        select(helper, table, Items.DIAMOND, 2, false);
        helper.assertFalse(table.isPlanned(diamonds), "cancelled recipe still planned");
        helper.assertTrue(table.isAssembling(emerald), "cancelling the current recipe did not move to the next");
        table.receiveLaserEnergy(120);

        boolean refused = false;
        try {
            table.readGuiData(PacketData.wrap(new byte[] {1, 5}, registryAccess), null);
        } catch (InvalidPacketException | RuntimeException e) {
            refused = true;
        }
        helper.assertTrue(refused, "a malformed selection was accepted");

        // The client copy reads the payload and finds the same recipes in its list.
        TileAssemblyTable client = new TileAssemblyTable(table.getBlockPos(), table.getBlockState());
        client.setLevel(helper.getLevel());
        byte[] bytes = PacketData.write(registryAccess, table::writePacketPayload);
        RegistryFriendlyByteBuf data = PacketData.wrap(bytes, registryAccess);
        try {
            client.readPacketPayload(data);
        } finally {
            data.release();
        }
        helper.assertTrue(client.isPlanned(emerald) && client.isAssembling(emerald) && !client.isPlanned(diamonds),
                "the client does not see the selection");

        CompoundTag tag = table.saveWithFullMetadata(registryAccess);
        helper.setBlock(TABLE, Blocks.AIR);
        helper.assertTrue(dropped(helper, Items.REDSTONE) == 2, "the inventory was not dropped when the table broke");
        TileAssemblyTable reloaded = placeTable(helper, TABLE);
        reloaded.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registryAccess, tag));
        helper.assertValueEqual(reloaded.getItem(0).getCount(), 2, "items loaded");
        helper.assertValueEqual(reloaded.getStoredEnergy(), 120L, "energy loaded");

        helper.succeedWhen(() -> {
            helper.assertTrue(reloaded.isPlanned(emerald) && !reloaded.isPlanned(diamonds), "plan not loaded");
            helper.assertTrue(reloaded.isAssembling(emerald), "current recipe not loaded");
            helper.assertValueEqual(reloaded.getRequiredEnergy(), 200L, "required energy after loading");
        });
    }

    private static void select(GameTestHelper helper, TileAssemblyTable table, Item item, int count,
            boolean select) {
        RegistryAccess registryAccess = helper.getLevel().registryAccess();
        SelectionMessage message = new SelectionMessage();
        message.select = select;
        // Only the item and components count; 4.2.2 compared the item id and damage.
        message.stack = new ItemStack(item, count);
        try {
            table.readGuiData(PacketData.wrap(PacketData.write(registryAccess, message::writeData), registryAccess), null);
        } catch (InvalidPacketException e) {
            throw helper.assertionException("selection refused: " + e.getMessage());
        }
    }

    private static AssemblyRecipe recipe(GameTestHelper helper, Item output) {
        AssemblyRecipe recipe = AssemblyRecipeManager.findRecipe(helper.getLevel(), new ItemStack(output));
        if (recipe == null) {
            throw helper.assertionException("no assembly recipe for " + output);
        }
        return recipe;
    }

    private static TileAssemblyTable placeTable(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, SiliconRegistry.assemblyTableBlock.get());
        return helper.getBlockEntity(pos, TileAssemblyTable.class);
    }

    private static TileLaser placeLaser(GameTestHelper helper) {
        helper.setBlock(LASER, SiliconRegistry.laserBlock.get().defaultBlockState().setValue(BlockLaser.FACING, Direction.UP));
        return helper.getBlockEntity(LASER, TileLaser.class);
    }

    private static int insert(GameTestHelper helper, int amount) {
        EnergyHandler handler = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(LASER),
                Direction.DOWN);
        helper.assertTrue(handler != null, "the laser has no energy handler");
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = handler.insert(amount, tx);
            tx.commit();
            return inserted;
        }
    }

    private static int dropped(GameTestHelper helper, Item item) {
        AABB box = new AABB(helper.absolutePos(TABLE)).inflate(2);
        int total = 0;
        for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
            if (entity.getItem().is(item))
                total += entity.getItem().getCount();
        }
        return total;
    }

    private static int count(Container container, Item item) {
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (container.getItem(i).is(item))
                total += container.getItem(i).getCount();
        }
        return total;
    }
}
