package buildcraftreforged.gametest;

import buildcraftreforged.core.CoreRegistry;
import buildcraftreforged.core.WrenchHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/** #28: the wrench rotates blocks, and any item in {@code c:tools/wrench} counts as a wrench on our blocks. */
final class WrenchTests {
    private static final BlockPos POS = new BlockPos(1, 1, 1);

    private WrenchTests() {
    }

    static void wrenchRotatesBlock(GameTestHelper helper) {
        helper.setBlock(POS, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.NORTH));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack wrench = new ItemStack(CoreRegistry.wrenchItem.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, wrench);

        // Clicking the top face turns the block around the vertical axis: north becomes east.
        InteractionResult result = wrench.onItemUseFirst(useOn(helper, player, Direction.UP));
        helper.assertTrue(result.consumesAction(), "wrench did not rotate the dispenser: " + result);
        helper.assertBlockProperty(POS, DispenserBlock.FACING, Direction.EAST);

        // Sneaking passes the click to the block, as in 4.2.2, except for levers, buttons and chests.
        player.setShiftKeyDown(true);
        result = wrench.onItemUseFirst(useOn(helper, player, Direction.UP));
        helper.assertFalse(result.consumesAction(), "sneaking wrench click rotated the dispenser");
        helper.assertBlockProperty(POS, DispenserBlock.FACING, Direction.EAST);
        helper.succeed();
    }

    static void wrenchTag(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(POS);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CoreRegistry.wrenchItem.get()));
        helper.assertTrue(player.getMainHandItem().is(Tags.Items.TOOLS_WRENCH), "our wrench is not in c:tools/wrench");
        helper.assertTrue(WrenchHelper.canWrench(player, InteractionHand.MAIN_HAND, pos), "our wrench is refused");

        // The GameTest data pack puts the debug stick in c:tools/wrench, standing in for another mod's wrench.
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DEBUG_STICK));
        helper.assertTrue(WrenchHelper.canWrench(player, InteractionHand.MAIN_HAND, pos), "a tagged foreign wrench is refused");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        helper.assertFalse(WrenchHelper.isWrench(player.getMainHandItem()), "a plain stick counts as a wrench");
        helper.succeed();
    }

    private static UseOnContext useOn(GameTestHelper helper, Player player, Direction face) {
        BlockPos pos = helper.absolutePos(POS);
        return new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos).relative(face, 0.5), face, pos, false));
    }
}
