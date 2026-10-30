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

import buildcraftreforged.api.transport.IPipe;
import java.util.LinkedList;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

public interface ITriggerProvider {

    /**
     * Returns the list of triggers that are available from the pipe holding the gate.
     */
    public abstract @Nullable LinkedList<ITrigger> getPipeTriggers(IPipe pipe);

    /**
     * Returns the list of triggers available to a gate next to the given block.
     */
    public abstract @Nullable LinkedList<ITrigger> getNeighborTriggers(Block block, @Nullable BlockEntity tile);

}
