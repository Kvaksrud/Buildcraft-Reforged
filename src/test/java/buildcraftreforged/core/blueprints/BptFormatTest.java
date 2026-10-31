package buildcraftreforged.core.blueprints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import buildcraftreforged.core.Box;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** #75: the blueprint and template file format, rotation, mirroring and the library indexes. */
class BptFormatTest {

    private static final HolderLookup.Provider REGISTRIES = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);

    @TempDir
    Path dir;

    /** As the inventory tests' ItemComponents: the unit-test game binds no item components, so bind the few used. */
    @BeforeAll
    @SuppressWarnings("deprecation") // builtInRegistryHolder: there is no other way to reach the item's holder here
    static void bindItemComponents() {
        for (Item item : new Item[] { Items.CHEST, Items.DIAMOND, Items.DIRT_PATH }) {
            Holder.Reference<Item> holder = item.builtInRegistryHolder();
            if (!holder.areComponentsBound()) {
                holder.bindComponents(DataComponentMap.builder().addAll(DataComponents.COMMON_ITEM_COMPONENTS).build());
            }
        }
    }

    private static BlockState stairs(Direction facing) {
        return Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing);
    }

    /** 3 x 2 x 4: stairs facing north at (0, 0, 0), a chest with data and requirements, air, and one empty slot. */
    private static BptBlueprint sample() {
        BptBlueprint bpt = new BptBlueprint(3, 2, 4);

        for (int x = 0; x < 3; ++x)
            for (int y = 0; y < 2; ++y)
                for (int z = 0; z < 4; ++z)
                    bpt.setBlockState(x, y, z, Blocks.AIR.defaultBlockState());

        bpt.setBlockState(0, 0, 0, stairs(Direction.NORTH));
        bpt.setBlockState(2, 0, 3, Blocks.STONE.defaultBlockState());
        bpt.setBlockState(1, 1, 2, Blocks.CHEST.defaultBlockState());
        bpt.contents[1][1][2].cpt.putString("id", "minecraft:chest");
        bpt.contents[1][1][2].cpt.putString("CustomName", "\"Box\"");
        bpt.contents[1][1][2].storedRequirements.add(new ItemStack(Items.CHEST));
        bpt.contents[1][1][2].storedRequirements.add(new ItemStack(Items.DIAMOND, 5));
        bpt.contents[0][1][3] = null;
        bpt.anchorX = -1;
        bpt.anchorY = 0;
        bpt.anchorZ = 2;
        bpt.setName("House");
        bpt.author = "Tester";

        return bpt;
    }

    private static BptContext context(BptBase bpt) {
        Box box = new Box();
        box.initialize(0, 0, 0, bpt.sizeX - 1, bpt.sizeY - 1, bpt.sizeZ - 1);
        return new BptContext(null, bpt instanceof BptBlueprint b ? b : null, box);
    }

    @Test
    void blueprintRoundTrip() throws Exception {
        BptBlueprint bpt = sample();
        bpt.file = dir.resolve("house.bpt");

        assertEquals(bpt.file, bpt.save(REGISTRIES));

        byte[] bytes = Files.readAllBytes(bpt.file);
        assertEquals((byte) 0x1f, bytes[0], "not gzip");
        assertEquals((byte) 0x8b, bytes[1], "not gzip");
        assertFalse(BptBase.isLegacyFile(bpt.file));

        CompoundTag nbt = NbtIo.readCompressed(bpt.file, NbtAccounter.unlimitedHeap());
        assertEquals(BptBase.currentDataVersion(), nbt.getIntOr("DataVersion", 0));
        assertEquals(BptBase.BPT_VERSION, nbt.getIntOr("BptVersion", 0));
        assertEquals("blueprint", nbt.getStringOr("kind", ""));
        assertEquals(4, nbt.getListOrEmpty("palette").size(), "air, stairs, stone, chest");

        BptBase loaded = BptBase.readBluePrint(bpt.file, 7, REGISTRIES);

        assertInstanceOf(BptBlueprint.class, loaded);
        assertEquals(bpt, loaded);
        assertEquals(7, loaded.position);
        assertEquals("House", loaded.getName());
        assertEquals("Tester", loaded.author);
        assertEquals(bpt.file, loaded.file);
        assertEquals(-1, loaded.anchorX);
        assertEquals(2, loaded.anchorZ);
        assertSame(stairs(Direction.NORTH), loaded.getSlot(0, 0, 0).state);
        assertNull(loaded.getSlot(0, 1, 3), "an empty slot stays empty");
        assertNotNull(loaded.getSlot(1, 0, 0), "an air slot stays a slot");

        BptSlot chest = loaded.getSlot(1, 1, 2);
        assertEquals(bpt.contents[1][1][2].cpt, chest.cpt);
        assertEquals(2, chest.storedRequirements.size());
        assertTrue(ItemStack.matches(new ItemStack(Items.DIAMOND, 5), chest.storedRequirements.get(1)));
        assertTrue(loaded.getSlot(2, 0, 3).cpt.isEmpty());
        assertTrue(loaded.getSlot(2, 0, 3).storedRequirements.isEmpty());

        // A second save of the loaded copy writes the same tag.
        CompoundTag again = loaded.writeToNBT(REGISTRIES);
        assertEquals(nbt, again);
    }

    @Test
    void templateRoundTrip() throws Exception {
        BptTemplate template = new BptTemplate(3, 3, 3);

        for (int x = 0; x < 3; ++x)
            for (int y = 0; y < 3; ++y)
                for (int z = 0; z < 3; ++z)
                    template.setMask(x, y, z, (x + 2 * y + z) % 3 == 0 ? 1 : 0);

        template.anchorY = 1;
        template.file = dir.resolve("t.bpt");
        template.save(REGISTRIES);

        CompoundTag nbt = NbtIo.readCompressed(template.file, NbtAccounter.unlimitedHeap());
        assertEquals("template", nbt.getStringOr("kind", ""));
        assertEquals(4, nbt.getByteArray("mask").orElseThrow().length, "27 bits in 4 bytes");
        assertFalse(nbt.contains("palette"));

        BptBase loaded = BptBase.readBluePrint(template.file, 1, REGISTRIES);

        assertInstanceOf(BptTemplate.class, loaded);
        assertEquals(template, loaded);

        for (int x = 0; x < 3; ++x)
            for (int y = 0; y < 3; ++y)
                for (int z = 0; z < 3; ++z) {
                    assertEquals((x + 2 * y + z) % 3 == 0, ((BptTemplate) loaded).isFilled(x, y, z), x + "," + y + "," + z);
                    assertNotNull(loaded.getSlot(x, y, z), "templates have a slot everywhere");
                }

        assertSame(BptTemplate.filledState(), loaded.getSlot(0, 0, 0).state);
    }

    @Test
    void rotateLeftTurnsClockwise() {
        BptBlueprint bpt = sample();
        BptBlueprint original = (BptBlueprint) bpt.clone();

        bpt.rotateLeft(context(bpt));

        // Sizes swap; the slot at (x, y, z) moves to (sizeZ - 1 - z, y, x).
        assertEquals(4, bpt.sizeX);
        assertEquals(2, bpt.sizeY);
        assertEquals(3, bpt.sizeZ);
        assertSame(Blocks.STONE.defaultBlockState(), bpt.getSlot(0, 0, 2).state);
        assertSame(Blocks.CHEST, bpt.getSlot(1, 1, 1).state.getBlock());
        assertEquals(2, bpt.getSlot(1, 1, 1).storedRequirements.size());
        assertNull(bpt.getSlot(0, 1, 0));

        // The block turns with the structure, north to east, and the slot knows its new place.
        BptSlot stairs = bpt.getSlot(3, 0, 0);
        assertSame(stairs(Direction.EAST), stairs.state);
        assertEquals(3, stairs.x);
        assertEquals(0, stairs.z);

        // Anchor (-1, 0, 2) becomes (sizeZ - 1 - 2, 0, -1).
        assertEquals(1, bpt.anchorX);
        assertEquals(0, bpt.anchorY);
        assertEquals(-1, bpt.anchorZ);

        BptContext context = context(original);
        for (int i = 0; i < 4; ++i)
            original.rotateLeft(context);
        assertEquals(sample(), original, "four turns are the identity");
        assertEquals(3, context.surroundingBox().pMax().x + 1 - context.surroundingBox().pMin().x);
    }

    @Test
    void rotationSurvivesSaving() throws Exception {
        BptBlueprint bpt = sample();
        bpt.rotateLeft(context(bpt));
        bpt.file = dir.resolve("r.bpt");
        bpt.save(REGISTRIES);

        BptBase loaded = BptBase.readBluePrint(bpt.file, 1, REGISTRIES);
        assertEquals(bpt, loaded);
        assertSame(stairs(Direction.EAST), loaded.getSlot(3, 0, 0).state);
    }

    @Test
    void mirror() {
        BptBlueprint bpt = sample();

        bpt.mirror(context(bpt), Mirror.FRONT_BACK);

        assertSame(stairs(Direction.NORTH), bpt.getSlot(2, 0, 0).state, "flipping x leaves north stairs");
        assertSame(Blocks.STONE.defaultBlockState(), bpt.getSlot(0, 0, 3).state);
        assertEquals(3, bpt.anchorX);
        assertEquals(2, bpt.getSlot(2, 0, 0).x);

        bpt.mirror(context(bpt), Mirror.FRONT_BACK);
        assertEquals(sample(), bpt);

        bpt.mirror(context(bpt), Mirror.LEFT_RIGHT);
        assertSame(stairs(Direction.SOUTH), bpt.getSlot(0, 0, 3).state, "flipping z turns north stairs south");
        assertSame(Blocks.CHEST, bpt.getSlot(1, 1, 1).state.getBlock());
        assertEquals(1, bpt.anchorZ);

        bpt.mirror(context(bpt), Mirror.NONE);
        assertEquals(1, bpt.anchorZ);
    }

    @Test
    void equalsComparesBlocks() {
        assertEquals(sample(), sample());
        assertEquals(sample().hashCode(), sample().hashCode());

        BptBlueprint other = sample();
        other.setBlockState(2, 0, 3, Blocks.DIRT.defaultBlockState());
        assertNotEquals(sample(), other);

        assertNotEquals(sample(), new BptTemplate(3, 2, 4));
    }

    @Test
    void cloneIsDeep() {
        BptBlueprint bpt = sample();
        BptBlueprint copy = (BptBlueprint) bpt.clone();

        assertEquals(bpt, copy);
        copy.getSlot(1, 1, 2).storedRequirements.get(1).setCount(1);
        copy.getSlot(1, 1, 2).cpt.putString("CustomName", "\"Other\"");
        assertEquals(5, bpt.getSlot(1, 1, 2).storedRequirements.get(1).getCount());
        assertEquals("\"Box\"", bpt.getSlot(1, 1, 2).cpt.getStringOr("CustomName", ""));
    }

    @Test
    void legacyFileIsRefused() throws IOException {
        Path old = dir.resolve("old.bpt");
        Files.writeString(old, "version:4.2.2\nkind:blueprint\nsizeX:1\n", StandardCharsets.ISO_8859_1);
        Path older = dir.resolve("older.bpt");
        Files.writeString(older, "kind:template\nsizeX:1\n", StandardCharsets.ISO_8859_1);

        assertTrue(BptBase.isLegacyFile(old));
        assertTrue(BptBase.isLegacyFile(older));
        BptError error = assertThrows(BptError.class, () -> BptBase.readBluePrint(old, 1, REGISTRIES));
        assertEquals(BptBase.LEGACY_MESSAGE, error.getMessage());
        assertNull(BptBase.loadBluePrint(older, 1, REGISTRIES));

        Path junk = dir.resolve("junk.bpt");
        Files.write(junk, new byte[] { 1, 2, 3 });
        assertFalse(BptBase.isLegacyFile(junk));
        assertThrows(BptError.class, () -> BptBase.readBluePrint(junk, 1, REGISTRIES));
    }

    @Test
    void versionsAreChecked() {
        CompoundTag nbt = sample().writeToNBT(REGISTRIES);

        CompoundTag newer = nbt.copy();
        newer.putInt("BptVersion", BptBase.BPT_VERSION + 1);
        assertThrows(BptError.class, () -> BptBase.readFromNBT(newer, 1, REGISTRIES));

        CompoundTag none = nbt.copy();
        none.remove("BptVersion");
        assertThrows(BptError.class, () -> BptBase.readFromNBT(none, 1, REGISTRIES));

        CompoundTag future = nbt.copy();
        future.putInt("DataVersion", BptBase.currentDataVersion() + 1);
        assertThrows(BptError.class, () -> BptBase.readFromNBT(future, 1, REGISTRIES));

        CompoundTag kind = nbt.copy();
        kind.putString("kind", "schematic");
        assertThrows(BptError.class, () -> BptBase.readFromNBT(kind, 1, REGISTRIES));

        CompoundTag huge = nbt.copy();
        huge.putInt("sizeY", BptBase.MAX_SIZE + 1);
        assertThrows(BptError.class, () -> BptBase.readFromNBT(huge, 1, REGISTRIES));

        CompoundTag shortArray = nbt.copy();
        shortArray.putIntArray("blocks", new int[] { 0 });
        assertThrows(BptError.class, () -> BptBase.readFromNBT(shortArray, 1, REGISTRIES));

        CompoundTag unknown = nbt.copy();
        CompoundTag missing = new CompoundTag();
        missing.putString("id", "othermod:gone");
        unknown.getListOrEmpty("palette").set(0, missing);
        BptError error = assertThrows(BptError.class, () -> BptBase.readFromNBT(unknown, 1, REGISTRIES));
        assertTrue(error.getMessage().contains("othermod:gone"), error.getMessage());
    }

    @Test
    void olderDataVersionIsUpgraded() throws BptError {
        // 1.16.5 (data version 2586) called the dirt path "grass_path"; 1.17 renamed it.
        BptBlueprint bpt = new BptBlueprint(1, 1, 1);
        bpt.setBlockState(0, 0, 0, Blocks.DIRT_PATH.defaultBlockState());
        bpt.getSlot(0, 0, 0).storedRequirements.add(new ItemStack(Items.DIRT_PATH));
        CompoundTag nbt = bpt.writeToNBT(REGISTRIES);
        nbt.putInt("DataVersion", 2586);
        CompoundTag oldState = new CompoundTag();
        oldState.putString("Name", "minecraft:grass_path");
        ListTag palette = new ListTag();
        palette.add(oldState);
        nbt.put("palette", palette);
        CompoundTag oldItem = new CompoundTag();
        oldItem.putString("id", "minecraft:grass_path");
        oldItem.putByte("Count", (byte) 1);
        nbt.getListOrEmpty("requirements").getCompoundOrEmpty(0).getListOrEmpty("items").set(0, oldItem);

        BptBase loaded = BptBase.readFromNBT(nbt, 1, REGISTRIES);

        assertEquals(2586, loaded.getDataVersion());
        assertSame(Blocks.DIRT_PATH.defaultBlockState(), loaded.getSlot(0, 0, 0).state);
        assertSame(Items.DIRT_PATH, loaded.getSlot(0, 0, 0).storedRequirements.getFirst().getItem());
    }

    @Test
    void namesAndPaths() {
        assertEquals("MyHouse 2", BlueprintPaths.sanitizeName(" My/../House: 2! "));
        assertEquals("unnamed", BlueprintPaths.sanitizeName("../.."));
        assertEquals("unnamed", BlueprintPaths.sanitizeName(null));
        assertEquals(BlueprintPaths.MAX_NAME_LENGTH, BlueprintPaths.sanitizeName("a".repeat(100)).length());

        assertEquals(dir.toAbsolutePath().normalize().resolve("a.bpt"), BlueprintPaths.resolve(dir, "a.bpt"));
        for (String bad : List.of("", ".", "..", "../a.bpt", "sub/a.bpt", "..\\a.bpt", "/etc/passwd"))
            assertNull(BlueprintPaths.resolve(dir, bad), bad);

        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        assertEquals("00000000-0000-0000-0000-000000000001.list", BlueprintPaths.playerIndexName(id));
    }

    @Test
    void rootIndexStoresAndReloads() throws IOException {
        Path library = dir.resolve("library");
        BptRootIndex index = new BptRootIndex(library, BlueprintPaths.ROOT_INDEX, REGISTRIES);
        index.loadIndex();

        BptBlueprint first = sample();
        first.setName("../My House!");
        BptBlueprint second = sample();
        second.setName("My House");
        BptTemplate third = new BptTemplate(1, 1, 1);
        third.setName("");

        assertEquals(1, index.storeBluePrint(first));
        assertEquals(2, index.storeBluePrint(second));
        assertEquals(3, index.storeBluePrint(third));
        assertTrue(Files.exists(library.resolve("My House.bpt")));
        assertTrue(Files.exists(library.resolve("My House_0.bpt")));
        assertTrue(Files.exists(library.resolve("unnamed.bpt")));

        // A 1.6.4 file dropped in, a hand-edited line pointing outside, and a deleted blueprint.
        Files.writeString(library.resolve("old.bpt"), "version:4.2.2\nkind:blueprint\n", StandardCharsets.ISO_8859_1);
        Files.writeString(library.resolve(BlueprintPaths.ROOT_INDEX), "My House.bpt\n../outside.bpt\nunnamed.bpt\n",
                StandardCharsets.ISO_8859_1);
        Files.delete(library.resolve("unnamed.bpt"));

        BptRootIndex reloaded = new BptRootIndex(library, BlueprintPaths.ROOT_INDEX, REGISTRIES);
        reloaded.loadIndex();
        reloaded.importNewFiles();

        assertEquals(first, reloaded.getBluePrint(1));
        assertEquals(1, reloaded.getBluePrint(1).position);
        assertNull(reloaded.getBluePrint(2), "the outside line keeps its number but loads nothing");
        assertNull(reloaded.getBluePrint(3), "deleted file");
        assertEquals(Integer.valueOf(4), reloaded.filesSet.get("My House_0.bpt"));
        assertEquals(Integer.valueOf(5), reloaded.filesSet.get("old.bpt"));
        assertNotNull(reloaded.getBluePrintFile(5));
        assertNull(reloaded.getBluePrint(5), "1.6.4 files are listed, not loaded");
        assertNull(reloaded.getBluePrint("missing.bpt"));
        assertEquals(second, reloaded.getBluePrint("My House_0.bpt"));

        List<String> lines = Files.readAllLines(library.resolve(BlueprintPaths.ROOT_INDEX), StandardCharsets.ISO_8859_1);
        assertEquals(List.of("My House.bpt", "", "", "My House_0.bpt", "old.bpt"), lines);
    }

    @Test
    void playerIndex() throws IOException {
        Path library = dir.resolve("library");
        BptRootIndex root = new BptRootIndex(library, BlueprintPaths.ROOT_INDEX, REGISTRIES);
        root.loadIndex();
        BptBlueprint a = sample();
        a.setName("a");
        BptBlueprint b = sample();
        b.setName("b");
        root.storeBluePrint(a);
        root.storeBluePrint(b);

        UUID player = UUID.randomUUID();
        BptPlayerIndex index = BptPlayerIndex.forPlayer(player, root);
        assertTrue(Files.exists(library.resolve(player + ".list")));
        assertEquals("a.bpt", index.nextBpt(null));
        assertEquals("b.bpt", index.nextBpt("a.bpt"));
        assertEquals("b.bpt", index.prevBpt(null));

        index.deleteBluePrint("a.bpt");
        Files.writeString(library.resolve(player + ".list"), "b.bpt\n../../x.bpt\n", StandardCharsets.ISO_8859_1, StandardOpenOption.APPEND);
        BptPlayerIndex reloaded = BptPlayerIndex.forPlayer(player, root);
        assertEquals("b.bpt", reloaded.nextBpt(null));
        assertNull(reloaded.nextBpt("b.bpt"));

        assertThrows(IOException.class, () -> new BptPlayerIndex("../x.list", root));
    }
}
