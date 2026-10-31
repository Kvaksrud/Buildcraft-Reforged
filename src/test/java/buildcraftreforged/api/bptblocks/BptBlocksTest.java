package buildcraftreforged.api.bptblocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import buildcraftreforged.api.blueprints.BlueprintManager;
import buildcraftreforged.api.blueprints.BptBlock;
import buildcraftreforged.api.blueprints.BptSlotInfo;
import buildcraftreforged.api.blueprints.IBptContext;
import buildcraftreforged.api.core.IBox;
import buildcraftreforged.api.core.Position;
import buildcraftreforged.transport.PipeModuleType;
import buildcraftreforged.transport.blueprints.BptBlockPipe;
import java.util.LinkedList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * #76: the schematics' rotation, mirroring, requirements and build rules that need no world, and the schematic
 * registry. Building in a world is GameTest {@code schematics_capture_and_rebuild_turned}.
 */
class BptBlocksTest {

    /** A context without a world, as for turning a stored blueprint. */
    private static final IBptContext NO_WORLD = new IBptContext() {
        @Override
        public Position rotatePositionLeft(Position pos) {
            return pos;
        }

        @Override
        public IBox surroundingBox() {
            return null;
        }

        @Override
        public Level world() {
            return null;
        }
    };

    /** As BptFormatTest: the unit-test game binds no item components, so bind the few used. */
    @BeforeAll
    @SuppressWarnings("deprecation") // builtInRegistryHolder: there is no other way to reach the item's holder here
    static void bindItemComponents() {
        for (Item item : new Item[] { Items.OAK_DOOR, Items.IRON_DOOR, Items.BED.red(), Items.OAK_STAIRS, Items.CHEST,
                Items.LEVER, Items.REPEATER, Items.OAK_SIGN, Items.WATER_BUCKET, Items.STONE, Items.REDSTONE }) {
            Holder.Reference<Item> holder = item.builtInRegistryHolder();
            if (!holder.areComponentsBound()) {
                holder.bindComponents(DataComponentMap.builder().addAll(DataComponents.COMMON_ITEM_COMPONENTS).build());
            }
        }
    }

    private static BptSlotInfo slot(BlockState state) {
        BptSlotInfo slot = new BptSlotInfo();
        slot.state = state;
        return slot;
    }

    private static BptSlotInfo turned(BptBlock bpt, BlockState state, int turns) {
        BptSlotInfo slot = slot(state);
        for (int i = 0; i < turns; ++i) {
            bpt.rotateLeft(slot, NO_WORLD);
        }
        return slot;
    }

    private static List<ItemStack> requirements(BptBlock bpt, BlockState state) {
        LinkedList<ItemStack> requirements = new LinkedList<>();
        bpt.addRequirements(slot(state), NO_WORLD, requirements);
        return requirements;
    }

    private static void assertStacks(List<ItemStack> actual, ItemStack... expected) {
        assertEquals(expected.length, actual.size(), "requirements " + actual);
        for (int i = 0; i < expected.length; ++i) {
            assertTrue(ItemStack.matches(expected[i], actual.get(i)), "requirement " + i + ": " + actual.get(i));
        }
    }

