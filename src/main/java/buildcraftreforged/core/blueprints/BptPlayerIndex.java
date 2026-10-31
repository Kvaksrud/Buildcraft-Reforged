/*
 * Modified by Buildcraft Reforged, 2026: ported from buildcraft/core/blueprints/BptPlayerIndex.java (no file header
 * at 4.2.2); the library folder is passed in and indexes are named by player UUID (BlueprintPaths, plan section
 * 19.2); index lines that leave the folder are refused; File becomes Path.
 */

package buildcraftreforged.core.blueprints;

import buildcraftreforged.core.utils.BCLog;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeMap;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One player's view of the library: the blueprint file names they keep, in name order. Ported from 4.2.2
 * {@code buildcraft.core.blueprints.BptPlayerIndex}. A new player starts with every file of the root index.
 */
public class BptPlayerIndex {

    private TreeMap<String, Path> bluePrintsFile = new TreeMap<String, Path>();

    private Path baseDir;
    private Path file;

    /** The index of {@code player}, in {@link BlueprintPaths#playerIndexName}. */
    public static BptPlayerIndex forPlayer(UUID player, BptRootIndex rootIndex) throws IOException {
        return new BptPlayerIndex(BlueprintPaths.playerIndexName(player), rootIndex);
    }

    public BptPlayerIndex(String filename, BptRootIndex rootIndex) throws IOException {
        baseDir = rootIndex.getBaseDir();
        Path indexFile = BlueprintPaths.resolve(baseDir, filename);

        if (indexFile == null)
            throw new IOException("Invalid index file name " + filename);

        file = indexFile;
        Files.createDirectories(baseDir);

        if (!Files.exists(file)) {
            Files.createFile(file);

            for (String file : rootIndex.filesSet.keySet()) {
                bluePrintsFile.put(file, baseDir.resolve(file));
            }

            saveIndex();
        } else {
            loadIndex();
        }
    }

    public void loadIndex() throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.ISO_8859_1)) {
            while (true) {
                String line = reader.readLine();

                if (line == null) {
                    break;
                }

                line = line.replaceAll("\\n", "");

                if (line.isEmpty()) {
                    continue;
                }

                Path bptFile = BlueprintPaths.resolve(baseDir, line);

                if (bptFile == null) {
                    BCLog.logger.warn("Player blueprint index " + file.getFileName() + " names a file outside the library: " + line);
                    continue;
                }

                bluePrintsFile.put(line, bptFile);
            }
        }
    }

    public void addBlueprint(Path file) throws IOException {
        bluePrintsFile.put(file.getFileName().toString(), file);

        saveIndex();
    }

    public void saveIndex() throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.ISO_8859_1)) {
            for (String line : bluePrintsFile.keySet()) {
                writer.write(line);
                writer.newLine();
            }
        }
    }

    public void deleteBluePrint(String fileName) {
        bluePrintsFile.remove(fileName);

        try {
            saveIndex();
        } catch (IOException e) {
            BCLog.logger.error("Could not save blueprint index " + file, e);
        }
    }

    public @Nullable String nextBpt(@Nullable String name) {
        if (bluePrintsFile.size() == 0)
            return null;
        else if (name == null)
            return bluePrintsFile.firstKey();
        else
            return bluePrintsFile.higherKey(name);
    }

    public @Nullable String prevBpt(@Nullable String name) {
        if (bluePrintsFile.size() == 0)
            return null;
        else if (name == null)
            return bluePrintsFile.lastKey();
        else
            return bluePrintsFile.lowerKey(name);
    }
}
