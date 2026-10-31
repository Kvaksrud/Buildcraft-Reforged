package buildcraftreforged.gametest;

import buildcraftreforged.core.CoreRegistry;
import buildcraftreforged.silicon.BlockLaser;
import buildcraftreforged.silicon.SiliconRegistry;
import buildcraftreforged.silicon.TileAdvancedCraftingTable;
import buildcraftreforged.silicon.TileLaser;
import buildcraftreforged.silicon.gui.ContainerAdvancedCraftingTable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * #73 Advanced Crafting Table: a vanilla recipe crafted with laser power (5000 FE, 4.2.2's 500 MJ), ingredients
 * pulled from a neighbouring chest and bound by ore tag (test-only tag c:gametest/advanced_crafting with amethyst
 * shards and prismarine crystals, and the shapeless test recipe gametest_advanced_crafting_tag: two of the tag -> an
 * echo shard), container items going out with the result, the gate actions, saving and the table's own recipe.
 */
final class AdvancedCraftingTableTests {
    static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();
    static final int MAX_TICKS = 600;

    static {
        TESTS.put("advanced_crafting_laser", AdvancedCraftingTableTests::laserCrafting);
        TESTS.put("advanced_crafting_tag_matching", AdvancedCraftingTableTests::tagMatching);
        TESTS.put("advanced_crafting_container_items", AdvancedCraftingTableTests::containerItems);
        TESTS.put("advanced_crafting_save_actions_recipe", AdvancedCraftingTableTests::saveActionsAndRecipe);
    }

    private static final BlockPos LASER = new BlockPos(2, 1, 2);
    private static final BlockPos TABLE = new BlockPos(2, 3, 2);
    private static final int FIRST_OUTPUT = 15;

    private AdvancedCraftingTableTests() {
    }

