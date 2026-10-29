package buildcraftreforged.gametest;

import buildcraftreforged.BuildcraftReforged;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/** The test-only block and block entity for the trigger GameTests. Only in dev runs; the jar has no such block. */
@EventBusSubscriber(modid = BuildcraftReforged.MOD_ID)
final class TriggerTestBlocks {
    static final Identifier ID = Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "gametest_trigger_block");
    static Block triggerTestBlock;
    static BlockEntityType<TriggerTestTile> triggerTestTile;

    private TriggerTestBlocks() {
    }

    @SubscribeEvent
    static void register(RegisterEvent event) {
        event.register(Registries.BLOCK, registry -> {
            triggerTestBlock = new TestBlock(BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, ID)));
            registry.register(ID, triggerTestBlock);
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, registry -> {
            triggerTestTile = new BlockEntityType<>(TriggerTestTile::new, triggerTestBlock);
            registry.register(ID, triggerTestTile);
        });
    }

    @SubscribeEvent
    static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, triggerTestTile, (tile, side) -> tile.inventory);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, triggerTestTile, (tile, side) -> tile.tank);
    }

    private static final class TestBlock extends Block implements EntityBlock {
        TestBlock(Properties properties) {
            super(properties);
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new TriggerTestTile(pos, state);
        }
    }
}
