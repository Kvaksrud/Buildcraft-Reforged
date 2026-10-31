package buildcraftreforged.gametest;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.api.gates.IAction;
import buildcraftreforged.core.BlockBuildCraft;
import buildcraftreforged.core.CoreRegistry;
import buildcraftreforged.core.IMachine;
import buildcraftreforged.core.TileBuildCraft;
import buildcraftreforged.core.status.IStatusProvider;
import buildcraftreforged.core.status.MachineOutput;
import buildcraftreforged.core.status.StatusPanel;
import buildcraftreforged.core.status.StatusReason;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * #63: a test-only machine, {@code gametest_output_block}, that makes one cobblestone whenever its {@link MachineOutput}
 * is empty and pushes it out every tick, the way a quarry hands on what it mines. Only in dev runs; the jar has no such
 * block.
 */
@EventBusSubscriber(modid = BuildcraftReforged.MOD_ID)
final class OutputTestBlocks {
    static final Identifier ID = Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "gametest_output_block");
    static Block outputTestBlock;
    static BlockEntityType<OutputTestTile> outputTestTile;

    private OutputTestBlocks() {
    }

    @SubscribeEvent
    static void register(RegisterEvent event) {
        if (!BuildcraftGameTests.isGameTestRun())
            return;
        event.register(Registries.BLOCK, registry -> {
            outputTestBlock = new TestBlock(BlockBuildCraft.defaultProperties(
                    BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, ID))));
            registry.register(ID, outputTestBlock);
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, registry -> {
            outputTestTile = new BlockEntityType<>(OutputTestTile::new, Set.of(outputTestBlock));
            registry.register(ID, outputTestTile);
        });
    }

    /** A machine block with the red light ({@link StatusPanel#BLOCKED}). */
    private static final class TestBlock extends BlockBuildCraft {
        TestBlock(Properties properties) {
            super(properties);
            registerDefaultState(defaultBlockState().setValue(StatusPanel.BLOCKED, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(StatusPanel.BLOCKED);
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new OutputTestTile(pos, state);
        }
    }

    static final class OutputTestTile extends TileBuildCraft implements IMachine, IStatusProvider {
        final MachineOutput output = new MachineOutput();
        /** Cobblestone made so far. */
        int made;

        OutputTestTile(BlockPos pos, BlockState state) {
            super(outputTestTile, pos, state);
        }

        @Override
        public void updateEntity() {
            if (level != null && !level.isClientSide()) {
                if (!output.hasPending()) {
                    output.add(new ItemStack(Items.COBBLESTONE));
                    made++;
                }
                output.push(level, worldPosition);
            }
            super.updateEntity();
        }

        @Override
        public boolean isActive() {
            return !output.isBlocked();
        }

        @Override
        public boolean isBlocked() {
            return output.isBlocked();
        }

        @Override
        public StatusReason getStatusReason() {
            return output.getReason();
        }

        @Override
        public boolean manageFluids() {
            return false;
        }

        @Override
        public boolean manageSolids() {
            return true;
        }

        @Override
        public boolean allowAction(IAction action) {
            return action == CoreRegistry.actionOn.get() || action == CoreRegistry.actionOff.get();
        }

        @Override
        protected void saveAdditional(ValueOutput out) {
            super.saveAdditional(out);
            output.save(out);
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            output.load(input);
        }
    }
}
