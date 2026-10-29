package buildcraftreforged.gametest.gui;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.core.BlockBuildCraft;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * #25: a test block, {@code buildcraftreforged:sample_gui}, whose GUI shows a ledger, a phantom slot, a value synced
 * from the server through a widget packet and a button that sends a setting back through {@code PacketGuiReturn}. It
 * lives in the GameTest source set, so it exists in dev runs ({@code /give @s buildcraftreforged:sample_gui}) and never
 * in the jar.
 */
@EventBusSubscriber(modid = BuildcraftReforged.MOD_ID)
public final class SampleGui {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "sample_gui");

    public static BlockSampleGui block;
    public static BlockEntityType<TileSampleGui> tileType;
    public static MenuType<ContainerSampleGui> menuType;

    private SampleGui() {
    }

    @SubscribeEvent
    static void register(RegisterEvent event) {
        event.register(Registries.BLOCK, registry -> {
            block = new BlockSampleGui(BlockBuildCraft.defaultProperties(
                    BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, ID))));
            registry.register(ID, block);
        });
        event.register(Registries.ITEM, registry -> registry.register(ID,
                new BlockItem(block, new Item.Properties().setId(ResourceKey.create(Registries.ITEM, ID))
                        .useBlockDescriptionPrefix())));
        event.register(Registries.BLOCK_ENTITY_TYPE, registry -> {
            tileType = new BlockEntityType<>(TileSampleGui::new, Set.of(block));
            registry.register(ID, tileType);
        });
        event.register(Registries.MENU, registry -> {
            menuType = IMenuTypeExtension.create(ContainerSampleGui::new);
            registry.register(ID, menuType);
        });
    }
}
