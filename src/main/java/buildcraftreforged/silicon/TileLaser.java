/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: TileEntity becomes TileBuildCraft's BlockEntity; power is FE as long (4.2.2
 * MJ × 10: configure(250, 1500, 250, 10000), perdition 5 FE/t, at most 40 FE/t sent); metadata becomes
 * BlockLaser.FACING; the EntityEnergyLaser is not spawned but kept here as render data and sent in the network payload
 * with the active flag, so the client's RenderLaserTile draws it; NBT becomes ValueInput/ValueOutput; invalidate
 * becomes setRemoved.
 */
package buildcraftreforged.silicon;

import buildcraftreforged.api.core.Position;
import buildcraftreforged.api.core.SafeTimeTracker;
import buildcraftreforged.api.gates.IAction;
import buildcraftreforged.api.gates.IActionReceptor;
import buildcraftreforged.api.power.ILaserTarget;
import buildcraftreforged.api.power.IPowerReceptor;
import buildcraftreforged.api.power.PowerHandler;
import buildcraftreforged.api.power.PowerHandler.PowerReceiver;
import buildcraftreforged.api.power.PowerHandler.Type;
import buildcraftreforged.core.CoreRegistry;
import buildcraftreforged.core.EntityEnergyLaser;
import buildcraftreforged.core.IMachine;
import buildcraftreforged.core.TileBuildCraft;
import buildcraftreforged.core.triggers.ActionMachineControl;
import java.util.LinkedList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class TileLaser extends TileBuildCraft implements IPowerReceptor, IActionReceptor, IMachine {

    private @Nullable EntityEnergyLaser laser = null;
    private final SafeTimeTracker laserTickTracker = new SafeTimeTracker();
    private final SafeTimeTracker searchTracker = new SafeTimeTracker();
    private final SafeTimeTracker networkTracker = new SafeTimeTracker();
    private @Nullable ILaserTarget laserTarget;
    protected PowerHandler powerHandler;
    private int nextNetworkUpdate = 3;
    private int nextLaserUpdate = 10;
    private int nextLaserSearch = 100;
    private ActionMachineControl.Mode lastMode = ActionMachineControl.Mode.Unknown;
    private static final PowerHandler.PerditionCalculator PERDITION = new PowerHandler.PerditionCalculator(5F);
    /** isActive as the client last heard it; 4.2.2 read the server's laserTarget through the light value. */
    private boolean active = false;
    /** What the clients were last sent, so the end of the beam and the light are sent at once. */
    private boolean sentActive = false;
    private boolean sentLaser = false;

    public TileLaser(BlockPos pos, BlockState state) {
        super(SiliconRegistry.laserTile.get(), pos, state);
        powerHandler = new PowerHandler(this, Type.MACHINE);
        initPowerProvider();
    }

    private void initPowerProvider() {
        powerHandler.configure(250, 1500, 250, 10000);
        powerHandler.setPerdition(PERDITION);
    }

    @Override
    public void updateEntity() {
        super.updateEntity();

        if (level == null || level.isClientSide())
            return;

        // If a gate disabled us, remove laser and do nothing.
        if (lastMode == ActionMachineControl.Mode.Off) {
            removeLaser();
            return;
        }

        // Check for available tables if none is linked to this laser.
        if (!isValidTable())
            if (canFindTable()) {
                findTable();
            }

        // If we still don't have a valid table or the existing has
        // become invalid, we disable the laser and do nothing.
        if (!isValidTable()) {
            removeLaser();
            return;
        }

        // Disable the laser and do nothing if no energy is available.
        if (powerHandler.getEnergyStored() == 0) {
            removeLaser();
            return;
        }

        // We have a table and can work, so we create a laser if
        // necessary.
        if (laser == null) {
            createLaser();
        }

        // We have a laser and may update it
        if (laser != null && canUpdateLaser()) {
            updateLaser();
        }

        // Consume power and transfer it to the table.
        long power = powerHandler.useEnergy(0, getMaxPowerSent(), true);
        laserTarget.receiveLaserEnergy(power);

        if (laser != null) {
            laser.pushPower(power);
        }

        onPowerSent(power);

        sendNetworkUpdate();
    }

    /** FE per tick: 4.2.2's 4 MJ. */
    protected long getMaxPowerSent() {
        return 40;
    }

    protected void onPowerSent(long power) {
    }

    protected boolean canFindTable() {
        return searchTracker.markTimeIfDelay(level, nextLaserSearch);
    }

    protected boolean canUpdateLaser() {
        return laserTickTracker.markTimeIfDelay(level, nextLaserUpdate);
    }

    protected boolean isValidTable() {

        if (laserTarget == null || laserTarget.isInvalidTarget() || !laserTarget.requiresLaserEnergy())
            return false;

        return true;
    }

    protected void findTable() {
        int xCoord = worldPosition.getX();
        int yCoord = worldPosition.getY();
        int zCoord = worldPosition.getZ();

        int minX = xCoord - 5;
        int minY = yCoord - 5;
        int minZ = zCoord - 5;
        int maxX = xCoord + 5;
        int maxY = yCoord + 5;
        int maxZ = zCoord + 5;

        switch (getFacing()) {
            case WEST:
                maxX = xCoord;
                break;
            case EAST:
                minX = xCoord;
                break;
            case DOWN:
                maxY = yCoord;
                break;
            case UP:
                minY = yCoord;
                break;
            case NORTH:
                maxZ = zCoord;
                break;
            default:
            case SOUTH:
                minZ = zCoord;
                break;
        }

        List<ILaserTarget> targets = new LinkedList<ILaserTarget>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int x = minX; x <= maxX; ++x) {
            for (int y = minY; y <= maxY; ++y) {
                for (int z = minZ; z <= maxZ; ++z) {

                    pos.set(x, y, z);
                    // 1.6.4's getBlockTileEntity did not load chunks; 26.x's getBlockEntity would.
                    if (!level.isLoaded(pos))
                        continue;

                    BlockEntity tile = level.getBlockEntity(pos);
                    if (tile instanceof ILaserTarget) {

                        ILaserTarget table = (ILaserTarget) tile;
                        if (table.requiresLaserEnergy()) {
                            targets.add(table);
                        }
                    }

                }
            }
        }

        if (targets.isEmpty())
            return;

        laserTarget = targets.get(level.getRandom().nextInt(targets.size()));
    }

    protected void createLaser() {

        Position position = new Position(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
        laser = new EntityEnergyLaser(level, position, new Position(position));
    }

    protected void updateLaser() {

        double px = 0, py = 0, pz = 0;

        switch (getFacing()) {

            case WEST:
                px = -0.3;
                break;
            case EAST:
                px = 0.3;
                break;
            case DOWN:
                py = -0.3;
                break;
            case UP:
                py = 0.3;
                break;
            case NORTH:
                pz = -0.3;
                break;
            case SOUTH:
            default:
                pz = 0.3;
                break;
        }

        int xCoord = worldPosition.getX();
        int yCoord = worldPosition.getY();
        int zCoord = worldPosition.getZ();
        Position head = new Position(xCoord + 0.5 + px, yCoord + 0.5 + py, zCoord + 0.5 + pz);
        Position tail = new Position(laserTarget.getXCoord() + 0.475 + (level.getRandom().nextFloat() - 0.5) / 5F, laserTarget.getYCoord() + 9F / 16F,
                laserTarget.getZCoord() + 0.475 + (level.getRandom().nextFloat() - 0.5) / 5F);

        laser.setPositions(head, tail);

        if (!laser.isVisible()) {
            laser.show();
        }
    }

    protected void removeLaser() {

        if (laser != null) {
            laser.setDead();
            laser = null;
        }

        // 4.2.2 killed the laser entity, which reached the clients by itself.
        if (sentLaser || sentActive != isValidTable()) {
            forceNetworkUpdate();
        }
    }

    /** 4.2.2's block metadata. */
    public Direction getFacing() {
        BlockState state = getBlockState();
        return state.hasProperty(BlockLaser.FACING) ? state.getValue(BlockLaser.FACING) : Direction.SOUTH;
    }

    /** The beam, for the renderer and tests; null while the laser is off. */
    public @Nullable EntityEnergyLaser getLaser() {
        return laser;
    }

    /** The target the laser feeds, for tests; null when none was found. */
    public @Nullable ILaserTarget getLaserTarget() {
        return laserTarget;
    }

    @Override
    public PowerReceiver getPowerReceiver(@Nullable Direction side) {
        return powerHandler.getPowerReceiver();
    }

    @Override
    public void doWork(PowerHandler workProvider) {
    }

    @Override
    public void sendNetworkUpdate() {
        if (networkTracker.markTimeIfDelay(level, nextNetworkUpdate)) {
            forceNetworkUpdate();
        }
    }

    private void forceNetworkUpdate() {
        sentActive = isValidTable();
        sentLaser = laser != null;
        super.sendNetworkUpdate();
    }

    @Override
    public void writePacketPayload(RegistryFriendlyByteBuf data) {
        super.writePacketPayload(data);
        data.writeBoolean(isValidTable());
        data.writeBoolean(laser != null);
        if (laser != null) {
            laser.writeData(data);
        }
    }

    @Override
    public void readPacketPayload(RegistryFriendlyByteBuf data) {
        super.readPacketPayload(data);
        active = data.readBoolean();
        if (data.readBoolean()) {
            if (laser == null) {
                laser = new EntityEnergyLaser(level);
            }
            laser.readData(data);
        } else {
            laser = null;
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);

        powerHandler.readFromNBT(input);
        initPowerProvider();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);

        powerHandler.writeToNBT(output);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (laser != null) {
            laser.setDead();
            laser = null;
        }
    }

    @Override
    public boolean isActive() {
        if (level != null && level.isClientSide())
            return active;
        return isValidTable();
    }

    @Override
    public boolean manageFluids() {
        return false;
    }

    @Override
    public boolean manageSolids() {
        return false;
    }

    @Override
    public boolean allowAction(IAction action) {
        return action == CoreRegistry.actionOn.get() || action == CoreRegistry.actionOff.get();
    }

    @Override
    public void actionActivated(IAction action) {
        if (action == CoreRegistry.actionOn.get()) {
            lastMode = ActionMachineControl.Mode.On;
        } else if (action == CoreRegistry.actionOff.get()) {
            lastMode = ActionMachineControl.Mode.Off;
        }
    }
}
