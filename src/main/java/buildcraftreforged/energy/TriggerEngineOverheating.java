/*
 * Buildcraft Reforged, 2026: new file, no 4.2.2 original. The Engine Overheating gate trigger (plan section 9).
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

/**
 * True while the engine's overheat timer runs, and while an engine the timer shut down (engines.explode = false) waits
 * for the wrench: the alarm stays on until someone deals with it. Offered by engines that can overheat.
 */
public class TriggerEngineOverheating extends BCTrigger {

    private static final Identifier icon =
            Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "triggers/trigger_engine_overheating");

    public TriggerEngineOverheating(String uniqueTag) {
        super(uniqueTag);
    }

    @Override
    public String getDescription() {
        return StringUtils.localize("gate.engine.overheating");
    }

    @Override
    public boolean isTriggerActive(@Nullable Direction side, BlockEntity tile, @Nullable ITriggerParameter parameter) {
        if (tile instanceof TileEngine engine) {
            return engine.overheatTimer >= 0 || engine.energyStage == EnergyStage.OVERHEAT;
        }

        return false;
    }

    @Override
    public Identifier getIcon() {
        return icon;
    }
}
