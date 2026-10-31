/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: render data instead of an Entity (see EntityLaser); power is FE as long
 * (4.2.2 MJ × 10), so the texture thresholds 1, 2 and 3 MJ/t become 10, 20 and 30 FE/t; the data watcher slot 15
 * becomes writeData/readData.
 */
package buildcraftreforged.core;

import static buildcraftreforged.core.EntityLaser.LASER_TEXTURES;

import buildcraftreforged.api.core.Position;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public class EntityEnergyLaser extends EntityLaser {

    public static final short POWER_AVERAGING = 100;
    public int displayStage = 0;
    private final long power[] = new long[POWER_AVERAGING];
    private int powerIndex = 0;
    private long powerAverage = 0;

    public EntityEnergyLaser(@Nullable Level world) {
        super(world);
    }

    public EntityEnergyLaser(@Nullable Level world, Position head, Position tail) {
        super(world, head, tail);
    }

    public void pushPower(long received) {

        powerAverage -= power[powerIndex];
        powerAverage += received;
        power[powerIndex] = received;
        powerIndex++;

        if (powerIndex == power.length) {
            powerIndex = 0;
        }
    }

    /** FE per tick over the last POWER_AVERAGING ticks. */
    public float getPowerAverage() {
        return (float) powerAverage / POWER_AVERAGING;
    }

    @Override
    public Identifier getTexture() {
        if (getPowerAverage() <= 10.0)
            return LASER_TEXTURES[0];
        else if (getPowerAverage() <= 20.0)
            return LASER_TEXTURES[1];
        else if (getPowerAverage() <= 30.0)
            return LASER_TEXTURES[2];
        else
            return LASER_TEXTURES[3];
    }

    @Override
    public void readData(RegistryFriendlyByteBuf data) {
        super.readData(data);
        powerAverage = data.readLong();
    }

    @Override
    public void writeData(RegistryFriendlyByteBuf data) {
        super.writeData(data);
        data.writeLong(powerAverage);
    }
}
