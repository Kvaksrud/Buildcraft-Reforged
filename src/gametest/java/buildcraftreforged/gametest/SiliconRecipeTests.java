package buildcraftreforged.gametest;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.api.recipes.AssemblyRecipe;
import buildcraftreforged.core.CoreRegistry;
import buildcraftreforged.silicon.AssemblyRecipeManager;
import buildcraftreforged.silicon.AssemblyTableRecipe;
import buildcraftreforged.silicon.BlockLaser;
import buildcraftreforged.silicon.SiliconRegistry;
import buildcraftreforged.silicon.TileAssemblyTable;
import buildcraftreforged.silicon.TileLaser;
import buildcraftreforged.transport.Gate.GateKind;
import buildcraftreforged.transport.GateSettings;
import buildcraftreforged.transport.PipeModuleType;
import buildcraftreforged.transport.TransportRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * #74: the chipsets and the shipped silicon recipes. Every 4.2.2 assembly recipe (BuildCraftSilicon.loadRecipes and
 * BuildCraftTransport's pipe wire, MJ × 10 = FE) and the ten module recipes load with their inputs, output and energy;
 * the gate AND/OR swaps craft and keep the gate's program; a Redstone Chipset is made from redstone with laser power
 * alone.
 */
final class SiliconRecipeTests {
    static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();
    static final int MAX_TICKS = 1200;

    static {
        TESTS.put("silicon_assembly_recipes_load", SiliconRecipeTests::assemblyRecipesLoad);
        TESTS.put("silicon_gate_and_or_crafting", SiliconRecipeTests::gateAndOrCrafting);
        TESTS.put("silicon_chipset_laser_crafting", SiliconRecipeTests::chipsetLaserCrafting);
    }

    private static final BlockPos TABLE = new BlockPos(2, 3, 2);

    private SiliconRecipeTests() {
    }

    /** One expected input: an item or a tag, and a count. */
    private record In(Object ingredient, int count) {
    }

    private record Expected(String id, Item result, int count, int energy, List<In> inputs) {
    }

    private static In in(Item item) {
        return new In(item, 1);
    }

    private static In in(Item item, int count) {
        return new In(item, count);
    }

    private static In tag(String tag, int count) {
        return new In(TagKey.create(Registries.ITEM, Identifier.parse(tag)), count);
    }

