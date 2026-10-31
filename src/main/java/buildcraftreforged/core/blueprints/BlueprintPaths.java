package buildcraftreforged.core.blueprints;

import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.loading.FMLPaths;
import org.jspecify.annotations.Nullable;

/**
 * Where blueprint libraries live, and the checks on the names that become file names. New in Buildcraft Reforged
 * (plan section 19.2): 4.2.2 wrote {@code blueprints/} under the process working directory, named player indexes
 * after player names and used any blueprint name as a file name.
 */
public final class BlueprintPaths {

    /** The library folder below the game or world folder. */
    public static final String LIBRARY_DIR = "buildcraft-reforged/blueprints";

    /** The root index file (4.2.2 {@code BuildCraftBuilders.getBptRootIndex}). */
    public static final String ROOT_INDEX = "index.txt";

    /** Extension of blueprint and template files, as 4.2.2; the content tells a new file from a 1.6.4 one. */
    public static final String EXTENSION = ".bpt";

    /** Longest blueprint name kept in a file name. */
    public static final int MAX_NAME_LENGTH = 64;

    private BlueprintPaths() {
    }

    /** The client's own library: {@code <gamedir>/buildcraft-reforged/blueprints/}. */
    public static Path clientLibrary() {
        return FMLPaths.GAMEDIR.get().resolve(LIBRARY_DIR).toAbsolutePath().normalize();
    }

    /** A server's library: {@code <world>/buildcraft-reforged/blueprints/}. */
    public static Path serverLibrary(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(LIBRARY_DIR).toAbsolutePath().normalize();
    }

    /** The player index file name, by UUID (4.2.2 used {@code <player name>.list}). */
    public static String playerIndexName(UUID player) {
        return player + ".list";
    }

    /**
     * A user-typed blueprint name reduced to {@code [A-Za-z0-9 _-]}, trimmed and capped at {@link #MAX_NAME_LENGTH};
     * {@code "unnamed"} when nothing is left (4.2.2's default for an empty name).
     */
    public static String sanitizeName(@Nullable String name) {
        if (name == null) {
            return "unnamed";
        }

        String clean = name.replaceAll("[^A-Za-z0-9 _-]", "").trim();

        if (clean.length() > MAX_NAME_LENGTH) {
            clean = clean.substring(0, MAX_NAME_LENGTH).trim();
        }

        return clean.isEmpty() ? "unnamed" : clean;
    }

    /**
     * The file {@code fileName} directly inside {@code baseDir}, or null when the name is empty, has a path separator
     * or would resolve anywhere else (index files are text a player can edit).
     */
    public static @Nullable Path resolve(Path baseDir, String fileName) {
        if (fileName.isEmpty() || fileName.contains("/") || fileName.contains("\\") || fileName.equals(".")
                || fileName.equals("..")) {
            return null;
        }

        Path dir = baseDir.toAbsolutePath().normalize();
        Path file;

        try {
            file = dir.resolve(fileName).normalize();
        } catch (RuntimeException e) {
            // InvalidPathException: a character the file system does not allow.
            return null;
        }

        if (!dir.equals(file.getParent())) {
            return null;
        }

        return file;
    }
}
