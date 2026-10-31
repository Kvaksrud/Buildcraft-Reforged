/*
 * Buildcraft Reforged, 2026: new file, no 4.2.2 original. Entry points of the status panel (plan section 10).
 */
package buildcraftreforged.core.status;

import net.minecraft.core.BlockPos;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Opening the panel of a block without a GUI, and the red-light hook for blocked machines.
 *
 * <p><b>Red light.</b> A block whose machine can be blocked adds {@link #BLOCKED} to its state definition. While its
 * block entity is an {@link IStatusProvider} and a {@code TileBuildCraft}, the server keeps the property equal to
 * {@code getStatusState() == BLOCKED} every tick ({@link #updateBlockedLight}), and the block's model shows the light
 * with a multipart case: {@code {"when": {"blocked": "true"}, "apply": {"model": "buildcraftreforged:block/status_light"}}}.
 */
public final class StatusPanel {
    /** True while the machine is blocked; drives the red light on the model. */
    public static final BooleanProperty BLOCKED = BooleanProperty.create("blocked");

    private StatusPanel() {
    }

    /**
     * Opens the status panel of tile for player: the right-click action of a working block without a GUI. Call it from
     * {@code useWithoutItem} on the server and return {@code InteractionResult.SUCCESS} on both sides.
     */
    public static <T extends BlockEntity & IStatusProvider> void open(Player player, T tile) {
        if (player.level().isClientSide()) {
            return;
        }
        player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> new ContainerStatus(containerId, tile),
                tile.getBlockState().getBlock().getName()), tile.getBlockPos());
    }

    /**
     * Sets {@link #BLOCKED} on tile's block from its State line, if the block has the property and it changed. Server
     * side; {@code TileBuildCraft.updateEntity} calls it every tick for an {@link IStatusProvider}.
     */
    public static void updateBlockedLight(BlockEntity tile) {
        Level level = tile.getLevel();
        if (level == null || level.isClientSide() || !(tile instanceof IStatusProvider provider)) {
            return;
        }
        BlockState state = tile.getBlockState();
        if (!state.hasProperty(BLOCKED)) {
            return;
        }
        boolean blocked = provider.getStatusState() == MachineStatus.BLOCKED;
        if (state.getValue(BLOCKED) != blocked) {
            BlockPos pos = tile.getBlockPos();
            level.setBlock(pos, state.setValue(BLOCKED, blocked), Block.UPDATE_ALL);
        }
    }
}