    /** The 4.2.2 table of docs/PORTING-NOTES.md "Assembly table (#72)", plus the module recipes of #74. */
    private static List<Expected> expected() {
        Item[] chip = new Item[5];
        for (int i = 0; i < 5; i++) {
            chip[i] = SiliconRegistry.getChipset(i);
        }
        Item red = TransportRegistry.redPipeWire.get();
        Item blue = TransportRegistry.bluePipeWire.get();
        Item green = TransportRegistry.greenPipeWire.get();
        Item yellow = TransportRegistry.yellowPipeWire.get();
        List<Expected> list = new ArrayList<>();
        list.add(new Expected("redstone_red_chipset", chip[0], 1, 100000, List.of(in(Items.REDSTONE))));
        list.add(new Expected("redstone_iron_chipset", chip[1], 1, 200000, List.of(in(Items.REDSTONE), in(Items.IRON_INGOT))));
        list.add(new Expected("redstone_gold_chipset", chip[2], 1, 400000, List.of(in(Items.REDSTONE), in(Items.GOLD_INGOT))));
        list.add(new Expected("redstone_diamond_chipset", chip[3], 1, 800000, List.of(in(Items.REDSTONE), in(Items.DIAMOND))));
        list.add(new Expected("redstone_pulsating_chipset", chip[4], 2, 400000,
                List.of(in(Items.REDSTONE), in(Items.ENDER_PEARL))));

        Item gate = TransportRegistry.getGateItem(GateKind.Single, false);
        list.add(new Expected("gate", gate, 1, 200000, List.of(in(chip[0]))));
        list.add(new Expected("autarchic_gate", TransportRegistry.getGateItem(GateKind.Single, true), 1, 100000,
                List.of(in(gate), in(chip[4]), in(chip[1]))));
        Object[][] tiers = {
                {GateKind.AND_2, GateKind.OR_2, "iron", chip[1], List.of(in(red)), 400000},
                {GateKind.AND_3, GateKind.OR_3, "gold", chip[2], List.of(in(red), in(blue)), 800000},
                {GateKind.AND_4, GateKind.OR_4, "diamond", chip[3], List.of(in(red), in(blue), in(green), in(yellow)), 1600000}};
        for (Object[] tier : tiers) {
            for (int op = 0; op < 2; op++) {
                GateKind kind = (GateKind) tier[op];
                String name = "gate_" + tier[2] + (op == 0 ? "_and" : "_or");
                List<In> inputs = new ArrayList<>();
                inputs.add(in((Item) tier[3]));
                @SuppressWarnings("unchecked")
                List<In> wires = (List<In>) tier[4];
                inputs.addAll(wires);
                int energy = (Integer) tier[5];
                Item normal = TransportRegistry.getGateItem(kind, false);
                list.add(new Expected(name, normal, 1, energy, inputs));
                list.add(new Expected("autarchic_" + name, TransportRegistry.getGateItem(kind, true), 1, energy / 2,
                        List.of(in(normal), in(chip[4]), in(chip[1]))));
            }
        }

        for (String colour : List.of("red", "blue", "green", "yellow")) {
            Item wire = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID,
                    colour + "_pipe_wire"));
            list.add(new Expected(colour + "_pipe_wire", wire, 8, 5000,
                    List.of(tag("c:dyes/" + colour, 1), in(Items.REDSTONE), in(Items.IRON_INGOT))));
        }

        Map<PipeModuleType, List<In>> modules = new LinkedHashMap<>();
        modules.put(PipeModuleType.EXTRACTOR, List.of(tag("minecraft:planks", 2)));
        modules.put(PipeModuleType.FILTERED_EXTRACTOR, List.of(in(Items.EMERALD, 2)));
        modules.put(PipeModuleType.DIRECTOR, List.of(in(Items.IRON_INGOT, 2)));
        modules.put(PipeModuleType.SORTER, List.of(in(Items.DIAMOND, 2)));
        modules.put(PipeModuleType.PICKUP, List.of(in(Items.OBSIDIAN, 2)));
        modules.put(PipeModuleType.VOID, List.of(tag("c:dyes/black", 1), in(Items.REDSTONE)));
        modules.put(PipeModuleType.COLOUR_TAG, List.of(in(Items.LAPIS_BLOCK, 2)));
        modules.put(PipeModuleType.COLOUR_SORTER, List.of(in(Items.LAPIS_BLOCK), in(Items.DIAMOND)));
        modules.put(PipeModuleType.FILTERED_COLOUR_TAG, List.of(in(Items.LAPIS_BLOCK), in(Items.EMERALD)));
        modules.put(PipeModuleType.ISOLATOR, List.of(in(Items.SANDSTONE, 2)));
        modules.forEach((type, material) -> {
            Item module = TransportRegistry.getModuleItem(type);
            List<In> inputs = new ArrayList<>();
            inputs.add(in(chip[0]));
            inputs.addAll(material);
            list.add(new Expected(BuiltInRegistries.ITEM.getKey(module).getPath(), module, 1, 20000, inputs));
        });
        return list;
    }

    /**
     * Each recipe loads under {@code assembly/<output>}, as the JSON type with the expected energy, inputs and blank
     * output, and the table finds it by its output; nothing else is in the folder.
     */
    static void assemblyRecipesLoad(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<Expected> expected = expected();
        helper.assertValueEqual(expected.size(), 33, "expected recipes (23 from 4.2.2, 10 modules)");
        for (Expected recipe : expected) {
            Identifier id = Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "assembly/" + recipe.id());
            Optional<RecipeHolder<?>> holder = level.getServer().getRecipeManager()
                    .byKey(ResourceKey.create(Registries.RECIPE, id));
            if (holder.isEmpty() || !(holder.get().value() instanceof AssemblyTableRecipe json)) {
                throw helper.assertionException("assembly recipe " + id + " did not load");
            }
            helper.assertValueEqual(json.energy, recipe.energy(), id + " energy");
            helper.assertValueEqual(json.inputs.size(), recipe.inputs().size(), id + " input count");
            for (int i = 0; i < json.inputs.size(); i++) {
                AssemblyTableRecipe.Input input = json.inputs.get(i);
                In want = recipe.inputs().get(i);
                Object got = input.ingredient().<Object>map(Holder::value, tag -> tag);
                helper.assertValueEqual(got, want.ingredient(), id + " input " + i);
                helper.assertValueEqual(input.count(), want.count(), id + " input " + i + " count");
            }

            ItemStack output = json.result.create();
            helper.assertTrue(output.is(recipe.result()) && output.getCount() == recipe.count(),
                    id + " makes " + output + ", not " + recipe.count() + " " + recipe.result());
            helper.assertTrue(output.getComponentsPatch().isEmpty(), id + " output is not blank");
            AssemblyRecipe found = AssemblyRecipeManager.findRecipe(level, output);
            helper.assertTrue(found != null && found.energy == recipe.energy(), id + " not found by its output");
        }

        long shipped = level.getServer().getRecipeManager().recipeMap().byType(SiliconRegistry.assemblyRecipeType.get())
                .stream()
                .filter(holder -> holder.id().identifier().getNamespace().equals(BuildcraftReforged.MOD_ID)
                        && holder.id().identifier().getPath().startsWith("assembly/"))
                .count();
        helper.assertValueEqual(shipped, (long) expected.size(), "assembly recipes shipped");
        for (int i = 0; i < 5; i++) {
            helper.assertValueEqual(SiliconRegistry.getChipset(i).getDescriptionId(), "item.redstoneChipset." + i,
                    "chipset " + i + " translation key");
        }
        helper.succeed();
    }

    /**
     * 4.2.2's shapeless swaps: a Redstone Chipset and an AND gate make the OR gate and back, for iron, gold and diamond,
     * normal and autarchic. A programmed gate keeps its program and name through the swap.
     */
    static void gateAndOrCrafting(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack chipset = new ItemStack(SiliconRegistry.getChipset(0));
        GateSettings program = new GateSettings(List.of(new GateSettings.Entry(0,
                Optional.of(CoreRegistry.triggerRedstoneActive.get()), ItemStack.EMPTY,
                Optional.of(CoreRegistry.actionRedstone.get()))));
        GateKind[][] pairs = {{GateKind.AND_2, GateKind.OR_2}, {GateKind.AND_3, GateKind.OR_3}, {GateKind.AND_4, GateKind.OR_4}};
        for (boolean autarchic : new boolean[] {false, true}) {
            for (GateKind[] pair : pairs) {
                for (int from = 0; from < 2; from++) {
                    Item in = TransportRegistry.getGateItem(pair[from], autarchic);
                    Item out = TransportRegistry.getGateItem(pair[1 - from], autarchic);

                    ItemStack blank = new ItemStack(in);
                    ItemStack crafted = craft(helper, level, blank, chipset);
                    helper.assertTrue(crafted.is(out) && crafted.getCount() == 1, in + " + chipset made " + crafted);
                    helper.assertTrue(crafted.getComponentsPatch().isEmpty(), in + " swap of a blank gate is not blank");

                    ItemStack programmed = new ItemStack(in);
                    programmed.set(TransportRegistry.gateSettings.get(), program);
                    programmed.set(DataComponents.CUSTOM_NAME, Component.literal("Door"));
                    crafted = craft(helper, level, chipset, programmed);
                    helper.assertTrue(crafted.is(out), "programmed " + in + " + chipset made " + crafted);
                    helper.assertValueEqual(crafted.get(TransportRegistry.gateSettings.get()), program,
                            "program after the swap of " + in);
                    helper.assertValueEqual(crafted.getHoverName().getString(), "Door", "name after the swap of " + in);
                }
            }
        }
        helper.assertTrue(level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,
                CraftingInput.of(2, 1, List.of(new ItemStack(TransportRegistry.getGateItem(GateKind.Single, false)),
                        chipset.copy())), level).isEmpty(), "the plain gate swaps into something");
        helper.succeed();
    }

    private static ItemStack craft(GameTestHelper helper, ServerLevel level, ItemStack a, ItemStack b) {
        CraftingInput input = CraftingInput.of(2, 1, List.of(a.copy(), b.copy()));
        Optional<RecipeHolder<CraftingRecipe>> recipe = level.getServer().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, level);
        if (recipe.isEmpty()) {
            throw helper.assertionException("no crafting recipe for " + a + " + " + b);
        }
        return recipe.get().value().assemble(input);
    }

    /**
     * A Redstone Chipset (100 000 FE) from one redstone, planned on a table under nine lasers that are kept charged;
     * the chipset goes into the chest beside the table and the redstone is used up.
     */
    static void chipsetLaserCrafting(GameTestHelper helper) {
        helper.setBlock(TABLE, SiliconRegistry.assemblyTableBlock.get());
        TileAssemblyTable table = helper.getBlockEntity(TABLE, TileAssemblyTable.class);
        helper.setBlock(TABLE.east(), Blocks.CHEST);
        Container chest = helper.getBlockEntity(TABLE.east(), ChestBlockEntity.class);
        table.setItem(0, new ItemStack(Items.REDSTONE));

        Item chipset = SiliconRegistry.getChipset(0);
        AssemblyRecipe recipe = AssemblyRecipeManager.findRecipe(helper.getLevel(), new ItemStack(chipset));
        if (recipe == null) {
            throw helper.assertionException("no assembly recipe for the Redstone Chipset");
        }
        helper.assertValueEqual(recipe.energy, 100000F, "Redstone Chipset energy");
        helper.assertTrue(table.getPotentialOutputs().contains(recipe), "one redstone does not offer the chipset");
        table.planOutput(recipe);
        helper.assertTrue(table.isAssembling(recipe) && table.requiresLaserEnergy(), "the chipset is not being made");

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
            int made = 0;
            for (int i = 0; i < chest.getContainerSize(); i++) {
                if (chest.getItem(i).is(chipset))
                    made += chest.getItem(i).getCount();
            }
            helper.assertValueEqual(made, 1, "Redstone Chipsets in the chest");
            helper.assertTrue(table.getItem(0).isEmpty(), "the redstone was not used");
            for (BlockPos pos : lasers) {
                TileLaser laser = helper.getBlockEntity(pos, TileLaser.class);
                helper.assertTrue(laser.getLaserTarget() == null || laser.getLaserTarget() == table,
                        "a laser aims elsewhere");
            }
        });
    }
}
