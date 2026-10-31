/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: IFluidHandler becomes a ResourceHandler<FluidResource> over the three tanks
 * (Capabilities.Fluid.BLOCK; fill goes into tank1 then tank2, drain comes from result), with the 4.2.2 fill/drain
 * methods kept; energy is FE (MJ x 10) through PowerEnergyHandler; recipes come from RefineryRecipeManager (JSON
 * first, then RefineryRecipes); the empty IInventory is gone and the GUI is a MenuProvider; the filter progress bars
 * become the menu's data slots and the filter packet a PacketGuiReturn; the stream payload becomes
 * writePacketPayload; NBT becomes ValueInput/ValueOutput; the status panel (plan section 10) is new.
 */
package buildcraftreforged.factory;

import buildcraftreforged.api.core.SafeTimeTracker;
import buildcraftreforged.api.gates.IAction;
import buildcraftreforged.api.power.IPowerReceptor;
import buildcraftreforged.api.power.PowerHandler;
import buildcraftreforged.api.power.PowerHandler.PowerReceiver;
import buildcraftreforged.api.power.PowerHandler.Type;
import buildcraftreforged.api.recipes.RefineryRecipes.Recipe;
import buildcraftreforged.core.BuildCraftConfiguration;
import buildcraftreforged.core.IMachine;
import buildcraftreforged.core.TileBuildCraft;
import buildcraftreforged.core.fluids.SingleUseTank;
import buildcraftreforged.core.fluids.TankManager;
import buildcraftreforged.core.network.IGuiReturnHandler;
import buildcraftreforged.core.network.InvalidPacketException;
import buildcraftreforged.core.network.PacketData;
import buildcraftreforged.core.status.FlowMeter;
import buildcraftreforged.core.status.IStatusProvider;
import buildcraftreforged.core.status.MachineStatus;
import buildcraftreforged.core.status.StatusReason;
import buildcraftreforged.core.status.StatusReport;
import buildcraftreforged.factory.gui.ContainerRefinery;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

