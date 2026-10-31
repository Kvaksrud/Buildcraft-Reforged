/*
 * Ported from buildcraft/core/IBuilderInventory.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: IInventory becomes Container.
 */
package buildcraftreforged.core;

import net.minecraft.world.Container;

public interface IBuilderInventory extends Container {

    public boolean isBuildingMaterial(int i);

}
