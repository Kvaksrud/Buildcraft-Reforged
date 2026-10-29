/*
 * Copyright (c) SpaceToad, 2011-2012
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: defines values on a ModConfigSpec.Builder instead of reading a Forge
 * Configuration; the category is the builder's current section, and the parse and reset helpers are gone because the
 * spec validates and corrects values itself.
 */
package buildcraftreforged.core.utils;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 *
 * @author CovertJaguar <http://www.railcraft.info/>
 */
public class ConfigUtils {

    private static final String COMMENT_PREFIX = "";
    private static final String COMMENT_SUFFIX = "";
    private final ModConfigSpec.Builder config;

    public ConfigUtils(ModConfigSpec.Builder config) {
        this.config = config;
    }

    public ModConfigSpec.BooleanValue get(String tag, boolean defaultValue, String comment) {
        comment = COMMENT_PREFIX + comment.replace("{t}", tag) + COMMENT_SUFFIX;
        return config.comment(comment).define(tag, defaultValue);
    }

    public ModConfigSpec.IntValue get(String tag, int defaultValue, String comment) {
        comment = COMMENT_PREFIX + comment.replace("{t}", tag) + COMMENT_SUFFIX;
        return config.comment(comment).defineInRange(tag, defaultValue, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    public ModConfigSpec.IntValue get(String tag, int min, int defaultValue, int max, String comment) {
        comment = COMMENT_PREFIX + comment.replace("{t}", tag) + COMMENT_SUFFIX;
        return config.comment(comment).defineInRange(tag, defaultValue, min, max);
    }

    public ModConfigSpec.DoubleValue get(String tag, float min, float defaultValue, float max, String comment) {
        comment = COMMENT_PREFIX + comment.replace("{t}", tag) + COMMENT_SUFFIX;
        return config.comment(comment).defineInRange(tag, defaultValue, min, max);
    }
}
