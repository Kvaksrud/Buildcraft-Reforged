/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: no longer an Entity. It is the render data of a textured box, held by the
 * block entity that made it and drawn by that block entity's BlockEntityRenderer through RenderEntityBlock; nothing is
 * spawned in the world, so there is no entity type, tracking or NBT. The Icon texture becomes a texture Identifier.
 */
package buildcraftreforged.core;

import buildcraftreforged.BuildcraftReforged;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public class EntityBlock {

    /** 4.2.2's BuildCraftCore.redLaserTexture, blueLaserTexture and stripesLaserTexture, set by Utils.createLaser. */
    public static final Identifier redLaserTexture = texture("red_laser");
    public static final Identifier blueLaserTexture = texture("blue_laser");
    public static final Identifier stripesLaserTexture = texture("stripes_laser");

    public @Nullable Identifier texture;
    public float rotationX = 0;
    public float rotationY = 0;
    public float rotationZ = 0;
    public double iSize, jSize, kSize;
    public double posX, posY, posZ;
    public boolean isDead = false;
    private int brightness = -1;
    public final @Nullable Level worldObj;

    public EntityBlock(@Nullable Level world) {
        this.worldObj = world;
    }

    public EntityBlock(@Nullable Level world, double xPos, double yPos, double zPos) {
        this(world);
        setPosition(xPos, yPos, zPos);
    }

    public EntityBlock(@Nullable Level world, double i, double j, double k, double iSize, double jSize, double kSize) {
        this(world);
        this.iSize = iSize;
        this.jSize = jSize;
        this.kSize = kSize;
        setPosition(i, j, k);
    }

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "textures/block/" + name + ".png");
    }

    public void setPosition(double d, double d1, double d2) {
        posX = d;
        posY = d1;
        posZ = d2;
    }

    public void moveEntity(double d, double d1, double d2) {
        setPosition(posX + d, posY + d1, posZ + d2);
    }

    public void setBrightness(int brightness) {
        this.brightness = brightness;
    }

    public void setDead() {
        isDead = true;
    }

    /** Packed light for rendering, or -1 to use the light at the box's position. */
    public int getBrightnessForRender() {
        return brightness;
    }
}
