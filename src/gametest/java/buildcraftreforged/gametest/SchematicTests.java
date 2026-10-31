package buildcraftreforged.gametest;

import buildcraftreforged.api.transport.IPipeTile.PipeType;
import buildcraftreforged.core.Box;
import buildcraftreforged.core.blueprints.BptBlueprint;
import buildcraftreforged.core.blueprints.BptContext;
import buildcraftreforged.core.blueprints.BptSlot;
import buildcraftreforged.energy.BlockEngine;
import buildcraftreforged.energy.EnergyRegistry;
import buildcraftreforged.energy.TileEngineWood;
import buildcraftreforged.factory.FactoryRegistry;
import buildcraftreforged.factory.TileTank;
import buildcraftreforged.silicon.SiliconRegistry;
import buildcraftreforged.silicon.TileLaser;
import buildcraftreforged.transport.PipeMaterial;
import buildcraftreforged.transport.PipeModuleType;
import buildcraftreforged.transport.TilePipe;
import buildcraftreforged.transport.TransportRegistry;
import buildcraftreforged.transport.module.ModuleExtractor;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * #76 schematics: capture a small mixed structure (vanilla blocks with facings, a door, a bed, a chest with contents,
 * redstone, a pipe with a module and a plug, an engine, a tank with water, an owned laser), turn it once and build it
 * beside the original the way the builder will: bottom layer first, two-block units from their lower or foot half,
 * then every slot's post-processing.
 */
final class SchematicTests {
    static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();
    static final int MAX_TICKS = 20;

    /** The captured box, relative to the test origin: 5 wide (x), 3 high (a stone floor and two layers), 3 deep (z). */
    private static final BlockPos MIN = new BlockPos(1, 2, 1);
    private static final BlockPos MAX = new BlockPos(5, 4, 3);
    /** Where the turned copy goes: 3 wide, 5 deep. */
    private static final BlockPos COPY = new BlockPos(8, 2, 1);

    static {
        TESTS.put("schematics_capture_and_rebuild_turned", SchematicTests::captureAndRebuildTurned);
    }

    private SchematicTests() {
    }

    /** A position of the original, by its place in the box (x 0-4, y 0-2, z 0-2). */
    private static BlockPos at(int x, int y, int z) {
        return MIN.offset(x, y, z);
    }

    /** A position of the copy, by its place in the turned box (x 0-2, y 0-2, z 0-4). */
    private static BlockPos copy(int x, int y, int z) {
        return COPY.offset(x, y, z);
    }

