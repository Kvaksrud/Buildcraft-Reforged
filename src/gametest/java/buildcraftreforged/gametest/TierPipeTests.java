package buildcraftreforged.gametest;

import buildcraftreforged.api.transport.IPipeTile.PipeType;
import buildcraftreforged.core.BuildCraftConfiguration;
import buildcraftreforged.transport.BlockPipe;
import buildcraftreforged.transport.PipeDefinition;
import buildcraftreforged.transport.PipeMaterial;
import buildcraftreforged.transport.PipeTierStats;
import buildcraftreforged.transport.TransportRegistry;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * #53: all 30 tier pipes exist, are craftable from their recipes (docs/TRANSPORT.md 2.1, Q17) and report the
 * configured caps.
 */
final class TierPipeTests {
    /** One item of each recipe material: the tag entries use a vanilla member (oak planks, smooth stone). */
    private static final Map<PipeMaterial, Item> MATERIAL_ITEMS = Map.of(
            PipeMaterial.WOOD, Items.OAK_PLANKS,
            PipeMaterial.STONE, Items.STONE,
            PipeMaterial.IRON, Items.IRON_INGOT,
            PipeMaterial.COPPER, Items.COPPER_INGOT,
            PipeMaterial.GOLD, Items.GOLD_INGOT,
            PipeMaterial.QUARTZ, Items.QUARTZ_BLOCK,
            PipeMaterial.DIAMOND, Items.DIAMOND,
            PipeMaterial.EMERALD, Items.EMERALD,
            PipeMaterial.NETHERITE, Items.NETHERITE_INGOT,
            PipeMaterial.ENDER, Items.ENDER_PEARL);

    private TierPipeTests() {
    }

    private static Item pipeItem(PipeType kind, PipeMaterial material) {
        return TransportRegistry.getPipeItem(new PipeDefinition(kind, material)).get();
    }

    private static void assertCrafts(GameTestHelper helper, int width, int height, List<Item> grid, Item result,
            int count) {
        ServerLevel level = helper.getLevel();
        CraftingInput input = CraftingInput.of(width, height, grid.stream().map(ItemStack::new).toList());
        RecipeHolder<CraftingRecipe> recipe = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level)
                .orElse(null);
        helper.assertTrue(recipe != null, "no recipe for " + grid);
        ItemStack crafted = recipe.value().assemble(input);
        helper.assertTrue(crafted.is(result) && crafted.getCount() == count,
                grid + " crafts " + crafted + ", expected " + count + " " + result);
    }

    static void craftable(GameTestHelper helper) {
        helper.assertTrue(TransportRegistry.PIPE_DEFINITIONS.size() == 30,
                TransportRegistry.PIPE_DEFINITIONS.size() + " pipes registered");
        Item sealant = TransportRegistry.pipeWaterproof.get();
        assertCrafts(helper, 1, 1, List.of(Items.DYE.pick(DyeColor.GREEN)), sealant, 1);

        for (PipeMaterial material : PipeMaterial.values()) {
            Item items = pipeItem(PipeType.ITEM, material);
            Item fluids = pipeItem(PipeType.FLUID, material);
            Item power = pipeItem(PipeType.POWER, material);
            Item ingredient = MATERIAL_ITEMS.get(material);

            assertCrafts(helper, 3, 1, List.of(ingredient, Items.GLASS, ingredient), items, 8);
            assertCrafts(helper, 2, 1, List.of(sealant, items), fluids, 1);
            assertCrafts(helper, 2, 1, List.of(items, Items.REDSTONE), power, 1);
            assertCrafts(helper, 1, 1, List.of(fluids), items, 1);
            assertCrafts(helper, 1, 1, List.of(power), items, 1);
        }
        helper.succeed();
    }

    static void caps(GameTestHelper helper) {
        helper.assertTrue(BuildCraftConfiguration.COMMON_SPEC.isLoaded(), "common config not loaded");
        BlockPos pos = new BlockPos(1, 1, 1);
        for (PipeDefinition definition : TransportRegistry.PIPE_DEFINITIONS) {
            BlockPipe block = TransportRegistry.getPipe(definition.kind(), definition.material());
            PipeTierStats configured = BuildCraftConfiguration.pipeTiers.get(definition.material());
            helper.assertTrue(block.getTierStats().equals(configured),
                    definition.registryName() + " reports " + block.getTierStats() + ", config says " + configured);
            helper.assertTrue(block.getTier() == definition.material().tier, definition.registryName() + " tier");

            helper.setBlock(pos, block);
            helper.assertTrue(helper.getBlockState(pos).getBlock() == block, definition.registryName() + " not placed");
        }
        helper.succeed();
    }
}
