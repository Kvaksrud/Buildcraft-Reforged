/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: pipes hold nothing, so the trigger reads the flow through the pipe
 * (docs/TRANSPORT.md 12): Empty is "nothing flows", Items/Fluid/Power Traversing is "something flows" (with a
 * parameter: that item, or the fluid in that container), Power Requested and Power Overloaded read the power phases;
 * Power Requested is offered on every power pipe (Q21). The legacy id is gone (registry entry); icons are GUI sprites.
 */
package buildcraftreforged.transport.triggers;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.api.gates.ITriggerParameter;
import buildcraftreforged.core.triggers.BCTrigger;
import buildcraftreforged.core.utils.StringUtils;
import buildcraftreforged.transport.ITriggerPipe;
import buildcraftreforged.transport.PipeFlow;
import buildcraftreforged.transport.TilePipe;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import org.jspecify.annotations.Nullable;

public class TriggerPipeContents extends BCTrigger implements ITriggerPipe {

    public enum Kind {

        Empty("buildcraft.pipe.contents.empty", "trigger_pipecontents_empty"),
        ContainsItems("buildcraft.pipe.contents.containsItems", "trigger_pipecontents_containsitems"),
        ContainsFluids("buildcraft.pipe.contents.containsFluids", "trigger_pipecontents_containsliquid"),
        ContainsEnergy("buildcraft.pipe.contents.containsEnergy", "trigger_pipecontents_containsenergy"),
        RequestsEnergy("buildcraft.pipe.contents.requestsEnergy", "trigger_pipecontents_requestsenergy"),
        TooMuchEnergy("buildcraft.pipe.contents.tooMuchEnergy", "trigger_pipecontents_toomuchenergy");
        private final Identifier icon;
        public final String tag;

        private Kind(String tag, String icon) {
            this.tag = tag;
            this.icon = Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "triggers/" + icon);
        }
    };
    Kind kind;

    public TriggerPipeContents(Kind kind) {
        super(kind.tag);
        this.kind = kind;
    }

    @Override
    public boolean hasParameter() {
        switch (kind) {
            case ContainsItems:
            case ContainsFluids:
                return true;
            default:
                return false;
        }
    }

    @Override
    public String getDescription() {

        switch (kind) {
            case Empty:
                return StringUtils.localize("gate.pipe.empty");
            case ContainsItems:
                return StringUtils.localize("gate.pipe.containsItems");
            case ContainsFluids:
                return StringUtils.localize("gate.pipe.containsFluids");
            case ContainsEnergy:
                return StringUtils.localize("gate.pipe.containsEnergy");
            case RequestsEnergy:
                return StringUtils.localize("gate.pipe.requestsEnergy");
            case TooMuchEnergy:
                return StringUtils.localize("gate.pipe.tooMuchEnergy");
        }

        return "";
    }

    @Override
    public boolean isTriggerActive(TilePipe pipe, @Nullable ITriggerParameter parameter) {
        PipeFlow flow = pipe.gateHolder.flow;
        ItemStack searched = parameter == null ? ItemStack.EMPTY : parameter.getItemStack();

        switch (pipe.kind) {
            case ITEM:
                if (kind == Kind.Empty)
                    return !flow.isFlowing();
                else if (kind == Kind.ContainsItems) {
                    if (!searched.isEmpty())
                        return flow.isFlowing() && ItemStack.isSameItem(flow.itemContent(), searched);
                    return flow.isFlowing();
                }
                return false;
            case FLUID:
                if (kind == Kind.Empty)
                    return !flow.isFlowing();
                else if (kind == Kind.ContainsFluids) {
                    if (!flow.isFlowing())
                        return false;
                    // 4.2.2 FluidContainerRegistry.getFluidForFilledItem: the fluid the container holds.
                    FluidStack searchedFluid = searched.isEmpty() ? FluidStack.EMPTY
                            : FluidUtil.getFirstStackContained(searched);
                    return searchedFluid.isEmpty() || FluidStack.isSameFluidSameComponents(searchedFluid,
                            flow.fluidContent());
                }
                return false;
            case POWER:
                switch (kind) {
                    case Empty:
                        return !flow.isFlowing();
                    case ContainsEnergy:
                        return flow.isFlowing();
                    case RequestsEnergy:
                        return flow.requestsEnergy();
                    case TooMuchEnergy:
                        return flow.isOverloaded();
                    default:
                        return false;
                }
            default:
                return false;
        }
    }

    @Override
    public Identifier getIcon() {
        return kind.icon;
    }
}
