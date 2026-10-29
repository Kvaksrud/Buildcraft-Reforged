/*
 * Modified by Buildcraft Reforged, 2026: ported from buildcraft/api/filler/IFillerRegistry.java (no file header at
 * 4.2.2); getActions() waits for the gates API.
 */

package buildcraftreforged.api.filler;

/**
 * Registry of filler patterns. Ported from 4.2.2 {@code buildcraft.api.filler.IFillerRegistry}.
 *
 * <p>4.2.2 also declared {@code Set<? extends IAction> getActions()}, the gate actions that select a pattern.
 * {@code IAction} belongs to the gates API (#31), which adds that method back here when it lands.
 */
public interface IFillerRegistry {

    public void addPattern(IFillerPattern pattern);

    public IFillerPattern getPattern(String patternName);

    public IFillerPattern getNextPattern(IFillerPattern currentPattern);

    public IFillerPattern getPreviousPattern(IFillerPattern currentPattern);
}
