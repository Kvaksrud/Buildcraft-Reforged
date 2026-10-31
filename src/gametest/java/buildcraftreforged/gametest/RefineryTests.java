package buildcraftreforged.gametest;

import buildcraftreforged.api.recipes.RefineryRecipes.Recipe;
import buildcraftreforged.core.CoreRegistry;
import buildcraftreforged.core.network.InvalidPacketException;
import buildcraftreforged.core.network.PacketData;
import buildcraftreforged.core.status.MachineStatus;
import buildcraftreforged.core.status.StatusReason;
import buildcraftreforged.energy.EnergyRegistry;
import buildcraftreforged.factory.BlockRefinery;
import buildcraftreforged.factory.FactoryRegistry;
import buildcraftreforged.factory.RefineryRecipeManager;
import buildcraftreforged.factory.TileRefinery;
import buildcraftreforged.factory.gui.ContainerRefinery;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * #68: the Refinery (4.2.2 BlockRefinery/TileRefinery). Oil becomes fuel at 4.2.2's rate (1 mB per tick for 12 MJ =
 * 120 FE), only with power; fluids go in through tank1 then tank2 and come out of the result tank; filters, the
 * wrench reset, a full result tank (blocked) and saving; the JSON recipes, including a two-ingredient one from the
 * GameTest data pack.
 */
final class RefineryTests {
    private static final BlockPos POS = new BlockPos(2, 1, 2);
    /** 4.2.2 RefineryRecipes oil → fuel: 12 MJ per mB. */
    private static final int ENERGY_PER_MB = 120;
    /** 4.2.2 configurePowerPerdition(1, 1): 1 MJ per tick. */
    private static final int PERDITION_PER_TICK = 10;
    private static final int MAX_RECEIVED = 1500;

    private RefineryTests() {
    }

    private static TileRefinery placeRefinery(GameTestHelper helper) {
        helper.setBlock(POS, FactoryRegistry.refineryBlock.get());
        return helper.getBlockEntity(POS, TileRefinery.class);
    }