    static void captureAndRebuildTurned(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        // Layer 0: stone floor. Layer 1, row z = 0: stairs, a door, a chest, two stone; row z = 1: redstone wire, the
        // door's swing, a wood item pipe with an Extractor facing the chest and a plug on its west side, a lever on
        // the stone, a repeater; row z = 2: a bed (foot, head), nothing, a tank with water, an owned laser.
        // Layer 2: the door's upper half and a redstone engine on the tank.
        for (int x = 0; x < 5; ++x)
            for (int z = 0; z < 3; ++z)
                helper.setBlock(at(x, 0, z), Blocks.STONE);
        helper.setBlock(at(0, 1, 0), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
        BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        helper.setBlock(at(1, 1, 0), door);
        helper.setBlock(at(1, 2, 0), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        helper.setBlock(at(2, 1, 0), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH));
        helper.getBlockEntity(at(2, 1, 0), ChestBlockEntity.class).setItem(4, new ItemStack(Items.DIAMOND, 5));
        helper.setBlock(at(3, 1, 0), Blocks.STONE);
        helper.setBlock(at(4, 1, 0), Blocks.STONE);
        helper.setBlock(at(0, 1, 1), Blocks.REDSTONE_WIRE);
        helper.setBlock(at(2, 1, 1), TransportRegistry.getPipe(PipeType.ITEM, PipeMaterial.WOOD));
        TilePipe pipe = helper.getBlockEntity(at(2, 1, 1), TilePipe.class);
        pipe.setModuleType(PipeModuleType.EXTRACTOR);
        pipe.addPlug(Direction.WEST);
        helper.setBlock(at(3, 1, 1), Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.WALL)
                .setValue(LeverBlock.FACING, Direction.SOUTH));
        helper.setBlock(at(4, 1, 1), Blocks.REPEATER.defaultBlockState().setValue(RepeaterBlock.FACING, Direction.NORTH)
                .setValue(RepeaterBlock.DELAY, 3));
        BlockState foot = Blocks.BED.red().defaultBlockState().setValue(AbstractBedBlock.FACING, Direction.EAST);
        helper.setBlock(at(0, 1, 2), foot);
        helper.setBlock(at(1, 1, 2), foot.setValue(AbstractBedBlock.PART, BedPart.HEAD));
        helper.setBlock(at(3, 1, 2), FactoryRegistry.tankBlock.get());
        helper.getBlockEntity(at(3, 1, 2), TileTank.class).tank.fill(new FluidStack(Fluids.WATER, 1000), true);
        helper.setBlock(at(3, 2, 2), EnergyRegistry.engineWood.block().get().defaultBlockState()
                .setValue(BlockEngine.FACING, Direction.UP));
        helper.setBlock(at(4, 1, 2), SiliconRegistry.laserBlock.get());
        ServerPlayer placer = helper.makeMockServerPlayerInLevel();
        placer.snapTo(Vec3.atBottomCenterOf(helper.absolutePos(at(4, 1, 2)).above(6)));
        BlockPos laserPos = helper.absolutePos(at(4, 1, 2));
        SiliconRegistry.laserBlock.get().setPlacedBy(level, laserPos, level.getBlockState(laserPos), placer,
                new ItemStack(SiliconRegistry.laserBlock.get()));
        helper.assertTrue(helper.getBlockEntity(at(4, 1, 2), TileLaser.class).getOwnerProfile() != null, "laser has no owner");

        // The pipe finds its connections and the Extractor its facing on the next ticks.
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(((ModuleExtractor) pipe.getModule()).getFacing() == Direction.NORTH,
                    "the Extractor faces " + ((ModuleExtractor) pipe.getModule()).getFacing());

            // Capture, as the architect does.
            BlockPos min = helper.absolutePos(MIN);
            BlockPos max = helper.absolutePos(MAX);
            Box box = new Box();
            box.initialize(Math.min(min.getX(), max.getX()), min.getY(), Math.min(min.getZ(), max.getZ()),
                    Math.max(min.getX(), max.getX()), max.getY(), Math.max(min.getZ(), max.getZ()));
            BptBlueprint bpt = new BptBlueprint(box.sizeX(), box.sizeY(), box.sizeZ());
            BptContext context = new BptContext(level, bpt, box);
            for (int x = box.xMin; x <= box.xMax; ++x)
                for (int y = box.yMin; y <= box.yMax; ++y)
                    for (int z = box.zMin; z <= box.zMax; ++z)
                        bpt.readFromWorld(context, null, x, y, z);

            // What the builder will ask for.
            Map<Item, Integer> required = new HashMap<>();
            for (int x = 0; x < bpt.sizeX; ++x)
                for (int y = 0; y < bpt.sizeY; ++y)
                    for (int z = 0; z < bpt.sizeZ; ++z) {
                        BptSlot slot = bpt.getSlot(x, y, z);
                        if (slot != null && !slot.ignoreBuilding())
                            for (ItemStack stack : slot.getRequirements(context))
                                required.merge(stack.getItem(), stack.getCount(), Integer::sum);
                    }
            Map<Item, Integer> expected = new HashMap<>();
            expected.put(Items.STONE, 17);
            expected.put(Items.OAK_STAIRS, 1);
            expected.put(Items.OAK_DOOR, 1);
            expected.put(Items.CHEST, 1);
            expected.put(Items.DIAMOND, 5);
            expected.put(Items.REDSTONE, 1);
            expected.put(TransportRegistry.getPipe(PipeType.ITEM, PipeMaterial.WOOD).asItem(), 1);
            expected.put(TransportRegistry.getModuleItem(PipeModuleType.EXTRACTOR), 1);
            expected.put(TransportRegistry.plugItem.get(), 1);
            expected.put(Items.LEVER, 1);
            expected.put(Items.REPEATER, 1);
            expected.put(Items.BED.red(), 1);
            expected.put(FactoryRegistry.tankBlockItem.get(), 1);
            expected.put(EnergyRegistry.engineWood.item().get(), 1);
            expected.put(SiliconRegistry.laserBlockItem.get(), 1);
            helper.assertTrue(required.equals(expected), "requirements " + required);

            // The tank's water is not captured (residual liquid), nor the laser's owner.
            BptSlot tankSlot = bpt.getSlot(3, 1, 2);
            helper.assertTrue(tankSlot.cpt.isEmpty(), "tank data captured: " + tankSlot.cpt);
            BptSlot laserSlot = bpt.getSlot(4, 1, 2);
            helper.assertTrue(laserSlot.cpt.contains("id") && !laserSlot.cpt.contains("ownerId") && !laserSlot.cpt.contains("owner"),
                    "laser data " + laserSlot.cpt);

            // Turn once: (x, y, z) goes to (sizeZ - 1 - z, y, x) = (2 - z, y, x), facings turn clockwise.
            Box copyBox = new Box();
            copyBox.initialize(0, 0, 0, bpt.sizeX - 1, bpt.sizeY - 1, bpt.sizeZ - 1);
            bpt.rotateLeft(new BptContext(null, bpt, copyBox));

            // Build as the builder does: layer by layer, each slot moved to its world position.
            BlockPos origin = helper.absolutePos(COPY);
            BptContext build = new BptContext(level, bpt, copyBox);
            for (int y = 0; y < bpt.sizeY; ++y)
                for (int x = 0; x < bpt.sizeX; ++x)
                    for (int z = 0; z < bpt.sizeZ; ++z) {
                        BptSlot slot = bpt.getSlot(x, y, z);
                        if (slot == null)
                            continue;
                        slot.x = origin.getX() + x;
                        slot.y = origin.getY() + y;
                        slot.z = origin.getZ() + z;
                        if (!slot.ignoreBuilding())
                            slot.buildBlock(build);
                    }
            for (int y = 0; y < bpt.sizeY; ++y)
                for (int x = 0; x < bpt.sizeX; ++x)
                    for (int z = 0; z < bpt.sizeZ; ++z) {
                        BptSlot slot = bpt.getSlot(x, y, z);
                        if (slot != null && !slot.ignoreBuilding())
                            slot.postProcessing(build);
                    }

            helper.assertBlockPresent(Blocks.STONE, copy(0, 0, 0));
            helper.assertBlockPresent(Blocks.STONE, copy(2, 0, 4));
            helper.assertBlockState(copy(2, 1, 0), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
            BlockState builtDoor = door.setValue(DoorBlock.FACING, Direction.EAST);
            helper.assertBlockState(copy(2, 1, 1), builtDoor);
            helper.assertBlockState(copy(2, 2, 1), builtDoor.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
            helper.assertBlockState(copy(2, 1, 2), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST));
            ChestBlockEntity chest = helper.getBlockEntity(copy(2, 1, 2), ChestBlockEntity.class);
            helper.assertTrue(ItemStack.matches(chest.getItem(4), new ItemStack(Items.DIAMOND, 5)), "built chest holds " + chest.getItem(4));
            helper.assertBlockPresent(Blocks.STONE, copy(2, 1, 3));
            helper.assertBlockPresent(Blocks.STONE, copy(2, 1, 4));
            helper.assertBlockPresent(Blocks.REDSTONE_WIRE, copy(1, 1, 0));
            TilePipe builtPipe = helper.getBlockEntity(copy(1, 1, 2), TilePipe.class);
            helper.assertTrue(builtPipe.getModule() instanceof ModuleExtractor extractor && extractor.getFacing() == Direction.EAST,
                    "built Extractor faces " + (builtPipe.getModule() instanceof ModuleExtractor e ? e.getFacing() : builtPipe.getModule()));
            helper.assertTrue(builtPipe.hasPlug(Direction.NORTH) && !builtPipe.hasPlug(Direction.WEST),
                    "built pipe plugs " + builtPipe.getPluggedMask());
            helper.assertBlockState(copy(1, 1, 3), Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.WALL)
                    .setValue(LeverBlock.FACING, Direction.WEST));
            helper.assertBlockState(copy(1, 1, 4), Blocks.REPEATER.defaultBlockState().setValue(RepeaterBlock.FACING, Direction.EAST)
                    .setValue(RepeaterBlock.DELAY, 3));
            BlockState builtFoot = foot.setValue(AbstractBedBlock.FACING, Direction.SOUTH);
            helper.assertBlockState(copy(0, 1, 0), builtFoot);
            helper.assertBlockState(copy(0, 1, 1), builtFoot.setValue(AbstractBedBlock.PART, BedPart.HEAD));
            helper.assertTrue(helper.getBlockEntity(copy(0, 1, 3), TileTank.class).tank.isEmpty(), "built tank holds fluid");
            helper.assertTrue(helper.getBlockEntity(copy(0, 2, 3), TileEngineWood.class) != null, "no engine built");
            helper.assertBlockProperty(copy(0, 2, 3), BlockEngine.FACING, Direction.UP);
            helper.assertTrue(helper.getBlockEntity(copy(0, 1, 4), TileLaser.class).getOwnerProfile() == null,
                    "built laser kept the original's owner");

            // The original is unchanged.
            helper.assertTrue(ItemStack.matches(helper.getBlockEntity(at(2, 1, 0), ChestBlockEntity.class).getItem(4),
                    new ItemStack(Items.DIAMOND, 5)), "original chest emptied");

            // The built Extractor keeps its facing once the pipe has found its connections.
            helper.runAfterDelay(2, () -> {
                TilePipe settled = helper.getBlockEntity(copy(1, 1, 2), TilePipe.class);
                helper.assertTrue(((ModuleExtractor) settled.getModule()).getFacing() == Direction.EAST,
                        "settled Extractor faces " + ((ModuleExtractor) settled.getModule()).getFacing());
                helper.succeed();
            });
        });
    }
}
