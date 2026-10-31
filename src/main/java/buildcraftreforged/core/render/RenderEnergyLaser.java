/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: ModelRenderer becomes RenderLaser.ModelBox; drawn from a BlockEntityRenderer
 * (see RenderLaser).
 */

package buildcraftreforged.core.render;

import buildcraftreforged.core.EntityEnergyLaser;
import buildcraftreforged.core.EntityLaser;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class RenderEnergyLaser extends RenderLaser {

    public static final RenderEnergyLaser INSTANCE = new RenderEnergyLaser();

    private ModelBox box[] = new ModelBox[40];

    public RenderEnergyLaser() {
        for (int i = 0; i < box.length; ++i) {
            box[i] = new ModelBox(box.length - i, 0);
        }
    }

    @Override
    protected ModelBox getBox(EntityLaser laser) {
        EntityEnergyLaser eLaser = (EntityEnergyLaser) laser;

        return box[eLaser.displayStage / 10];
    }

    @Override
    protected void iterate(EntityLaser laser) {
        EntityEnergyLaser eLaser = (EntityEnergyLaser) laser;

        eLaser.displayStage = eLaser.displayStage + 1;
        if (eLaser.displayStage >= box.length * 10) {
            eLaser.displayStage = 0;
        }
    }
}