public class TileRefinery extends TileBuildCraft implements ResourceHandler<FluidResource>, IPowerReceptor, IMachine,
        MenuProvider, IGuiReturnHandler, IStatusProvider {

    public static int LIQUID_PER_SLOT = FluidType.BUCKET_VOLUME * 4;
    public SingleUseTank tank1 = new SingleUseTank("tank1", LIQUID_PER_SLOT, this);
    public SingleUseTank tank2 = new SingleUseTank("tank2", LIQUID_PER_SLOT, this);
    public SingleUseTank result = new SingleUseTank("result", LIQUID_PER_SLOT, this);
    public TankManager<SingleUseTank> tankManager = new TankManager<SingleUseTank>(tank1, tank2, result);
    public float animationSpeed = 1;
    private int animationStage = 0;
    SafeTimeTracker time = new SafeTimeTracker();
    SafeTimeTracker updateNetworkTime = new SafeTimeTracker();
    private PowerHandler powerHandler;
    private boolean isActive;
    /** Not in 4.2.2: a recipe matched but the result tank had no room (status panel, Machine trigger). */
    private boolean isBlocked;
    /** Not in 4.2.2: the status panel's Flow and Power lines, counted per refining cycle. */
    public final FlowMeter flowMeter = new FlowMeter(() -> level == null ? 0 : level.getGameTime());

    public TileRefinery(BlockPos pos, BlockState state) {
        super(FactoryRegistry.refineryTile.get(), pos, state);
        powerHandler = new PowerHandler(this, Type.MACHINE);
        initPowerProvider();
    }

    /** 4.2.2 configure(50, 150, 25, 1000) and configurePowerPerdition(1, 1), in FE. */
    private void initPowerProvider() {
        powerHandler.configure(500, 1500, 250, 10000);
        powerHandler.configurePowerPerdition(10, 1);
    }

    @Override
    public PowerReceiver getPowerReceiver(@Nullable Direction side) {
        return powerHandler.getPowerReceiver();
    }

    @Override
    public void doWork(PowerHandler workProvider) {
    }

    /**
     * 4.2.2 did not call TileBuildCraft.updateEntity here, so its power handler only applied perdition when energy
     * arrived or was used. It is called now: the status panel and the 4.2.2 init flag need it, and the handler's
     * per-tick update loses the same 1 MJ (10 FE) per tick that the lazy perdition caught up on.
     */
    @Override
    public void updateEntity() {
        super.updateEntity();

        if (level == null)
            return;

        if (level.isClientSide()) {
            simpleAnimationIterate();
            return;
        }

        if (updateNetworkTime.markTimeIfDelay(level, BuildCraftConfiguration.updateFactor.get()))
            sendNetworkUpdate();

        isActive = false;
        isBlocked = false;

        Recipe currentRecipe = RefineryRecipeManager.findRefineryRecipe(level, tank1.getFluid(), tank2.getFluid());

        if (currentRecipe == null) {
            decreaseAnimation();
            return;
        }

        if (result.fill(currentRecipe.result.copy(), false) != currentRecipe.result.getAmount()) {
            isBlocked = true;
            decreaseAnimation();
            return;
        }

        if (!containsInput(currentRecipe.ingredient1) || !containsInput(currentRecipe.ingredient2)) {
            decreaseAnimation();
            return;
        }

        isActive = true;

        if (powerHandler.getEnergyStored() >= currentRecipe.energy) {
            increaseAnimation();
        } else {
            decreaseAnimation();
        }

        if (!time.markTimeIfDelay(level, currentRecipe.delay))
            return;

        long energyUsed = powerHandler.useEnergy(currentRecipe.energy, currentRecipe.energy, true);

        if (energyUsed != 0) {
            flowMeter.energyIn(energyUsed);
            if (consumeInput(currentRecipe.ingredient1) && consumeInput(currentRecipe.ingredient2)) {
                result.fill(currentRecipe.result, true);
                flowMeter.fluidOut(currentRecipe.result.getAmount());
            }
        }
    }

    private boolean containsInput(@Nullable FluidStack ingredient) {
        if (ingredient == null)
            return true;

        return containsFluid(tank1.getFluid(), ingredient) || containsFluid(tank2.getFluid(), ingredient);
    }

    private boolean consumeInput(@Nullable FluidStack liquid) {
        if (liquid == null)
            return true;

        if (containsFluid(tank1.getFluid(), liquid)) {
            tank1.drain(liquid.getAmount(), true);
            flowMeter.fluidIn(liquid.getAmount());
            return true;
        } else if (containsFluid(tank2.getFluid(), liquid)) {
            tank2.drain(liquid.getAmount(), true);
            flowMeter.fluidIn(liquid.getAmount());
            return true;
        }

        return false;
    }

    /** 1.6.4 {@code FluidStack.containsFluid}: same fluid and components, and at least as much. */
    private static boolean containsFluid(FluidStack stack, FluidStack other) {
        return !stack.isEmpty() && FluidStack.isSameFluidSameComponents(stack, other)
                && stack.getAmount() >= other.getAmount();
    }

    @Override
    public boolean isActive() {
        return isActive;
    }

    @Override
    public boolean isBlocked() {
        return isBlocked;
    }

    @Override
    public boolean manageFluids() {
        return true;
    }

    @Override
    public boolean manageSolids() {
        return true;
    }

    @Override
    protected void loadAdditional(ValueInput data) {
        super.loadAdditional(data);

        tankManager.readFromNBT(data);

        animationStage = data.getIntOr("animationStage", 0);
        animationSpeed = data.getFloatOr("animationSpeed", 1);

        powerHandler.readFromNBT(data);
        initPowerProvider();
    }

    @Override
    protected void saveAdditional(ValueOutput data) {
        super.saveAdditional(data);

        tankManager.writeToNBT(data);

        data.putInt("animationStage", animationStage);
        data.putFloat("animationSpeed", animationSpeed);
        powerHandler.writeToNBT(data);
    }

    public int getAnimationStage() {
        return animationStage;
    }

    /**
     * Used to iterate the animation without computing the speed
     */
    public void simpleAnimationIterate() {
        if (animationSpeed > 1) {
            animationStage += animationSpeed;

            if (animationStage > 300) {
                animationStage = 100;
            }
        } else if (animationStage > 0) {
            animationStage--;
        }
    }

    public void increaseAnimation() {
        if (animationSpeed < 2) {
            animationSpeed = 2;
        } else if (animationSpeed <= 5) {
            animationSpeed += 0.1;
        }

        animationStage += animationSpeed;

        if (animationStage > 300) {
            animationStage = 100;
        }
    }

    public void decreaseAnimation() {
        if (animationSpeed >= 1) {
            animationSpeed -= 0.1;

            animationStage += animationSpeed;

            if (animationStage > 300) {
                animationStage = 100;
            }
        } else {
            if (animationStage > 0) {
                animationStage--;
            }
        }
    }

    public void resetFilters() {
        for (SingleUseTank tank : tankManager) {
            tank.setAcceptedFluid(null);
        }
        setChanged();
    }

    public void setFilter(int number, @Nullable Fluid fluid) {
        tankManager.get(number).setAcceptedFluid(fluid);
        setChanged();
    }

    public @Nullable Fluid getFilter(int number) {
        return tankManager.get(number).getAcceptedFluid();
    }

    @Override
    public boolean allowAction(IAction action) {
        return false;
    }

    /* SMP GUI */
    /** 4.2.2's progress bar ids 0 and 1 carried the filters' fluid ids; 0 is no filter now, a fluid is its id + 1. */
    public void getGUINetworkData(int id, int data) {
        switch (id) {
            case 0:
                setFilter(0, fluidFromNetworkId(data));
                break;
            case 1:
                setFilter(1, fluidFromNetworkId(data));
                break;
        }
    }

    public int sendGUINetworkData(int id) {
        return toNetworkId(getFilter(id));
    }

    public static int toNetworkId(@Nullable Fluid fluid) {
        return fluid == null ? 0 : BuiltInRegistries.FLUID.getId(fluid) + 1;
    }

    public static @Nullable Fluid fluidFromNetworkId(int id) {
        if (id <= 0)
            return null;
        Fluid fluid = BuiltInRegistries.FLUID.byId(id - 1);
        return fluid == null || fluid == Fluids.EMPTY ? null : fluid;
    }

    /** The GUI sends both filters back (4.2.2: one REFINERY_FILTER_SET packet per change). */
    @Override
    public void writeGuiData(RegistryFriendlyByteBuf data) {
        data.writeVarInt(toNetworkId(getFilter(0)));
        data.writeVarInt(toNetworkId(getFilter(1)));
    }

    @Override
    public void readGuiData(RegistryFriendlyByteBuf data, @Nullable Player player) throws InvalidPacketException {
        int filter0 = PacketData.readVarInt(data, 0, BuiltInRegistries.FLUID.size());
        int filter1 = PacketData.readVarInt(data, 0, BuiltInRegistries.FLUID.size());
        setFilter(0, fluidFromNetworkId(filter0));
        setFilter(1, fluidFromNetworkId(filter1));
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ContainerRefinery(containerId, playerInventory, this);
    }

    /* ITANKCONTAINER */
    public int fill(@Nullable Direction from, FluidStack resource, boolean doFill) {
        if (resource.isEmpty())
            return 0;

        int used = 0;
        FluidStack resourceUsing = resource.copy();

        used += tank1.fill(resourceUsing, doFill);
        resourceUsing.shrink(used);
        used += tank2.fill(resourceUsing, doFill);

        return used;
    }

    public FluidStack drain(@Nullable Direction from, int maxEmpty, boolean doDrain) {
        return result.drain(maxEmpty, doDrain);
    }

    public FluidStack drain(@Nullable Direction from, FluidStack resource, boolean doDrain) {
        if (resource.isEmpty() || !FluidStack.isSameFluidSameComponents(resource, result.getFluid()))
            return FluidStack.EMPTY;
        return drain(from, resource.getAmount(), doDrain);
    }

    public boolean canFill(@Nullable Direction from, Fluid fluid) {
        return true;
    }

    public boolean canDrain(@Nullable Direction from, Fluid fluid) {
        return true;
    }

    // ResourceHandler: index i is tankManager's tank i. Insert reaches tank1 and tank2 only and extract result only,
    // so the default insert(resource, ...) fills tank1 then tank2, as 4.2.2's fill, and extract drains result.

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
        return tankManager.get(index) != result && tankManager.isValid(index, resource);
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        Objects.checkIndex(index, size());
        if (tankManager.get(index) == result)
            return 0;
        return tankManager.insert(index, resource, amount, transaction);
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        Objects.checkIndex(index, size());
        if (tankManager.get(index) != result)
            return 0;
        return tankManager.extract(index, resource, amount, transaction);
    }

    // Network
    @Override
    public void writePacketPayload(RegistryFriendlyByteBuf data) {
        data.writeFloat(animationSpeed);
        tankManager.writeData(data);
    }

    @Override
    public void readPacketPayload(RegistryFriendlyByteBuf data) {
        animationSpeed = data.readFloat();
        tankManager.readData(data);
    }

    // Status panel (plan section 10), not in 4.2.2.

    @Override
    public MachineStatus getStatusState() {
        if (isBlocked)
            return MachineStatus.BLOCKED;
        if (!isActive)
            return MachineStatus.IDLE;
        Recipe currentRecipe = RefineryRecipeManager.findRefineryRecipe(level, tank1.getFluid(), tank2.getFluid());
        if (currentRecipe != null && powerHandler.getEnergyStored() < currentRecipe.energy)
            return MachineStatus.NO_POWER;
        return MachineStatus.WORKING;
    }

    @Override
    public StatusReason getStatusReason() {
        return isBlocked ? StatusReason.OUTPUT_FULL : StatusReason.NONE;
    }

    @Override
    public FlowMeter getStatusFlow() {
        return flowMeter;
    }

    @Override
    public StatusReport.Power getStatusPower() {
        return StatusReport.Power.drawn(flowMeter, powerHandler.getEnergyStored(), powerHandler.getMaxEnergyStored());
    }
}
