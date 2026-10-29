/*
 * Ported from buildcraft/core/utils/INBTTagable.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: NBTTagCompound becomes ValueInput/ValueOutput, the block entity save API,
 * so an implementation can be saved as a child of a block entity.
 */
package buildcraftreforged.core.utils;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public interface INBTTagable {

    void readFromNBT(ValueInput nbttagcompound);

    void writeToNBT(ValueOutput nbttagcompound);
}
