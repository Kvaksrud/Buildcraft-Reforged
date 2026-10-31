package buildcraftreforged.gametest;

import buildcraftreforged.api.transport.IPipeTile.PipeType;
import buildcraftreforged.transport.PipeMaterial;
import buildcraftreforged.transport.PipeModuleType;
import buildcraftreforged.transport.TileFilteredBuffer;
import buildcraftreforged.transport.TilePipe;
import buildcraftreforged.transport.TransportRegistry;
import buildcraftreforged.transport.gui.ContainerFilteredBuffer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * #62 Filtered Buffer: nine filter slots over nine buffer slots; a buffer slot takes only the item of the filter in its
 * column (any damage for a damageable item), from pipes, hoppers and the GUI alike, and gives everything out on every
 * face. In the padded area.
 */
final class FilteredBufferTests {
    static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();
    static final int MAX_TICKS = 400;
    private static final int Y = 2;

    static {
        TESTS.put("filtered_buffer_filters_insert", FilteredBufferTests::filtersInsert);
        TESTS.put("filtered_buffer_pipe_extracts", FilteredBufferTests::pipeExtracts);
        TESTS.put("filtered_buffer_menu_save_and_recipe", FilteredBufferTests::menuSaveAndRecipe);
    }

    private FilteredBufferTests() {
    }