    @Test
    void doorTurnsBothHalvesAndCostsOneDoor() {
        BptBlockDoor bpt = new BptBlockDoor(Blocks.OAK_DOOR, new ItemStack(Items.OAK_DOOR));
        BlockState lower = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH)
                .setValue(DoorBlock.HINGE, DoorHingeSide.RIGHT).setValue(DoorBlock.OPEN, true);
        BlockState upper = lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);

        // Clockwise seen from above, as BptBlockRotateMeta's tables turned: north, east, south, west.
        assertEquals(lower.setValue(DoorBlock.FACING, Direction.EAST), turned(bpt, lower, 1).state);
        assertEquals(upper.setValue(DoorBlock.FACING, Direction.SOUTH), turned(bpt, upper, 2).state);
        assertEquals(lower.setValue(DoorBlock.FACING, Direction.WEST), turned(bpt, lower, 3).state);
        assertEquals(lower, turned(bpt, lower, 4).state);

        assertStacks(requirements(bpt, lower), new ItemStack(Items.OAK_DOOR));
        assertStacks(requirements(bpt, upper));
        assertFalse(bpt.ignoreBuilding(slot(lower)));
        assertTrue(bpt.ignoreBuilding(slot(upper)));

        // Mirroring left to right swaps the hinge, as the block state does.
        BptSlotInfo mirrored = slot(lower);
        bpt.mirror(mirrored, NO_WORLD, Mirror.LEFT_RIGHT);
        assertEquals(DoorHingeSide.LEFT, mirrored.state.getValue(DoorBlock.HINGE));
        assertEquals(Direction.SOUTH, mirrored.state.getValue(DoorBlock.FACING));
    }

    @Test
    void stairsTurnAndKeepTheirHalf() {
        BptBlockStairs bpt = new BptBlockStairs(Blocks.OAK_STAIRS);
        BlockState stairs = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH)
                .setValue(StairBlock.HALF, Half.TOP);

        for (int turns = 0; turns < 4; ++turns) {
            BptSlotInfo slot = turned(bpt, stairs, turns);
            assertEquals(Direction.from2DDataValue(Direction.NORTH.get2DDataValue() + turns),
                    slot.state.getValue(StairBlock.FACING));
            assertEquals(Half.TOP, slot.state.getValue(StairBlock.HALF));
        }

        assertStacks(requirements(bpt, stairs), new ItemStack(Items.OAK_STAIRS));
    }

    @Test
    void bedTurnsAndCostsOneBedFromTheFoot() {
        BptBlockBed bpt = new BptBlockBed(Blocks.BED.red());
        BlockState foot = Blocks.BED.red().defaultBlockState().setValue(AbstractBedBlock.FACING, Direction.EAST);
        BlockState head = foot.setValue(AbstractBedBlock.PART, BedPart.HEAD);

        assertEquals(foot.setValue(AbstractBedBlock.FACING, Direction.SOUTH), turned(bpt, foot, 1).state);
        assertEquals(head.setValue(AbstractBedBlock.FACING, Direction.WEST), turned(bpt, head, 2).state);
        assertEquals(foot, turned(bpt, foot, 4).state);

        assertStacks(requirements(bpt, foot), new ItemStack(Items.BED.red()));
        assertStacks(requirements(bpt, head));
        assertFalse(bpt.ignoreBuilding(slot(foot)));
        assertTrue(bpt.ignoreBuilding(slot(head)));
    }

    @Test
    void chestTurnsAndCostsItself() {
        BptBlockRotateInventory bpt = new BptBlockRotateInventory(Blocks.CHEST);
        BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH);

        assertEquals(chest.setValue(ChestBlock.FACING, Direction.EAST), turned(bpt, chest, 1).state);
        assertEquals(chest.setValue(ChestBlock.FACING, Direction.SOUTH), turned(bpt, chest, 2).state);
        assertEquals(chest, turned(bpt, chest, 4).state);

        // Without recorded contents (no "inv" list) only the chest; contents are covered by the GameTest.
        assertStacks(requirements(bpt, chest), new ItemStack(Items.CHEST));
    }

    @Test
    void leverRepeaterAndSignsTurnTheirFacingOnly() {
        BptBlockLever lever = new BptBlockLever(Blocks.LEVER);
        BlockState wallLever = Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.WALL)
                .setValue(LeverBlock.FACING, Direction.SOUTH).setValue(LeverBlock.POWERED, true);
        assertEquals(wallLever.setValue(LeverBlock.FACING, Direction.WEST), turned(lever, wallLever, 1).state);
        BlockState floorLever = wallLever.setValue(LeverBlock.FACE, AttachFace.FLOOR);
        assertEquals(AttachFace.FLOOR, turned(lever, floorLever, 1).state.getValue(LeverBlock.FACE));
        assertStacks(requirements(lever, wallLever), new ItemStack(Items.LEVER));

        BptBlockRedstoneRepeater repeater = new BptBlockRedstoneRepeater(Blocks.REPEATER);
        BlockState slow = Blocks.REPEATER.defaultBlockState().setValue(RepeaterBlock.FACING, Direction.NORTH)
                .setValue(RepeaterBlock.DELAY, 3);
        assertEquals(slow.setValue(RepeaterBlock.FACING, Direction.EAST), turned(repeater, slow, 1).state);
        assertStacks(requirements(repeater, slow), new ItemStack(Items.REPEATER));

        // 4.2.2 added 4 of 16 steps to a standing sign and left wall signs as they were; both turn now.
        BptBlockSign standing = new BptBlockSign(Blocks.OAK_SIGN, false);
        BlockState post = Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 3);
        assertEquals(7, turned(standing, post, 1).state.getValue(StandingSignBlock.ROTATION));
        assertEquals(3, turned(standing, post, 4).state.getValue(StandingSignBlock.ROTATION));
        BptBlockSign wall = new BptBlockSign(Blocks.OAK_WALL_SIGN, true);
        BlockState onWall = Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, Direction.NORTH);
        assertEquals(Direction.EAST, turned(wall, onWall, 1).state.getValue(WallSignBlock.FACING));
        assertStacks(requirements(wall, onWall), new ItemStack(Items.OAK_SIGN));
    }

    @Test
    void fluidsBuildSourcesOnlyFromBuckets() {
        BptBlockFluid bpt = new BptBlockFluid(Blocks.WATER, new ItemStack(Items.WATER_BUCKET));
        BlockState source = Blocks.WATER.defaultBlockState();
        BlockState flowing = source.setValue(LiquidBlock.LEVEL, 3);

        assertStacks(requirements(bpt, source), new ItemStack(Items.WATER_BUCKET));
        assertStacks(requirements(bpt, flowing));
        assertFalse(bpt.ignoreBuilding(slot(source)));
        assertTrue(bpt.ignoreBuilding(slot(flowing)));
        assertEquals(flowing, turned(bpt, flowing, 1).state);
    }

    @Test
    void customStacksAndIgnoredBlocks() {
        assertStacks(requirements(new BptBlockCustomStack(Blocks.STONE, new ItemStack(Blocks.STONE)),
                Blocks.STONE.defaultBlockState()), new ItemStack(Items.STONE));
        assertStacks(requirements(new BptBlockCustomStack(Blocks.REDSTONE_WIRE, new ItemStack(Items.REDSTONE)),
                Blocks.REDSTONE_WIRE.defaultBlockState()), new ItemStack(Items.REDSTONE));

        BptBlockIgnore ignore = new BptBlockIgnore(Blocks.SNOW);
        assertStacks(requirements(ignore, Blocks.SNOW.defaultBlockState()));
        assertTrue(ignore.ignoreBuilding(slot(Blocks.SNOW.defaultBlockState())));
        assertTrue(ignore.isValid(slot(Blocks.SNOW.defaultBlockState()), NO_WORLD));
    }

    @Test
    void delegateCopiesSharedProperties() {
        BlockState lit = Blocks.FURNACE.defaultBlockState().setValue(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT, true)
                .setValue(net.minecraft.world.level.block.AbstractFurnaceBlock.FACING, Direction.WEST);
        BlockState smoker = BptBlockDelegate.copyProperties(lit, Blocks.SMOKER.defaultBlockState());
        assertSame(Blocks.SMOKER, smoker.getBlock());
        assertEquals(Direction.WEST, smoker.getValue(net.minecraft.world.level.block.AbstractFurnaceBlock.FACING));
        assertTrue(smoker.getValue(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT));
    }

    @Test
    void registryPrefersConstructedSchematicsOverFamilies() {
        // A block nothing else in the tests registers.
        Block block = Blocks.DEEPSLATE_TILE_STAIRS;
        Block other = Blocks.MUD_BRICK_STAIRS;
        BlueprintManager.registerFamily(candidate -> candidate == block || candidate == other, BptBlockStairs::new);

        BptBlock explicit = new BptBlockIgnoreMeta(block);
        assertSame(explicit, BlueprintManager.getBptBlock(block), "a constructed schematic beats a family");

        BptBlock fromFamily = BlueprintManager.getBptBlock(other);
        assertInstanceOf(BptBlockStairs.class, fromFamily);
        assertSame(fromFamily, BlueprintManager.getBptBlock(other), "the family's schematic is kept");

        BptBlock fallback = BlueprintManager.getBptBlock(Blocks.CALCITE);
        assertSame(BptBlock.class, fallback.getClass(), "no family: the default schematic");
    }

    @Test
    void pipeSidesTurnWithTheBlueprint() {
        CompoundTag cpt = new CompoundTag();
        cpt.putByte("plugs", (byte) (1 << Direction.NORTH.get3DDataValue() | 1 << Direction.UP.get3DDataValue()));
        cpt.putByte("disconnected", (byte) (1 << Direction.WEST.get3DDataValue()));
        cpt.putString("facade[" + Direction.SOUTH.get3DDataValue() + "]", "south facade");
        cpt.putString("module", PipeModuleType.SORTER.getSerializedName());
        CompoundTag settings = new CompoundTag();
        settings.putInt("facing", Direction.EAST.get3DDataValue());
        ListTag filters = new ListTag();
        CompoundTag filter = new CompoundTag();
        // Row NORTH, column 4.
        filter.putByte("Slot", (byte) (Direction.NORTH.get3DDataValue() * 9 + 4));
        filters.add(filter);
        settings.put("filters", filters);
        cpt.put("module_settings", settings);

        BptBlockPipe.turnSides(cpt, net.minecraft.world.level.block.Rotation.CLOCKWISE_90::rotate);

        assertEquals((byte) (1 << Direction.EAST.get3DDataValue() | 1 << Direction.UP.get3DDataValue()), cpt.getByteOr("plugs", (byte) 0));
        assertEquals((byte) (1 << Direction.NORTH.get3DDataValue()), cpt.getByteOr("disconnected", (byte) 0));
        assertEquals("south facade", cpt.getStringOr("facade[" + Direction.WEST.get3DDataValue() + "]", ""));
        assertFalse(cpt.contains("facade[" + Direction.SOUTH.get3DDataValue() + "]"));
        CompoundTag turnedSettings = cpt.getCompoundOrEmpty("module_settings");
        assertEquals(Direction.SOUTH.get3DDataValue(), turnedSettings.getIntOr("facing", -1));
        CompoundTag turnedFilter = turnedSettings.getListOrEmpty("filters").getCompoundOrEmpty(0);
        assertEquals(Direction.EAST.get3DDataValue() * 9 + 4, turnedFilter.getByteOr("Slot", (byte) 0));

        // Mirroring front to back flips east and west only.
        BptBlockPipe.turnSides(cpt, Mirror.FRONT_BACK::mirror);
        assertEquals((byte) (1 << Direction.WEST.get3DDataValue() | 1 << Direction.UP.get3DDataValue()), cpt.getByteOr("plugs", (byte) 0));
        assertEquals("south facade", cpt.getStringOr("facade[" + Direction.EAST.get3DDataValue() + "]", ""));
    }
}
