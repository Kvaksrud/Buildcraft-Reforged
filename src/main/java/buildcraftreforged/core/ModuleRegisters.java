package buildcraftreforged.core;

import buildcraftreforged.BuildcraftReforged;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * One set of {@link DeferredRegister}s, all in the {@code buildcraftreforged} namespace. Each module's registry class
 * ({@code CoreRegistry}, {@code EnergyRegistry}, ...) owns one instance, so modules add entries in their own file.
 * NeoForge allows several registers for the same registry and namespace; the registry names must still be unique
 * across modules.
 */
public final class ModuleRegisters {
    public final DeferredRegister.Blocks blocks = DeferredRegister.createBlocks(BuildcraftReforged.MOD_ID);
    public final DeferredRegister.Items items = DeferredRegister.createItems(BuildcraftReforged.MOD_ID);
    public final DeferredRegister<BlockEntityType<?>> blockEntityTypes = create(Registries.BLOCK_ENTITY_TYPE);
    public final DeferredRegister<MenuType<?>> menus = create(Registries.MENU);
    public final DeferredRegister<Fluid> fluids = create(Registries.FLUID);
    public final DeferredRegister<FluidType> fluidTypes = create(NeoForgeRegistries.Keys.FLUID_TYPES);
    public final DeferredRegister<RecipeType<?>> recipeTypes = create(Registries.RECIPE_TYPE);
    public final DeferredRegister<RecipeSerializer<?>> recipeSerializers = create(Registries.RECIPE_SERIALIZER);
    public final DeferredRegister<SoundEvent> soundEvents = create(Registries.SOUND_EVENT);
    public final DeferredRegister.DataComponents dataComponents =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, BuildcraftReforged.MOD_ID);
    public final DeferredRegister<CreativeModeTab> creativeModeTabs = create(Registries.CREATIVE_MODE_TAB);

    private static <T> DeferredRegister<T> create(ResourceKey<? extends Registry<T>> key) {
        return DeferredRegister.create(key, BuildcraftReforged.MOD_ID);
    }

    public void register(IEventBus modBus) {
        blocks.register(modBus);
        items.register(modBus);
        blockEntityTypes.register(modBus);
        menus.register(modBus);
        fluids.register(modBus);
        fluidTypes.register(modBus);
        recipeTypes.register(modBus);
        recipeSerializers.register(modBus);
        soundEvents.register(modBus);
        dataComponents.register(modBus);
        creativeModeTabs.register(modBus);
    }
}
