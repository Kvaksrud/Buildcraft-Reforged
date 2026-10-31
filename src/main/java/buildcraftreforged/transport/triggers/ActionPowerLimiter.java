/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: the limit is in FE/t (docs/TRANSPORT.md Q7); the legacy id is gone (registry
 * entry); the icon is a GUI sprite.
 */
package buildcraftreforged.transport.triggers;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.core.triggers.BCAction;
import buildcraftreforged.transport.PowerMode;
import java.util.Locale;
import net.minecraft.resources.Identifier;

/** Sets the limit of a Director on a power pipe, the Power Limiter (docs/TRANSPORT.md Q7, #55). */
public class ActionPowerLimiter extends BCAction {

    private final Identifier icon;
    public final PowerMode limit;

    public ActionPowerLimiter(PowerMode limit) {
        super("buildcraft.power.limiter." + limit.name().toLowerCase(Locale.ENGLISH));

        this.limit = limit;
        icon = Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID,
                "triggers/trigger_limiter_" + limit.name().toLowerCase(Locale.ENGLISH));
    }

    @Override
    public String getDescription() {
        return limit.maxPower + " FE/t Limit";
    }

    @Override
    public Identifier getIcon() {
        return icon;
    }
}
