/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: energy is FE (4.2.2 MJ × 10), so the limits are × 10 and a fuel's
 * powerPerCycle (FE, from the combustion_fuels data map, #43) is divided by 10 before HEAT_PER_MJ heats the engine;
 * IFluidHandler becomes a ResourceHandler<FluidResource> over the two tanks (Capabilities.Fluid.BLOCK; index 0 the fuel
 * tank, which takes only fuels, index 1 the coolant tank, which takes only coolants); FluidContainerRegistry becomes
 * the item's fluid capability; the GUI is a MenuProvider opened with openMenu; the tank progress bars are guiData ids 4
 * to 7 (4.2.2: 15 to 20; the GUI looks the colours up); BiomeGenBase.temperature becomes the biome's base
 * temperature; NBT becomes ValueInput/ValueOutput; the status panel shows OUT_OF_FUEL; an engine shut down by its
 * overheat timer (#40) cools as an unpowered one; getTriggers waits for the engine triggers (#41).
 */
package buildcraftreforged.energy;

import buildcraftreforged.api.fuels.IronEngineCoolant;
import buildcraftreforged.api.fuels.IronEngineCoolant.Coolant;
import buildcraftreforged.api.fuels.IronEngineFuel;
import buildcraftreforged.api.fuels.IronEngineFuel.Fuel;
import buildcraftreforged.core.fluids.FluidUtils;
import buildcraftreforged.core.fluids.Tank;
import buildcraftreforged.core.fluids.TankManager;
import buildcraftreforged.core.status.MachineStatus;
import buildcraftreforged.core.utils.Utils;
import buildcraftreforged.energy.gui.ContainerEngine;
import buildcraftreforged.transport.BlockPipe;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

public class TileEngineIron extends TileEngineWithInventory implements ResourceHandler<FluidResource>, MenuProvider {

    public static int MAX_LIQUID = FluidType.BUCKET_VOLUME * 10;
    /** °C per MJ burnt; powerPerCycle is FE, so burn() divides it by 10 first. */
    public static float HEAT_PER_MJ = 0.0023F;
    public static float COOLDOWN_RATE = 0.05F;
    public static int MAX_COOLANT_PER_TICK = 40;
    int burnTime = 0;
    public Tank tankFuel = new Tank("tankFuel", MAX_LIQUID, this);
    public Tank tankCoolant = new Tank("tankCoolant", MAX_LIQUID, this);
    private TankManager<Tank> tankManager = new TankManager<Tank>();
    private @Nullable Fuel currentFuel = null;
    public int penaltyCooling = 0;
    boolean lastPowered = false;
    private @Nullable Biome biomeCache;
    // Client side: the tanks as the GUI's data slots last described them (4.2.2 kept placeholder FluidStacks).
    private int clientFuelId, clientFuelAmount, clientCoolantId, clientCoolantAmount;

    public TileEngineIron(BlockPos pos, BlockState state) {
        super(EnergyRegistry.engineIron.tile().get(), pos, state, 1);
        tankManager.add(tankFuel);
        tankManager.add(tankCoolant);
    }

    @Override
    public Identifier getTextureFile() {
        return IRON_TEXTURE;
    }

    /**
     * A fluid container fills (or drains) the tanks instead of opening the GUI. BlockEngine reaches this through
     * useWithoutItem, which is the main hand's.
     */
    @Override
    public boolean onBlockActivated(Player player, Direction side) {
        ItemStack current = player.getMainHandItem();
        if (!current.isEmpty()) {
            if (current.getItem() instanceof BlockItem item && item.getBlock() instanceof BlockPipe) {
                return false;
            }
            if (level != null && !level.isClientSide()) {
                if (FluidUtils.handleRightClick(this, player, InteractionHand.MAIN_HAND, true, true)) {
                    return true;
                }
            } else {
                if (ItemAccess.forPlayerInteraction(player, InteractionHand.MAIN_HAND).getCapability(Capabilities.Fluid.ITEM) != null) {
                    return true;
                }
            }
        }
        if (level != null && !level.isClientSide()) {
            player.openMenu(this, worldPosition);
        }
        return true;
    }

    @Override
    public float explosionRange() {
        return 4;
    }

    @Override
    public float getPistonSpeed() {
        if (level != null && !level.isClientSide()) {
            return Math.max(0.07f * getHeatLevel(), 0.01f);
        }
        switch (getEnergyStage()) {
            case BLUE:
                return 0.04F;
            case GREEN:
                return 0.05F;
            case YELLOW:
                return 0.06F;
            case RED:
                return 0.07F;
            default:
                return 0;
        }
    }

    private float getBiomeTempScalar() {
        if (biomeCache == null)
            biomeCache = Objects.requireNonNull(level).getBiome(worldPosition).value();
        float tempScalar = biomeCache.getBaseTemperature() - 1.0F;
        tempScalar *= 0.5F;
        tempScalar += 1.0F;
        return tempScalar;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        biomeCache = null;
    }

    @Override
    public boolean isBurning() {
        FluidStack fuel = tankFuel.getFluid();
        return !fuel.isEmpty() && fuel.getAmount() > 0 && penaltyCooling == 0 && isRedstonePowered;
    }

    @Override
    public void burn() {
        FluidStack fuel = this.tankFuel.getFluid();
        if (currentFuel == null && !fuel.isEmpty()) {
            currentFuel = IronEngineFuel.getFuelForFluid(fuel.getFluid());
        }

        if (currentFuel == null)
            return;

        if (penaltyCooling <= 0 && isRedstonePowered) {

            lastPowered = true;

            if (burnTime > 0 || fuel.getAmount() > 0) {
                if (burnTime > 0) {
                    burnTime--;
                }
                if (burnTime <= 0) {
                    if (!fuel.isEmpty()) {
                        fuel.shrink(1);
                        if (fuel.getAmount() <= 0) {
                            tankFuel.setFluid(FluidStack.EMPTY);
                        }
                        setChanged();
                        burnTime = currentFuel.totalBurningTime / FluidType.BUCKET_VOLUME;
                    } else {
                        currentFuel = null;
                        return;
                    }
                }
                currentOutput = currentFuel.powerPerCycle; // Comment out for constant power
                addEnergy(currentFuel.powerPerCycle);
                heat += currentFuel.powerPerCycle / 10F * HEAT_PER_MJ * getBiomeTempScalar();
            }
        } else if (penaltyCooling <= 0) {
            if (lastPowered) {
                lastPowered = false;
                penaltyCooling = 10;
                // 10 tick of penalty on top of the cooling
            }
        }
    }

    @Override
    public void updateHeatLevel() {
    }

    @Override
    public void engineUpdate() {

        final ItemStack stack = getItem(0);
        if (!stack.isEmpty()) {
            FluidStack liquid = getFluidForFilledItem(stack);
            if (liquid.isEmpty() && heat > MIN_HEAT * 2) {
                liquid = IronEngineCoolant.getFluidCoolant(stack);
            }

            if (liquid != null && !liquid.isEmpty()) {
                if (fill(null, liquid, false) == liquid.getAmount()) {
                    fill(null, liquid, true);
                    setItem(0, Utils.consumeItem(stack));
                }
            }
        }

        // An engine shut down by its overheat timer cools as an unpowered one.
        if (heat > MIN_HEAT && (penaltyCooling > 0 || !isRedstonePowered || energyStage == EnergyStage.OVERHEAT)) {
            heat -= COOLDOWN_RATE;
            coolEngine(MIN_HEAT);
            getEnergyStage();
        } else if (heat > IDEAL_HEAT) {
            coolEngine(IDEAL_HEAT);
        }

        if (heat <= MIN_HEAT && penaltyCooling > 0)
            penaltyCooling--;

        if (heat <= MIN_HEAT)
            heat = MIN_HEAT;
    }

    private void coolEngine(float idealHeat) {
        float extraHeat = heat - idealHeat;

        FluidStack coolant = this.tankCoolant.getFluid();
        if (coolant.isEmpty())
            return;

        int coolantAmount = Math.min(MAX_COOLANT_PER_TICK, coolant.getAmount());
        Coolant currentCoolant = IronEngineCoolant.getCoolant(coolant);
        if (currentCoolant != null) {
            float cooling = currentCoolant.getDegreesCoolingPerMB(heat);
            cooling /= getBiomeTempScalar();
            if (coolantAmount * cooling > extraHeat) {
                tankCoolant.drain(Math.round(extraHeat / cooling), true);
                heat -= extraHeat;
            } else {
                tankCoolant.drain(coolantAmount, true);
                heat -= coolantAmount * cooling;
            }
        }
    }

    /** 1.6.4 FluidContainerRegistry.getFluidForFilledItem: the first fluid the stack's fluid handler holds. */
    private static FluidStack getFluidForFilledItem(ItemStack stack) {
        if (stack.isEmpty())
            return FluidStack.EMPTY;
        ResourceHandler<FluidResource> handler = ItemAccess.forStack(stack.copyWithCount(1)).getCapability(Capabilities.Fluid.ITEM);
        if (handler == null)
            return FluidStack.EMPTY;
        for (int index = 0; index < handler.size(); index++) {
            FluidResource resource = handler.getResource(index);
            int amount = handler.getAmountAsInt(index);
            if (!resource.isEmpty() && amount > 0)
                return resource.toStack(amount);
        }
        return FluidStack.EMPTY;
    }

    @Override
    public int getScaledBurnTime(int i) {
        return !this.tankFuel.getFluid().isEmpty() ? (int) (((float) this.tankFuel.getFluid().getAmount() / (float) (MAX_LIQUID)) * i) : 0;
    }

    @Override
    protected void loadAdditional(ValueInput data) {
        super.loadAdditional(data);
        tankManager.readFromNBT(data);

        burnTime = data.getIntOr("burnTime", 0);
        penaltyCooling = data.getIntOr("penaltyCooling", 0);

    }

    @Override
    protected void saveAdditional(ValueOutput data) {
        super.saveAdditional(data);
        tankManager.writeToNBT(data);

        data.putInt("burnTime", burnTime);
        data.putInt("penaltyCooling", penaltyCooling);

    }

    public int getScaledCoolant(int i) {
        return !tankCoolant.getFluid().isEmpty() ? (int) (((float) tankCoolant.getFluid().getAmount() / (float) (MAX_LIQUID)) * i) : 0;
    }

    /** Client side. A fluid's network id is its registry id + 1, 0 for none; the GUI looks the colour up. */
    @Override
    public void getGUINetworkData(int id, int value) {
        super.getGUINetworkData(id, value);
        switch (id) {
            // Fluid Fuel ID
            case 4:
                clientFuelId = value & 0xffff;
                setClientTank(tankFuel, clientFuelId, clientFuelAmount);
                break;
            // Fluid Coolant ID
            case 5:
                clientCoolantId = value & 0xffff;
                setClientTank(tankCoolant, clientCoolantId, clientCoolantAmount);
                break;
            // Fluid Fuel amount
            case 6:
                clientFuelAmount = value & 0xffff;
                setClientTank(tankFuel, clientFuelId, clientFuelAmount);
                break;
            // Fluid Coolant amount
            case 7:
                clientCoolantAmount = value & 0xffff;
                setClientTank(tankCoolant, clientCoolantId, clientCoolantAmount);
                break;
        }
    }

    private static void setClientTank(Tank tank, int fluidId, int amount) {
        Fluid fluid = fluidId <= 0 ? null : BuiltInRegistries.FLUID.byId(fluidId - 1);
        if (fluid == null || fluid == Fluids.EMPTY || amount <= 0) {
            tank.setFluid(FluidStack.EMPTY);
        } else {
            tank.setFluid(new FluidStack(fluid, amount));
        }
    }

    @Override
    public int sendGUINetworkData(int id) {
        switch (id) {
            case 4:
                return toNetworkId(tankFuel.getFluid());
            case 5:
                return toNetworkId(tankCoolant.getFluid());
            case 6:
                return tankFuel.getFluidAmount();
            case 7:
                return tankCoolant.getFluidAmount();
            default:
                return super.sendGUINetworkData(id);
        }
    }

    private static int toNetworkId(FluidStack stack) {
        return stack.isEmpty() ? 0 : BuiltInRegistries.FLUID.getId(stack.getFluid()) + 1;
    }

    @Override
    protected int getGUINetworkDataCount() {
        return 8;
    }

    @Override
    public boolean isActive() {
        return penaltyCooling <= 0;
    }

    /* ITANKCONTAINER */
    public FluidStack drain(@Nullable Direction from, int maxDrain, boolean doDrain) {
        return tankFuel.drain(maxDrain, doDrain);
    }

    public FluidStack drain(@Nullable Direction from, FluidStack resource, boolean doDrain) {
        if (resource.isEmpty())
            return FluidStack.EMPTY;
        if (tankCoolant.getFluidType() == resource.getFluid())
            return tankCoolant.drain(resource.getAmount(), doDrain);
        if (tankFuel.getFluidType() == resource.getFluid())
            return tankFuel.drain(resource.getAmount(), doDrain);
        return FluidStack.EMPTY;
    }

    public boolean canDrain(@Nullable Direction from, Fluid fluid) {
        return true;
    }

    public int fill(@Nullable Direction from, FluidStack resource, boolean doFill) {

        // Handle coolant
        if (IronEngineCoolant.getCoolant(resource) != null)
            return tankCoolant.fill(resource, doFill);

        if (IronEngineFuel.getFuelForFluid(resource.getFluid()) != null)
            return tankFuel.fill(resource, doFill);

        return 0;
    }

    public boolean canFill(@Nullable Direction from, Fluid fluid) {
        if (IronEngineCoolant.isCoolant(fluid))
            return true;

        if (IronEngineFuel.getFuelForFluid(fluid) != null)
            return true;

        return false;
    }

    // ResourceHandler: index 0 is tankFuel, index 1 tankCoolant. Each takes only what fill sends it; anything may be
    // drained, as 4.2.2's drain(resource).

    @Override
    public int size() {
        return tankManager.size();
    }

    @Override
    public FluidResource getResource(int index) {
        return tankManager.getResource(index);
    }

    @Override
    public long getAmountAsLong(int index) {
        return tankManager.getAmountAsLong(index);
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        return tankManager.getCapacityAsLong(index, resource);
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        Objects.checkIndex(index, size());
        return resource.isEmpty() || tankFor(resource) == tankManager.get(index);
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        Objects.checkIndex(index, size());
        if (tankFor(resource) != tankManager.get(index))
            return 0;
        return tankManager.insert(index, resource, amount, transaction);
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        return tankManager.extract(index, resource, amount, transaction);
    }

    /** fill's choice: the coolant tank for a coolant, else the fuel tank for a fuel, else none. */
    private @Nullable Tank tankFor(FluidResource resource) {
        if (IronEngineCoolant.isCoolant(resource.getFluid()))
            return tankCoolant;
        if (IronEngineFuel.getFuelForFluid(resource.getFluid()) != null)
            return tankFuel;
        return null;
    }

    @Override
    public boolean canPlaceItem(int i, ItemStack itemstack) {
        if (itemstack.isEmpty())
            return false;
        if (IronEngineCoolant.getCoolant(itemstack) != null)
            return true;
        return !getFluidForFilledItem(itemstack).isEmpty();
    }

    public FluidStack getFuel() {
        return tankFuel.getFluid();
    }

    public FluidStack getCoolant() {
        return tankCoolant.getFluid();
    }

    @Override
    public float maxEnergyReceived() {
        return 20000;
    }

    @Override
    public float maxEnergyExtracted() {
        return 5000;
    }

    @Override
    public float getMaxEnergy() {
        return 100000;
    }

    @Override
    public float getCurrentOutput() {
        if (currentFuel == null) {
            return 0;
        }
        return currentFuel.powerPerCycle;
    }

    // SEAM(#41): getTriggers adds the four fluid triggers (empty, contains, space, full) to the engine's heat triggers.

    /** Powered with an empty fuel tank. */
    @Override
    public MachineStatus getStatusState() {
        MachineStatus state = super.getStatusState();
        if (state == MachineStatus.WORKING && tankFuel.isEmpty() && burnTime <= 0)
            return MachineStatus.OUT_OF_FUEL;
        return state;
    }

    /* MENU */
    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ContainerEngine(EnergyRegistry.engineIronMenu.get(), containerId, playerInventory, this);
    }

    /** Ticks left on the current millibucket of fuel, for tests. */
    public int getBurnTime() {
        return burnTime;
    }
}
