/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: no longer an Entity. Like core/EntityBlock it is render data the quarry keeps
 * (TileQuarry.arm) and RenderQuarry draws, so it is not spawned, saved or ticked: the quarry creates it from its box
 * in createUtilsIfNeeded on both sides, which is what readEntityFromNBT and findAndJoinQuarry restored in 4.2.2;
 * onUpdate and setDead's server side are gone with the entity. FactoryProxy.newDrill and newDrillHead are inlined with
 * FactoryProxyClient's textures.
 */

package buildcraftreforged.factory;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.core.EntityBlock;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

public class EntityMechanicalArm {
    /** 4.2.2 FactoryProxyClient.drillTexture, "buildcraft:blockDrillTexture". */
    public static final Identifier DRILL_TEXTURE =
            Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "textures/block/drill.png");
    /** 4.2.2 FactoryProxyClient.drillHeadTexture, "buildcraft:blockDrillHeadTexture". */
    public static final Identifier DRILL_HEAD_TEXTURE =
            Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "textures/block/drill_head.png");

    EntityBlock xArm, yArm, zArm, head;

    protected TileQuarry parent;

    private double armSizeX;
    private double armSizeZ;
    private double xRoot;
    private double yRoot;
    private double zRoot;

    private int headX, headY, headZ;

    public EntityMechanicalArm(Level world, double i, double j, double k, double width, double height, TileQuarry parent) {
        makeParts(world);
        this.xRoot = i;
        this.yRoot = j;
        this.zRoot = k;
        setArmSize(width, height);
        setHead(i, j - 2, k);

        this.parent = parent;
        parent.setArm(this);
        updatePosition();
    }

    void setHead(double x, double y, double z) {
        this.headX = (int) (x * 32D);
        this.headY = (int) (y * 32D);
        this.headZ = (int) (z * 32D);
    }

    private void setArmSize(double x, double z) {
        armSizeX = x;
        xArm.iSize = x;
        armSizeZ = z;
        zArm.kSize = z;
    }

    private void makeParts(Level world) {
        xArm = newDrill(world, 0, 0, 0, 1, 0.5, 0.5);
        yArm = newDrill(world, 0, 0, 0, 0.5, 1, 0.5);
        zArm = newDrill(world, 0, 0, 0, 0.5, 0.5, 1);

        head = newDrillHead(world, 0, 0, 0, 0.2, 1, 0.2);
    }

    private static EntityBlock newDrill(Level w, double i, double j, double k, double l, double d, double e) {
        EntityBlock eb = new EntityBlock(w, i, j, k, l, d, e);
        eb.texture = DRILL_TEXTURE;
        return eb;
    }

    private static EntityBlock newDrillHead(Level w, double i, double j, double k, double l, double d, double e) {
        EntityBlock eb = new EntityBlock(w, i, j, k, l, d, e);
        eb.texture = DRILL_HEAD_TEXTURE;
        return eb;
    }

    public void updatePosition() {
        double[] head = getHead();
        this.xArm.setPosition(xRoot, yRoot, head[2] + 0.25);
        this.yArm.jSize = yRoot - head[1] - 1;
        this.yArm.setPosition(head[0] + 0.25, head[1] + 1, head[2] + 0.25);
        this.zArm.setPosition(head[0] + 0.25, yRoot, zRoot);
        this.head.setPosition(head[0] + 0.4, head[1], head[2] + 0.4);
    }

    public void setDead() {
        xArm.setDead();
        yArm.setDead();
        zArm.setDead();
        head.setDead();
    }

    /** The four parts, for RenderQuarry: the x, y and z arms and the drill head. */
    public EntityBlock[] getParts() {
        return new EntityBlock[] { xArm, yArm, zArm, head };
    }

    private double[] getHead() {
        return new double[] { this.headX / 32D, this.headY / 32D, this.headZ / 32D };
    }
}
