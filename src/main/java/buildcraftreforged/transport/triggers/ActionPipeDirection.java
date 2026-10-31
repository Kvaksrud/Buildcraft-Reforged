/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: Direction in place of ForgeDirection; the legacy id is gone (registry entry);
 * the icon is a GUI sprite.
 */
package buildcraftreforged.transport.triggers;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.core.triggers.BCAction;
import java.util.Locale;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

/** Turns the Director (and the Colour Sorter's output) to a side (docs/TRANSPORT.md 8.4, 8.9, #55, #57). */
public class ActionPipeDirection extends BCAction {

    private final Identifier icon;
    public final Direction direction;

    public ActionPipeDirection(Direction direction) {
        super("buildcraft.pipe.dir." + direction.name().toLowerCase(Locale.ENGLISH));

        this.direction = direction;
        icon = Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID,
                "triggers/trigger_dir_" + direction.name().toLowerCase(Locale.ENGLISH));
    }

    @Override
    public String getDescription() {
        return direction.name().substring(0, 1) + direction.name().substring(1).toLowerCase(Locale.ENGLISH) + " Pipe Direction";
    }

    @Override
    public Identifier getIcon() {
        return icon;
    }
}
