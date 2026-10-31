package buildcraftreforged.gametest;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.api.recipes.AssemblyRecipe;
import buildcraftreforged.silicon.AssemblyRecipeManager;
import buildcraftreforged.silicon.BlockLaser;
import buildcraftreforged.silicon.SiliconRegistry;
import buildcraftreforged.silicon.TileAssemblyTable;
import buildcraftreforged.transport.FacadeAssemblyRecipe;
import buildcraftreforged.transport.ItemFacade;
import buildcraftreforged.transport.TransportRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * #144: the Assembly Table recipes for the Pipe Plug (8 from 1 Cobblestone Structure Pipe, 10 000 FE) and for
 * facades (6 of a block from 3 Cobblestone Structure Pipes and the block, 80 000 FE, one recipe per facade with a
 * recipe), as 4.2.2 {@code BuildCraftTransport.postInit} and {@code ItemFacade.addFacade}. The recipe list checked here
 * is the one JEI's Assembly Table category shows ({@code AssemblyRecipeManager.getRecipes}). In the padded area.
 */
final class PlugFacadeRecipeTests {
    static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();
    static final int MAX_TICKS = 1200;

    static {
        TESTS.put("plug_facade_recipes_listed", PlugFacadeRecipeTests::recipesListed);
        TESTS.put("plug_facade_laser_crafting", PlugFacadeRecipeTests::laserCrafting);
    }

    private static final BlockPos TABLE = new BlockPos(2, 3, 2);

    private PlugFacadeRecipeTests() {
    }

    private static Item structurePipe() {
        return TransportRegistry.pipeStructureCobblestoneItem.get();
    }

    private static ItemStack facade(BlockState state, int count) {
        return ItemFacade.getStack(state).copyWithCount(count);
    }

    /** How many of {@code input}'s ItemStack entries are {@code item}, and their total count. */
    private static int countOf(AssemblyRecipe recipe, Item item) {
        int count = 0;
        for (Object input : recipe.input) {
            if (input instanceof ItemStack stack && stack.is(item))
                count += stack.getCount();
        }
        return count;
    }

    /**
     * Plug: found by its output, 10 000 FE, one Structure Pipe, 8 plugs. Facades: one JSON recipe of the facade type
     * that becomes one table recipe per listed facade state except the turned logs, each 80 000 FE, 3 Structure Pipes
     * and the block, 6 facades carrying the state.
     */
    static void recipesListed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AssemblyRecipe plug = AssemblyRecipeManager.findRecipe(level, new ItemStack(TransportRegistry.plugItem.get()));
        if (plug == null)
            throw helper.assertionException("no plug recipe");
        helper.assertValueEqual(plug.energy, 10000F, "plug energy");
        helper.assertValueEqual(plug.output.getCount(), 8, "plugs per craft");
        helper.assertValueEqual(plug.input.length, 1, "plug inputs");
        helper.assertValueEqual(countOf(plug, structurePipe()), 1, "Structure Pipes per plug batch");

