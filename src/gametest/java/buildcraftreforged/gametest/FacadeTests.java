package buildcraftreforged.gametest;

import buildcraftreforged.api.transport.IPipeTile.PipeType;
import buildcraftreforged.core.network.PacketData;
import buildcraftreforged.transport.ItemFacade;
import buildcraftreforged.transport.PipeConnections;
import buildcraftreforged.transport.PipeMaterial;
import buildcraftreforged.transport.TilePipe;
import buildcraftreforged.transport.TransportRegistry;
import buildcraftreforged.transport.graph.PipeNetwork;
import buildcraftreforged.transport.graph.PipeNetworks;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * #61: facades and plugs. Which blocks make facades and the log turning recipe; fitting and taking off facades by
 * using the item and sneaking (they do not touch connections); a plug cutting a connection and the network and
 * taking it off restoring both; saving, the client payload and the drops when the pipe breaks. In the padded area
 * (see {@link OilFuelTests#PADDING}).
 */
final class FacadeTests {
    static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();
    static final int MAX_TICKS = 100;

    static {
        TESTS.put("facade_rules_and_recipe", FacadeTests::rulesAndRecipe);
        TESTS.put("facade_place_and_strip", FacadeTests::placeAndStrip);
        TESTS.put("plug_blocks_connection", FacadeTests::plugBlocksConnection);
        TESTS.put("facade_plug_save_sync_and_drops", FacadeTests::saveSyncAndDrops);
    }

    private static final int SETTLE = 3;
    private static final BlockPos MIDDLE = new BlockPos(2, 2, 2);

    private FacadeTests() {
    }

    private static BlockState itemPipe() {
        return TransportRegistry.getPipe(PipeType.ITEM, PipeMaterial.STONE).defaultBlockState();
    }

    private static TilePipe tile(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos, TilePipe.class);
    }

    private static BlockState log(Direction.Axis axis) {
        return Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, axis);
    }

    /** A click at {@code local} (block coordinates) on the {@code face} of the block at {@code pos}. */
    private static UseOnContext click(GameTestHelper helper, Player player, BlockPos pos, double x, double y, double z,
            Direction face) {
        BlockPos absolute = helper.absolutePos(pos);
        return new UseOnContext(player, InteractionHand.MAIN_HAND, hit(absolute, x, y, z, face));
    }

    private static BlockHitResult hit(BlockPos absolute, double x, double y, double z, Direction face) {
        Vec3 at = new Vec3(absolute.getX() + x, absolute.getY() + y, absolute.getZ() + z);
        return new BlockHitResult(at, face, absolute, false);
    }

    private static boolean use(GameTestHelper helper, Player player, ItemStack stack, BlockPos pos, double x, double y,
            double z, Direction face) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return stack.useOn(click(helper, player, pos, x, y, z, face)).consumesAction();
    }

    private static List<ItemEntity> drops(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(8));
    }

    private static void clearDrops(GameTestHelper helper) {
        for (ItemEntity entity : drops(helper))
            entity.discard();
    }

    private static void assertMask(GameTestHelper helper, BlockPos pos, String what, Direction... sides) {
        int expected = 0;
        for (Direction side : sides) {
            expected |= PipeConnections.bit(side);
        }
        int actual = tile(helper, pos).getConnectionMask();
        helper.assertTrue(actual == expected, what + ": mask " + Integer.toBinaryString(actual) + ", expected "
                + Integer.toBinaryString(expected));
    }

    /**
     * 4.2.2's rules on block states: solid full model blocks without a block entity, glass allowed, the listed
     * exceptions refused; a log once per axis; "Facade: Stone"; the turning recipe cycles a log facade's axis Y, X, Z.
     */
    static void rulesAndRecipe(GameTestHelper helper) {
        for (Block allowed : List.of(Blocks.STONE, Blocks.DIRT, Blocks.GLASS, Blocks.OAK_PLANKS, Blocks.STONE_BRICKS,
                Blocks.OAK_LOG)) {
            helper.assertTrue(ItemFacade.isFacadeBlock(allowed), allowed + " should make a facade");
        }
        for (Block refused : List.of(Blocks.GRASS_BLOCK, Blocks.BEDROCK, Blocks.SPONGE, Blocks.REDSTONE_LAMP,
                Blocks.JACK_O_LANTERN, Blocks.OAK_LEAVES, Blocks.CHEST, Blocks.FURNACE, Blocks.TORCH, Blocks.STONE_SLAB,
                Blocks.AIR, Blocks.WATER)) {
            helper.assertFalse(ItemFacade.isFacadeBlock(refused), refused + " should not make a facade");
        }

        List<ItemStack> all = ItemFacade.getAllFacades();
        for (Direction.Axis axis : Direction.Axis.values()) {
            helper.assertTrue(all.stream().anyMatch(s -> log(axis).equals(ItemFacade.getBlockState(s))),
                    "no oak log facade along " + axis);
        }
        helper.assertTrue(ItemFacade.isFacadeState(Blocks.STONE.defaultBlockState()), "stone facade missing");
        helper.assertFalse(ItemFacade.isFacadeState(Blocks.GRASS_BLOCK.defaultBlockState()), "grass facade listed");
        String name = ItemFacade.getStack(Blocks.STONE.defaultBlockState()).getHoverName().getString();
        helper.assertTrue(name.endsWith(": Stone"), "facade name " + name);

        ItemFacade.FacadeRecipe recipe = ItemFacade.FacadeRecipe.INSTANCE;
        Direction.Axis[] cycle = {Direction.Axis.Y, Direction.Axis.X, Direction.Axis.Z, Direction.Axis.Y};
        for (int i = 0; i < 3; i++) {
            CraftingInput input = CraftingInput.of(2, 2, List.of(ItemStack.EMPTY, ItemFacade.getStack(log(cycle[i])),
                    ItemStack.EMPTY, ItemStack.EMPTY));
            helper.assertTrue(recipe.matches(input, helper.getLevel()), "log facade along " + cycle[i] + " not turned");
            helper.assertValueEqual(ItemFacade.getBlockState(recipe.assemble(input)), log(cycle[i + 1]),
                    "turned facade");
        }
        ItemStack stone = ItemFacade.getStack(Blocks.STONE.defaultBlockState());
        helper.assertFalse(recipe.matches(CraftingInput.of(1, 1, List.of(stone)), helper.getLevel()),
                "a stone facade turns");
        helper.assertFalse(recipe.matches(CraftingInput.of(2, 1, List.of(ItemFacade.getStack(log(Direction.Axis.Y)),
                stone)), helper.getLevel()), "two facades turn");
        ResourceKey<net.minecraft.world.item.crafting.Recipe<?>> key = ResourceKey.create(Registries.RECIPE,
                Identifier.fromNamespaceAndPath("buildcraftreforged", "facade_rotation"));
        helper.assertTrue(helper.getLevel().getServer().getRecipeManager().byKey(key).isPresent(),
                "the turning recipe is not loaded");
        helper.succeed();
    }

    /**
     * Using a facade on the pipe centre puts it on the clicked face, on an arm on that arm's side, on another block's
     * face on the pipe beside it; the facade makes the side solid and collides, and leaves connections alone.
     * Sneaking with an empty hand, or with a facade, on a facade takes it off and drops it.
     */
    static void placeAndStrip(GameTestHelper helper) {
        helper.setBlock(MIDDLE, itemPipe());
        helper.setBlock(MIDDLE.east(), itemPipe());
        helper.setBlock(MIDDLE.north(), Blocks.DIRT);

        helper.runAfterDelay(SETTLE, () -> {
            assertMask(helper, MIDDLE, "before facades", Direction.EAST);
            TilePipe pipe = tile(helper, MIDDLE);
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            BlockState stone = Blocks.STONE.defaultBlockState();
            ItemStack stoneFacades = ItemFacade.getStack(stone).copyWithCount(3);

            helper.assertTrue(use(helper, player, stoneFacades, MIDDLE, 0.5, 0.75, 0.5, Direction.UP),
                    "facade not fitted on the centre");
            helper.assertValueEqual(pipe.getFacade(Direction.UP), stone, "facade on top");
            helper.assertValueEqual(stoneFacades.getCount(), 2, "facades left");
            helper.assertFalse(use(helper, player, stoneFacades, MIDDLE, 0.5, 1.0, 0.5, Direction.UP),
                    "the same facade went on twice");

            ItemStack dirtFacade = ItemFacade.getStack(Blocks.DIRT.defaultBlockState());
            helper.assertTrue(use(helper, player, dirtFacade, MIDDLE, 0.875, 0.75, 0.5, Direction.UP),
                    "facade not fitted on the east arm");
            helper.assertValueEqual(pipe.getFacade(Direction.EAST), Blocks.DIRT.defaultBlockState(), "facade east");

            // 4.2.2 onItemUse: a click on the dirt's face towards the pipe.
            helper.assertTrue(use(helper, player, stoneFacades, MIDDLE.north(), 0.5, 0.5, 1.0, Direction.SOUTH),
                    "facade not fitted from the neighbouring block");
            helper.assertValueEqual(pipe.getFacade(Direction.NORTH), stone, "facade north");

            ItemStack grass = ItemFacade.getStack(Blocks.GRASS_BLOCK.defaultBlockState());
            helper.assertFalse(use(helper, player, grass, MIDDLE, 0.5, 0.25, 0.5, Direction.DOWN),
                    "a grass facade went on");
            helper.assertFalse(pipe.hasFacade(Direction.DOWN), "facade below");

            BlockPos absolute = helper.absolutePos(MIDDLE);
            BlockState state = helper.getLevel().getBlockState(absolute);
            helper.assertTrue(pipe.isSolidOnSide(Direction.UP) && !pipe.isSolidOnSide(Direction.DOWN), "solid sides");
            helper.assertValueEqual(state.getCollisionShape(helper.getLevel(), absolute, CollisionContext.empty())
                    .max(Direction.Axis.Y), 1.0, "collision top with a facade");
            helper.assertTrue(state.isFaceSturdy(helper.getLevel(), absolute, Direction.UP),
                    "a torch cannot stand on the facade");

            helper.runAfterDelay(SETTLE, () -> {
                assertMask(helper, MIDDLE, "a facade does not cut the connection", Direction.EAST);
                assertMask(helper, MIDDLE.east(), "nor on the other pipe", Direction.WEST);

                clearDrops(helper);
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                player.setShiftKeyDown(true);
                helper.assertTrue(state.useWithoutItem(helper.getLevel(), player,
                        hit(absolute, 0.5, 1.0, 0.5, Direction.UP)).consumesAction(), "facade not stripped");
                helper.assertFalse(pipe.hasFacade(Direction.UP), "top facade still there");
                helper.assertTrue(pipe.hasFacade(Direction.EAST) && pipe.hasFacade(Direction.NORTH),
                        "stripping took other facades");
                List<ItemEntity> dropped = drops(helper);
                helper.assertTrue(dropped.size() == 1 && stone.equals(ItemFacade.getBlockState(dropped.get(0).getItem())),
                        "stone facade not dropped");

                clearDrops(helper);
                // The dirt facade stack was used up fitting it; sneak-use another facade on it.
                helper.assertTrue(use(helper, player, ItemFacade.getStack(stone), MIDDLE, 1.0, 0.3, 0.2, Direction.EAST),
                        "sneaking with a facade did not take the east one off");
                helper.assertFalse(pipe.hasFacade(Direction.EAST), "east facade still there");
                helper.assertValueEqual(drops(helper).size(), 1, "east facade drops");
                helper.succeed();
            });
        });
    }

    /**
     * A plug fitted on an arm cuts that connection on both pipes and splits the network; the plug's box is in the
     * outline but not the collision shape. Sneak-using a plug on it takes it off, drops it, and restores both.
     */
    static void plugBlocksConnection(GameTestHelper helper) {
        BlockPos west = MIDDLE.west();
        BlockPos east = MIDDLE.east();
        helper.setBlock(west.west(), Blocks.CHEST);
        helper.setBlock(west, itemPipe());
        helper.setBlock(MIDDLE, itemPipe());
        helper.setBlock(east, itemPipe());

        helper.runAfterDelay(SETTLE, () -> {
            PipeNetwork before = PipeNetworks.get(helper.getLevel(), helper.absolutePos(west));
            helper.assertTrue(before != null && before.size() == 3, "three pipes in one network: " + before);

            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            ItemStack plugs = new ItemStack(TransportRegistry.plugItem.get(), 2);
            helper.assertTrue(use(helper, player, plugs, MIDDLE, 0.875, 0.75, 0.5, Direction.UP), "plug not fitted");
            TilePipe pipe = tile(helper, MIDDLE);
            helper.assertTrue(pipe.hasPlug(Direction.EAST), "plug not on the east arm");
            helper.assertValueEqual(plugs.getCount(), 1, "plugs left");
            helper.assertFalse(use(helper, player, plugs, MIDDLE, 0.875, 0.5, 0.5, Direction.EAST),
                    "a click on the plug fitted another");

            helper.runAfterDelay(SETTLE, () -> {
                assertMask(helper, MIDDLE, "plugged east", Direction.WEST);
                assertMask(helper, east, "the far pipe", new Direction[0]);
                PipeNetwork split = PipeNetworks.get(helper.getLevel(), helper.absolutePos(west));
                helper.assertTrue(split != null && split != before && split.size() == 2, "network not split: " + split);
                PipeNetwork alone = PipeNetworks.get(helper.getLevel(), helper.absolutePos(east));
                helper.assertTrue(alone != null && alone.size() == 1, "far pipe not on its own: " + alone);

                BlockPos absolute = helper.absolutePos(MIDDLE);
                BlockState state = helper.getLevel().getBlockState(absolute);
                helper.assertValueEqual(state.getShape(helper.getLevel(), absolute).max(Direction.Axis.X), 0.875,
                        "outline with the plug");
                helper.assertValueEqual(state.getCollisionShape(helper.getLevel(), absolute, CollisionContext.empty())
                        .max(Direction.Axis.X), 0.75, "collision without the plug");

                clearDrops(helper);
                player.setShiftKeyDown(true);
                helper.assertTrue(use(helper, player, plugs, MIDDLE, 0.875, 0.5, 0.5, Direction.EAST),
                        "plug not taken off");
                helper.assertFalse(pipe.hasPlug(Direction.EAST), "plug still there");
                List<ItemEntity> dropped = drops(helper);
                helper.assertTrue(dropped.size() == 1 && dropped.get(0).getItem().is(TransportRegistry.plugItem.get()),
                        "plug not dropped");

                helper.runAfterDelay(SETTLE, () -> {
                    assertMask(helper, MIDDLE, "unplugged", Direction.WEST, Direction.EAST);
                    PipeNetwork after = PipeNetworks.get(helper.getLevel(), helper.absolutePos(east));
                    helper.assertTrue(after != null && after.size() == 3, "network not joined again: " + after);
                    helper.succeed();
                });
            });
        });
    }

    /** Facades and plugs survive saving and loading, reach the client in the payload, and drop when the pipe breaks. */
    static void saveSyncAndDrops(GameTestHelper helper) {
        helper.setBlock(MIDDLE, itemPipe());
        TilePipe pipe = tile(helper, MIDDLE);
        BlockState logX = log(Direction.Axis.X);
        helper.assertTrue(pipe.addFacade(Direction.UP, logX), "facade not added");
        helper.assertTrue(pipe.addFacade(Direction.SOUTH, Blocks.GLASS.defaultBlockState()), "glass facade not added");
        helper.assertTrue(pipe.addPlug(Direction.DOWN), "plug not added");
        helper.assertFalse(pipe.addPlug(Direction.DOWN), "plug added twice");

        RegistryAccess registryAccess = helper.getLevel().registryAccess();
        CompoundTag tag = pipe.saveWithFullMetadata(registryAccess);
        TilePipe loaded = new TilePipe(pipe.getBlockPos(), pipe.getBlockState());
        loaded.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registryAccess, tag));
        helper.assertValueEqual(loaded.getFacade(Direction.UP), logX, "facade loaded");
        helper.assertValueEqual(loaded.getFacade(Direction.SOUTH), Blocks.GLASS.defaultBlockState(), "glass loaded");
        helper.assertTrue(loaded.hasPlug(Direction.DOWN) && !loaded.hasPlug(Direction.UP), "plug loaded");
        helper.assertFalse(loaded.hasFacade(Direction.NORTH), "facade loaded where there was none");

        TilePipe client = new TilePipe(pipe.getBlockPos(), pipe.getBlockState());
        byte[] bytes = PacketData.write(registryAccess, pipe::writePacketPayload);
        RegistryFriendlyByteBuf data = PacketData.wrap(bytes, registryAccess);
        try {
            client.readPacketPayload(data);
        } finally {
            data.release();
        }
        helper.assertValueEqual(client.getFacade(Direction.UP), logX, "facade synced");
        helper.assertValueEqual(client.getFacadeMask(), PipeConnections.bit(Direction.UP)
                | PipeConnections.bit(Direction.SOUTH), "facade sides synced");
        helper.assertTrue(client.hasPlug(Direction.DOWN), "plug synced");

        clearDrops(helper);
        helper.setBlock(MIDDLE, Blocks.AIR);
        List<ItemEntity> dropped = drops(helper);
        helper.assertTrue(dropped.stream().anyMatch(e -> logX.equals(ItemFacade.getBlockState(e.getItem()))),
                "log facade not dropped");
        helper.assertTrue(dropped.stream().anyMatch(
                e -> Blocks.GLASS.defaultBlockState().equals(ItemFacade.getBlockState(e.getItem()))),
                "glass facade not dropped");
        helper.assertTrue(dropped.stream().anyMatch(e -> e.getItem().is(TransportRegistry.plugItem.get())),
                "plug not dropped");
        helper.succeed();
    }
}
