/**
 * Copyright (c) SpaceToad, 2011-2012
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: the Latin-1 text file becomes compressed NBT with DataVersion and BptVersion
 * headers (plan section 13.4); setBlockId becomes setBlockState; File becomes Path; rotateLeft keeps the slots'
 * coordinates current; mirror added; equals fixed; a 1.6.4 file fails with a clear message.
 */

package buildcraftreforged.core.blueprints;

import buildcraftreforged.core.Box;
import buildcraftreforged.core.utils.BCLog;
import com.mojang.datafixers.DSL;
import com.mojang.serialization.Dynamic;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * A blueprint or template: a box of slots with an anchor, a name and an author. Ported from 4.2.2
 * {@code buildcraft.core.blueprints.BptBase}.
 *
 * <p>The file is gzip-compressed NBT (format in {@code docs/PORTING-NOTES.md}, "Blueprint file format (#75)"). The
 * header holds the game's {@code DataVersion}, so blocks, block entities and items saved by an older game go through
 * the vanilla data fixers on load, and {@link #BPT_VERSION}, the version of this layout. 4.2.2's text header lines
 * ({@code sizeX:}, {@code anchorX:}, {@code name:} and so on) become tags of the same names. A 1.6.4 text file is
 * recognised and refused with {@link #LEGACY_MESSAGE}; the importer (plan section 13.5, #13) reads those.
 */
public abstract class BptBase {

    /** Version of the NBT layout. Raise it when the layout changes, and keep reading the older ones. */
    public static final int BPT_VERSION = 1;

    /** Shown for a 1.6.4 text blueprint (plan section 13.5). */
    public static final String LEGACY_MESSAGE = "1.6.4 blueprint, import not yet available";

    /** Largest side of a blueprint, and largest volume, accepted from a file. */
    public static final int MAX_SIZE = 4096;
    public static final int MAX_VOLUME = 1 << 24;

    public static final String KIND_BLUEPRINT = "blueprint";
    public static final String KIND_TEMPLATE = "template";

    BptSlot contents[][][];

    public int position;

    public int anchorX, anchorY, anchorZ;
    public int sizeX, sizeY, sizeZ;

    protected @Nullable String name;

    public @Nullable String author;

    public @Nullable Path file;

    /** 4.2.2 kept the mod version that wrote the file; now the layout version and the game data version read. */
    protected int bptVersion = BPT_VERSION;
    protected int dataVersion = currentDataVersion();

    public BptBase() {
        contents = new BptSlot[0][0][0];
    }

    public BptBase(int sizeX, int sizeY, int sizeZ) {
        contents = new BptSlot[sizeX][sizeY][sizeZ];

        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;

        anchorX = 0;
        anchorY = 0;
        anchorZ = 0;
    }

    /** 4.2.2 {@code setBlockId}: creates the slot if needed and sets its block. */
    public void setBlockState(int x, int y, int z, BlockState state) {
        if (contents[x][y][z] == null) {
            contents[x][y][z] = new BptSlot();
            contents[x][y][z].x = x;
            contents[x][y][z].y = y;
            contents[x][y][z].z = z;
        }

        contents[x][y][z].state = state;
    }

    /** The slot at blueprint coordinates, or null where nothing was captured. New: 4.2.2 code read contents directly. */
    public @Nullable BptSlot getSlot(int x, int y, int z) {
        return contents[x][y][z];
    }

    /**
     * Turns the blueprint 90 degrees clockwise seen from above (north to east), the way 4.2.2 turned it: the slot at
     * (x, y, z) moves to (sizeZ - 1 - z, y, x).
     */
    public void rotateLeft(BptContext context) {
        BptSlot newContents[][][] = new BptSlot[sizeZ][sizeY][sizeX];

        for (int x = 0; x < sizeZ; ++x) {
            for (int y = 0; y < sizeY; ++y) {
                for (int z = 0; z < sizeX; ++z) {
                    newContents[x][y][z] = contents[z][y][(sizeZ - 1) - x];

                    if (newContents[x][y][z] != null) {
                        try {
                            newContents[x][y][z].rotateLeft(context);
                        } catch (Throwable t) {
                            // Defensive code against errors in implementers
                            BCLog.logger.error("BptBase.rotateLeft", t);
                        }

                        // 4.2.2 left the slots' own coordinates at their old values.
                        newContents[x][y][z].x = x;
                        newContents[x][y][z].y = y;
                        newContents[x][y][z].z = z;
                    }
                }
            }
        }

        int newAnchorX, newAnchorY, newAnchorZ;

        newAnchorX = (sizeZ - 1) - anchorZ;
        newAnchorY = anchorY;
        newAnchorZ = anchorX;

        contents = newContents;
        int tmp = sizeX;
        sizeX = sizeZ;
        sizeZ = tmp;

        anchorX = newAnchorX;
        anchorY = newAnchorY;
        anchorZ = newAnchorZ;

        context.rotateLeft();
    }

    /**
     * Mirrors the blueprint in place. New: 4.2.2 could not mirror. {@link Mirror#LEFT_RIGHT} flips the z axis and
     * {@link Mirror#FRONT_BACK} the x axis, as for vanilla structure templates; each slot's block state is mirrored by
     * its schematic.
     */
    public void mirror(BptContext context, Mirror mirror) {
        if (mirror == Mirror.NONE) {
            return;
        }

        boolean flipX = mirror == Mirror.FRONT_BACK;
        BptSlot newContents[][][] = new BptSlot[sizeX][sizeY][sizeZ];

        for (int x = 0; x < sizeX; ++x) {
            for (int y = 0; y < sizeY; ++y) {
                for (int z = 0; z < sizeZ; ++z) {
                    BptSlot slot = flipX ? contents[(sizeX - 1) - x][y][z] : contents[x][y][(sizeZ - 1) - z];
                    newContents[x][y][z] = slot;

                    if (slot != null) {
                        try {
                            slot.mirror(context, mirror);
                        } catch (Throwable t) {
                            // Defensive code against errors in implementers, as rotateLeft
                            BCLog.logger.error("BptBase.mirror", t);
                        }

                        slot.x = x;
                        slot.y = y;
                        slot.z = z;
                    }
                }
            }
        }

        contents = newContents;

        if (flipX) {
            anchorX = (sizeX - 1) - anchorX;
        } else {
            anchorZ = (sizeZ - 1) - anchorZ;
        }
    }

    /**
     * Writes the blueprint to {@link #file}. Returns the file, or null when it could not be written (4.2.2 printed the
     * stack trace and returned null too).
     */
    public @Nullable Path save(HolderLookup.Provider registries) {
        if (file == null) {
            return null;
        }

        try {
            Files.createDirectories(file.toAbsolutePath().getParent());

            NbtIo.writeCompressed(writeToNBT(registries), file);

            return file;
        } catch (IOException e) {
            BCLog.logger.error("Could not save blueprint " + file, e);
        }

        return null;
    }

    /** The whole file as one tag: the 4.2.2 header lines, then the kind's own data. */
    public CompoundTag writeToNBT(HolderLookup.Provider registries) {
        CompoundTag nbt = new CompoundTag();

        NbtUtils.addCurrentDataVersion(nbt);
        nbt.putInt("BptVersion", BPT_VERSION);

        if (this instanceof BptTemplate) {
            nbt.putString("kind", KIND_TEMPLATE);
        } else {
            nbt.putString("kind", KIND_BLUEPRINT);
        }

        nbt.putInt("sizeX", sizeX);
        nbt.putInt("sizeY", sizeY);
        nbt.putInt("sizeZ", sizeZ);
        nbt.putInt("anchorX", anchorX);
        nbt.putInt("anchorY", anchorY);
        nbt.putInt("anchorZ", anchorZ);

        if (name != null) {
            nbt.putString("name", name);
        }

        if (author != null) {
            nbt.putString("author", author);
        }

        saveAttributes(nbt, registries);

        return nbt;
    }

    /**
     * Same size, anchor and blocks. 4.2.2 compared block ids only and, by an inverted class check, returned false for
     * any two blueprints of the same class, so the architect never recognised a repeated capture; the check is fixed.
     */
    @Override
    public boolean equals(Object o) {
        if (o == null || o.getClass() != getClass())
            return false;

        BptBase bpt = (BptBase) o;

        if (sizeX != bpt.sizeX || sizeY != bpt.sizeY || sizeZ != bpt.sizeZ || anchorX != bpt.anchorX || anchorY != bpt.anchorY || anchorZ != bpt.anchorZ)
            return false;

        for (int x = 0; x < sizeX; ++x) {
            for (int y = 0; y < sizeY; ++y) {
                for (int z = 0; z < sizeZ; ++z) {
                    if (contents[x][y][z] != null && bpt.contents[x][y][z] == null)
                        return false;

                    if (contents[x][y][z] == null && bpt.contents[x][y][z] != null)
                        return false;

                    if (contents[x][y][z] == null && bpt.contents[x][y][z] == null) {
                        continue;
                    }

                    if (contents[x][y][z].state != bpt.contents[x][y][z].state)
                        return false;
                }
            }
        }

        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass(), sizeX, sizeY, sizeZ, anchorX, anchorY, anchorZ);
    }

    /**
     * Loads a blueprint file, or returns null and logs why it could not (4.2.2 printed the stack trace and returned
     * null). {@link #readBluePrint} gives the reason to the caller.
     */
    public static @Nullable BptBase loadBluePrint(Path file, int position, HolderLookup.Provider registries) {
        try {
            return readBluePrint(file, position, registries);
        } catch (BptError e) {
            BCLog.logger.warn("Could not load blueprint " + file + ": " + e.getMessage());
            return null;
        }
    }

    /** Loads a blueprint file; the error says why it could not, {@link #LEGACY_MESSAGE} for a 1.6.4 file. */
    public static BptBase readBluePrint(Path file, int position, HolderLookup.Provider registries) throws BptError {
        CompoundTag nbt;

        try {
            if (isLegacyFile(file))
                throw new BptError(LEGACY_MESSAGE);

            nbt = NbtIo.readCompressed(file, NbtAccounter.uncompressedQuota());
        } catch (IOException | RuntimeException e) {
            throw new BptError("Not a readable blueprint file: " + e.getMessage(), e);
        }

        BptBase result = readFromNBT(nbt, position, registries);
        result.file = file;

        return result;
    }

    /** Reads a blueprint from its tag (the file's content); the kind's own data is upgraded from its DataVersion. */
    public static BptBase readFromNBT(CompoundTag nbt, int position, HolderLookup.Provider registries) throws BptError {
        int bptVersion = nbt.getIntOr("BptVersion", 0);

        if (bptVersion < 1)
            throw new BptError("Not a blueprint: no BptVersion");

        if (bptVersion > BPT_VERSION)
            throw new BptError("Blueprint format " + bptVersion + " is newer than this version reads (" + BPT_VERSION + ")");

        int dataVersion = NbtUtils.getDataVersion(nbt, -1);

        if (dataVersion < 0)
            throw new BptError("Not a blueprint: no DataVersion");

        if (dataVersion > currentDataVersion())
            throw new BptError("Blueprint saved by a newer game (data version " + dataVersion + ")");

        BptBase result;
        String kind = nbt.getStringOr("kind", "");

        if (kind.equals(KIND_TEMPLATE)) {
            result = new BptTemplate();
        } else if (kind.equals(KIND_BLUEPRINT)) {
            result = new BptBlueprint();
        } else
            throw new BptError("Unknown blueprint kind \"" + kind + "\"");

        result.position = position;
        result.bptVersion = bptVersion;
        result.dataVersion = dataVersion;

        result.sizeX = nbt.getIntOr("sizeX", 0);
        result.sizeY = nbt.getIntOr("sizeY", 0);
        result.sizeZ = nbt.getIntOr("sizeZ", 0);
        result.anchorX = nbt.getIntOr("anchorX", 0);
        result.anchorY = nbt.getIntOr("anchorY", 0);
        result.anchorZ = nbt.getIntOr("anchorZ", 0);
        result.name = nbt.getString("name").orElse(null);
        result.author = nbt.getString("author").orElse(null);

        if (result.sizeX < 1 || result.sizeY < 1 || result.sizeZ < 1 || result.sizeX > MAX_SIZE || result.sizeY > MAX_SIZE
                || result.sizeZ > MAX_SIZE || (long) result.sizeX * result.sizeY * result.sizeZ > MAX_VOLUME)
            throw new BptError("Blueprint size " + result.sizeX + " x " + result.sizeY + " x " + result.sizeZ + " out of range");

        result.contents = new BptSlot[result.sizeX][result.sizeY][result.sizeZ];
        result.loadAttributes(nbt, registries);

        return result;
    }

    /**
     * True for a 1.6.4 (or older) text blueprint: not gzip, and the first line is a {@code key:value} header line such
     * as {@code version:4.2.2} or, for 3.x files without a version, {@code kind:blueprint}.
     */
    public static boolean isLegacyFile(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            int b0 = input.read();
            int b1 = input.read();

            if (b0 == 0x1f && b1 == 0x8b) {
                return false;
            }
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(file), StandardCharsets.ISO_8859_1))) {
            String line = reader.readLine();

            return line != null && line.matches("[A-Za-z]+:.*");
        }
    }

    public void setName(@Nullable String name) {
        this.name = name;
    }

    public String getName() {
        if (name == null)
            return "#" + position;
        else
            return name;
    }

    /** The layout version the blueprint was read with ({@link #BPT_VERSION} for a new one). */
    public int getBptVersion() {
        return bptVersion;
    }

    /** The game data version the blueprint was saved with, before any upgrade on load. */
    public int getDataVersion() {
        return dataVersion;
    }

    @Override
    public final BptBase clone() {
        BptBase res = null;

        try {
            res = getClass().getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            BCLog.logger.error("BptBase.clone", e);

            return null;
        }

        res.anchorX = anchorX;
        res.anchorY = anchorY;
        res.anchorZ = anchorZ;

        res.sizeX = sizeX;
        res.sizeY = sizeY;
        res.sizeZ = sizeZ;

        res.position = position;
        res.name = name;
        res.author = author;

        res.contents = new BptSlot[sizeX][sizeY][sizeZ];

        for (int x = 0; x < sizeX; ++x) {
            for (int y = 0; y < sizeY; ++y) {
                for (int z = 0; z < sizeZ; ++z)
                    if (contents[x][y][z] != null) {
                        res.contents[x][y][z] = contents[x][y][z].clone();
                    }
            }
        }

        copyTo(res);

        return res;
    }

    protected void copyTo(BptBase base) {

    }

    public Box getBoxForPos(int x, int y, int z) {
        int xMin = x - anchorX;
        int yMin = y - anchorY;
        int zMin = z - anchorZ;
        int xMax = x + sizeX - anchorX - 1;
        int yMax = y + sizeY - anchorY - 1;
        int zMax = z + sizeZ - anchorZ - 1;

        Box res = new Box();
        res.initialize(xMin, yMin, zMin, xMax, yMax, zMax);
        res.reorder();

        return res;
    }

    /** Index of (x, y, z) in the flat arrays of the file: x outermost, then y, then z, 4.2.2's loop order. */
    protected int index(int x, int y, int z) {
        return (x * sizeY + y) * sizeZ + z;
    }

    /** The game's current data version, written to every file. */
    public static int currentDataVersion() {
        return SharedConstants.getCurrentVersion().dataVersion().version();
    }

    /** Runs {@code tag} through the vanilla data fixers from this blueprint's data version to the current one. */
    protected Tag fix(DSL.TypeReference type, Tag tag) {
        if (dataVersion >= currentDataVersion()) {
            return tag;
        }

        return DataFixers.getDataFixer().update(type, new Dynamic<>(NbtOps.INSTANCE, tag), dataVersion, currentDataVersion()).getValue();
    }

    /** 4.2.2 {@code loadAttribute}: reads the kind's own data; sizes, anchor and contents array are set. */
    public abstract void loadAttributes(CompoundTag nbt, HolderLookup.Provider registries) throws BptError;

    public abstract void saveAttributes(CompoundTag nbt, HolderLookup.Provider registries);
}
