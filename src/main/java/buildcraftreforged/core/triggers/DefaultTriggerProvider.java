/*
 * Modified by Buildcraft Reforged, 2026: ported from buildcraft/core/triggers/DefaultTriggerProvider.java (no file
 * header at 4.2.2); IInventory and IFluidHandler checks read the capabilities; triggers come from CoreRegistry; the
 * Machine trigger's blocked state is offered with the other two.
 */

package buildcraftreforged.core.triggers;

import buildcraftreforged.api.gates.IOverrideDefaultTriggers;
import buildcraftreforged.api.gates.ITrigger;
import buildcraftreforged.api.gates.ITriggerProvider;
import buildcraftreforged.api.transport.IPipe;
import buildcraftreforged.core.CoreRegistry;
import buildcraftreforged.core.IMachine;
import java.util.LinkedList;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

public class DefaultTriggerProvider implements ITriggerProvider {

    @Override
    public LinkedList<ITrigger> getNeighborTriggers(Block block, @Nullable BlockEntity tile) {
        if (tile instanceof IOverrideDefaultTriggers) {
            return ((IOverrideDefaultTriggers) tile).getTriggers();
        }

        LinkedList<ITrigger> res = new LinkedList<ITrigger>();

        ResourceHandler<ItemResource> inventory = BCTrigger.getCapability(Capabilities.Item.BLOCK, tile, null);
        if (inventory != null && inventory.size() > 0) {
            res.add(CoreRegistry.triggerEmptyInventory.get());
            res.add(CoreRegistry.triggerContainsInventory.get());
            res.add(CoreRegistry.triggerSpaceInventory.get());
            res.add(CoreRegistry.triggerFullInventory.get());
            res.add(CoreRegistry.triggerInventoryBelow25.get());
            res.add(CoreRegistry.triggerInventoryBelow50.get());
            res.add(CoreRegistry.triggerInventoryBelow75.get());
        }

        ResourceHandler<FluidResource> tanks = BCTrigger.getCapability(Capabilities.Fluid.BLOCK, tile, null);
        if (tanks != null && tanks.size() > 0) {
            res.add(CoreRegistry.triggerEmptyFluid.get());
            res.add(CoreRegistry.triggerContainsFluid.get());
            res.add(CoreRegistry.triggerSpaceFluid.get());
            res.add(CoreRegistry.triggerFullFluid.get());
        }

        if (tile instanceof IMachine) {
            res.add(CoreRegistry.triggerMachineActive.get());
            res.add(CoreRegistry.triggerMachineInactive.get());
            res.add(CoreRegistry.triggerMachineBlocked.get());
        }

        if (block != null && block.defaultBlockState().isSignalSource()) {
            // res.add(BuildCraftCore.triggerRedstoneActive);
            // res.add(BuildCraftCore.triggerRedstoneInactive);
        }

        return res;
    }

    @Override
    public @Nullable LinkedList<ITrigger> getPipeTriggers(IPipe pipe) {
        return null;
    }
}
