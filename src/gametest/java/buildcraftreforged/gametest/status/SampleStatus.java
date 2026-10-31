package buildcraftreforged.gametest.status;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.core.BlockBuildCraft;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * #26: a test block without a GUI, {@code buildcraftreforged:sample_status}. Right-click opens the status panel on its
 * own; the block cycles through Working, Blocked and Idle every five seconds and shows the red light while blocked.
 * GameTest source set only ({@code /give @s buildcraftreforged:sample_status}).
 */
@EventBusSubscriber(modid = BuildcraftReforged.MOD_ID)
public final class SampleStatus {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "sample_status");

    public static BlockSampleStatus block;
    public static BlockEntityType<TileSampleStatus> tileType;

    private SampleStatus() {
    }

    @SubscribeEvent
    static void register(RegisterEvent event) {
        event.register(Registries.BLOCK, registry -> {
            block = new BlockSampleStatus(BlockBuildCraft.defaultProperties(
                    BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, ID))));
            registry.register(ID, block);
        });
        event.register(Registries.ITEM, registry -> registry.register(ID,
                new BlockItem(block, new Item.Properties().setId(ResourceKey.create(Registries.ITEM, ID))
                        .useBlockDescriptionPrefix())));
        event.register(Registries.BLOCK_ENTITY_TYPE, registry -> {
            tileType = new BlockEntityType<>(TileSampleStatus::new, Set.of(block));
            registry.register(ID, tileType);
        });
    }
}
