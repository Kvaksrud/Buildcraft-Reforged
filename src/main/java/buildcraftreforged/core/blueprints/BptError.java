/**
 * Copyright (c) SpaceToad, 2011-2012
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: logs through the mod's SLF4J logger; takes a cause.
 */

package buildcraftreforged.core.blueprints;

import buildcraftreforged.core.utils.BCLog;

public class BptError extends Exception {

    /**
     *
     */
    private static final long serialVersionUID = 3579188081467555542L;

    public BptError(String str) {
        super(str);

        BCLog.logger.debug("BLUEPRINT ERROR:" + str);

    }

    public BptError(String str, Throwable cause) {
        super(str, cause);

        BCLog.logger.debug("BLUEPRINT ERROR:" + str, cause);
    }

}
