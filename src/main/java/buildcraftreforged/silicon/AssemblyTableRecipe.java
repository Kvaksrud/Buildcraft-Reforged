/*
 * Buildcraft Reforged, 2026: the data-driven form of 4.2.2's AssemblyRecipe (plan section 6: "custom RecipeType +
 * serializer for Assembly Table"). 4.2.2 registered assembly recipes in Java, in BuildCraftSilicon.loadRecipes,
 * BuildCraftTransport.postInit and ItemFacade.addFacade; they are now JSON files of type
 * buildcraftreforged:assembly. The format is in docs/PORTING-NOTES.md, "Assembly table (#72)".
 */
package buildcraftreforged.silicon;

import buildcraftreforged.api.recipes.AssemblyRecipe;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * An Assembly Table recipe as read from JSON. Recipes load before item components are bound, so this keeps the item
 * templates; {@link #toAssemblyRecipe()} builds the 4.2.2 {@link AssemblyRecipe} (input array, output stack, energy)
 * the table works on, and {@link AssemblyRecipeManager} does that once per recipe load.
 *
 * <p>Not a recipe-book recipe: it is {@link #isSpecial() special} and not placeable; the table asks
 * {@link AssemblyRecipeManager} for its recipes.
 */
public class AssemblyTableRecipe implements Recipe<RecipeInput> {

    /** One input: an exact item (4.2.2: an ItemStack) or an item tag (4.2.2: an Ore Dictionary name), with a count. */
    public record Input(Either<Holder<Item>, TagKey<Item>> ingredient, int count) {
        public static final Codec<Input> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.mapEither(Item.CODEC.fieldOf("item"), TagKey.codec(Registries.ITEM).fieldOf("tag"))
                        .forGetter(Input::ingredient),
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(Input::count))
                .apply(i, Input::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Input> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.either(Item.STREAM_CODEC, TagKey.streamCodec(Registries.ITEM)), Input::ingredient,
                ByteBufCodecs.VAR_INT, Input::count,
                Input::new);
    }

    public static final MapCodec<AssemblyTableRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ExtraCodecs.POSITIVE_INT.fieldOf("energy").forGetter(r -> r.energy),
            Input.CODEC.listOf(1, 12).fieldOf("inputs").forGetter(r -> r.inputs),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(r -> r.result))
            .apply(i, AssemblyTableRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, AssemblyTableRecipe> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, r -> r.energy,
            Input.STREAM_CODEC.apply(ByteBufCodecs.list(12)), r -> r.inputs,
            ItemStackTemplate.STREAM_CODEC, r -> r.result,
            AssemblyTableRecipe::new);
    public static final RecipeSerializer<AssemblyTableRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    /** FE (4.2.2: MJ; × 10). */
    public final int energy;
    public final List<Input> inputs;
    public final ItemStackTemplate result;

    /**
     * @param energy FE (4.2.2: MJ; × 10)
     * @param inputs at most 12, the table's slot count
     * @param result the output
     */
    public AssemblyTableRecipe(int energy, List<Input> inputs, ItemStackTemplate result) {
        this.energy = energy;
        this.inputs = List.copyOf(inputs);
        this.result = result;
    }

    /** The 4.2.2 recipe. Only once item components are bound (not while recipes load). */
    public AssemblyRecipe toAssemblyRecipe() {
        return new AssemblyRecipe(energy, result.create(), toInput(inputs));
    }

    /** 4.2.2's input array: an ItemStack per item input; a tag followed by its Integer count per tag input. */
    private static Object[] toInput(List<Input> inputs) {
        List<Object> input = new ArrayList<>();
        for (Input entry : inputs) {
            entry.ingredient().ifLeft(item -> input.add(new ItemStack(item, entry.count())))
                    .ifRight(tag -> {
                        input.add(tag);
                        input.add(entry.count());
                    });
        }
        return input.toArray();
    }

    @Override
    public boolean matches(RecipeInput recipeInput, Level level) {
        ItemStack[] items = new ItemStack[recipeInput.size()];
        for (int i = 0; i < items.length; i++) {
            items[i] = recipeInput.getItem(i);
        }
        return toAssemblyRecipe().canBeDone(items);
    }

    @Override
    public ItemStack assemble(RecipeInput recipeInput) {
        return result.create();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public RecipeSerializer<AssemblyTableRecipe> getSerializer() {
        return SiliconRegistry.assemblyRecipeSerializer.get();
    }

    @Override
    public RecipeType<AssemblyTableRecipe> getType() {
        return SiliconRegistry.assemblyRecipeType.get();
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.CRAFTING_MISC;
    }
}
