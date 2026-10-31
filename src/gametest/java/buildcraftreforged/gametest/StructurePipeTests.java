package buildcraftreforged.gametest;

import buildcraftreforged.api.transport.IPipe.WireColor;
import buildcraftreforged.api.transport.IPipeTile.PipeType;
import buildcraftreforged.transport.BlockPipe;
import buildcraftreforged.transport.Gate.GateKind;
import buildcraftreforged.transport.PipeConnections;
import buildcraftreforged.transport.PipeDefinition;
import buildcraftreforged.transport.PipeMaterial;
import buildcraftreforged.transport.PipeTierStats;
import buildcraftreforged.transport.TilePipe;
import buildcraftreforged.transport.TransportRegistry;
import buildcraftreforged.transport.graph.PipeNetwork;
import buildcraftreforged.transport.graph.PipeNetworks;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * #54: the Cobblestone Structure Pipe (docs/TRANSPORT.md 11). It connects only to other structure pipes, never to an
 * item, fluid or power pipe or an inventory; it joins no network; it holds a gate, wire, a facade and a plug; its wire
 * reaches any wired neighbour; and it crafts from gravel and a stone transport pipe (Q17). In the padded area (see
 * {@link OilFuelTests#PADDING}).
 */
final class StructurePipeTests {
    static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();
    static final int MAX_TICKS = 60;

    static {
        TESTS.put("structure_pipe_connects_only_to_structure", StructurePipeTests::connectsOnlyToStructure);
        TESTS.put("structure_pipe_holds_pluggables", StructurePipeTests::holdsPluggables);
    }

    private static final int SETTLE = 3;

    private StructurePipeTests() {
    }

    private static TilePipe tile(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos, TilePipe.class);
    }

    private static void assertMask(GameTestHelper helper, BlockPos pos, String what, Direction... sides) {
        int expected = 0;
        for (Direction side : sides)
            expected |= PipeConnections.bit(side);
        int actual = tile(helper, pos).getConnectionMask();
        helper.assertTrue(actual == expected, what + ": mask " + Integer.toBinaryString(actual) + ", expected "
                + Integer.toBinaryString(expected));
    }

    /**
     * Chest, structure, structure, stone item pipe in a row along x, with a fluid pipe and a power pipe on top of the
     * second structure pipe and a structure pipe under it: the structure pipes connect to each other only, the item
     * pipe's network is that pipe alone, and no network starts at a structure pipe. The recipe gives one.
     */
    static void connectsOnlyToStructure(GameTestHelper helper) {
        BlockPos chest = new BlockPos(1, 2, 2);
        BlockPos first = chest.east();
        BlockPos second = first.east();
        BlockPos itemPipe = second.east();
        BlockPos below = second.below();
        BlockPos fluidPipe = second.above();
        BlockPos powerPipe = second.south();
        helper.setBlock(chest, Blocks.CHEST);
        helper.setBlock(first, TransportRegistry.pipeStructureCobblestone.get());
        helper.setBlock(second, TransportRegistry.pipeStructureCobblestone.get());
        helper.setBlock(below, TransportRegistry.pipeStructureCobblestone.get());
        helper.setBlock(itemPipe, TransportRegistry.getPipe(PipeType.ITEM, PipeMaterial.STONE));
        helper.setBlock(fluidPipe, TransportRegistry.getPipe(PipeType.FLUID, PipeMaterial.STONE));
        helper.setBlock(powerPipe, TransportRegistry.getPipe(PipeType.POWER, PipeMaterial.STONE));

        helper.runAfterDelay(SETTLE, () -> {
            BlockPipe block = TransportRegistry.pipeStructureCobblestone.get();
            helper.assertTrue(block.kind == PipeType.STRUCTURE && tile(helper, first).kind == PipeType.STRUCTURE,
                    "structure kind");
            helper.assertTrue(block.getTier() == 0 && block.getTierStats().equals(PipeTierStats.NONE),
                    "a structure pipe has no tier");
            helper.assertValueEqual(TransportRegistry.pipeStructureCobblestoneItem.get().getName(
                    new ItemStack(block)).getString(), "Cobblestone Structure Pipe", "name");

            assertMask(helper, first, "first structure pipe: not the chest", Direction.EAST);
            assertMask(helper, second, "second structure pipe: structure pipes only", Direction.WEST, Direction.DOWN);
            assertMask(helper, below, "structure pipe below", Direction.UP);
            assertMask(helper, itemPipe, "item pipe: not the structure pipe");
            assertMask(helper, fluidPipe, "fluid pipe: not the structure pipe");
            assertMask(helper, powerPipe, "power pipe: not the structure pipe");

            ServerLevel level = helper.getLevel();
            for (BlockPos structure : List.of(first, second, below)) {
                helper.assertTrue(PipeNetworks.get(level, helper.absolutePos(structure)) == null,
                        "a network at the structure pipe " + structure);
            }
            PipeNetwork items = PipeNetworks.get(level, helper.absolutePos(itemPipe));
            helper.assertTrue(items != null && items.size() == 1, "the item pipe's network: " + items);

            Item stoneItemPipe = TransportRegistry.getPipeItem(new PipeDefinition(PipeType.ITEM, PipeMaterial.STONE)).get();
            CraftingInput input = CraftingInput.of(2, 1, List.of(new ItemStack(Items.GRAVEL), new ItemStack(stoneItemPipe)));
            RecipeHolder<CraftingRecipe> recipe = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level)
                    .orElse(null);
            helper.assertTrue(recipe != null, "no Structure Pipe recipe");
            ItemStack crafted = recipe.value().assemble(input);
            helper.assertTrue(crafted.is(TransportRegistry.pipeStructureCobblestoneItem.get()) && crafted.getCount() == 1,
                    "gravel + stone pipe crafts " + crafted);
            helper.succeed();
        });
    }

    /**
     * A gate, red wire, a facade and a plug go on a structure pipe; the plug cuts its connection to the next structure
     * pipe; red wire reaches a wired item pipe beside it although the two pipes do not connect, while two item pipes
     * of different kinds side by side do not share wire. The structure pipe offers no Pipe Contents triggers.
     */
    static void holdsPluggables(GameTestHelper helper) {
        BlockPos structure = new BlockPos(2, 2, 2);
        BlockPos other = structure.west();
        BlockPos itemPipe = structure.east();
        BlockPos fluidPipe = itemPipe.east();
        helper.setBlock(structure, TransportRegistry.pipeStructureCobblestone.get());
        helper.setBlock(other, TransportRegistry.pipeStructureCobblestone.get());
        helper.setBlock(itemPipe, TransportRegistry.getPipe(PipeType.ITEM, PipeMaterial.STONE));
        helper.setBlock(fluidPipe, TransportRegistry.getPipe(PipeType.FLUID, PipeMaterial.STONE));

        helper.runAfterDelay(SETTLE, () -> {
            assertMask(helper, structure, "before the plug", Direction.WEST);
            TilePipe pipe = tile(helper, structure);

            pipe.gateHolder.addOrStripGate(null, new ItemStack(TransportRegistry.getGateItem(GateKind.AND_2, false)),
                    Vec3.ZERO);
            helper.assertTrue(pipe.gateHolder.hasGate(), "no gate on the structure pipe");
            List<?> triggers = buildcraftreforged.api.gates.ActionManager.getPipeTriggers(pipe.gateHolder);
            helper.assertFalse(triggers.contains(TransportRegistry.triggerPipeEmpty.get())
                    || triggers.contains(TransportRegistry.triggerPipeItems.get()), "Pipe Contents on a structure pipe");

            for (BlockPos wired : List.of(structure, itemPipe, fluidPipe)) {
                tile(helper, wired).gateHolder.addOrStripWire(null,
                        new ItemStack(TransportRegistry.getPipeWire(WireColor.Red)), WireColor.Red);
            }
            helper.assertTrue(pipe.gateHolder.isWired(WireColor.Red), "no wire on the structure pipe");
            helper.assertTrue(pipe.gateHolder.isWireConnectedTo(tile(helper, itemPipe), WireColor.Red),
                    "structure wire does not reach the item pipe");
            helper.assertFalse(tile(helper, itemPipe).gateHolder.isWireConnectedTo(tile(helper, fluidPipe), WireColor.Red),
                    "wire between an item and a fluid pipe");

            helper.assertTrue(pipe.addFacade(Direction.UP, Blocks.STONE.defaultBlockState()), "facade not fitted");
            helper.assertTrue(pipe.isSolidOnSide(Direction.UP), "the facade side is not solid");
            helper.assertTrue(pipe.addPlug(Direction.WEST), "plug not fitted");

            helper.runAfterDelay(SETTLE, () -> {
                assertMask(helper, structure, "the plug cuts the structure connection");
                assertMask(helper, other, "on both pipes");
                helper.assertTrue(pipe.hasFacade(Direction.UP) && pipe.hasPlug(Direction.WEST), "pluggables kept");
                helper.succeed();
            });
        });
    }
}
