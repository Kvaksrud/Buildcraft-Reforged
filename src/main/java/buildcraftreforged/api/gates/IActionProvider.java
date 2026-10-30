/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: BlockEntity in place of TileEntity.
 */

package buildcraftreforged.api.gates;

import java.util.LinkedList;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

public interface IActionProvider {

    /**
     * Returns the list of actions available to a gate next to the given block.
     */
    public abstract @Nullable LinkedList<IAction> getNeighborActions(Block block, @Nullable BlockEntity tile);

}
