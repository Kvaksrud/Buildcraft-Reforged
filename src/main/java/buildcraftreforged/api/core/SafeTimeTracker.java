/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: World becomes Level, getTotalWorldTime becomes getGameTime; the time
 * comparison moved into overloads that take the game time, for unit tests (public, so PowerHandler's tests can
 * drive it).
 */
package buildcraftreforged.api.core;

import net.minecraft.world.level.Level;

public class SafeTimeTracker {

    private long lastMark = Long.MIN_VALUE;
    private long duration = -1;

    /**
     * Return true if a given delay has passed since last time marked was called
     * successfully.
     */
    public boolean markTimeIfDelay(Level world, long delay) {
        if (world == null)
            return false;

        return markTimeIfDelay(world.getGameTime(), delay);
    }

    public boolean markTimeIfDelay(long currentTime, long delay) {
        if (currentTime < lastMark) {
            lastMark = currentTime;
            return false;
        } else if (lastMark + delay <= currentTime) {
            duration = currentTime - lastMark;
            lastMark = currentTime;
            return true;
        } else
            return false;

    }

    public long durationOfLastDelay() {
        return duration > 0 ? duration : 0;
    }

    public void markTime(Level world) {
        markTime(world.getGameTime());
    }

    void markTime(long currentTime) {
        lastMark = currentTime;
    }
}
