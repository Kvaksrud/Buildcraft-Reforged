package buildcraftreforged.gametest;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.core.BlockBuildCraft;
import buildcraftreforged.core.TileBuildCraft;
import buildcraftreforged.core.fluids.FluidUtils;
import buildcraftreforged.core.fluids.Tank;
import buildcraftreforged.core.fluids.TankManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * #24: a test tank block entity (one 16-bucket Tank in a TankManager, as 4.2.2's TileTank) fills and drains through
 * Capabilities.Fluid.BLOCK, rolls back aborted transactions, takes a water bucket through FluidUtils.handleRightClick,
 * and saves with the 4.2.2 tag names. The block exists only in GameTest runs.
 */
final class FluidTankTests {
    private static final BlockPos POS = new BlockPos(1, 1, 1);
    static final int CAPACITY = 16000;

    private static @Nullable Block testTankBlock;
    private static @Nullable BlockEntityType<TestTankTile> testTankType;

    private FluidTankTests() {
    }

    static final class TestTankBlock extends BlockBuildCraft {
        TestTankBlock(BlockBehaviour.Properties properties) {
            super(properties);
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new TestTankTile(pos, state);
        }
    }

    static final class TestTankTile extends TileBuildCraft {
        final Tank tank = new Tank("tank", CAPACITY, this);
        final TankManager<Tank> tankManager = new TankManager<>(tank);

        TestTankTile(BlockPos pos, BlockState state) {
            super(testTankType, pos, state);
        }

        @Override
        protected void saveAdditional(ValueOutput output) {
            super.saveAdditional(output);
            tankManager.writeToNBT(output);
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            tankManager.readFromNBT(input);
        }

        @Override
        public void writePacketPayload(RegistryFriendlyByteBuf data) {
            tankManager.writeData(data);
        }

        @Override
        public void readPacketPayload(RegistryFriendlyByteBuf data) {
            tankManager.readData(data);
        }
    }

    static void register(RegisterEvent event) {
        Identifier id = Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "test_tank");
        event.register(Registries.BLOCK, registry -> {
            testTankBlock = new TestTankBlock(BlockBuildCraft.defaultProperties(BlockBehaviour.Properties.of())
                    .setId(ResourceKey.create(Registries.BLOCK, id)));
            registry.register(id, testTankBlock);
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, registry -> {
            testTankType = new BlockEntityType<>(TestTankTile::new, testTankBlock);
            registry.register(id, testTankType);
        });
    }

    static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, testTankType, (tile, side) -> tile.tankManager);
    }

    static void fillAndDrain(GameTestHelper helper) {
        helper.setBlock(POS, testTankBlock);
        TestTankTile tile = helper.getBlockEntity(POS, TestTankTile.class);
        ResourceHandler<FluidResource> handler = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(POS), Direction.UP);
        helper.assertTrue(handler == tile.tankManager, "the tank manager is not the block's fluid handler");
        FluidResource water = FluidResource.of(Fluids.WATER);

        try (Transaction tx = Transaction.openRoot()) {
            helper.assertValueEqual(handler.insert(water, 1000, tx), 1000, "water inserted");
            tx.commit();
        }
        helper.assertValueEqual(tile.tank.getFluidAmount(), 1000, "amount after insert");

        // An aborted transaction leaves the tank as it was.
        try (Transaction tx = Transaction.openRoot()) {
            helper.assertValueEqual(handler.insert(water, 5000, tx), 5000, "water inserted before abort");
        }
        helper.assertValueEqual(tile.tank.getFluidAmount(), 1000, "amount after abort");

        try (Transaction tx = Transaction.openRoot()) {
            helper.assertValueEqual(handler.insert(FluidResource.of(Fluids.LAVA), 1000, tx), 0, "lava into a water tank");
            helper.assertValueEqual(handler.extract(FluidResource.of(Fluids.LAVA), 1000, tx), 0, "lava out of a water tank");
            helper.assertValueEqual(handler.extract(water, 400, tx), 400, "water extracted");
            helper.assertValueEqual(handler.insert(water, 100000, tx), CAPACITY - 600, "insert up to capacity");
            tx.commit();
        }
        helper.assertTrue(tile.tank.isFull(), "tank not full");

        try (Transaction tx = Transaction.openRoot()) {
            helper.assertValueEqual(handler.extract(water, 100000, tx), CAPACITY, "drain everything");
            tx.commit();
        }
        helper.assertTrue(tile.tank.isEmpty(), "tank not empty");
        helper.succeed();
    }

    static void bucketAndSave(GameTestHelper helper) {
        helper.setBlock(POS, testTankBlock);
        TestTankTile tile = helper.getBlockEntity(POS, TestTankTile.class);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        // 4.2.2 FluidUtils.handleRightClick: a full bucket empties into the tank, an empty one fills from it.
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        helper.assertTrue(FluidUtils.handleRightClick(tile.tankManager, player, InteractionHand.MAIN_HAND, true, true), "water bucket refused");
        helper.assertValueEqual(tile.tank.getFluidAmount(), 1000, "amount after bucket");
        helper.assertTrue(player.getMainHandItem().is(Items.BUCKET), "bucket not emptied: " + player.getMainHandItem());

        tile.tank.fill(new FluidStack(Fluids.WATER, 500), true);
        helper.assertTrue(FluidUtils.handleRightClick(tile.tankManager, player, InteractionHand.MAIN_HAND, true, true), "empty bucket refused");
        helper.assertValueEqual(tile.tank.getFluidAmount(), 500, "amount after filling a bucket");
        helper.assertTrue(player.getMainHandItem().is(Items.WATER_BUCKET), "bucket not filled: " + player.getMainHandItem());

        // Saved with the 4.2.2 tag names: a compound named after the tank, holding Forge's FluidTank tags.
        CompoundTag saved = tile.saveWithoutMetadata(helper.getLevel().registryAccess());
        CompoundTag tankTag = saved.getCompoundOrEmpty("tank");
        helper.assertValueEqual(tankTag.getStringOr("FluidName", ""), "minecraft:water", "FluidName");
        helper.assertValueEqual(tankTag.getIntOr("Amount", 0), 500, "Amount");

        TestTankTile loaded = new TestTankTile(tile.getBlockPos(), tile.getBlockState());
        loaded.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved));
        helper.assertTrue(FluidStack.matches(loaded.tank.getFluid(), new FluidStack(Fluids.WATER, 500)), "loaded " + loaded.tank.getFluid());
        helper.succeed();
    }
}
