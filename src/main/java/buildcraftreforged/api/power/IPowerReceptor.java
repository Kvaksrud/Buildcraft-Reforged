/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: ForgeDirection becomes Direction (null for 4.2.2's UNKNOWN), World becomes
 * Level.
 */
package buildcraftreforged.api.power;

import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * This interface should be implemented by any Tile Entity that wishes to be
 * able to receive power.
 *
 * A block entity type whose tiles implement it registers {@link PowerEnergyHandler#registerEnergyHandler} so that the
 * receivers are also Forge Energy handlers.
 *
 * @author CovertJaguar <http://www.railcraft.info/>
 */
public interface IPowerReceptor {

    /**
     * Get the PowerReceiver for this side of the block. You can return the same
     * PowerReceiver for all sides or one for each side.
     *
     * You should NOT return null to this method unless you mean to NEVER
     * receive power from that side. Returning null, after previous returning a
     * PowerReceiver, will most likely cause pipe connections to derp out and
     * engines to eventually explode.
     *
     * @param side the side, or null for no particular side
     * @return
     */
    public PowerHandler.@Nullable PowerReceiver getPowerReceiver(@Nullable Direction side);

    /**
     * Call back from the PowerHandler that is called when the stored power
     * exceeds the activation power.
     *
     * It can be triggered by update() calls or power modification calls.
     *
     * @param workProvider
     */
    public void doWork(PowerHandler workProvider);

    public @Nullable Level getWorld();
}
