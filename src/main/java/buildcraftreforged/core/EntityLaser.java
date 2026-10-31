/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: no longer an Entity, as EntityBlock. It is the render data of a beam, held by
 * the block entity that made it and drawn by that block entity's BlockEntityRenderer through core/render/RenderLaser;
 * nothing is spawned, so there is no bounding box, tracking or NBT (4.2.2 noted the NBT was useless). The data watcher
 * becomes writeData/readData, which the owning tile calls from its network payload. Positions are sent as doubles: the
 * 4.2.2 int encoding (d * 8192) overflowed beyond 262144 blocks from the origin.
 */
package buildcraftreforged.core;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.api.core.Position;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public abstract class EntityLaser {

    public static final Identifier[] LASER_TEXTURES = new Identifier[]{
        texture("laser_1"),
        texture("laser_2"),
        texture("laser_3"),
        texture("laser_4")};
    /** 4.2.2's getBrightnessForRender: 210, used as packed light as RenderMarker does. */
    public static final int BRIGHTNESS = 210;
    protected Position head, tail;
    public double renderSize = 0;
    public double angleY = 0;
    public double angleZ = 0;
    private boolean isVisible = false;
    protected boolean needsUpdate = true;
    public boolean isDead = false;
    public final @Nullable Level worldObj;

    public EntityLaser(@Nullable Level world) {
        this.worldObj = world;

        head = new Position(0, 0, 0);
        tail = new Position(0, 0, 0);
    }

    public EntityLaser(@Nullable Level world, Position head, Position tail) {
        this.worldObj = world;

        this.head = head;
        this.tail = tail;
    }

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "textures/entity/" + name + ".png");
    }

    /**
     * 4.2.2's onUpdate without the bounding box and the data watcher: works out the length and angles RenderLaser
     * draws with. Called after the positions change, on either side.
     */
    public void onUpdate() {

        if (head == null || tail == null)
            return;

        double dx = head.x - tail.x;
        double dy = head.y - tail.y;
        double dz = head.z - tail.z;

        renderSize = Math.sqrt(dx * dx + dy * dy + dz * dz);
        angleZ = 360 - (Math.atan2(dz, dx) * 180.0 / Math.PI + 180.0);
        dx = Math.sqrt(renderSize * renderSize - dy * dy);
        angleY = -Math.atan2(dy, dx) * 180 / Math.PI;
    }

    /** 4.2.2's updateDataClient: reads what writeData wrote. */
    public void readData(RegistryFriendlyByteBuf data) {
        head.x = data.readDouble();
        head.y = data.readDouble();
        head.z = data.readDouble();
        tail.x = data.readDouble();
        tail.y = data.readDouble();
        tail.z = data.readDouble();

        isVisible = data.readBoolean();
        onUpdate();
    }

    /** 4.2.2's updateDataServer. */
    public void writeData(RegistryFriendlyByteBuf data) {
        data.writeDouble(head.x);
        data.writeDouble(head.y);
        data.writeDouble(head.z);
        data.writeDouble(tail.x);
        data.writeDouble(tail.y);
        data.writeDouble(tail.z);

        data.writeBoolean(isVisible);
        needsUpdate = false;
    }

    public void setPositions(Position head, Position tail) {
        this.head = head;
        this.tail = tail;

        needsUpdate = true;
        onUpdate();
    }

    public Position getHead() {
        return head;
    }

    public Position getTail() {
        return tail;
    }

    public void show() {
        isVisible = true;
        needsUpdate = true;
    }

    public void hide() {
        isVisible = false;
        needsUpdate = true;
    }

    public boolean isVisible() {
        return this.isVisible;
    }

    public void setDead() {
        isDead = true;
    }

    public abstract Identifier getTexture();

    public int getBrightnessForRender() {
        return BRIGHTNESS;
    }
}
