/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: registry entries in place of legacy ids, GUI sprite ids in place of Icons.
 */
package buildcraftreforged.core.triggers;

import buildcraftreforged.api.gates.ActionManager;
import buildcraftreforged.api.gates.IAction;
import net.minecraft.resources.Identifier;

public abstract class BCAction implements IAction {

    protected final String uniqueTag;

    public BCAction(String uniqueTag) {
        this.uniqueTag = uniqueTag;
        ActionManager.registerAction(this);
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

    public int getTextureMap() {
        return 1;
    }

    @Override
    public boolean hasParameter() {
        return false;
    }
}
