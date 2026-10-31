/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: the legacy id is gone (registry entry); the icon is a GUI sprite.
 */
package buildcraftreforged.transport.triggers;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.core.triggers.BCAction;
import buildcraftreforged.core.utils.EnumColor;
import buildcraftreforged.core.utils.StringUtils;
import java.util.Locale;
import net.minecraft.resources.Identifier;

/** Sets the colour of the Colour Tag and Colour Sorter modules (docs/TRANSPORT.md 8.8, 8.9, #57). */
public class ActionPipeColor extends BCAction {

    private final Identifier icon;
    public final EnumColor color;

    public ActionPipeColor(EnumColor color) {
        super("buildcraft.pipe." + color.getTag());

        this.color = color;
        icon = Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID,
                "triggers/color_" + color.name().toLowerCase(Locale.ENGLISH));
    }

    @Override
    public String getDescription() {
        return String.format(StringUtils.localize("gate.pipe.item.color"), color.getLocalizedName());
    }

    @Override
    public Identifier getIcon() {
        return icon;
    }
}
