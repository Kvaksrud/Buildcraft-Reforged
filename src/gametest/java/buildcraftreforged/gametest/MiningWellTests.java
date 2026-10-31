package buildcraftreforged.gametest;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.api.transport.IPipeTile.PipeType;
import buildcraftreforged.core.status.MachineStatus;
import buildcraftreforged.core.status.StatusPanel;
import buildcraftreforged.core.status.StatusReason;
import buildcraftreforged.factory.BlockMiningWell;
import buildcraftreforged.factory.FactoryRegistry;
import buildcraftreforged.factory.TileMiningWell;
import buildcraftreforged.transport.PipeMaterial;
import buildcraftreforged.transport.TilePipe;
import buildcraftreforged.transport.TransportRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * #66: the mining well digs its column with power, leaves Mining Pipes, stops at bedrock and lava, pushes the drops
 * into a chest or an item pipe beside it, takes its pipes with it when removed, and stops at a block its owner may not
 * break. They build in the padded area (see {@link OilFuelTests#PADDING}), on a bedrock floor at y = 2.
 */
@EventBusSubscriber(modid = BuildcraftReforged.MOD_ID)
final class MiningWellTests {
    static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();
    static final int MAX_TICKS = 200;
    /** The well's receive cap at mining.cost.multipler 1 (4.2.2 100 MJ). */
    private static final int MAX_RECEIVED = 1000;

    /** Absolute positions the stand-in claim mod protects. */
    private static final Set<BlockPos> PROTECTED = ConcurrentHashMap.newKeySet();
    /** The UUID of the last player the stand-in refused. */
    private static volatile UUID lastRefused;

    static {
        TESTS.put("mining_well_digs", MiningWellTests::digs);
        TESTS.put("mining_well_output", MiningWellTests::output);
        TESTS.put("mining_well_protected", MiningWellTests::protectedBlock);
    }

    private MiningWellTests() {
    }

    @SubscribeEvent
    static void onBreak(BreakBlockEvent event) {
        if (PROTECTED.contains(event.getPos())) {
            lastRefused = event.getPlayer().getUUID();
            event.setCanceled(true);
        }
    }

    /** Places a well at pos as a mock player, so it has an owner. */
    private static TileMiningWell placeWell(GameTestHelper helper, BlockPos pos, ServerPlayer placer) {
        helper.setBlock(pos, FactoryRegistry.miningWellBlock.get());
        BlockPos absolute = helper.absolutePos(pos);
        BlockState state = helper.getLevel().getBlockState(absolute);
        FactoryRegistry.miningWellBlock.get().setPlacedBy(helper.getLevel(), absolute, state, placer,
                new ItemStack(FactoryRegistry.miningWellBlock.get()));
        return helper.getBlockEntity(pos, TileMiningWell.class);
    }

    private static ServerPlayer placer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.snapTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(0, 12, 0))));
        return player;
    }

    /** A column under well: blocks from the top down, then bedrock. */
    private static void column(GameTestHelper helper, BlockPos well, Block... blocks) {
        BlockPos pos = well.below();
        for (Block block : blocks) {
            helper.setBlock(pos, block);
            pos = pos.below();
        }
        helper.setBlock(pos, Blocks.BEDROCK);
    }

    private static void power(GameTestHelper helper, BlockPos well) {
        EnergyHandler handler = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(well),
                Direction.UP);
        helper.assertTrue(handler != null, "the mining well has no energy capability");
        try (Transaction tx = Transaction.openRoot()) {
            handler.insert(MAX_RECEIVED, tx);
            tx.commit();
        }
    }

    private static int count(GameTestHelper helper, BlockPos pos, Item item) {
        Container container = (Container) helper.getBlockEntity(pos, BlockEntity.class);
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.is(item))
                count += stack.getCount();
        }
        return count;
    }

    /** Items of kind item in item entities within 32 blocks of pos. */
    private static int thrown(GameTestHelper helper, BlockPos pos, Item item) {
        AABB area = new AABB(helper.absolutePos(pos)).inflate(32);
        int count = 0;
        for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, area)) {
            if (entity.getItem().is(item))
                count += entity.getItem().getCount();
        }
        return count;
    }

    private static void assertPipes(GameTestHelper helper, BlockPos well, int count) {
        for (int i = 1; i <= count; i++) {
            helper.assertBlockPresent(FactoryRegistry.plainPipeBlock.get(), well.below(i));
        }
    }

    /**
     * Without power nothing happens. With power the well digs stone, dirt and gravel down to bedrock, one block per
     * 640 FE, leaving Mining Pipes; the drops are thrown out above it (nothing beside it takes them). At bedrock it is
     * done (not active). A second well stops at lava and leaves it alone. Removing the first well removes its pipes.
     */
    static void digs(GameTestHelper helper) {
        BlockPos well = new BlockPos(2, 7, 2);
        column(helper, well, Blocks.STONE, Blocks.DIRT, Blocks.GRAVEL, Blocks.DIRT);
        TileMiningWell tile = placeWell(helper, well, placer(helper));
        helper.assertTrue(tile.getOwnerProfile() != null, "the well recorded no owner");

        BlockPos lavaWell = new BlockPos(6, 7, 6);
        column(helper, lavaWell, Blocks.DIRT, Blocks.LAVA, Blocks.DIRT);
        // Keep the lava source in place: stone all around it.
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(lavaWell.below(2).relative(side), Blocks.STONE);
        }
        TileMiningWell lavaTile = placeWell(helper, lavaWell, placer(helper));

        helper.runAtTickTime(10, () -> {
            helper.assertBlockPresent(Blocks.STONE, well.below());
            helper.assertTrue(tile.isActive(), "the well is done before it dug");
            helper.assertValueEqual(tile.getStatusState(), MachineStatus.NO_POWER, "status without power");
        });
        helper.onEachTick(() -> {
            if (helper.getTick() > 10) {
                if (helper.getLevel().getBlockEntity(helper.absolutePos(well)) instanceof TileMiningWell)
                    power(helper, well);
                power(helper, lavaWell);
            }
        });

        helper.runAtTickTime(60, () -> {
            assertPipes(helper, well, 4);
            helper.assertBlockPresent(Blocks.BEDROCK, well.below(5));
            helper.assertFalse(tile.isActive(), "the well still digs at bedrock");
            // 4.2.2 throws the drops up at 1 block per tick; they may still be above the test area.
            helper.assertTrue(thrown(helper, well, Items.COBBLESTONE) == 1, "cobblestone thrown out: " + thrown(helper, well, Items.COBBLESTONE));
            // Two dirt from this well, one from the lava well.
            helper.assertTrue(thrown(helper, well, Items.DIRT) == 3, "dirt thrown out: " + thrown(helper, well, Items.DIRT));

            assertPipes(helper, lavaWell, 1);
            helper.assertBlockPresent(Blocks.LAVA, lavaWell.below(2));
            helper.assertFalse(lavaTile.isActive(), "the well still digs at lava");

            helper.setBlock(well, Blocks.AIR);
            for (int i = 1; i <= 4; i++) {
                helper.assertBlockPresent(Blocks.AIR, well.below(i));
            }
            helper.assertBlockPresent(Blocks.BEDROCK, well.below(5));
            helper.succeed();
        });
    }

    /**
     * The drops go into a chest beside the well, or into an item pipe beside it that leads to a chest; nothing is
     * thrown out. The pipe connects to the well although the well has no inventory (4.2.2 IMachine.manageSolids).
     */
    static void output(GameTestHelper helper) {
        BlockPos chestWell = new BlockPos(1, 5, 1);
        column(helper, chestWell, Blocks.DIRT, Blocks.DIRT);
        helper.setBlock(chestWell.east(), Blocks.CHEST);
        placeWell(helper, chestWell, placer(helper));

        BlockPos pipeWell = new BlockPos(5, 5, 5);
        column(helper, pipeWell, Blocks.DIRT, Blocks.DIRT);
        helper.setBlock(pipeWell.east(), TransportRegistry.getPipe(PipeType.ITEM, PipeMaterial.STONE));
        helper.setBlock(pipeWell.east(2), Blocks.CHEST);
        placeWell(helper, pipeWell, placer(helper));

        // Pipe budgets start empty on load; let them fill before the first push.
        helper.onEachTick(() -> {
            if (helper.getTick() > 25) {
                power(helper, chestWell);
                power(helper, pipeWell);
            }
        });
        helper.runAtTickTime(25, () -> helper.assertTrue(
                helper.getBlockEntity(pipeWell.east(), TilePipe.class)
                        .isPipeConnected(Direction.WEST), "the item pipe does not connect to the well"));

        helper.runAtTickTime(80, () -> {
            assertPipes(helper, chestWell, 2);
            assertPipes(helper, pipeWell, 2);
            helper.assertValueEqual(count(helper, chestWell.east(), Items.DIRT), 2, "dirt in the chest beside the well");
            helper.assertValueEqual(count(helper, pipeWell.east(2), Items.DIRT), 2, "dirt in the chest behind the pipe");
            helper.assertEntityNotPresent(EntityTypes.ITEM);
            helper.succeed();
        });
    }

    /**
     * A claim refuses the owner at the third block: the well digs two, stops with the block in place, shows blocked
     * (State, Reason, red light) and the claim saw the owner's UUID. When the claim lifts, it digs on to bedrock.
     */
    static void protectedBlock(GameTestHelper helper) {
        BlockPos well = new BlockPos(2, 6, 2);
        column(helper, well, Blocks.DIRT, Blocks.DIRT, Blocks.DIRT);
        ServerPlayer placer = placer(helper);
        TileMiningWell tile = placeWell(helper, well, placer);
        BlockPos claimed = helper.absolutePos(well.below(3));
        PROTECTED.add(claimed);
        lastRefused = null;

        helper.onEachTick(() -> {
            if (helper.getTick() > 5)
                power(helper, well);
        });

        helper.runAtTickTime(40, () -> {
            try {
                assertPipes(helper, well, 2);
                helper.assertBlockPresent(Blocks.DIRT, well.below(3));
                helper.assertTrue(tile.isBlocked(), "the well is not blocked");
                helper.assertValueEqual(tile.getStatusState(), MachineStatus.BLOCKED, "State line");
                helper.assertValueEqual(tile.getStatusReason(), StatusReason.PROTECTED_BLOCK, "Reason line");
                helper.assertTrue(helper.getBlockState(well).getValue(StatusPanel.BLOCKED), "no red light");
                helper.assertValueEqual(lastRefused, placer.getUUID(), "breaker seen by the claim");
            } finally {
                PROTECTED.remove(claimed);
            }
        });

        helper.runAtTickTime(80, () -> {
            assertPipes(helper, well, 3);
            helper.assertBlockPresent(Blocks.BEDROCK, well.below(4));
            helper.assertFalse(tile.isBlocked(), "the well is still blocked");
            helper.assertFalse(tile.isActive(), "the well still digs at bedrock");
            helper.assertFalse(helper.getBlockState(well).getValue(StatusPanel.BLOCKED), "red light still on");
            helper.assertTrue(helper.getBlockState(well).getBlock() instanceof BlockMiningWell, "the well is gone");
            helper.succeed();
        });
    }
}
