/*
 * Modified by Buildcraft Reforged, 2026: ported from buildcraft/core/triggers/DefaultActionProvider.java (no file
 * header at 4.2.2); actions come from CoreRegistry, FMLLog becomes BCLog.
 */

package buildcraftreforged.core.triggers;

import buildcraftreforged.api.gates.IAction;
import buildcraftreforged.api.gates.IActionProvider;
import buildcraftreforged.core.CoreRegistry;
import buildcraftreforged.core.IMachine;
import buildcraftreforged.core.utils.BCLog;
import java.util.LinkedList;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

public class DefaultActionProvider implements IActionProvider {

    @Override
    public LinkedList<IAction> getNeighborActions(Block block, @Nullable BlockEntity tile) {
        LinkedList<IAction> res = new LinkedList<IAction>();

        res.add(CoreRegistry.actionRedstone.get());

        try {
            if (tile instanceof IMachine) {
                IMachine machine = (IMachine) tile;
                if (machine.allowAction(CoreRegistry.actionOn.get()))
                    res.add(CoreRegistry.actionOn.get());
                if (machine.allowAction(CoreRegistry.actionOff.get()))
                    res.add(CoreRegistry.actionOff.get());
                if (machine.allowAction(CoreRegistry.actionLoop.get()))
                    res.add(CoreRegistry.actionLoop.get());
            }
        } catch (Throwable error) {
            BCLog.logger.error("Outdated API detected, please update your mods!", error);
        }

        return res;
    }
}
