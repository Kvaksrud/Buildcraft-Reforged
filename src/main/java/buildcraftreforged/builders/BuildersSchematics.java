/*
 * Buildcraft Reforged, 2026: the schematic registrations of 4.2.2 BuildCraftBuilders.load, BuildCraftEnergy.load,
 * BuildCraftFactory.load, BuildCraftSilicon.load and BuildCraftTransport.load, gathered in one place (#76).
 */

package buildcraftreforged.builders;

import buildcraftreforged.api.blueprints.BlueprintManager;
import buildcraftreforged.api.blueprints.BptBlock;
import buildcraftreforged.api.bptblocks.BptBlockBed;
import buildcraftreforged.api.bptblocks.BptBlockCustomStack;
import buildcraftreforged.api.bptblocks.BptBlockDirt;
import buildcraftreforged.api.bptblocks.BptBlockDoor;
import buildcraftreforged.api.bptblocks.BptBlockFluid;
import buildcraftreforged.api.bptblocks.BptBlockIgnore;
import buildcraftreforged.api.bptblocks.BptBlockIgnoreMeta;
import buildcraftreforged.api.bptblocks.BptBlockInventory;
import buildcraftreforged.api.bptblocks.BptBlockLever;
import buildcraftreforged.api.bptblocks.BptBlockPiston;
import buildcraftreforged.api.bptblocks.BptBlockPumpkin;
import buildcraftreforged.api.bptblocks.BptBlockRedstoneRepeater;
import buildcraftreforged.api.bptblocks.BptBlockRotateInventory;
import buildcraftreforged.api.bptblocks.BptBlockRotateMeta;
import buildcraftreforged.api.bptblocks.BptBlockSign;
import buildcraftreforged.api.bptblocks.BptBlockStairs;
import buildcraftreforged.api.bptblocks.BptBlockWallSide;
import buildcraftreforged.core.blueprints.BptBlockMachine;
import buildcraftreforged.energy.BlockEngine;
import buildcraftreforged.energy.BptBlockEngine;
import buildcraftreforged.factory.BptBlockFrame;
import buildcraftreforged.factory.BptBlockRefinery;
import buildcraftreforged.factory.BptBlockTank;
import buildcraftreforged.factory.FactoryRegistry;
import buildcraftreforged.silicon.SiliconRegistry;
import buildcraftreforged.transport.BlockPipe;
import buildcraftreforged.transport.blueprints.BptBlockPipe;
import java.util.function.Function;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;

/**
 * Which block gets which schematic. 4.2.2 constructed one schematic per vanilla block id in
 * {@code BuildCraftBuilders.load} and one per BuildCraft block in each module's {@code load}. Since the flattening many
 * of those ids are families of blocks (every wood's door, stairs, sign, button, fence gate and trapdoor; every bed
 * colour; the wall variants of torches and signs), so each 4.2.2 line becomes a family by block class where one exists,
 * which also covers other mods' doors and stairs. Everything is registered through
 * {@link BlueprintManager#registerFamily}, so a schematic another mod constructs for a block always wins.
 *
 * <p>Changes to the list, each because 4.2.2's block no longer exists or a sibling would otherwise lose its contents
 * or be built wrong:
 * <ul>
 * <li>Gone: {@code lockedChest} (the 1.6.4 April Fools chest), and the Delegate lines for the burning furnace, the
 * unlit redstone torch and the moving piston: lit is a property now, and a moving piston is ignored like its head
 * (4.2.2 built a moving-piston block in its place).</li>
 * <li>Families: the trapped chest, copper chests, smoker, blast furnace and dropper share their 4.2.2 sibling's class
 * and schematic; the attached stems cost seeds as the stems do; dirt paths count as dirt; hoppers and barrels keep
 * their contents as requirements as chests do (4.2.2 copied an unlisted container's contents for free).</li>
 * <li>BuildCraft's quarry, mining well, pump, flood gate and filler are built empty ({@link BptBlockMachine}).</li>
 * </ul>
 * The architect table, builder and blueprint library register theirs with their blocks (#77, #78, #80): 4.2.2 gave
 * the first two {@code BptBlockRotateInventory} and the library {@code BptBlockInventory}.
 */
public final class BuildersSchematics {

    private BuildersSchematics() {
    }

