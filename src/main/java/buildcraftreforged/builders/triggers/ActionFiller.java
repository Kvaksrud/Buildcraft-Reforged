/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: a registry entry without a legacy id; the icon is the pattern's GUI sprite.
 */
package buildcraftreforged.builders.triggers;

import buildcraftreforged.api.filler.IFillerPattern;
import buildcraftreforged.core.triggers.BCAction;
import net.minecraft.resources.Identifier;

public class ActionFiller extends BCAction {

    public final IFillerPattern pattern;

    public ActionFiller(IFillerPattern pattern) {
        super("filler:" + pattern.getUniqueTag());
        this.pattern = pattern;
    }

    @Override
    public String getDescription() {
        return "Pattern: " + pattern.getDisplayName().getString();
    }

    @Override
    public Identifier getIcon() {
        return pattern.getIcon();
    }

    @Override
    public int getTextureMap() {
        return 0;
    }
}
