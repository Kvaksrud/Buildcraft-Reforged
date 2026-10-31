/*
 * Buildcraft Reforged, 2026: the data-driven form of 4.2.2's RefineryRecipes.Recipe (plan section 6: "custom
 * RecipeType + serializer for ... Refinery"). 4.2.2 registered the one refinery recipe, oil to fuel, in Java in
 * BuildCraftEnergy.preInit; it is the JSON file data/buildcraftreforged/recipe/refinery/fuel.json of type
 * buildcraftreforged:refinery now. The format is in docs/PORTING-NOTES.md, "Refinery (#68)".
 */
package buildcraftreforged.factory;

import buildcraftreforged.api.recipes.RefineryRecipes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jspecify.annotations.Nullable;

/**
 * A refinery recipe read from JSON: one or two fluid ingredients, a fluid result, the FE per cycle and the ticks
 * between cycles. The refinery works on the 4.2.2 {@link RefineryRecipes.Recipe} that {@link #getRecipe()} builds, so
 * 4.2.2's matching and sort order apply unchanged.
 *
 * <p>Fluids are a fluid id and an amount rather than a {@code FluidStack}: the stacks are made on first use, after
 * the fluids' data components are bound, not while the recipe is decoded.
 *
 * <p>Not a recipe-book recipe: it is {@link #isSpecial() special}, not placeable and never matched through
 * {@link #matches}'s vanilla callers; the refinery asks {@link RefineryRecipeManager}.
 */
public class RefineryRecipe implements Recipe<RecipeInput> {

    /** A fluid and an amount in mB, the JSON form of a 1.6.4 {@code FluidStack}. */
    public record FluidAmount(Holder<Fluid> fluid, int amount) {
        public static final Codec<FluidAmount> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.FLUID.holderByNameCodec().validate(fluid -> fluid.value() == Fluids.EMPTY
                        ? DataResult.error(() -> "Refinery fluids cannot be empty")
                        : DataResult.success(fluid)).fieldOf("fluid").forGetter(FluidAmount::fluid),
                ExtraCodecs.POSITIVE_INT.fieldOf("amount").forGetter(FluidAmount::amount))
                .apply(i, FluidAmount::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, FluidAmount> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.holderRegistry(Registries.FLUID), FluidAmount::fluid,
                ByteBufCodecs.VAR_INT, FluidAmount::amount,
                FluidAmount::new);

        public FluidStack toStack() {
            return new FluidStack(fluid, amount);
        }
    }

    public static final MapCodec<RefineryRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            FluidAmount.CODEC.fieldOf("ingredient1").forGetter(r -> r.ingredient1),
            FluidAmount.CODEC.optionalFieldOf("ingredient2").forGetter(r -> r.ingredient2),
            FluidAmount.CODEC.fieldOf("result").forGetter(r -> r.result),
            ExtraCodecs.NON_NEGATIVE_INT.fieldOf("energy").forGetter(r -> r.energy),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("delay", 1).forGetter(r -> r.delay))
            .apply(i, RefineryRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, RefineryRecipe> STREAM_CODEC = StreamCodec.composite(
            FluidAmount.STREAM_CODEC, r -> r.ingredient1,
            ByteBufCodecs.optional(FluidAmount.STREAM_CODEC), r -> r.ingredient2,
            FluidAmount.STREAM_CODEC, r -> r.result,
            ByteBufCodecs.VAR_INT, r -> r.energy,
            ByteBufCodecs.VAR_INT, r -> r.delay,
            RefineryRecipe::new);
    public static final RecipeSerializer<RefineryRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    public final FluidAmount ingredient1;
    public final Optional<FluidAmount> ingredient2;
    public final FluidAmount result;
    /** FE per cycle (4.2.2: MJ; × 10). */
    public final int energy;
    /** Ticks between cycles. */
    public final int delay;
    private RefineryRecipes.@Nullable Recipe recipe;

    public RefineryRecipe(FluidAmount ingredient1, Optional<FluidAmount> ingredient2, FluidAmount result, int energy,
            int delay) {
        this.ingredient1 = ingredient1;
        this.ingredient2 = ingredient2;
        this.result = result;
        this.energy = energy;
        this.delay = delay;
    }

    /** The 4.2.2 recipe the refinery runs, made on first use. */
    public RefineryRecipes.Recipe getRecipe() {
        if (recipe == null) {
            recipe = RefineryRecipes.createRecipe(ingredient1.toStack(),
                    ingredient2.map(FluidAmount::toStack).orElse(null), result.toStack(), energy, delay);
        }
        return recipe;
    }

    @Override
    public boolean matches(RecipeInput input, Level level) {
        return false;
    }

    @Override
    public ItemStack assemble(RecipeInput input) {
        return ItemStack.EMPTY;
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
    public RecipeSerializer<RefineryRecipe> getSerializer() {
        return FactoryRegistry.refineryRecipeSerializer.get();
    }

    @Override
    public RecipeType<RefineryRecipe> getType() {
        return FactoryRegistry.refineryRecipeType.get();
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
