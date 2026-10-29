/*
 * Modified by Buildcraft Reforged, 2026: ported from buildcraft/api/transport/ISolidSideTile.java (no file header at
 * 4.2.2); Direction in place of ForgeDirection.
 */

package buildcraftreforged.api.transport;

import net.minecraft.core.Direction;

/**
 * A block entity that reports solid sides, so torches and levers can sit on a pipe with a facade. Ported from 4.2.2
 * {@code buildcraft.api.transport.ISolidSideTile}. Expected to survive plan section 8 (facades are kept, 8.5).
 */
public interface ISolidSideTile {
    public boolean isSolidOnSide(Direction side);
}
