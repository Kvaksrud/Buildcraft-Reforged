/*
 * Copyright (c) SpaceToad, 2011-2012
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: package and ItemStack import only.
 */
package buildcraftreforged.api.filler;

import net.minecraft.world.item.ItemStack;

/**
 * Steps through a filler pattern one block at a time. Ported from 4.2.2 {@code buildcraft.api.filler.IPatternIterator}.
 *
 * @author CovertJaguar <http://www.railcraft.info/>
 */
public interface IPatternIterator {

    public boolean iteratePattern(ItemStack stackToPlace);
}
