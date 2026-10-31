/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: the legacy id is gone (registry entry); icons are GUI sprites
 * (textures/gui/sprites/triggers) named instead of registered; BlockEntity and Direction in place of TileEntity and
 * ForgeDirection.
 */
package buildcraftreforged.energy;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.api.gates.ITriggerParameter;
import buildcraftreforged.core.triggers.BCTrigger;
import buildcraftreforged.core.utils.StringUtils;
import buildcraftreforged.energy.TileEngine.EnergyStage;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

public class TriggerEngineHeat extends BCTrigger {

    public EnergyStage stage;
    private static final Identifier iconBlue = sprite("trigger_engineheat_blue");
    private static final Identifier iconGreen = sprite("trigger_engineheat_green");
    private static final Identifier iconYellow = sprite("trigger_engineheat_yellow");
    private static final Identifier iconRed = sprite("trigger_engineheat_red");

    public TriggerEngineHeat(EnergyStage stage, String uniqueTag) {
        super(uniqueTag);

        this.stage = stage;
    }

    @Override
    public String getDescription() {
        switch (stage) {
            case BLUE:
                return StringUtils.localize("gate.engine.blue");
            case GREEN:
                return StringUtils.localize("gate.engine.green");
            case YELLOW:
                return StringUtils.localize("gate.engine.yellow");
            default:
                return StringUtils.localize("gate.engine.red");
        }
    }

    @Override
    public boolean isTriggerActive(@Nullable Direction side, BlockEntity tile, @Nullable ITriggerParameter parameter) {
        if (tile instanceof TileEngine) {
            TileEngine engine = ((TileEngine) tile);

            return engine.getEnergyStage() == stage;
        }

        return false;
    }

    @Override
    public Identifier getIcon() {
        switch (stage) {
            case BLUE:
                return iconBlue;
            case GREEN:
                return iconGreen;
            case YELLOW:
                return iconYellow;
            default:
                return iconRed;
        }
    }

    private static Identifier sprite(String name) {
        return Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "triggers/" + name);
    }
}
