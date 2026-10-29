/*
 * Modified by Buildcraft Reforged, 2026: ported from buildcraft/api/filler/IFillerRegistry.java (no file header at
 * 4.2.2).
 */

package buildcraftreforged.api.filler;

import buildcraftreforged.api.gates.IAction;
import java.util.Set;

/**
 * Registry of filler patterns. Ported from 4.2.2 {@code buildcraft.api.filler.IFillerRegistry}.
 */
public interface IFillerRegistry {

    public void addPattern(IFillerPattern pattern);

    public IFillerPattern getPattern(String patternName);

    public IFillerPattern getNextPattern(IFillerPattern currentPattern);

    public IFillerPattern getPreviousPattern(IFillerPattern currentPattern);

    public Set<? extends IAction> getActions();
}
