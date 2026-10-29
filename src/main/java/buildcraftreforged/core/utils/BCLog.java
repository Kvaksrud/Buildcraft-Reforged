/*
 * Copyright (c) SpaceToad, 2011-2012
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: java.util.logging under FMLLog becomes the mod's SLF4J logger;
 * initLog takes the version from the mod container instead of Version.
 */
package buildcraftreforged.core.utils;

import buildcraftreforged.BuildcraftReforged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author CovertJaguar <http://www.railcraft.info/>
 */
public class BCLog {

    public static final Logger logger = LoggerFactory.getLogger(BuildcraftReforged.MOD_ID);

    public static void initLog(String version) {
        logger.info("Starting Buildcraft Reforged " + version);
        logger.info("Copyright (c) SpaceToad, 2011");
        logger.info("http://www.mod-buildcraft.com");
    }

    public static void logErrorAPI(String mod, Throwable error, Class<?> classFile) {
        StringBuilder msg = new StringBuilder(mod);
        msg.append(" API error, please update your mods. Error: ").append(error);
        StackTraceElement[] stackTrace = error.getStackTrace();
        if (stackTrace.length > 0)
            msg.append(", ").append(stackTrace[0]);
        logger.error(msg.toString());

        if (classFile != null) {
            msg = new StringBuilder(mod);
            msg.append(" API error: ").append(classFile.getSimpleName()).append(" is loaded from ").append(classFile.getProtectionDomain().getCodeSource().getLocation());
            logger.error(msg.toString());
        }
    }
}
