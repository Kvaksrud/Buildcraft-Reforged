/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: the pipe is the pipe block entity.
 */

package buildcraftreforged.transport;

import buildcraftreforged.api.gates.ITriggerParameter;
import org.jspecify.annotations.Nullable;

/** A trigger that reads the pipe holding the gate instead of its neighbours. */
public interface ITriggerPipe {

    public boolean isTriggerActive(TilePipe pipe, @Nullable ITriggerParameter parameter);

}
