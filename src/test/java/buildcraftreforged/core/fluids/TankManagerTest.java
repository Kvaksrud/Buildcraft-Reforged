package buildcraftreforged.core.fluids;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** #24: TankManager keeps 4.2.2's fill/drain order, saves with the 4.2.2 tag names and syncs its tanks. */
class TankManagerTest {

    /**
     * A FluidStack reads its fluid's default data components, which a running game binds when it loads its data
     * packs. The unit-test game loads none, so the fluids get empty ones; vanilla fluids have none anyway.
     */
    @BeforeAll
    static void bindFluidComponents() {
        BuiltInRegistries.FLUID.listElements()
                .filter(holder -> !holder.areComponentsBound())
                .forEach(holder -> holder.bindComponents(DataComponentMap.EMPTY));
    }

    private static FluidStack water(int amount) {
        return new FluidStack(Fluids.WATER, amount);
    }

    private static FluidStack lava(int amount) {
        return new FluidStack(Fluids.LAVA, amount);
    }

    @Test
    void fillGoesToTheFirstTankThatTakesAny() {
        Tank a = new Tank("a", 1000, null);
        Tank b = new Tank("b", 1000, null);
        TankManager<Tank> manager = new TankManager<>(a, b);

        assertEquals(1000, manager.fill(null, water(1500), true));
        assertEquals(1000, a.getFluidAmount());
        assertTrue(b.isEmpty());

        // a is full, so the next fill goes to b; lava mixes with the water in neither.
        assertEquals(500, manager.fill(null, water(500), true));
        assertEquals(500, b.getFluidAmount());
        assertEquals(0, manager.fill(null, lava(800), true));
        assertEquals(500, b.getFluidAmount());
    }

    @Test
    void drainByFluidAndByAmount() {
        Tank a = new Tank("a", 1000, null);
        Tank b = new Tank("b", 1000, null);
        TankManager<Tank> manager = new TankManager<>(a, b);
        a.fill(water(300), true);
        b.fill(lava(700), true);

        FluidStack drained = manager.drain(null, lava(200), true);
        assertTrue(FluidStack.matches(lava(200), drained));
        assertEquals(500, b.getFluidAmount());
        assertTrue(manager.drain(null, FluidStack.EMPTY, true).isEmpty());

        // By amount: the first tank with anything in it.
        drained = manager.drain(null, 1000, true);
        assertTrue(FluidStack.matches(water(300), drained));
        assertTrue(a.isEmpty());
        assertNull(a.getFluidType());
        assertTrue(FluidStack.matches(lava(500), manager.drain(null, 1000, false)));
    }

    @Test
    void resourceHandlerIndexesTheTanks() {
        Tank a = new Tank("a", 1000, null);
        Tank b = new Tank("b", 4000, null);
        TankManager<Tank> manager = new TankManager<>(a, b);
        b.fill(water(250), true);

        assertEquals(2, manager.size());
        assertTrue(manager.getResource(0).isEmpty());
        assertEquals(FluidResource.of(Fluids.WATER), manager.getResource(1));
        assertEquals(250, manager.getAmountAsLong(1));
        assertEquals(4000, manager.getCapacityAsLong(1, FluidResource.of(Fluids.WATER)));
    }

