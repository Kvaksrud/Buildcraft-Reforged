/*
 * Modified by Buildcraft Reforged, 2026: ported from buildcraft/api/filler/FillerManager.java (no file header at
 * 4.2.2); unchanged.
 */

package buildcraftreforged.api.filler;

/**
 * Holder of the filler pattern registry. Ported from 4.2.2 {@code buildcraft.api.filler.FillerManager}. The builders
 * phase sets {@link #registry} to its {@code FillerRegistry} and adds the 4.2.2 patterns.
 */
public class FillerManager {

    public static IFillerRegistry registry;

}
