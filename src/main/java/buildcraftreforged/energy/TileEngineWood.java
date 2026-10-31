/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: energy is FE (4.2.2 MJ × 10), so OUTPUT, the pulse added every 16 ticks and
 * the limits are × 10 and maxEnergyExtracted is 10 FE (docs/PORTING-NOTES.md, Power); power pipes may connect, item
 * and fluid pipes still may not; the block entity type comes from EnergyRegistry; CoreProxy.isSimulating becomes
 * the level's client check; it never runs the overheat timer (#40), since it never overheated in 4.2.2.
 */
package buildcraftreforged.energy;

import buildcraftreforged.api.transport.IPipeTile.PipeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

public class TileEngineWood extends TileEngine {

    /** FE per tick (4.2.2: 0.05 MJ). */
    public static final float OUTPUT = 0.5F;

    public TileEngineWood(BlockPos pos, BlockState state) {
        super(EnergyRegistry.engineWood.tile().get(), pos, state);
    }

    @Override
    public Identifier getTextureFile() {
        return WOOD_TEXTURE;
    }

    /** Its stages stop at RED (computeEnergyStage), so 4.2.2's redstone engine never exploded. */
    @Override
    protected boolean canOverheat() {
        return false;
    }

    @Override
    public float explosionRange() {
        return 1;
    }

    @Override
    public float minEnergyReceived() {
        return 0;
    }

    @Override
    public float maxEnergyReceived() {
        return 500;
    }

    @Override
    protected EnergyStage computeEnergyStage() {
        float energyLevel = getEnergyLevel();
        if (energyLevel < 0.25f)
            return EnergyStage.BLUE;
        else if (energyLevel < 0.5f)
            return EnergyStage.GREEN;
        else if (energyLevel < 0.75f)
            return EnergyStage.YELLOW;
        else
            return EnergyStage.RED;
    }

    @Override
    public float getPistonSpeed() {
        if (level != null && !level.isClientSide())
            return Math.max(0.08f * getHeatLevel(), 0.01f);
        switch (getEnergyStage()) {
            case GREEN:
                return 0.02F;
            case YELLOW:
                return 0.04F;
            case RED:
                return 0.08F;
            default:
                return 0.01F;
        }
    }

    @Override
    public void engineUpdate() {
        super.engineUpdate();

        if (isRedstonePowered)
            if (level.getGameTime() % 16 == 0)
                addEnergy(10);
    }

    /**
     * 4.2.2 refused every pipe. A power pipe now connects (the plan section 7 redstone engine powers any accepting block,
     * and the pipe takes the pulse either way); item and fluid pipes still do not.
     */
    @Override
    public ConnectOverride overridePipeConnection(PipeType type, Direction with) {
        if (type == PipeType.POWER)
            return ConnectOverride.DEFAULT;
        return ConnectOverride.DISCONNECT;
    }

    @Override
    public boolean isBurning() {
        return isRedstonePowered;
    }

    @Override
    public int getScaledBurnTime(int i) {
        return 0;
    }

    @Override
    public float getMaxEnergy() {
        return 1000;
    }

    @Override
    public float getCurrentOutput() {
        return OUTPUT;
    }

    /** 4.2.2: 1 + MIN_POWERLOSS = 1.01 MJ; 10 FE, the plan section 7 pulse (docs/PORTING-NOTES.md, Power). */
    @Override
    public float maxEnergyExtracted() {
        return 10;
    }
}