    /**
     * Two planks in a column on the template make sticks: the table shows the result, asks a laser for 5000 FE and puts
     * the four sticks in its output slots. Only the output slots can be taken from outside.
     */
    static void laserCrafting(GameTestHelper helper) {
        TileAdvancedCraftingTable table = placeTable(helper);
        table.getCraftingSlots().setItem(0, new ItemStack(Items.OAK_PLANKS));
        table.getCraftingSlots().setItem(3, new ItemStack(Items.OAK_PLANKS));
        table.setItem(0, new ItemStack(Items.OAK_PLANKS, 3));

        helper.assertFalse(table.canPlaceItem(FIRST_OUTPUT, new ItemStack(Items.OAK_PLANKS)), "the output takes items");
        helper.assertTrue(table.canPlaceItem(0, new ItemStack(Items.OAK_PLANKS)), "the input refuses items");
        helper.assertFalse(table.canTakeItemThroughFace(0, table.getItem(0), Direction.DOWN), "the input gives items");
        helper.assertTrue(table.canTakeItemThroughFace(FIRST_OUTPUT, ItemStack.EMPTY, Direction.DOWN),
                "the output keeps its items");

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        if (!(table.createMenu(1, player.getInventory(), player) instanceof ContainerAdvancedCraftingTable menu)) {
            throw helper.assertionException("the table made no advanced crafting menu");
        }
        helper.assertValueEqual(menu.slots.size(), 9 + 1 + 15 + 9 + 36, "menu slots");

        // Keeps the laser charged once it is placed. Registered at the start: registered from inside the delayed
        // action below, the delayed action ran twice and the test failed.
        helper.onEachTick(() -> charge(helper));
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(table.getOutputSlot().getItem(0).is(Items.STICK), "the result slot shows no sticks");
            helper.assertValueEqual(table.getRequiredEnergy(), 5000L, "required FE");
            helper.assertTrue(table.requiresLaserEnergy(), "the table wants no laser energy");
            helper.assertValueEqual(table.getStoredEnergy(), 0L, "stored FE before the laser");

            helper.setBlock(LASER, SiliconRegistry.laserBlock.get().defaultBlockState()
                    .setValue(BlockLaser.FACING, Direction.UP));
            TileLaser laser = helper.getBlockEntity(LASER, TileLaser.class);

            helper.succeedWhen(() -> {
                helper.assertTrue(laser.getLaserTarget() == table, "the laser did not pick the table");
                helper.assertValueEqual(count(table, Items.STICK), 4, "sticks made");
                helper.assertTrue(table.getItem(FIRST_OUTPUT).is(Items.STICK), "the sticks are not in the output slot");
                helper.assertValueEqual(table.getItem(0).getCount(), 1, "planks left");
                helper.assertTrue(table.getStoredEnergy() < 5000, "the craft used no energy");

                // The GUI numbers, as 16-bit data slots read back on a client copy.
                TileAdvancedCraftingTable client = new TileAdvancedCraftingTable(table.getBlockPos(), table.getBlockState());
                for (int i = 0; i < 6; i++) {
                    client.getGUINetworkData(i, (short) table.sendGUINetworkData(i));
                }
                helper.assertValueEqual(client.getStoredEnergy(), table.getStoredEnergy(), "stored FE on the client");
                helper.assertTrue(client.getRecentEnergyAverage() > 0, "no energy rate on the client");
            });
        });
    }

    /**
     * Amethyst shards on the template, prismarine crystals in the chest beside the table: the table pulls the crystals
     * (same ore tag) and not the cobblestone, binds them, and crafts the tag recipe once it has the energy.
     */
    static void tagMatching(GameTestHelper helper) {
        TileAdvancedCraftingTable table = placeTable(helper);
        BlockPos chestPos = TABLE.west();
        helper.setBlock(chestPos, Blocks.CHEST);
        Container chest = helper.getBlockEntity(chestPos, ChestBlockEntity.class);
        chest.setItem(0, new ItemStack(Items.COBBLESTONE, 4));
        chest.setItem(1, new ItemStack(Items.PRISMARINE_CRYSTALS, 3));
        table.getCraftingSlots().setItem(0, new ItemStack(Items.AMETHYST_SHARD));
        table.getCraftingSlots().setItem(1, new ItemStack(Items.AMETHYST_SHARD));

        helper.runAfterDelay(6, () -> {
            helper.assertValueEqual(count(table, Items.PRISMARINE_CRYSTALS), 2, "crystals pulled from the chest");
            helper.assertValueEqual(count(chest, Items.PRISMARINE_CRYSTALS), 1, "crystals left in the chest");
            helper.assertValueEqual(count(chest, Items.COBBLESTONE), 4, "cobblestone left in the chest");
            helper.assertTrue(table.getOutputSlot().getItem(0).is(Items.ECHO_SHARD), "the result slot shows no echo shard");
            helper.assertValueEqual(count(table, Items.ECHO_SHARD), 0, "crafted without energy");
            helper.assertTrue(table.requiresLaserEnergy(), "the table wants no laser energy");

            table.receiveLaserEnergy(5000);
            helper.succeedWhen(() -> {
                helper.assertTrue(table.getItem(FIRST_OUTPUT).is(Items.ECHO_SHARD), "no echo shard in the output slot");
                helper.assertValueEqual(count(table, Items.ECHO_SHARD), 1, "echo shards made");
                // The last crystal comes over for the next craft, which has no energy yet.
                helper.assertValueEqual(count(chest, Items.PRISMARINE_CRYSTALS), 0, "crystals left in the chest");
                helper.assertValueEqual(count(table, Items.PRISMARINE_CRYSTALS), 1, "crystals left in the table");
                helper.assertValueEqual(count(chest, Items.COBBLESTONE), 4, "cobblestone taken");
            });
        });
    }

    /** A cake: the three milk buckets come back as empty buckets with the cake, as 4.2.2's internal player had them. */
    static void containerItems(GameTestHelper helper) {
        TileAdvancedCraftingTable table = placeTable(helper);
        Container grid = table.getCraftingSlots();
        Item[] cake = {
                Items.MILK_BUCKET, Items.MILK_BUCKET, Items.MILK_BUCKET,
                Items.SUGAR, Items.EGG, Items.SUGAR,
                Items.WHEAT, Items.WHEAT, Items.WHEAT};
        for (int i = 0; i < cake.length; i++) {
            grid.setItem(i, new ItemStack(cake[i]));
        }
        for (int i = 0; i < 3; i++) {
            table.setItem(i, new ItemStack(Items.MILK_BUCKET));
        }
        table.setItem(3, new ItemStack(Items.SUGAR, 2));
        table.setItem(4, new ItemStack(Items.EGG));
        table.setItem(5, new ItemStack(Items.WHEAT, 3));
        table.receiveLaserEnergy(5000);

        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(table, Items.CAKE), 1, "cakes made");
            helper.assertValueEqual(count(table, Items.BUCKET), 3, "buckets back");
            helper.assertValueEqual(count(table, Items.MILK_BUCKET), 0, "milk left");
            for (int i = 0; i < FIRST_OUTPUT; i++) {
                helper.assertTrue(table.getItem(i).isEmpty(), "input slot " + i + " not used up");
            }
            helper.assertValueEqual(table.getStoredEnergy(), 0L, "energy kept after crafting");
            helper.assertFalse(table.requiresLaserEnergy(), "a table with nothing to craft wants energy");
        });
    }

    /**
     * The template, inputs and energy are saved; breaking the table drops the inputs, not the template; the Off action
     * stops crafting and On starts it again; and the table's own recipe (OWO/OCO/ORO) crafts it.
     */
    static void saveActionsAndRecipe(GameTestHelper helper) {
        RegistryAccess registryAccess = helper.getLevel().registryAccess();
        List<ItemStack> items = new ArrayList<>();
        Item[] pattern = {
                Items.OBSIDIAN, Items.CRAFTING_TABLE, Items.OBSIDIAN,
                Items.OBSIDIAN, Items.CHEST, Items.OBSIDIAN,
                Items.OBSIDIAN, SiliconRegistry.getChipset(0), Items.OBSIDIAN};
        for (Item item : pattern) {
            items.add(new ItemStack(item));
        }
        ItemStack crafted = helper.getLevel().recipeAccess()
                .getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(3, 3, items), helper.getLevel())
                .map(holder -> holder.value().assemble(CraftingInput.of(3, 3, items))).orElse(ItemStack.EMPTY);
        helper.assertTrue(crafted.is(SiliconRegistry.advancedCraftingTableBlockItem.get()),
                "OWO/OCO/ORO does not make the table: " + crafted);

        TileAdvancedCraftingTable table = placeTable(helper);
        table.getCraftingSlots().setItem(0, new ItemStack(Items.OAK_PLANKS));
        table.getCraftingSlots().setItem(3, new ItemStack(Items.OAK_PLANKS));
        table.setItem(0, new ItemStack(Items.OAK_PLANKS, 2));
        table.receiveLaserEnergy(120);

        CompoundTag tag = table.saveWithFullMetadata(registryAccess);
        helper.setBlock(TABLE, Blocks.AIR);
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(helper.absolutePos(TABLE)).inflate(2)).stream()
                .mapToInt(entity -> entity.getItem().is(Items.OAK_PLANKS) ? entity.getItem().getCount() : 0).sum() == 2,
                "breaking the table did not drop exactly its two input planks");
        helper.killAllEntitiesOfClass(ItemEntity.class);

        TileAdvancedCraftingTable reloaded = placeTable(helper);
        reloaded.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registryAccess, tag));
        helper.assertTrue(reloaded.getCraftingSlots().getItem(3).is(Items.OAK_PLANKS), "template not loaded");
        helper.assertValueEqual(reloaded.getItem(0).getCount(), 2, "inputs loaded");
        helper.assertValueEqual(reloaded.getStoredEnergy(), 120L, "energy loaded");

        reloaded.actionActivated(CoreRegistry.actionOff.get());
        reloaded.receiveLaserEnergy(5000);
        helper.runAfterDelay(5, () -> {
            helper.assertValueEqual(count(reloaded, Items.STICK), 0, "crafted while switched off");
            helper.assertFalse(reloaded.requiresLaserEnergy(), "a switched-off table wants energy");
            helper.assertTrue(reloaded.allowAction(CoreRegistry.actionOn.get()), "the On action is not allowed");

            reloaded.actionActivated(CoreRegistry.actionOn.get());
            helper.succeedWhen(() -> helper.assertValueEqual(count(reloaded, Items.STICK), 4, "sticks after On"));
        });
    }

    private static TileAdvancedCraftingTable placeTable(GameTestHelper helper) {
        helper.setBlock(TABLE, SiliconRegistry.advancedCraftingTableBlock.get());
        return helper.getBlockEntity(TABLE, TileAdvancedCraftingTable.class);
    }

    private static void charge(GameTestHelper helper) {
        EnergyHandler handler = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(LASER),
                Direction.DOWN);
        if (handler == null)
            return;
        try (Transaction tx = Transaction.openRoot()) {
            handler.insert(1500, tx);
            tx.commit();
        }
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
