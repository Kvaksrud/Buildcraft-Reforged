/*
 * Ported from buildcraft/factory/PumpDimensionList.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: dimension ids are dimension keys ("minecraft:the_nether") and fluid names are
 * fluid registry ids ("minecraft:lava"); a fluid name without a namespace (1.6.4's "lava") still matches by path. The
 * list is parsed from BuildCraftConfiguration.pumpDimensionList on first use and again when the option changes,
 * instead of once in BuildCraftFactory.load; a malformed option is logged and the default list used, instead of
 * stopping the game.
 */
package buildcraftreforged.factory;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.core.BuildCraftConfiguration;
import buildcraftreforged.core.DefaultProps;
import buildcraftreforged.core.fluids.FluidUtils;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import org.jspecify.annotations.Nullable;

public class PumpDimensionList {

    private static @Nullable String parsedString;
    private static @Nullable PumpDimensionList parsed;

    /** 4.2.2's BuildCraftFactory.pumpDimensionList, for the current value of the option. */
    public static PumpDimensionList get() {
        String string = BuildCraftConfiguration.pumpDimensionList.get();
        PumpDimensionList list = parsed;
        if (list == null || !string.equals(parsedString)) {
            try {
                list = new PumpDimensionList(string);
            } catch (RuntimeException e) {
                BuildcraftReforged.LOGGER.error("Ignoring pumping.controlList, using \"{}\": {}",
                        DefaultProps.PUMP_DIMENSION_LIST, e.getMessage());
                list = new PumpDimensionList(DefaultProps.PUMP_DIMENSION_LIST);
            }
            parsed = list;
            parsedString = string;
        }
        return list;
    }

    public PumpDimensionList(String string) {

        entries = new LinkedList<Entry>();

        for (String entryString : string.trim().split(",")) {

            Entry e = new Entry();

            if (entryString.startsWith("+/")) {
                e.isWhitelist = true;
            } else if (entryString.startsWith("-/")) {
                e.isWhitelist = false;
            } else
                throw new RuntimeException("Malformed pumping.controlList entry: " + entryString + " (must start with +/ or -/)");

            entryString = entryString.substring(2);
            int i = entryString.indexOf('/');

            if (i < 0)
                throw new RuntimeException("Malformed pumping.controlList entry: " + entryString + " (missing second /)");

            String dimIDString = entryString.substring(0, i);

            if (dimIDString.equals("*"))
                e.matchAnyDim = true;
            else
                e.dimID = dimIDString.toLowerCase(Locale.ENGLISH);

            e.fluidName = entryString.substring(i + 1).toLowerCase(Locale.ENGLISH);
            if (e.fluidName.equals("*"))
                e.matchAnyFluid = true;

            entries.add(0, e);
        }

        entries = new ArrayList<Entry>(entries);
    }

    private class Entry {
        boolean isWhitelist;
        String fluidName = "";
        String dimID = "";
        boolean matchAnyFluid;
        boolean matchAnyDim;

        boolean matches(Fluid fluid, String dim) {
            if (!matchAnyFluid) {
                String name = FluidUtils.getFluidName(fluid);
                if (!name.equals(fluidName) && !(fluidName.indexOf(':') < 0 && name.endsWith(":" + fluidName)))
                    return false;
            }
            if (!matchAnyDim && !dimID.equals(dim))
                return false;
            return true;
        }
    }

    private List<Entry> entries;

    public boolean isFluidAllowed(Fluid fluid, String dim) {
        for (Entry e : entries)
            if (e.matches(fluid, dim))
                return e.isWhitelist;
        return false;
    }

    /** 4.2.2 passed worldObj.provider.dimensionId. */
    public boolean isFluidAllowed(Fluid fluid, Level world) {
        return isFluidAllowed(fluid, world.dimension().identifier().toString());
    }
}
