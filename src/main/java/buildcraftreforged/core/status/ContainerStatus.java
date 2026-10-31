/*
 * Buildcraft Reforged, 2026: new file, no 4.2.2 original. The menu behind the status panel of a block without a GUI
 * (plan section 10).
 */
package buildcraftreforged.core.status;

import buildcraftreforged.core.CoreRegistry;
import buildcraftreforged.core.gui.BuildCraftContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/** A menu with no slots, only a {@link StatusWidget} that is watched while the menu is open. */
public class ContainerStatus extends BuildCraftContainer {
    /** Client side: stands in for the block entity, which only the server reads. */
    private static final IStatusProvider CLIENT_PROVIDER = new IStatusProvider() {
    };

    public final BlockPos pos;
    public final @Nullable BlockEntity tile;
    public final StatusWidget statusWidget;

    /** Client side: the block position comes with the open-menu packet. */
    public ContainerStatus(int containerId, Inventory inventoryplayer, RegistryFriendlyByteBuf extraData) {
        this(containerId, extraData.readBlockPos(), null, CLIENT_PROVIDER);
    }

    /** Server side. */
    public <T extends BlockEntity & IStatusProvider> ContainerStatus(int containerId, T tile) {
        this(containerId, tile.getBlockPos(), tile, tile);
    }

    private ContainerStatus(int containerId, BlockPos pos, @Nullable BlockEntity tile, IStatusProvider provider) {
        super(CoreRegistry.statusMenu.get(), containerId, 0);
        this.pos = pos;
        this.tile = tile;
        statusWidget = new StatusWidget(provider, true);
        addWidget(statusWidget);
    }

    @Override
    public boolean stillValid(Player player) {
        return tile != null && Container.stillValidBlockEntity(tile, player);
    }
}
