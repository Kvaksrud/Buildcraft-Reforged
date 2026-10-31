/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: energy is FE (4.2.2 MJ × 10), so MAX_OUTPUT and the limits are × 10 (the PID's
 * other constants follow from MAX_OUTPUT or are fractions); the GUI is a MenuProvider opened with openMenu;
 * TileEntityFurnace.getItemBurnTime becomes the item's cooking_fuel component, resolved as the vanilla furnace does;
 * the burn-time progress bars are guiData ids 4 and 5 (4.2.2: 15 and 16); NBT becomes ValueInput/ValueOutput; the
 * status panel shows OUT_OF_FUEL; getTriggers waits for the engine triggers (#41).
 */
package buildcraftreforged.energy;

import buildcraftreforged.core.status.MachineStatus;
import buildcraftreforged.core.utils.Utils;
import buildcraftreforged.energy.gui.ContainerEngine;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import net.minecraft.world.phys.Vec3;

public class TileEngineStone extends TileEngineWithInventory implements MenuProvider {

    /** FE per tick (4.2.2: 1 MJ). */
    final float MAX_OUTPUT = 10f;
    final float MIN_OUTPUT = MAX_OUTPUT / 3;
    final float TARGET_OUTPUT = 0.375f;
    final float kp = 1f;
    final float ki = 0.05f;
    final float eLimit = (MAX_OUTPUT - MIN_OUTPUT) / ki;
    int burnTime = 0;
    int totalBurnTime = 0;
    float esum = 0;

    public TileEngineStone(BlockPos pos, BlockState state) {
        super(EnergyRegistry.engineStone.tile().get(), pos, state, 1);
    }

    @Override
    public boolean onBlockActivated(Player player, Direction side) {
        if (level != null && !level.isClientSide()) {
            player.openMenu(this, worldPosition);
        }
        return true;
    }

    @Override
    public Identifier getTextureFile() {
        return STONE_TEXTURE;
    }

    @Override
    public float explosionRange() {
        return 2;
    }

    @Override
    public boolean isBurning() {
        return burnTime > 0;
    }

    @Override
    public void burn() {
        if (burnTime > 0) {
            burnTime--;

            float output = getCurrentOutput();
            currentOutput = output; // Comment out for constant power
            addEnergy(output);
        }

        if (burnTime == 0 && isRedstonePowered) {
            burnTime = totalBurnTime = getItemBurnTime(getItem(0));

            if (burnTime > 0) {
                setItem(0, Utils.consumeItem(getItem(0)));
            }
        }
    }

    @Override
    public int getScaledBurnTime(int i) {
        return (int) (((float) burnTime / (float) totalBurnTime) * i);
    }

    /**
     * 1.6.4 TileEntityFurnace.getItemBurnTime: the cooking_fuel component, resolved in the context the vanilla furnace
     * uses (BaseContainerBlockEntity.getLootContext), so data-driven burn times match the furnace's.
     */
    private int getItemBurnTime(ItemStack itemstack) {
        if (itemstack.isEmpty() || !(level instanceof ServerLevel serverLevel))
            return 0;

        return ResolvableInt.getFromItem(itemstack, DataComponents.COOKING_FUEL, CookingFuel::burnTime, getLootContext(serverLevel), 0);
    }

    private LootContext getLootContext(ServerLevel serverLevel) {
        return new LootContext.Builder(new LootParams.Builder(serverLevel)
                .withParameter(LootContextParams.BLOCK_STATE, getBlockState())
                .withParameter(LootContextParams.BLOCK_ENTITY, this)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(worldPosition))
                .withParameter(LootContextParams.CONTAINER, this)
                .create(LootContextParamSets.CONTAINER_PROCESS))
                .create(Optional.empty());
    }

    /* SAVING & LOADING */
    @Override
    protected void loadAdditional(ValueInput data) {
        super.loadAdditional(data);
        burnTime = data.getIntOr("burnTime", 0);
        totalBurnTime = data.getIntOr("totalBurnTime", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput data) {
        super.saveAdditional(data);
        data.putInt("burnTime", burnTime);
        data.putInt("totalBurnTime", totalBurnTime);
    }

    @Override
    public void getGUINetworkData(int id, int value) {
        super.getGUINetworkData(id, value);
        switch (id) {
            case 4:
                burnTime = value & 0xffff;
                break;
            case 5:
                totalBurnTime = value & 0xffff;
                break;
        }
    }

    @Override
    public int sendGUINetworkData(int id) {
        switch (id) {
            case 4:
                return burnTime;
            case 5:
                return totalBurnTime;
            default:
                return super.sendGUINetworkData(id);
        }
    }

    @Override
    protected int getGUINetworkDataCount() {
        return 6;
    }

    @Override
    public float maxEnergyReceived() {
        return 2000;
    }

    @Override
    public float maxEnergyExtracted() {
        return 1000;
    }

    @Override
    public float getMaxEnergy() {
        return 10000;
    }

    @Override
    public float getCurrentOutput() {
        float e = TARGET_OUTPUT * getMaxEnergy() - energy;
        esum = Math.min(Math.max(esum + e, -eLimit), eLimit);
        return Math.min(Math.max(e * kp + esum * ki, MIN_OUTPUT), MAX_OUTPUT);
    }

    // SEAM(#41): getTriggers adds the four inventory triggers to the engine's heat triggers, as 4.2.2.

    /** Powered with nothing burning and nothing in the slot that burns. */
    @Override
    public MachineStatus getStatusState() {
        MachineStatus state = super.getStatusState();
        if (state == MachineStatus.WORKING && burnTime == 0 && getItemBurnTime(getItem(0)) == 0)
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
        return new ContainerEngine(EnergyRegistry.engineStoneMenu.get(), containerId, playerInventory, this);
    }

    /** Ticks of fuel left in the current item, for tests. */
    public int getBurnTime() {
        return burnTime;
    }

    public int getTotalBurnTime() {
        return totalBurnTime;
    }
}
