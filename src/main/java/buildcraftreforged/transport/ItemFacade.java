/*
 * Ported from buildcraft/transport/ItemFacade.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: the block id and metadata in the item's NBT become a captured BlockState in
 * the buildcraftreforged:facade data component; initialize() reads the block registry instead of Block's static fields
 * by reflection, with 4.2.2's rules on block states (a solid, full, model-rendered block without a block entity; glass
 * allowed; bedrock, grass, oak leaves, sponge, redstone lamps and jack o'lanterns refused), and a block's subtypes
 * become the three axes of a pillar block (4.2.2: the rotated logs); the facade list is built on first use. Fitting
 * on a pipe (4.2.2 BlockGenericPipe.addOrStripFacade) is the item's useOn. FacadeRecipe, the crafting-table
 * turning helper for logs, is a CustomRecipe that cycles the axis. The assembly-table recipe of addFacade waits for
 * the Cobblestone Structure Pipe (docs/PORTING-NOTES.md, #61). Icons and the item renderer are a special item model
 * (FacadeItemRenderer).
 */
package buildcraftreforged.transport;

import buildcraftreforged.api.transport.FacadeManager;
import buildcraftreforged.core.ItemBuildCraft;
import buildcraftreforged.transport.PipeParts.Part;
import buildcraftreforged.transport.PipeParts.RaytraceResult;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class ItemFacade extends ItemBuildCraft {

    private static volatile @Nullable List<ItemStack> allFacades;
    private static volatile Set<BlockState> allFacadeStates = Set.of();

    public ItemFacade(Properties properties) {
        super(properties);
        setPassSneakClick(true);
    }

    /** 4.2.2 {@code getItemDisplayName}: "Facade: Stone". The axis of a pillar block is not named, as 4.2.2 masked it. */
    @Override
    public Component getName(ItemStack itemstack) {
        Component name = super.getName(itemstack);
        BlockState state = getBlockState(itemstack);
        if (state == null)
            return name;
        return Component.empty().append(name).append(": ").append(state.getBlock().getName());
    }

    /**
     * 4.2.2 {@code BlockGenericPipe.addOrStripFacade} for a click on a pipe, else 4.2.2 {@code onItemUse}: a click on
     * the face of another block puts the facade on the side of the pipe beside that face.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level worldObj = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (worldObj.getBlockEntity(pos) instanceof TilePipe pipe) {
            if (addOrStripFacade(pipe, context.getPlayer(), context.getItemInHand(), context.getClickLocation(),
                    context.getClickedFace()))
                return InteractionResult.SUCCESS;
            return super.useOn(context);
        }

        Direction side = context.getClickedFace();
        if (worldObj.getBlockEntity(pos.relative(side)) instanceof TilePipe pipeTile
                && addFacade(context.getPlayer(), context.getItemInHand(), pipeTile, side.getOpposite()))
            return InteractionResult.SUCCESS;
        return super.useOn(context);
    }

    /** Sneaking on a facade takes it off; a click on any other part of the pipe fits the facade on that side. */
    public static boolean addOrStripFacade(TilePipe pipe, @Nullable Player player, ItemStack stack, Vec3 hit,
            Direction side) {
        RaytraceResult rayTraceResult = PipeParts.doRayTrace(pipe, hit, side);
        if (player != null && player.isShiftKeyDown()) {
            if (rayTraceResult != null && rayTraceResult.hitPart() == Part.Facade) {
                if (pipe.dropFacade(rayTraceResult.sideHit()))
                    return true;
            }
        }
        if (rayTraceResult != null && rayTraceResult.hitPart() != Part.Facade) {
            if (addFacade(player, stack, pipe, rayTraceResult.sideHit() != null ? rayTraceResult.sideHit() : side))
                return true;
        }
        return false;
    }

    private static boolean addFacade(@Nullable Player player, ItemStack stack, TilePipe pipe, Direction side) {
        BlockState state = getBlockState(stack);
        if (state == null || !isFacadeState(state))
            return false;
        if (pipe.addFacade(side, state)) {
            if (!pipe.getLevel().isClientSide() && (player == null || !player.getAbilities().instabuild)) {
                stack.shrink(1);
            }
            return true;
        }
        return false;
    }

    // Which blocks may be facades (4.2.2 initialize)

    /**
     * Every facade, in block registry order: the facade-able blocks of {@link #isFacadeBlock}, a pillar block once per
     * axis (Y, X, Z, as 4.2.2's log metadata 0, 4, 8), then the states other mods added through
     * {@link FacadeManager}. Built on first use, after the registries are frozen.
     */
    public static List<ItemStack> getAllFacades() {
        List<ItemStack> facades = allFacades;
        if (facades == null) {
            facades = initialize();
        }
        List<ItemStack> copies = new ArrayList<>(facades.size());
        for (ItemStack stack : facades) {
            copies.add(stack.copy());
        }
        return copies;
    }

    private static synchronized List<ItemStack> initialize() {
        if (allFacades != null)
            return allFacades;
        Set<BlockState> states = new LinkedHashSet<>();
        for (Block b : BuiltInRegistries.BLOCK) {
            if (!isFacadeBlock(b))
                continue;
            BlockState base = b.defaultBlockState();
            if (base.hasProperty(BlockStateProperties.AXIS)) {
                // Special handling for logs
                states.add(base.setValue(BlockStateProperties.AXIS, Direction.Axis.Y));
                states.add(base.setValue(BlockStateProperties.AXIS, Direction.Axis.X));
                states.add(base.setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
            } else {
                states.add(base);
            }
        }
        states.addAll(FacadeManager.getAddedFacades());
        List<ItemStack> facades = new ArrayList<>(states.size());
        for (BlockState state : states) {
            facades.add(getStack(state));
        }
        allFacadeStates = Collections.unmodifiableSet(states);
        allFacades = Collections.unmodifiableList(facades);
        return allFacades;
    }

    /**
     * 4.2.2's rule for one block: glass explicitly; never bedrock, grass, oak leaves, sponge, redstone lamps or jack
     * o'lanterns; otherwise an opaque cube ({@code isSolidRender}) that renders as a normal block (a model with a full
     * collision cube) and has no block entity. The block also needs an item, as 4.2.2 built an ItemStack of it.
     */
    public static boolean isFacadeBlock(Block b) {
        if (b.asItem() == Items.AIR)
            return false;
        if (b != Blocks.GLASS) { //Explicitly allow glass
            if (b == Blocks.BEDROCK
                    || b == Blocks.GRASS_BLOCK
                    || b == Blocks.OAK_LEAVES
                    || b == Blocks.SPONGE
                    || b == Blocks.REDSTONE_LAMP
                    || b == Blocks.JACK_O_LANTERN) {
                return false;
            }
            BlockState state = b.defaultBlockState();
            try {
                if (!state.isSolidRender() || state.hasBlockEntity() || state.getRenderShape() != RenderShape.MODEL
                        || !state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
                    return false;
                }
            } catch (RuntimeException e) {
                // A block whose shape needs a real level is not a normal block.
                return false;
            }
        }
        return true;
    }

    /** Whether a facade may carry {@code state}: one of {@link #getAllFacades}. */
    public static boolean isFacadeState(BlockState state) {
        if (allFacades == null)
            initialize();
        return allFacadeStates.contains(state);
    }

    public static @Nullable BlockState getBlockState(ItemStack stack) {
        return stack.get(TransportRegistry.facadeState.get());
    }

    public static ItemStack getStack(BlockState state) {
        ItemStack stack = new ItemStack(TransportRegistry.facadeItem.get());
        stack.set(TransportRegistry.facadeState.get(), state);
        return stack;
    }

    /**
     * 4.2.2 {@code FacadeRecipe}, the facade turning helper: a single facade of a pillar block in the crafting grid
     * turns it to the next axis, Y to X to Z and back to Y.
     */
    public static class FacadeRecipe extends CustomRecipe {

        public static final FacadeRecipe INSTANCE = new FacadeRecipe();
        public static final MapCodec<FacadeRecipe> MAP_CODEC = MapCodec.unit(INSTANCE);
        public static final StreamCodec<RegistryFriendlyByteBuf, FacadeRecipe> STREAM_CODEC = StreamCodec.unit(INSTANCE);
        public static final RecipeSerializer<FacadeRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

        private static @Nullable ItemStack slotMatch(CraftingInput inventorycrafting) {
            ItemStack slotmatch = null;
            for (int i = 0; i < inventorycrafting.size(); i++) {
                ItemStack slot = inventorycrafting.getItem(i);
                if (slot.isEmpty())
                    continue;
                if (slot.getItem() instanceof ItemFacade && slotmatch == null) {
                    slotmatch = slot;
                } else {
                    return null;
                }
            }
            return slotmatch;
        }

        @Override
        public boolean matches(CraftingInput inventorycrafting, Level world) {
            ItemStack slotmatch = slotMatch(inventorycrafting);
            if (slotmatch == null)
                return false;
            BlockState state = getBlockState(slotmatch);
            return state != null && state.hasProperty(BlockStateProperties.AXIS);
        }

        @Override
        public ItemStack assemble(CraftingInput inventorycrafting) {
            ItemStack slotmatch = slotMatch(inventorycrafting);
            if (slotmatch == null)
                return ItemStack.EMPTY;
            BlockState state = getBlockState(slotmatch);
            if (state == null || !state.hasProperty(BlockStateProperties.AXIS))
                return ItemStack.EMPTY;
            Direction.Axis next = switch (state.getValue(BlockStateProperties.AXIS)) {
                case Y -> Direction.Axis.X;
                case X -> Direction.Axis.Z;
                case Z -> Direction.Axis.Y;
            };
            return getStack(state.setValue(BlockStateProperties.AXIS, next));
        }

        @Override
        public RecipeSerializer<FacadeRecipe> getSerializer() {
            return SERIALIZER;
        }
    }
}