    public static void register() {
        // 4.2.2 BuildCraftBuilders.load
        block(Blocks.SNOW, BptBlockIgnore::new);
        block(Blocks.SHORT_GRASS, BptBlockIgnore::new);
        block(Blocks.FERN, BptBlockIgnore::new);
        block(Blocks.DEAD_BUSH, BptBlockIgnore::new);
        block(Blocks.ICE, BptBlockIgnore::new);
        block(Blocks.PISTON_HEAD, BptBlockIgnore::new);
        block(Blocks.MOVING_PISTON, BptBlockIgnore::new);

        block(Blocks.DIRT, BptBlockDirt::new);
        block(Blocks.GRASS_BLOCK, BptBlockDirt::new);
        block(Blocks.FARMLAND, BptBlockDirt::new);
        block(Blocks.DIRT_PATH, BptBlockDirt::new);

        family(BaseTorchBlock.class, BptBlockWallSide::new);

        block(Blocks.LADDER, BptBlockRotateMeta::new);
        family(FenceGateBlock.class, BptBlockRotateMeta::new);

        family(AbstractFurnaceBlock.class, BptBlockRotateInventory::new);
        family(ChestBlock.class, BptBlockRotateInventory::new);
        family(DispenserBlock.class, BptBlockRotateInventory::new);

        family(BrewingStandBlock.class, BptBlockInventory::new);
        family(HopperBlock.class, BptBlockInventory::new);
        family(BarrelBlock.class, BptBlockInventory::new);

        block(Blocks.VINE, BptBlockRotateMeta::new);
        family(TrapDoorBlock.class, BptBlockRotateMeta::new);

        family(ButtonBlock.class, BptBlockLever::new);
        family(LeverBlock.class, BptBlockLever::new);

        customStack(Blocks.STONE, Blocks.STONE);
        customStack(Blocks.REDSTONE_WIRE, Items.REDSTONE);
        customStack(Blocks.CAKE, Items.CAKE);
        customStack(Blocks.WHEAT, Items.WHEAT_SEEDS);
        customStack(Blocks.PUMPKIN_STEM, Items.PUMPKIN_SEEDS);
        customStack(Blocks.ATTACHED_PUMPKIN_STEM, Items.PUMPKIN_SEEDS);
        customStack(Blocks.MELON_STEM, Items.MELON_SEEDS);
        customStack(Blocks.ATTACHED_MELON_STEM, Items.MELON_SEEDS);
        customStack(Blocks.GLOWSTONE, Blocks.GLOWSTONE);

        family(RepeaterBlock.class, BptBlockRedstoneRepeater::new);

        family(LiquidBlock.class, block -> new BptBlockFluid(block,
                new ItemStack(block.defaultBlockState().getFluidState().getType().getBucket())));

        block(Blocks.RAIL, BptBlockIgnoreMeta::new);
        block(Blocks.POWERED_RAIL, BptBlockIgnoreMeta::new);
        block(Blocks.DETECTOR_RAIL, BptBlockIgnoreMeta::new);
        block(Blocks.GLASS_PANE, BptBlockIgnoreMeta::new);

        family(PistonBaseBlock.class, BptBlockPiston::new);

        family(CarvedPumpkinBlock.class, BptBlockPumpkin::new);

        family(StairBlock.class, BptBlockStairs::new);

        family(DoorBlock.class, block -> new BptBlockDoor(block, new ItemStack(block)));

        family(AbstractBedBlock.class, BptBlockBed::new);

        family(SignBlock.class,
                block -> new BptBlockSign(block, block instanceof WallSignBlock || block instanceof WallHangingSignBlock));

        // 4.2.2 BuildCraftBuilders.load, BuildCraft's own blocks
        BlueprintManager.registerSchematic(BuildersRegistry.markerBlock, BptBlockWallSide::new);
        BlueprintManager.registerSchematic(BuildersRegistry.pathMarkerBlock, BptBlockWallSide::new);
        BlueprintManager.registerSchematic(BuildersRegistry.fillerBlock, BptBlockMachine::new);

        // 4.2.2 BuildCraftEnergy.load
        family(BlockEngine.class, BptBlockEngine::new);

        // 4.2.2 BuildCraftFactory.load
        BlueprintManager.registerSchematic(FactoryRegistry.frameBlock, BptBlockFrame::new);
        BlueprintManager.registerSchematic(FactoryRegistry.refineryBlock, BptBlockRefinery::new);
        BlueprintManager.registerSchematic(FactoryRegistry.tankBlock, BptBlockTank::new);
        BlueprintManager.registerSchematic(FactoryRegistry.quarryBlock, BptBlockMachine::new);
        BlueprintManager.registerSchematic(FactoryRegistry.miningWellBlock, BptBlockMachine::new);
        BlueprintManager.registerSchematic(FactoryRegistry.pumpBlock, BptBlockMachine::new);
        BlueprintManager.registerSchematic(FactoryRegistry.floodGateBlock, BptBlockMachine::new);

        // 4.2.2 BuildCraftSilicon.load
        BlueprintManager.registerSchematic(SiliconRegistry.laserBlock, BptBlockRotateMeta::new);
        BlueprintManager.registerSchematic(SiliconRegistry.assemblyTableBlock, BptBlockInventory::new);

        // 4.2.2 BuildCraftTransport.load: one BptBlockPipe for the generic pipe block, now one block per kind and tier
        family(BlockPipe.class, BptBlockPipe::new);
    }

    private static void block(Block block, Function<Block, ? extends BptBlock> factory) {
        BlueprintManager.registerFamily(candidate -> candidate == block, factory);
    }

    private static void family(Class<? extends Block> blockClass, Function<Block, ? extends BptBlock> factory) {
        BlueprintManager.registerFamily(blockClass::isInstance, factory);
    }

    private static void customStack(Block block, ItemLike item) {
        block(block, candidate -> new BptBlockCustomStack(candidate, new ItemStack(item)));
    }
}
