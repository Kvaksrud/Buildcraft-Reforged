/*
 * Ported from buildcraft/builders/triggers/BuildersActionProvider.java (BuildCraft 4.2.2, MMPL; the original has no
 * header). Modified by Buildcraft Reforged, 2026: Block and BlockEntity in place of the 1.6.4 types.
 */
package buildcraftreforged.builders.triggers;

import buildcraftreforged.api.filler.FillerManager;
import buildcraftreforged.api.gates.IAction;
import buildcraftreforged.api.gates.IActionProvider;
import buildcraftreforged.builders.TileFiller;
import java.util.LinkedList;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/**
 *
 * @author CovertJaguar <http://www.railcraft.info>
 */
public class BuildersActionProvider implements IActionProvider {

    @Override
    public LinkedList<IAction> getNeighborActions(Block block, @Nullable BlockEntity tile) {
        LinkedList<IAction> actions = new LinkedList<IAction>();
        if (tile instanceof TileFiller) {
            actions.addAll(FillerManager.registry.getActions());
        }
        return actions;
    }
}