    @Test
    void transactionsCommitAndRollBack() {
        Tank a = new Tank("a", 1000, null);
        Tank b = new Tank("b", 1000, null);
        TankManager<Tank> manager = new TankManager<>(a, b);
        FluidResource water = FluidResource.of(Fluids.WATER);

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(1000, manager.insert(water, 1200, tx));
            assertEquals(1000, a.getFluidAmount());
        }
        assertTrue(a.isEmpty(), "aborted insert left fluid behind");

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(700, manager.insert(water, 700, tx));
            tx.commit();
        }
        assertEquals(700, a.getFluidAmount());

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(0, manager.extract(FluidResource.of(Fluids.LAVA), 100, tx));
            assertEquals(300, manager.extract(water, 300, tx));
            try (Transaction nested = Transaction.open(tx)) {
                assertEquals(400, manager.extract(water, 1000, nested));
            }
            tx.commit();
        }
        assertEquals(400, a.getFluidAmount());
    }

    @Test
    void singleUseTankRemembersItsFluidAndRollsBack() {
        SingleUseTank tank = new SingleUseTank("tank", 1000, null);
        TankManager<SingleUseTank> manager = new TankManager<>(tank);

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(100, manager.insert(FluidResource.of(Fluids.LAVA), 100, tx));
            assertSame(Fluids.LAVA, tank.getAcceptedFluid());
        }
        assertNull(tank.getAcceptedFluid(), "aborted insert kept the accepted fluid");

        assertEquals(100, manager.fill(null, water(100), true));
        manager.drain(null, 100, true);
        assertTrue(tank.isEmpty());
        assertEquals(0, manager.fill(null, lava(100), true), "an emptied single-use tank took another fluid");
        assertFalse(tank.isValid(0, FluidResource.of(Fluids.LAVA)));
        tank.reset();
        assertEquals(100, manager.fill(null, lava(100), true));
    }

    @Test
    void restrictedTankTakesOnlyItsFluids() {
        RestrictedTank tank = new RestrictedTank("tank", 1000, null, Fluids.WATER);
        assertEquals(0, tank.fill(lava(100), true));
        assertEquals(100, tank.fill(water(100), true));
        assertFalse(tank.isValid(0, FluidResource.of(Fluids.LAVA)));
    }

    @Test
    void savesAndLoadsWithTheTagNames() {
        SingleUseTank a = new SingleUseTank("tank1", 1000, null);
        Tank b = new Tank("tank2", 1000, null);
        a.fill(water(640), true);
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, RegistryAccess.EMPTY);
        new TankManager<Tank>(a, b).writeToNBT(output);
        CompoundTag tag = output.buildResult();

        CompoundTag tank1 = tag.getCompoundOrEmpty("tank1");
        assertEquals("minecraft:water", tank1.getStringOr("FluidName", ""));
        assertEquals(640, tank1.getIntOr("Amount", 0));
        assertEquals("minecraft:water", tank1.getStringOr("acceptedFluid", ""));
        assertTrue(tag.getCompoundOrEmpty("tank2").contains("Empty"));

        SingleUseTank loadedA = new SingleUseTank("tank1", 1000, null);
        Tank loadedB = new Tank("tank2", 1000, null);
        new TankManager<Tank>(loadedA, loadedB).readFromNBT(TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, tag));
        assertTrue(FluidStack.matches(water(640), loadedA.getFluid()));
        assertSame(Fluids.WATER, loadedA.getAcceptedFluid());
        assertTrue(loadedB.isEmpty());
    }

    @Test
    void loadsForgeNamesWithoutNamespace() {
        CompoundTag tankTag = new CompoundTag();
        tankTag.putString("FluidName", "lava");
        tankTag.putInt("Amount", 1000);
        CompoundTag tag = new CompoundTag();
        tag.put("tank", tankTag);

        Tank tank = new Tank("tank", 16000, null);
        tank.readFromNBT(TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, tag));
        assertTrue(FluidStack.matches(lava(1000), tank.getFluid()));
    }

    @Test
    void syncsThroughWriteAndReadData() {
        Tank a = new Tank("a", 1000, null);
        Tank b = new Tank("b", 1000, null);
        a.fill(lava(123), true);
        RegistryFriendlyByteBuf data = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        new TankManager<Tank>(a, b).writeData(data);

        Tank clientA = new Tank("a", 1000, null);
        Tank clientB = new Tank("b", 1000, null);
        clientB.fill(water(50), true);
        new TankManager<Tank>(clientA, clientB).readData(data);
        assertTrue(FluidStack.matches(lava(123), clientA.getFluid()));
        assertTrue(clientB.isEmpty());
        assertEquals(0xFFFFFF, clientB.colorRenderCache);
        assertEquals(0, data.readableBytes());
    }
}
