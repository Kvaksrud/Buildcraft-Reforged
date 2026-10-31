package buildcraftreforged.transport;

import buildcraftreforged.api.recipes.AssemblyRecipe;
import buildcraftreforged.silicon.AssemblyTableRecipe;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The Assembly Table's facade recipes (#144), 4.2.2 {@code ItemFacade.addFacade}: one recipe per facade, made at
 * start-up from the facade list, of 3 Cobblestone Structure Pipes and the block, 8 000 MJ, giving 6 facades. Data
 * packs cannot list blocks that are only known once the registries are frozen, so one JSON file of type
 * {@code buildcraftreforged:assembly_facades} holds the shared part (energy, inputs, result count) in the
 * {@code buildcraftreforged:assembly} format, and {@link #toAssemblyRecipes()} adds the block and the facade state for
 * every block state {@link ItemFacade#getAllFacades()} lists. As in 4.2.2, the sideways log facades have no recipe of
 * their own: the turning recipe makes them from the upright one.
 */
public class FacadeAssemblyRecipe extends AssemblyTableRecipe {

    public static final MapCodec<FacadeAssemblyRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ExtraCodecs.POSITIVE_INT.fieldOf("energy").forGetter(r -> r.energy),
            Input.CODEC.listOf(1, 11).fieldOf("inputs").forGetter(r -> r.inputs),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(r -> r.result))
            .apply(i, FacadeAssemblyRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, FacadeAssemblyRecipe> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, r -> r.energy,
            Input.STREAM_CODEC.apply(ByteBufCodecs.list(11)), r -> r.inputs,
            ItemStackTemplate.STREAM_CODEC, r -> r.result,
            FacadeAssemblyRecipe::new);
    public static final RecipeSerializer<FacadeAssemblyRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    /**
     * @param energy FE per facade batch
     * @param inputs the shared inputs, at most 11: the table's 12 slots less the block
     * @param result the facade item and count; each recipe adds its block state
     */
    public FacadeAssemblyRecipe(int energy, List<Input> inputs, ItemStackTemplate result) {
        super(energy, inputs, result);
    }

    /** One 4.2.2 recipe per facade with a recipe, in the facade list's order. */
    @Override
    public List<AssemblyRecipe> toAssemblyRecipes() {
        AssemblyRecipe shared = toAssemblyRecipe();
        List<AssemblyRecipe> recipes = new ArrayList<>();
        for (ItemStack facade : ItemFacade.getAllFacades()) {
            BlockState state = ItemFacade.getBlockState(facade);
            if (state == null || !hasRecipe(state))
                continue;
            Item block = state.getBlock().asItem();
            if (block == Items.AIR)
                continue;
            Object[] input = new Object[shared.input.length + 1];
            System.arraycopy(shared.input, 0, input, 0, shared.input.length);
            input[shared.input.length] = new ItemStack(block);
            ItemStack output = result.create();
            output.set(TransportRegistry.facadeState.get(), state);
            recipes.add(new AssemblyRecipe((int) shared.energy, output, input));
        }
        return recipes;
    }

    /** 4.2.2 added the turned log facades to the list without a recipe; the upright state has one. */
    public static boolean hasRecipe(BlockState state) {
        return !state.hasProperty(BlockStateProperties.AXIS)
                || state.getValue(BlockStateProperties.AXIS) == net.minecraft.core.Direction.Axis.Y;
    }

    @Override
    public boolean matches(RecipeInput recipeInput, Level level) {
        ItemStack[] items = new ItemStack[recipeInput.size()];
        for (int i = 0; i < items.length; i++)
            items[i] = recipeInput.getItem(i);
        for (AssemblyRecipe recipe : toAssemblyRecipes()) {
            if (recipe.canBeDone(items))
                return true;
        }
        return false;
    }

    @Override
    public RecipeSerializer<FacadeAssemblyRecipe> getSerializer() {
        return TransportRegistry.facadeAssemblyRecipeSerializer.get();
    }
}
