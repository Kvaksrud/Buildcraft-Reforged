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
import java.util.Locale;
import net.minecraft.resources.Identifier;

/** Selects a filter colour on the Filtered Colour Tag module (docs/TRANSPORT.md 8.10, #57). */
public class ActionExtractionPreset extends BCAction {

    private final Identifier icon;
    public final EnumColor color;

    public ActionExtractionPreset(EnumColor color) {
        super("buildcraft.extraction.preset." + color.getTag());

        this.color = color;
        icon = Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID,
                "triggers/extraction_preset_" + color.name().toLowerCase(Locale.ENGLISH));
    }

    @Override
    public String getDescription() {
        return color.getName() + " Extraction Preset";
    }

    @Override
    public Identifier getIcon() {
        return icon;
    }
}
