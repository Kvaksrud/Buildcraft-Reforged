/*
 * Modified by Buildcraft Reforged, 2026: ported from buildcraft/api/transport/FacadeManager.java (no file header at
 * 4.2.2). The reflective call into ItemFacade.addFacade(ItemStack) becomes a list of block states that ItemFacade
 * reads when it builds its facade list; the "add-facade:id@meta" message is gone with numeric ids.
 */

package buildcraftreforged.api.transport;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Adds a facade for a block state that the facade rules would not pick (a facade may carry any state added here).
 * Call it during mod loading: the facade list is built the first time it is needed, after the registries are frozen.
 */
public class FacadeManager {

    private static final List<BlockState> addedFacades = new CopyOnWriteArrayList<>();

    public static void addFacade(BlockState state) {
        if (!addedFacades.contains(state))
            addedFacades.add(state);
    }

    public static List<BlockState> getAddedFacades() {
        return List.copyOf(addedFacades);
    }
}
