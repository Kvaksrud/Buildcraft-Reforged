/**
 * Copyright (c) 2011-2014, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: PipePowerIron.PowerMode as a top-level enum, so the Power Limiter action
 * exists before the Director module (#55) that applies it; maxPower is in FE per tick (4.2.2 MJ x 10,
 * docs/TRANSPORT.md Q7).
 */
package buildcraftreforged.transport;

/** The Power Limiter modes: 20 to 1 280 FE/t (4.2.2: 2 to 128 MJ/t). The names stay, they are in the unique tags. */
public enum PowerMode {

    M2(20), M4(40), M8(80), M16(160), M32(320), M64(640), M128(1280);
    public static final PowerMode[] VALUES = values();
    public final int maxPower;

    private PowerMode(int max) {
        this.maxPower = max;
    }

    public PowerMode getNext() {
        PowerMode next = VALUES[(ordinal() + 1) % VALUES.length];
        return next;
    }

    public PowerMode getPrevious() {
        PowerMode previous = VALUES[(ordinal() + VALUES.length - 1) % VALUES.length];
        return previous;
    }

    public static PowerMode fromId(int id) {
        if (id < 0 || id >= VALUES.length)
            return M128;
        return VALUES[id];
    }
}
