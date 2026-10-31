/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: the library folder is passed in (BlueprintPaths) instead of ./blueprints/;
 * blueprint names are sanitised and index lines that leave the folder are refused (plan section 19.2); File becomes
 * Path; loading and saving take the registries.
 */

package buildcraftreforged.core.blueprints;

import buildcraftreforged.core.utils.BCLog;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import org.jspecify.annotations.Nullable;

/**
 * The library's numbered list of blueprint files: number n is line n of the index file, an empty line once its file is
 * gone, so numbers held by blueprint items stay valid. Ported from 4.2.2
 * {@code buildcraft.core.blueprints.BptRootIndex}; the index stays Latin-1 text, one file name per line.
 */
public class BptRootIndex {

    private TreeMap<Integer, Path> bluePrintsFile = new TreeMap<Integer, Path>();
    public TreeMap<String, Integer> filesSet = new TreeMap<String, Integer>();

    private TreeMap<Integer, BptBase> bluePrints = new TreeMap<Integer, BptBase>();

    private Path baseDir;
    private Path file;
    private final HolderLookup.Provider registries;

    public int maxBpt = 0;

    /**
     * @param baseDir the library folder, {@link BlueprintPaths#clientLibrary} or {@link BlueprintPaths#serverLibrary}
     * @param filename the index file name, {@link BlueprintPaths#ROOT_INDEX}
     */
    public BptRootIndex(Path baseDir, String filename, HolderLookup.Provider registries) throws IOException {
        this.baseDir = baseDir.toAbsolutePath().normalize();
        this.registries = registries;
        Path indexFile = BlueprintPaths.resolve(this.baseDir, filename);

        if (indexFile == null)
            throw new IOException("Invalid index file name " + filename);

        file = indexFile;
        Files.createDirectories(this.baseDir);

        if (!Files.exists(file)) {
            Files.createFile(file);
        }
    }

    public Path getBaseDir() {
        return baseDir;
    }

    public void loadIndex() throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.ISO_8859_1)) {
            while (true) {
                String line = reader.readLine();

                if (line == null) {
                    break;
                }

                line = line.replaceAll("\\n", "");

                maxBpt++;

                if (line.isEmpty()) {
                    continue;
                }

                // Lines are file names inside the library; anything else is skipped but keeps its number.
                Path bptFile = BlueprintPaths.resolve(baseDir, line);

                if (bptFile == null) {
                    BCLog.logger.warn("Blueprint index line " + maxBpt + " is not a file in the library: " + line);
                    continue;
                }

                filesSet.put(line, maxBpt);

                if (Files.exists(bptFile)) {
                    bluePrintsFile.put(maxBpt, bptFile);
                }

            }
        }

        saveIndex();
    }

    public void importNewFiles() throws IOException {
        List<String> files = new ArrayList<String>();

        try (Stream<Path> list = Files.list(baseDir)) {
            list.forEach(p -> files.add(p.getFileName().toString()));
        }

        files.sort(null);

        for (String foundFile : files) {
            if (!foundFile.endsWith(BlueprintPaths.EXTENSION) || BlueprintPaths.resolve(baseDir, foundFile) == null) {
                continue;
            }

            if (!filesSet.containsKey(foundFile)) {
                maxBpt++;
                filesSet.put(foundFile, maxBpt);

                Path newFile = baseDir.resolve(foundFile);

                bluePrintsFile.put(maxBpt, newFile);

                // for (BptPlayerIndex playerIndex : BuildCraftBuilders.playerLibrary.values())
                // playerIndex.addBlueprint(newFile);
            }
        }

        saveIndex();
    }

    public void saveIndex() throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.ISO_8859_1)) {
            for (int i = 1; i <= maxBpt; ++i) {
                Path f = bluePrintsFile.get(i);

                if (f != null) {
                    writer.write(f.getFileName().toString());
                }

                writer.newLine();
            }
        }
    }

    public @Nullable BptBase getBluePrint(int number) {
        if (!bluePrints.containsKey(number))
            if (bluePrintsFile.containsKey(number)) {
                BptBase bpt = BptBase.loadBluePrint(bluePrintsFile.get(number), number, registries);

                if (bpt != null) {
                    bluePrints.put(number, bpt);
                    bpt.file = bluePrintsFile.get(number);
                } else {
                    bluePrintsFile.remove(number);
                    return null;
                }
            }

        return bluePrints.get(number);
    }

    public @Nullable BptBase getBluePrint(String filename) {
        Integer number = filesSet.get(filename);

        // 4.2.2 unboxed a missing name into a NullPointerException.
        return number == null ? null : getBluePrint(number);
    }

    /** The file of blueprint {@code number}, loaded or not (the library lists 1.6.4 files without loading them). */
    public @Nullable Path getBluePrintFile(int number) {
        return bluePrintsFile.get(number);
    }

    public int storeBluePrint(BptBase bluePrint) {
        String name = BlueprintPaths.sanitizeName(bluePrint.name);

        if (filesSet.containsKey(name + BlueprintPaths.EXTENSION)) {
            int n = 0;

            while (filesSet.containsKey(name + "_" + n + BlueprintPaths.EXTENSION)) {
                n++;
            }

            name = name + "_" + n;
        }

        maxBpt++;

        filesSet.put(name + BlueprintPaths.EXTENSION, maxBpt);

        name = name + BlueprintPaths.EXTENSION;

        Path bptFile = baseDir.resolve(name);

        bluePrintsFile.put(maxBpt, bptFile);
        bluePrints.put(maxBpt, bluePrint);
        bluePrint.file = bptFile;
        bluePrint.save(registries);
        bluePrint.position = maxBpt;

        try {
            saveIndex();
        } catch (IOException e) {
            BCLog.logger.error("Could not save blueprint index " + file, e);
        }

        return maxBpt;
    }
}