    private static TileFilteredBuffer buffer(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, TransportRegistry.filteredBuffer.get());
        return helper.getBlockEntity(pos, TileFilteredBuffer.class);
    }

    private static Container chest(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, Blocks.CHEST);
        return (Container) helper.getBlockEntity(pos, BlockEntity.class);
    }

    /** Gold item pipes from z = from to z = to on row x, with an Extractor on the first. */
    private static void extractorLine(GameTestHelper helper, int x, int from, int to) {
        for (int z = from; z <= to; z++) {
            helper.setBlock(new BlockPos(x, Y, z), TransportRegistry.getPipe(PipeType.ITEM, PipeMaterial.GOLD));
        }
        helper.getBlockEntity(new BlockPos(x, Y, from), TilePipe.class).setModuleType(PipeModuleType.EXTRACTOR);
    }

    private static int count(Container inv, Item item) {
        int count = 0;
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            if (inv.getItem(slot).is(item))
                count += inv.getItem(slot).getCount();
        }
        return count;
    }

    private static int insert(ResourceHandler<ItemResource> handler, ItemStack stack) {
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = ResourceHandlerUtil.insertStacking(handler, ItemResource.of(stack), stack.getCount(), transaction);
            transaction.commit();
            return inserted;
        }
    }

    /**
     * A chest of cobblestone, dirt and a worn iron pickaxe feeds the buffer through an Extractor line, and a vanilla
     * hopper on top feeds it cobblestone and dirt. Only the filtered items go in, each into its own column; the dirt
     * stays where it was. Direct inserts through the capability follow the same rule, including the 64 cap.
     */
    static void filtersInsert(GameTestHelper helper) {
        Container source = chest(helper, new BlockPos(0, Y, 0));
        extractorLine(helper, 0, 1, 3);
        BlockPos bufferPos = new BlockPos(0, Y, 4);
        TileFilteredBuffer buffer = buffer(helper, bufferPos);
        buffer.getFilters().setItem(0, new ItemStack(Items.COBBLESTONE));
        buffer.getFilters().setItem(2, new ItemStack(Items.IRON_PICKAXE));
        ItemStack worn = new ItemStack(Items.IRON_PICKAXE);
        worn.setDamageValue(50);
        source.setItem(0, new ItemStack(Items.COBBLESTONE, 10));
        source.setItem(1, new ItemStack(Items.DIRT, 6));
        source.setItem(2, worn);

        helper.setBlock(bufferPos.above(), Blocks.HOPPER);
        Container hopper = (Container) helper.getBlockEntity(bufferPos.above(), BlockEntity.class);
        hopper.setItem(0, new ItemStack(Items.COBBLESTONE, 4));
        hopper.setItem(1, new ItemStack(Items.DIRT, 2));

        // The rule itself: same item; any damage for a damageable item; nothing without a filter.
        helper.assertTrue(buffer.canPlaceItem(2, worn), "a worn pickaxe under a new pickaxe filter");
        helper.assertFalse(buffer.canPlaceItem(1, new ItemStack(Items.COBBLESTONE)), "cobblestone under no filter");
        helper.assertFalse(buffer.canPlaceItem(2, new ItemStack(Items.COBBLESTONE)), "cobblestone under a pickaxe filter");

        // The capability on a side face: stone has no column, sand gets one and goes only there.
        ResourceHandler<ItemResource> side = helper.getLevel().getCapability(Capabilities.Item.BLOCK,
                helper.absolutePos(bufferPos), Direction.EAST);
        helper.assertTrue(side != null, "no item capability on the buffer's side");
        helper.assertValueEqual(insert(side, new ItemStack(Items.SAND, 3)), 0, "sand with no sand filter");
        buffer.getFilters().setItem(5, new ItemStack(Items.SAND));
        helper.assertValueEqual(insert(side, new ItemStack(Items.SAND, 70)), 64, "sand into its column");
        helper.assertValueEqual(buffer.getItem(5).getCount(), 64, "sand in column 5");

        helper.succeedWhen(() -> {
            helper.assertValueEqual(buffer.getItem(0).getCount(), 14, "cobblestone in column 0");
            helper.assertTrue(buffer.getItem(2).is(Items.IRON_PICKAXE), "the pickaxe is not in column 2");
            helper.assertValueEqual(buffer.getItem(2).getDamageValue(), 50, "pickaxe damage");
            helper.assertValueEqual(count(buffer, Items.DIRT), 0, "dirt in the buffer");
            helper.assertValueEqual(count(source, Items.DIRT), 6, "dirt left in the chest");
            helper.assertValueEqual(count(hopper, Items.DIRT), 2, "dirt left in the hopper");
            helper.assertValueEqual(count(source, Items.COBBLESTONE) + count(hopper, Items.COBBLESTONE), 0,
                    "cobblestone left outside");
            helper.assertEntityNotPresent(EntityTypes.ITEM);
        });
    }

    /**
     * An Extractor line pulls everything out of a buffer into a chest; a vanilla hopper under a second buffer pulls
     * its contents out of the bottom face. The filters stay.
     */
    static void pipeExtracts(GameTestHelper helper) {
        TileFilteredBuffer buffer = buffer(helper, new BlockPos(0, Y, 0));
        extractorLine(helper, 0, 1, 3);
        Container target = chest(helper, new BlockPos(0, Y, 4));
        buffer.getFilters().setItem(0, new ItemStack(Items.COBBLESTONE));
        buffer.getFilters().setItem(8, new ItemStack(Items.DIAMOND));
        buffer.setItem(0, new ItemStack(Items.COBBLESTONE, 12));
        buffer.setItem(8, new ItemStack(Items.DIAMOND, 5));

        TileFilteredBuffer upper = buffer(helper, new BlockPos(2, Y + 1, 0));
        helper.setBlock(new BlockPos(2, Y, 0), Blocks.HOPPER);
        Container hopper = (Container) helper.getBlockEntity(new BlockPos(2, Y, 0), BlockEntity.class);
        upper.getFilters().setItem(4, new ItemStack(Items.REDSTONE));
        upper.setItem(4, new ItemStack(Items.REDSTONE, 3));

        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(target, Items.COBBLESTONE), 12, "cobblestone in the chest");
            helper.assertValueEqual(count(target, Items.DIAMOND), 5, "diamonds in the chest");
            helper.assertTrue(buffer.isEmpty(), "items left in the buffer");
            helper.assertValueEqual(count(hopper, Items.REDSTONE), 3, "redstone in the hopper");
            helper.assertTrue(upper.isEmpty(), "items left in the upper buffer");
            helper.assertTrue(buffer.getFilters().getItem(0).is(Items.COBBLESTONE)
                    && buffer.getFilters().getItem(8).is(Items.DIAMOND), "the filters changed");
            helper.assertEntityNotPresent(EntityTypes.ITEM);
        });
    }

    /**
     * The menu: 4.2.2's slot positions; a filter cannot be cleared while its column holds items; shift-click puts an
     * item only into its column; save and load of both inventories; the recipe and registry name.
     */
    static void menuSaveAndRecipe(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, Y, 0);
        TileFilteredBuffer buffer = buffer(helper, pos);
        ServerLevel level = helper.getLevel();
        buffer.getFilters().setItem(0, new ItemStack(Items.COBBLESTONE));
        buffer.getFilters().setItem(1, new ItemStack(Items.DIRT));
        buffer.setItem(0, new ItemStack(Items.COBBLESTONE, 2));

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.snapTo(Vec3.atBottomCenterOf(helper.absolutePos(pos.above())));
        // A mock player has no mod channel, so open the menu on the server side only (see GuiTests.sampleMenu).
        if (!(buffer.createMenu(1, player.getInventory(), player) instanceof ContainerFilteredBuffer menu)) {
            throw helper.assertionException("the buffer made no ContainerFilteredBuffer");
        }
        helper.assertValueEqual(menu.getType(), TransportRegistry.filteredBufferMenu.get(), "menu type");
        helper.assertValueEqual(menu.slots.size(), 18 + 27 + 9, "slots");
        helper.assertValueEqual(menu.slots.get(0).x + "," + menu.slots.get(0).y, "8,27", "first filter slot");
        helper.assertValueEqual(menu.slots.get(1).x + "," + menu.slots.get(1).y, "8,61", "first buffer slot");
        helper.assertValueEqual(menu.slots.get(17).x + "," + menu.slots.get(17).y, "152,61", "last buffer slot");
        helper.assertValueEqual(menu.slots.get(18).x + "," + menu.slots.get(18).y, "8,86", "first inventory slot");
        helper.assertValueEqual(menu.slots.get(45).x + "," + menu.slots.get(45).y, "8,144", "first hotbar slot");
        helper.assertTrue(menu.stillValid(player), "the menu is not valid for a player next to the buffer");

        // Middle click clears a filter only when its column is empty (SlotPhantomLockable).
        menu.clicked(0, 2, ContainerInput.PICKUP, player);
        helper.assertTrue(buffer.getFilters().getItem(0).is(Items.COBBLESTONE), "a locked filter was cleared");
        menu.clicked(2, 2, ContainerInput.PICKUP, player);
        helper.assertTrue(buffer.getFilters().getItem(1).isEmpty(), "an unlocked filter was not cleared");
        // A filter holds one item, whatever is clicked into it.
        player.containerMenu = menu;
        menu.setCarried(new ItemStack(Items.SAND, 10));
        menu.clicked(4, 0, ContainerInput.PICKUP, player);
        helper.assertValueEqual(buffer.getFilters().getItem(2).getCount(), 1, "sand filter count");
        helper.assertValueEqual(menu.getCarried().getCount(), 10, "the held sand changed");
        menu.setCarried(ItemStack.EMPTY);

        // Shift-click: cobblestone goes to column 0, gravel (no filter) stays in the player's inventory.
        player.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 5));
        player.getInventory().setItem(1, new ItemStack(Items.GRAVEL, 5));
        menu.quickMoveStack(player, 45);
        menu.quickMoveStack(player, 46);
        helper.assertValueEqual(buffer.getItem(0).getCount(), 7, "cobblestone shift-clicked into column 0");
        helper.assertValueEqual(count(buffer, Items.GRAVEL), 0, "gravel shift-clicked into the buffer");
        helper.assertFalse(menu.slots.get(3).mayPlace(new ItemStack(Items.COBBLESTONE)), "cobblestone fits column 1");
        menu.removed(player);
        player.containerMenu = player.inventoryMenu;

        CompoundTag tag = buffer.saveWithFullMetadata(level.registryAccess());
        TileFilteredBuffer copy = new TileFilteredBuffer(buffer.getBlockPos(), buffer.getBlockState());
        copy.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
        helper.assertValueEqual(copy.getItem(0).getCount(), 7, "saved cobblestone");
        helper.assertTrue(copy.getFilters().getItem(0).is(Items.COBBLESTONE), "saved cobblestone filter");
        helper.assertTrue(copy.getFilters().getItem(2).is(Items.SAND), "saved sand filter");
        helper.assertTrue(copy.getFilters().getItem(1).isEmpty(), "saved empty filter");

        Item planks = Items.OAK_PLANKS;
        Item pipe = TransportRegistry.getPipe(PipeType.ITEM, PipeMaterial.DIAMOND).asItem();
        assertCrafts(helper, List.of(planks, pipe, planks, planks, Items.CHEST, planks, planks, Items.PISTON, planks),
                TransportRegistry.filteredBufferItem.get());
        helper.assertValueEqual(BuiltInRegistries.BLOCK.getKey(TransportRegistry.filteredBuffer.get()).getPath(),
                "filtered_buffer_block", "buffer id");

        // Breaking the buffer drops the stored items, not the filters (phantom copies).
        player.discard();
        helper.destroyBlock(pos);
        helper.succeedWhen(() -> {
            helper.assertItemEntityCountIs(Items.COBBLESTONE, pos, 2.0, 7);
            helper.assertItemEntityNotPresent(Items.SAND, pos, 2.0);
        });
    }

    private static void assertCrafts(GameTestHelper helper, List<Item> grid, Item result) {
        ServerLevel level = helper.getLevel();
        CraftingInput input = CraftingInput.of(3, 3, grid.stream().map(ItemStack::new).toList());
        RecipeHolder<CraftingRecipe> recipe = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level).orElse(null);
        helper.assertTrue(recipe != null, "no recipe for " + grid);
        ItemStack crafted = recipe.value().assemble(input);
        helper.assertTrue(crafted.is(result) && crafted.getCount() == 1, grid + " crafts " + crafted + ", expected " + result);
    }
}