    private static ResourceHandler<FluidResource> fluids(GameTestHelper helper) {
        ResourceHandler<FluidResource> handler = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK,
                helper.absolutePos(POS), Direction.NORTH);
        helper.assertTrue(handler != null, "the refinery has no fluid capability");
        return handler;
    }

    private static int insertFluid(GameTestHelper helper, Fluid fluid, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = fluids(helper).insert(FluidResource.of(fluid), amount, tx);
            tx.commit();
            return inserted;
        }
    }

    private static int extractFluid(GameTestHelper helper, Fluid fluid, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            int extracted = fluids(helper).extract(FluidResource.of(fluid), amount, tx);
            tx.commit();
            return extracted;
        }
    }

    private static int insertEnergy(GameTestHelper helper, int amount) {
        EnergyHandler handler = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(POS),
                Direction.DOWN);
        helper.assertTrue(handler != null, "the refinery has no energy capability");
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = handler.insert(amount, tx);
            tx.commit();
            return inserted;
        }
    }

    private static long energy(TileRefinery tile) {
        return tile.getPowerReceiver(null).getEnergyStored();
    }

    private static Fluid oil() {
        return EnergyRegistry.fluidOil.get();
    }

    private static Fluid fuel() {
        return EnergyRegistry.fluidFuel.get();
    }

    /** Oil in, power on: 1 mB of fuel per tick for 120 FE each, nothing without power. */
    static void oilToFuel(GameTestHelper helper) {
        TileRefinery tile = placeRefinery(helper);

        helper.assertValueEqual(insertFluid(helper, oil(), 1000), 1000, "oil accepted");
        helper.assertValueEqual(tile.tank1.getFluidAmount(), 1000, "oil in tank1");
        helper.assertValueEqual(tile.tank2.getFluidAmount(), 0, "tank2 after oil");
        // The result tank takes nothing from outside, and only the result comes out.
        helper.assertValueEqual(insertFluid(helper, fuel(), 1000), 1000, "fuel accepted (into tank2)");
        helper.assertValueEqual(tile.tank2.getFluidAmount(), 1000, "fuel in tank2");
        helper.assertValueEqual(tile.result.getFluidAmount(), 0, "the result tank was filled from outside");
        helper.assertValueEqual(extractFluid(helper, oil(), 1000), 0, "oil drained from an input tank");
        helper.assertValueEqual(extractFluid(helper, fuel(), 1000), 0, "fuel drained from an input tank");
        tile.tank2.drain(1000, true);

        helper.runAfterDelay(5, () -> {
            helper.assertValueEqual(tile.result.getFluidAmount(), 0, "fuel made without power");
            helper.assertTrue(tile.isActive(), "a refinery with oil and room is not active");
            helper.assertValueEqual(tile.getStatusState(), MachineStatus.NO_POWER, "status without power");
        });

        // Power for 40 ticks: 1500 FE per tick (the receive cap) fills the 10000 FE store.
        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (tick >= 6 && tick < 46) {
                insertEnergy(helper, MAX_RECEIVED);
            }
        });

        long[] mark = new long[4];
        helper.runAfterDelay(20, () -> mark[0] = tile.result.getFluidAmount());
        helper.runAfterDelay(40, () -> {
            helper.assertValueEqual(tile.result.getFluidAmount() - mark[0], 20L, "fuel made in 20 powered ticks");
            helper.assertValueEqual(tile.getStatusState(), MachineStatus.WORKING, "status while refining");
            helper.assertValueEqual(tile.result.getFluid().getFluid(), fuel(), "the result is fuel");
            helper.assertValueEqual(tile.tank1.getFluidAmount() + tile.result.getFluidAmount(), 1000,
                    "oil used and fuel made differ");
        });

        // Unpowered from tick 46: each mB costs 120 FE, and 10 FE leak away every tick.
        helper.runAfterDelay(50, () -> {
            mark[1] = tile.result.getFluidAmount();
            mark[2] = energy(tile);
        });
        helper.runAfterDelay(60, () -> {
            long made = tile.result.getFluidAmount() - mark[1];
            helper.assertValueEqual(made, 10L, "fuel made in 10 ticks on stored energy");
            helper.assertValueEqual(mark[2] - energy(tile), made * ENERGY_PER_MB + 10L * PERDITION_PER_TICK,
                    "energy used for 10 mB");

            // The result comes out through the capability.
            int fuelMade = tile.result.getFluidAmount();
            helper.assertValueEqual(extractFluid(helper, fuel(), 10000), fuelMade, "fuel drained");
            helper.assertValueEqual(tile.result.getFluidAmount(), 0, "result after draining");
            helper.succeed();
        });
    }

    /** Filters, the GUI's filter return and data slots, the wrench reset, a blocked result tank and saving. */
    static void filtersAndBlocked(GameTestHelper helper) {
        TileRefinery tile = placeRefinery(helper);
        RegistryAccess registryAccess = helper.getLevel().registryAccess();

        // tank1 filtered to water: oil goes to tank2, and the oil recipe still runs from there.
        tile.setFilter(0, Fluids.WATER);
        helper.assertValueEqual(insertFluid(helper, oil(), 4000), 4000, "oil accepted");
        helper.assertValueEqual(tile.tank1.getFluidAmount(), 0, "oil in the water-filtered tank1");
        helper.assertValueEqual(tile.tank2.getFluidAmount(), 4000, "oil in tank2");
        helper.assertValueEqual(insertFluid(helper, oil(), 1000), 0, "oil past full tanks");

        // The GUI sends both filters; ids out of range are refused.
        try {
            tile.readGuiData(PacketData.wrap(PacketData.write(registryAccess, buf -> {
                buf.writeVarInt(TileRefinery.toNetworkId(Fluids.LAVA));
                buf.writeVarInt(0);
            }), registryAccess), null);
        } catch (InvalidPacketException e) {
            throw helper.assertionException("valid filters refused: " + e.getMessage());
        }
        helper.assertValueEqual(tile.getFilter(0), Fluids.LAVA, "filter 0 from the GUI");
        helper.assertTrue(tile.getFilter(1) == null, "filter 1 from the GUI was not cleared");
        boolean refused = false;
        try {
            tile.readGuiData(PacketData.wrap(PacketData.write(registryAccess, buf -> {
                buf.writeVarInt(Integer.MAX_VALUE);
                buf.writeVarInt(0);
            }), registryAccess), null);
        } catch (InvalidPacketException e) {
            refused = true;
        }
        helper.assertTrue(refused, "an unknown fluid id was accepted");
        helper.assertValueEqual(tile.getFilter(0), Fluids.LAVA, "filter 0 after a refused return");

        // The menu's data slots carry the filters to a client copy of the tile (4.2.2's progress bars).
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(tile.createMenu(1, player.getInventory(), player) instanceof ContainerRefinery,
                "the refinery made no refinery menu");
        TileRefinery clientTile = new TileRefinery(tile.getBlockPos(), tile.getBlockState());
        ContainerRefinery client = new ContainerRefinery(1, player.getInventory(), clientTile);
        tile.setFilter(1, oil());
        for (int i = 0; i < 2; i++) {
            // What the server menu's data slot i reads, as broadcastChanges sends it.
            client.setData(i, tile.sendGUINetworkData(i));
        }
        helper.assertValueEqual(clientTile.getFilter(0), Fluids.LAVA, "client filter 0");
        helper.assertValueEqual(clientTile.getFilter(1), oil(), "client filter 1");

        // A sneaking wrench click resets every filter (4.2.2 onBlockActivated).
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CoreRegistry.wrenchItem.get()));
        player.setShiftKeyDown(true);
        BlockPos abs = helper.absolutePos(POS);
        InteractionResult result = helper.getBlockState(POS).useItemOn(player.getMainHandItem(), helper.getLevel(),
                player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
        helper.assertTrue(result.consumesAction(), "the wrench click was not used: " + result);
        helper.assertTrue(tile.getFilter(0) == null && tile.getFilter(1) == null && tile.getFilter(2) == null,
                "filters after the wrench");

        // A full result tank blocks the refinery; draining it lets the refinery go on.
        tile.result.fill(new FluidStack(fuel(), TileRefinery.LIQUID_PER_SLOT), true);
        helper.onEachTick(() -> insertEnergy(helper, MAX_RECEIVED));
        helper.runAfterDelay(5, () -> {
            helper.assertValueEqual(tile.tank2.getFluidAmount(), 4000, "oil used with a full result tank");
            helper.assertTrue(tile.isBlocked(), "a full result tank does not block");
            helper.assertFalse(tile.isActive(), "a blocked refinery is active");
            helper.assertValueEqual(tile.getStatusState(), MachineStatus.BLOCKED, "status when blocked");
            helper.assertValueEqual(tile.getStatusReason(), StatusReason.OUTPUT_FULL, "reason when blocked");
            helper.assertValueEqual(extractFluid(helper, fuel(), 100), 100, "fuel drained");
        });
        helper.runAfterDelay(15, () -> {
            helper.assertFalse(tile.isBlocked(), "still blocked after draining");
            helper.assertTrue(tile.tank2.getFluidAmount() < 4000, "no oil used after draining");

            // Tanks, filters and energy survive a save and load.
            tile.setFilter(0, Fluids.WATER);
            CompoundTag saved = tile.saveWithoutMetadata(helper.getLevel().registryAccess());
            TileRefinery reloaded = new TileRefinery(tile.getBlockPos(), tile.getBlockState());
            reloaded.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registryAccess, saved));
            helper.assertValueEqual(reloaded.tank2.getFluidAmount(), tile.tank2.getFluidAmount(), "tank2 reloaded");
            helper.assertValueEqual(reloaded.result.getFluidAmount(), tile.result.getFluidAmount(), "result reloaded");
            helper.assertValueEqual(reloaded.getFilter(0), Fluids.WATER, "filter reloaded");
            helper.assertValueEqual(energy(reloaded), energy(tile), "energy reloaded");
            helper.succeed();
        });
    }

    /**
     * The JSON recipes: oil → fuel ships with the mod, and the GameTest data pack adds water + lava → oil, which
     * needs both tanks and both inputs. The block faces its placer, as 4.2.2's onBlockPlacedBy turned it.
     */
    static void recipes(GameTestHelper helper) {
        Recipe oil = RefineryRecipeManager.findRefineryRecipe(helper.getLevel(), new FluidStack(oil(), 1), null);
        helper.assertTrue(oil != null, "no recipe for oil");
        helper.assertValueEqual(oil.result.getFluid(), fuel(), "oil recipe result");
        helper.assertValueEqual(oil.result.getAmount(), 1, "oil recipe result amount");
        helper.assertValueEqual(oil.ingredient1.getAmount(), 1, "oil recipe input amount");
        helper.assertValueEqual(oil.energy, ENERGY_PER_MB, "oil recipe energy");
        helper.assertValueEqual(oil.delay, 1, "oil recipe delay");
        helper.assertTrue(RefineryRecipeManager.findRefineryRecipe(helper.getLevel(), new FluidStack(fuel(), 1000), null) == null,
                "fuel refines");

        Recipe mixed = RefineryRecipeManager.findRefineryRecipe(helper.getLevel(), new FluidStack(Fluids.LAVA, 1000),
                new FluidStack(Fluids.WATER, 1000));
        helper.assertTrue(mixed != null && mixed.result.getFluid() == oil(), "no water + lava recipe");
        helper.assertTrue(RefineryRecipeManager.findRefineryRecipe(helper.getLevel(), new FluidStack(Fluids.WATER, 1000),
                null) == null, "water alone matched the two-ingredient recipe");

        TileRefinery tile = placeRefinery(helper);
        helper.assertValueEqual(insertFluid(helper, Fluids.WATER, 1000), 1000, "water accepted");
        helper.assertValueEqual(insertFluid(helper, Fluids.LAVA, 1000), 1000, "lava accepted");
        helper.assertValueEqual(tile.tank1.getFluid().getFluid(), Fluids.WATER, "water in tank1");
        helper.assertValueEqual(tile.tank2.getFluid().getFluid(), Fluids.LAVA, "lava in tank2");
        helper.onEachTick(() -> insertEnergy(helper, MAX_RECEIVED));

        // Placed by a player standing west of the block: it faces west, towards the player.
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos placed = POS.east(2);
        BlockPos abs = helper.absolutePos(placed);
        player.snapTo(Vec3.atBottomCenterOf(abs.west(2)));
        helper.setBlock(placed, FactoryRegistry.refineryBlock.get());
        FactoryRegistry.refineryBlock.get().setPlacedBy(helper.getLevel(), abs, helper.getBlockState(placed), player,
                new ItemStack(FactoryRegistry.refineryBlockItem.get()));
        helper.assertValueEqual(helper.getBlockState(placed).getValue(BlockRefinery.FACING), Direction.WEST,
                "facing after placement");

        helper.runAfterDelay(20, () -> {
            helper.assertValueEqual(tile.result.getFluid().getFluid(), oil(), "water + lava made no oil");
            int cycles = tile.result.getFluidAmount() / 2;
            helper.assertTrue(cycles > 0, "no cycles");
            helper.assertValueEqual(tile.tank1.getFluidAmount(), 1000 - cycles * 10, "water used");
            helper.assertValueEqual(tile.tank2.getFluidAmount(), 1000 - cycles * 5, "lava used");
            helper.succeed();
        });
    }
}
