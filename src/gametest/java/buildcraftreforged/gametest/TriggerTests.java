package buildcraftreforged.gametest;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.api.gates.ActionManager;
import buildcraftreforged.api.gates.IAction;
import buildcraftreforged.api.gates.ITrigger;
import buildcraftreforged.api.gates.ITriggerParameter;
import buildcraftreforged.api.gates.TriggerParameter;
import buildcraftreforged.core.CoreRegistry;
import com.mojang.serialization.JsonOps;
import java.util.LinkedList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

/** #31: the core triggers and actions, evaluated against a test machine and a vanilla chest. */
final class TriggerTests {
    private static final BlockPos POS = new BlockPos(1, 1, 1);

    private TriggerTests() {
    }

    static void registries(GameTestHelper helper) {
        ITrigger active = CoreRegistry.triggerMachineActive.get();
        helper.assertTrue(ActionManager.getTrigger(id("trigger_machine_active")) == active, "registry id lookup");
        helper.assertTrue(ActionManager.triggers.get("buildcraft.work.scheduled") == active, "4.2.2 tag lookup");
        helper.assertTrue(ActionManager.triggers.get("buildcraft.work.blocked") == CoreRegistry.triggerMachineBlocked.get(),
                "blocked trigger tag");
        helper.assertTrue(ActionManager.triggers.get("buildcraft.filteredBuffer.below_25") == CoreRegistry.triggerInventoryBelow25.get(),
                "4.2.2 filtered buffer alias");
        helper.assertTrue(ActionManager.actions.get("buildcraft.machine.loop") == CoreRegistry.actionLoop.get(), "action tag lookup");
        helper.assertTrue(ActionManager.getAction(id("action_redstone")) == CoreRegistry.actionRedstone.get(), "action id lookup");

        String encoded = ActionManager.TRIGGER_CODEC.encodeStart(JsonOps.INSTANCE, active).getOrThrow().getAsString();
        helper.assertTrue(encoded.equals("buildcraftreforged:trigger_machine_active"), "trigger codec wrote " + encoded);
        IAction decoded = ActionManager.ACTION_CODEC.parse(JsonOps.INSTANCE,
                ActionManager.ACTION_CODEC.encodeStart(JsonOps.INSTANCE, CoreRegistry.actionOn.get()).getOrThrow()).getOrThrow();
        helper.assertTrue(decoded == CoreRegistry.actionOn.get(), "action codec round trip");

        // The parameter keeps one item with its components.
        RegistryOps<com.google.gson.JsonElement> ops = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        ItemStack named = new ItemStack(Items.COBBLESTONE, 5);
        named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("x"));
        TriggerParameter parameter = TriggerParameter.of(named);
        TriggerParameter read = TriggerParameter.CODEC.parse(ops, TriggerParameter.CODEC.encodeStart(ops, parameter).getOrThrow())
                .getOrThrow();
        helper.assertTrue(read.getItemStack().getCount() == 1 && ItemStack.isSameItemSameComponents(read.getItemStack(), named),
                "parameter codec round trip: " + read.getItemStack());
        helper.succeed();
    }

    static void machine(GameTestHelper helper) {
        TriggerTestTile tile = place(helper);
        ITrigger active = CoreRegistry.triggerMachineActive.get();
        ITrigger inactive = CoreRegistry.triggerMachineInactive.get();
        ITrigger blocked = CoreRegistry.triggerMachineBlocked.get();

        expect(helper, tile, "idle", List.of(inactive), List.of(active, blocked), null);
        tile.active = true;
        expect(helper, tile, "working", List.of(active), List.of(inactive, blocked), null);
        tile.active = false;
        tile.blocked = true;
        expect(helper, tile, "blocked", List.of(blocked), List.of(active, inactive), null);

        // A chest is no machine.
        helper.setBlock(POS.above(), Blocks.CHEST);
        BlockEntity chest = helper.getBlockEntity(POS.above(), BlockEntity.class);
        helper.assertFalse(inactive.isTriggerActive(Direction.UP, chest, null), "chest counted as an idle machine");

        // What a gate next to the machine offers.
        LinkedList<ITrigger> triggers = ActionManager.getNeighborTriggers(tile.getBlockState().getBlock(), tile);
        helper.assertTrue(triggers.containsAll(List.of(active, inactive, blocked, CoreRegistry.triggerEmptyInventory.get(),
                CoreRegistry.triggerInventoryBelow75.get(), CoreRegistry.triggerFullFluid.get())), "neighbour triggers: " + triggers);
        LinkedList<IAction> actions = ActionManager.getNeighborActions(tile.getBlockState().getBlock(), tile);
        helper.assertTrue(actions.equals(List.of(CoreRegistry.actionRedstone.get(), CoreRegistry.actionOn.get(),
                CoreRegistry.actionOff.get())), "neighbour actions: " + actions);
        LinkedList<ITrigger> chestTriggers = ActionManager.getNeighborTriggers(Blocks.CHEST, chest);
        helper.assertTrue(chestTriggers.contains(CoreRegistry.triggerFullInventory.get()) && !chestTriggers.contains(active)
                && !chestTriggers.contains(CoreRegistry.triggerEmptyFluid.get()), "chest triggers: " + chestTriggers);
        helper.succeed();
    }

    static void inventory(GameTestHelper helper) {
        TriggerTestTile tile = place(helper);
        ITrigger empty = CoreRegistry.triggerEmptyInventory.get();
        ITrigger contains = CoreRegistry.triggerContainsInventory.get();
        ITrigger space = CoreRegistry.triggerSpaceInventory.get();
        ITrigger full = CoreRegistry.triggerFullInventory.get();
        ITrigger below25 = CoreRegistry.triggerInventoryBelow25.get();
        ITrigger below50 = CoreRegistry.triggerInventoryBelow50.get();
        ITrigger below75 = CoreRegistry.triggerInventoryBelow75.get();
        ITriggerParameter stone = parameter(Items.STONE);
        ITriggerParameter dirt = parameter(Items.DIRT);

        // The level triggers require a parameter.
        expect(helper, tile, "empty", List.of(empty, space), List.of(contains, full, below25, below50, below75), null);
        expect(helper, tile, "empty, stone", List.of(empty, space, below25, below50, below75), List.of(contains, full), stone);

        // 40 of 128 stone: 31%.
        tile.inventory.set(0, ItemResource.of(Items.STONE), 40);
        expect(helper, tile, "40 stone", List.of(contains, space), List.of(empty, full, below75), null);
        expect(helper, tile, "40 stone, stone", List.of(contains, space, below50), List.of(empty, full, below25), stone);
        expect(helper, tile, "40 stone, dirt", List.of(empty, space, below25), List.of(contains, full), dirt);

        // 64 stone and 64 dirt: full, and no slot left for stone.
        tile.inventory.set(0, ItemResource.of(Items.STONE), 64);
        tile.inventory.set(1, ItemResource.of(Items.DIRT), 64);
        expect(helper, tile, "full", List.of(contains, full), List.of(empty, space), null);
        expect(helper, tile, "full, stone", List.of(contains, full), List.of(empty, space, below75), stone);

        // 32 dirt in the second slot: space for dirt, none for stone, and the gate sees "full" without a filter only
        // when no slot is empty (4.2.2 counted space for a stack only with a filter).
        tile.inventory.set(1, ItemResource.of(Items.DIRT), 32);
        expect(helper, tile, "stone full, dirt half", List.of(full), List.of(space), null);
        expect(helper, tile, "dirt half, dirt", List.of(space, below75), List.of(full, below50), dirt);
        expect(helper, tile, "dirt half, stone", List.of(full), List.of(space), stone);

        // A vanilla chest through its item handler capability.
        helper.setBlock(POS.above(), Blocks.CHEST);
        BlockEntity chest = helper.getBlockEntity(POS.above(), BlockEntity.class);
        helper.assertTrue(empty.isTriggerActive(Direction.UP, chest, null), "empty chest");
        ((net.minecraft.world.Container) chest).setItem(3, new ItemStack(Items.STONE, 16));
        helper.assertTrue(contains.isTriggerActive(Direction.UP, chest, stone), "chest with stone");
        helper.assertFalse(contains.isTriggerActive(Direction.UP, chest, dirt), "chest without dirt");
        helper.succeed();
    }

    static void fluid(GameTestHelper helper) {
        TriggerTestTile tile = place(helper);
        ITrigger empty = CoreRegistry.triggerEmptyFluid.get();
        ITrigger contains = CoreRegistry.triggerContainsFluid.get();
        ITrigger space = CoreRegistry.triggerSpaceFluid.get();
        ITrigger full = CoreRegistry.triggerFullFluid.get();
        ITriggerParameter water = parameter(Items.WATER_BUCKET);
        ITriggerParameter lava = parameter(Items.LAVA_BUCKET);

        expect(helper, tile, "empty", List.of(empty, space), List.of(contains, full), null);
        expect(helper, tile, "empty, water", List.of(empty, space), List.of(contains, full), water);

        tile.tank.set(0, FluidResource.of(Fluids.WATER), 1000);
        expect(helper, tile, "1000 water", List.of(contains, space), List.of(empty, full), null);
        expect(helper, tile, "1000 water, water", List.of(contains, space), List.of(empty, full), water);
        // The tank holds water, so it has no space for lava and counts as full for it.
        expect(helper, tile, "1000 water, lava", List.of(empty, full), List.of(contains, space), lava);

        tile.tank.set(0, FluidResource.of(Fluids.WATER), 4000);
        expect(helper, tile, "4000 water", List.of(contains, full), List.of(empty, space), null);
        expect(helper, tile, "4000 water, water", List.of(contains, full), List.of(empty, space), water);
        helper.succeed();
    }

    private static TriggerTestTile place(GameTestHelper helper) {
        helper.setBlock(POS, TriggerTestBlocks.triggerTestBlock);
        return helper.getBlockEntity(POS, TriggerTestTile.class);
    }

    private static ITriggerParameter parameter(net.minecraft.world.item.Item item) {
        ITriggerParameter parameter = CoreRegistry.triggerContainsInventory.get().createParameter();
        parameter.set(new ItemStack(item));
        return parameter;
    }

    private static void expect(GameTestHelper helper, BlockEntity tile, String what, List<ITrigger> on, List<ITrigger> off,
            ITriggerParameter parameter) {
        for (ITrigger trigger : on) {
            helper.assertTrue(trigger.isTriggerActive(Direction.UP, tile, parameter), what + ": " + trigger.getUniqueTag() + " is off");
        }
        for (ITrigger trigger : off) {
            helper.assertFalse(trigger.isTriggerActive(Direction.UP, tile, parameter), what + ": " + trigger.getUniqueTag() + " is on");
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, path);
    }
}
