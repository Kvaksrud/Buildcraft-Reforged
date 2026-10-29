package buildcraftreforged.core;

import java.util.Locale;

/** Registry names from 4.2.2 unlocalised names: {@code engineWood} becomes {@code engine_wood}. */
public final class RegistryNames {
    private RegistryNames() {
    }

    public static String of(String unlocalizedName) {
        return unlocalizedName.replaceAll("([a-z0-9])([A-Z])", "$1_$2").replace('.', '_').toLowerCase(Locale.ENGLISH);
    }
}
