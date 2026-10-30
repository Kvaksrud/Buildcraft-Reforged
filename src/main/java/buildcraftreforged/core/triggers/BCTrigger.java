/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: registry entries in place of legacy ids, GUI sprite ids in place of Icons,
 * BlockEntity and Direction in place of TileEntity and ForgeDirection; getCapability added for the subclasses.
 */
package buildcraftreforged.core.triggers;

import buildcraftreforged.api.gates.ActionManager;
import buildcraftreforged.api.gates.ITrigger;
import buildcraftreforged.api.gates.ITriggerParameter;
import buildcraftreforged.api.gates.TriggerParameter;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.jspecify.annotations.Nullable;

/**
 * This class has to be implemented to create new triggers kinds to BuildCraft
 * gates. There is an instance per kind, which will get called wherever the
 * trigger can be active.
 */
public abstract class BCTrigger implements ITrigger {

    protected final String uniqueTag;

    public BCTrigger(String uniqueTag) {
        this.uniqueTag = uniqueTag;
        ActionManager.registerTrigger(this);
    }

    @Override
    public String getUniqueTag() {
        return uniqueTag;
    }

    public int getIconIndex() {
        return 0;
    }

    @Override
    public Identifier getIcon() {
        return ActionTriggerIconProvider.INSTANCE.getIcon(getIconIndex());
    }

    @Override
    public boolean hasParameter() {
        return false;
    }

    @Override
    public boolean requiresParameter() {
        return false;
    }

    @Override
    public String getDescription() {
        return "";
    }

    @Override
    public boolean isTriggerActive(@Nullable Direction side, BlockEntity tile, @Nullable ITriggerParameter parameter) {
        return false;
    }

    @Override
    public final ITriggerParameter createParameter() {
        return new TriggerParameter();
    }

    /**
     * The tile's capability on the given side, null when it has none. Stands in for 4.2.2's {@code instanceof
     * IInventory} and {@code instanceof IFluidHandler}, so vanilla and other mods' blocks count too.
     */
    protected static <T> @Nullable T getCapability(BlockCapability<T, @Nullable Direction> capability,
            @Nullable BlockEntity tile, @Nullable Direction side) {
        if (tile == null) {
            return null;
        }
        Level level = tile.getLevel();
        if (level == null) {
            return null;
        }
        return level.getCapability(capability, tile.getBlockPos(), tile.getBlockState(), tile, side);
    }
}