        Identifier id = Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "assembly/pipe_facade");
        helper.assertTrue(level.getServer().getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, id))
                .filter(holder -> holder.value() instanceof FacadeAssemblyRecipe).isPresent(),
                "the facade recipe file did not load as its own type");

        List<AssemblyRecipe> all = AssemblyRecipeManager.getRecipes(level);
        long facadeRecipes = all.stream().filter(r -> r.output.is(TransportRegistry.facadeItem.get())).count();
        long withRecipe = ItemFacade.getAllFacades().stream()
                .filter(stack -> FacadeAssemblyRecipe.hasRecipe(ItemFacade.getBlockState(stack))
                        && ItemFacade.getBlockState(stack).getBlock().asItem() != Items.AIR)
                .count();
        helper.assertTrue(withRecipe > 100, "only " + withRecipe + " facades with a recipe");
        helper.assertValueEqual(facadeRecipes, withRecipe, "one facade recipe per facade");

        for (BlockState state : List.of(Blocks.STONE.defaultBlockState(), Blocks.GLASS.defaultBlockState(),
                Blocks.OAK_LOG.defaultBlockState())) {
            AssemblyRecipe recipe = AssemblyRecipeManager.findRecipe(level, facade(state, 1));
            if (recipe == null)
                throw helper.assertionException("no facade recipe for " + state);
            helper.assertValueEqual(recipe.energy, 80000F, state + " facade energy");
            helper.assertValueEqual(recipe.output.getCount(), 6, state + " facades per craft");
            helper.assertValueEqual(ItemFacade.getBlockState(recipe.output), state, "facade state");
            helper.assertValueEqual(countOf(recipe, structurePipe()), 3, state + " Structure Pipes");
            helper.assertValueEqual(countOf(recipe, state.getBlock().asItem()), 1, state + " block");
            helper.assertValueEqual(recipe.input.length, 2, state + " inputs");
        }
        BlockState sideways = Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X);
        helper.assertTrue(ItemFacade.isFacadeState(sideways), "the sideways log facade is not listed");
        helper.assertTrue(AssemblyRecipeManager.findRecipe(level, facade(sideways, 1)) == null,
                "the sideways log facade has a recipe of its own");
        helper.succeed();
    }

    /**
     * Four Structure Pipes and a stone block on a table under nine charged lasers: with the plug and the stone facade
     * planned, the table makes 8 plugs and 6 stone facades into the chest beside it and uses all five items.
     */
    static void laserCrafting(GameTestHelper helper) {
        helper.setBlock(TABLE, SiliconRegistry.assemblyTableBlock.get());
        TileAssemblyTable table = helper.getBlockEntity(TABLE, TileAssemblyTable.class);
        helper.setBlock(TABLE.east(), Blocks.CHEST);
        Container chest = helper.getBlockEntity(TABLE.east(), ChestBlockEntity.class);
        table.setItem(0, new ItemStack(structurePipe(), 4));
        table.setItem(1, new ItemStack(Items.STONE));

        BlockState stone = Blocks.STONE.defaultBlockState();
        AssemblyRecipe plug = AssemblyRecipeManager.findRecipe(helper.getLevel(), new ItemStack(TransportRegistry.plugItem.get()));
        AssemblyRecipe stoneFacade = AssemblyRecipeManager.findRecipe(helper.getLevel(), facade(stone, 1));
        if (plug == null || stoneFacade == null)
            throw helper.assertionException("plug or stone facade recipe missing");
        List<AssemblyRecipe> offered = table.getPotentialOutputs();
        helper.assertTrue(offered.contains(plug) && offered.contains(stoneFacade),
                "Structure Pipes and stone do not offer the plug and the stone facade");
        helper.assertFalse(offered.stream().anyMatch(r -> r.output.is(TransportRegistry.facadeItem.get())
                && r != stoneFacade), "another facade is offered");
        table.planOutput(plug);
        table.planOutput(stoneFacade);

        List<BlockPos> lasers = new ArrayList<>();
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                BlockPos pos = new BlockPos(x, 1, z);
                helper.setBlock(pos, SiliconRegistry.laserBlock.get().defaultBlockState()
                        .setValue(BlockLaser.FACING, Direction.UP));
                lasers.add(pos);
            }
        }
        helper.onEachTick(() -> {
            for (BlockPos pos : lasers) {
                EnergyHandler handler = helper.getLevel().getCapability(Capabilities.Energy.BLOCK,
                        helper.absolutePos(pos), Direction.DOWN);
                if (handler == null)
                    continue;
                try (Transaction tx = Transaction.openRoot()) {
                    handler.insert(1500, tx);
                    tx.commit();
                }
            }
        });

        helper.succeedWhen(() -> {
            int plugs = 0;
            int facades = 0;
            for (int i = 0; i < chest.getContainerSize(); i++) {
                ItemStack stack = chest.getItem(i);
                if (stack.is(TransportRegistry.plugItem.get()))
                    plugs += stack.getCount();
                if (stack.is(TransportRegistry.facadeItem.get()) && stone.equals(ItemFacade.getBlockState(stack)))
                    facades += stack.getCount();
            }
            helper.assertValueEqual(plugs, 8, "plugs in the chest");
            helper.assertValueEqual(facades, 6, "stone facades in the chest");
            helper.assertTrue(table.getItem(0).isEmpty() && table.getItem(1).isEmpty(), "inputs left on the table");
        });
    }
}
