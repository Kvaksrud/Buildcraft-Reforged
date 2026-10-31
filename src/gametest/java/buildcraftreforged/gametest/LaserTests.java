package buildcraftreforged.gametest;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.api.power.ILaserTarget;
import buildcraftreforged.core.CoreRegistry;
import buildcraftreforged.core.EntityEnergyLaser;
import buildcraftreforged.core.network.PacketData;
import buildcraftreforged.silicon.BlockLaser;
import buildcraftreforged.silicon.SiliconRegistry;
import buildcraftreforged.silicon.TileLaser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * #71 laser: target search by facing and range, FE intake through the energy capability, delivery to an
 * {@link ILaserTarget} at 40 FE/t, the gate On/Off actions, the beam's render data and its network payload. The target
 * is a test-only block (gametest_laser_target) that wants {@link LaserTarget#NEEDED} FE, as a table wants the energy of
 * its current recipe.
 */
final class LaserTests {
    static final Identifier TARGET_ID = Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "gametest_laser_target");
    static Block targetBlock;
    static BlockEntityType<LaserTarget> targetTile;

    private static final BlockPos LASER = new BlockPos(2, 1, 2);
    private static final double PRECISION = 1e-9;

    private LaserTests() {
    }

    static void register(RegisterEvent event) {
        event.register(Registries.BLOCK, registry -> {
            targetBlock = new TargetBlock(BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, TARGET_ID)));
            registry.register(TARGET_ID, targetBlock);
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, registry -> {
            targetTile = new BlockEntityType<>(LaserTarget::new, targetBlock);
            registry.register(TARGET_ID, targetTile);
        });
    }

    /** A powered laser facing up feeds a target three blocks above it, draws the beam, and stops when the target is full. */
    static void feedsTarget(GameTestHelper helper) {
        BlockPos targetPos = LASER.above(3);
        TileLaser laser = placeLaser(helper, LASER, Direction.UP);
        helper.setBlock(targetPos, targetBlock);
        LaserTarget target = helper.getBlockEntity(targetPos, LaserTarget.class);

        helper.assertValueEqual(insert(helper, LASER, Direction.DOWN, 1500), 1500, "FE accepted by the laser");
        helper.assertTrue(insert(helper, LASER, Direction.NORTH, 1) == 1, "the laser refused FE on its north side");

        helper.runAfterDelay(1, () -> {
            helper.assertTrue(laser.getLaserTarget() == target, "the laser did not find the target");
            helper.assertTrue(laser.isActive(), "a laser with a target is not active");
            helper.assertTrue(target.received > 0 && target.received % 40 == 0,
                    "the target did not get 40 FE per tick: " + target.received);

            EntityEnergyLaser beam = laser.getLaser();
            helper.assertTrue(beam != null && beam.isVisible(), "no visible beam");
            BlockPos abs = helper.absolutePos(LASER);
            BlockPos absTarget = helper.absolutePos(targetPos);
            // updateLaser: head 0.3 out of the block centre towards the facing, tail 9/16 up the target's block.
            assertClose(helper, beam.getHead().x, abs.getX() + 0.5, "head x");
            assertClose(helper, beam.getHead().y, abs.getY() + 0.8, "head y");
            assertClose(helper, beam.getHead().z, abs.getZ() + 0.5, "head z");
            assertClose(helper, beam.getTail().y, absTarget.getY() + 9 / 16D, "tail y");
            helper.assertTrue(Math.abs(beam.getTail().x - (absTarget.getX() + 0.475)) <= 0.1, "tail x off the target");
            double dx = beam.getTail().x - beam.getHead().x;
            double dy = beam.getTail().y - beam.getHead().y;
            double dz = beam.getTail().z - beam.getHead().z;
            helper.assertTrue(Math.abs(beam.renderSize - Math.sqrt(dx * dx + dy * dy + dz * dz)) < 1e-9,
                    "the beam length is not head to tail: " + beam.renderSize);
            // RenderLaser turns its +x box about z by angleY: about +90 points it up at the target.
            helper.assertTrue(Math.abs(beam.angleY - 90) < 5, "an upward beam is not turned up: " + beam.angleY);

            // The payload the client gets rebuilds the same beam.
            TileLaser client = new TileLaser(abs, laser.getBlockState());
            byte[] bytes = PacketData.write(helper.getLevel().registryAccess(), laser::writePacketPayload);
            RegistryFriendlyByteBuf data = PacketData.wrap(bytes, helper.getLevel().registryAccess());
            try {
                client.readPacketPayload(data);
            } finally {
                data.release();
            }
            EntityEnergyLaser clientBeam = client.getLaser();
            helper.assertTrue(clientBeam != null && clientBeam.isVisible(), "the payload carried no visible beam");
            assertClose(helper, clientBeam.getHead().y, beam.getHead().y, "client head y");
            assertClose(helper, clientBeam.getTail().x, beam.getTail().x, "client tail x");
            helper.assertTrue(Math.abs(clientBeam.renderSize - beam.renderSize) < 0.001, "client beam length");
            helper.assertTrue(clientBeam.getTexture().equals(beam.getTexture()), "client beam texture");

            // A gate's Off action turns the beam off and stops delivery; On resumes it.
            laser.actionActivated(CoreRegistry.actionOff.get());
            helper.runAfterDelay(1, () -> {
                helper.assertTrue(laser.getLaser() == null, "the beam stayed on after the Off action");
                long frozen = target.received;
                helper.runAfterDelay(2, () -> {
                    helper.assertValueEqual(target.received, frozen, "energy delivered while switched off");
                    laser.actionActivated(CoreRegistry.actionOn.get());
                    helper.succeedWhen(() -> {
                        helper.assertTrue(target.received >= LaserTarget.NEEDED, "target not fed: " + target.received);
                        helper.assertTrue(target.received < LaserTarget.NEEDED + 40, "target overfed: " + target.received);
                        helper.assertTrue(laser.getLaser() == null, "the beam stayed on for a full target");
                        helper.assertFalse(laser.isActive(), "a laser with a full target is still active");
                    });
                });
            });
        });
    }

    /**
     * findTable looks 5 blocks each way, only on the facing's side of the laser (its own layer included), every 100
     * ticks; an unpowered laser finds a target but draws no beam and sends nothing.
     */
    static void targetSearch(GameTestHelper helper) {
        TileLaser laser = placeLaser(helper, LASER, Direction.UP);
        insert(helper, LASER, Direction.DOWN, 1500);
        BlockPos behind = LASER.below().east();
        BlockPos tooFar = LASER.above(6);
        helper.setBlock(behind, targetBlock);
        helper.setBlock(tooFar, targetBlock);

        BlockPos unpoweredPos = LASER.east(12);
        TileLaser unpowered = placeLaser(helper, unpoweredPos, Direction.EAST);
        helper.setBlock(unpoweredPos.east(5), targetBlock);
        LaserTarget unpoweredTarget = helper.getBlockEntity(unpoweredPos.east(5), LaserTarget.class);

        helper.runAfterDelay(2, () -> {
            helper.assertTrue(laser.getLaserTarget() == null, "the laser found a target behind it or out of range");
            helper.assertTrue(laser.getLaser() == null, "a beam without a target");
            helper.assertValueEqual(helper.getBlockEntity(behind, LaserTarget.class).received, 0L, "fed behind");
            helper.assertValueEqual(helper.getBlockEntity(tooFar, LaserTarget.class).received, 0L, "fed out of range");

            helper.assertTrue(unpowered.getLaserTarget() == unpoweredTarget, "the unpowered laser found no target");
            helper.assertTrue(unpowered.getLaser() == null, "an unpowered laser draws a beam");
            helper.assertValueEqual(unpoweredTarget.received, 0L, "an unpowered laser sent energy");

            // The stored FE is saved under 4.2.2's tag.
            CompoundTag tag = laser.saveWithFullMetadata(helper.getLevel().registryAccess());
            helper.assertTrue(tag.getCompound("powerProvider").isPresent(), "no powerProvider tag: " + tag);

            // A target at the edge of the range, in the laser's own layer, is found on the next search.
            BlockPos edge = LASER.east(5);
            helper.setBlock(edge, targetBlock);
            LaserTarget edgeTarget = helper.getBlockEntity(edge, LaserTarget.class);
            helper.succeedWhen(() -> {
                helper.assertTrue(laser.getLaserTarget() == edgeTarget, "the laser did not find the target 5 blocks away");
                helper.assertTrue(edgeTarget.received > 0, "the target 5 blocks away got no energy");
            });
        });
    }

    private static TileLaser placeLaser(GameTestHelper helper, BlockPos pos, Direction facing) {
        helper.setBlock(pos, SiliconRegistry.laserBlock.get().defaultBlockState().setValue(BlockLaser.FACING, facing));
        return helper.getBlockEntity(pos, TileLaser.class);
    }

    private static int insert(GameTestHelper helper, BlockPos pos, Direction side, int amount) {
        EnergyHandler handler = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(pos), side);
        helper.assertTrue(handler != null, "the laser has no energy handler on " + side);
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = handler.insert(amount, tx);
            tx.commit();
            return inserted;
        }
    }

    private static void assertClose(GameTestHelper helper, double actual, double expected, String what) {
        helper.assertTrue(Math.abs(actual - expected) <= PRECISION, what + ": " + actual + ", expected " + expected);
    }

    /** The simplest ILaserTarget: wants NEEDED FE, then no more. */
    static final class LaserTarget extends BlockEntity implements ILaserTarget {
        static final long NEEDED = 400;
        long received = 0;

        LaserTarget(BlockPos pos, BlockState state) {
            super(targetTile, pos, state);
        }

        @Override
        public boolean requiresLaserEnergy() {
            return received < NEEDED;
        }

        @Override
        public void receiveLaserEnergy(long energy) {
            received += energy;
        }

        @Override
        public boolean isInvalidTarget() {
            return isRemoved();
        }

        @Override
        public int getXCoord() {
            return worldPosition.getX();
        }

        @Override
        public int getYCoord() {
            return worldPosition.getY();
        }

        @Override
        public int getZCoord() {
            return worldPosition.getZ();
        }
    }

    private static final class TargetBlock extends Block implements EntityBlock {
        TargetBlock(Properties properties) {
            super(properties);
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new LaserTarget(pos, state);
        }
    }
}
