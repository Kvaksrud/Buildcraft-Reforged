/*
 * Ported from buildcraft/core/utils/StringUtils.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: Localization.get becomes Language.getInstance().getOrDefault, which reads
 * en_us.json (and the client's selected language) on both sides.
 */
package buildcraftreforged.core.utils;

import net.minecraft.locale.Language;

public class StringUtils {

    public static String localize(String key) {
        return Language.getInstance().getOrDefault(key);
    }
}
