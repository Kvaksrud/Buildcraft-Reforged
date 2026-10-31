/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: each pattern's ActionFiller is an entry of the action registry, registered
 * through the DeferredRegister given to the constructor, so addPattern must run before the registries freeze.
 */
package buildcraftreforged.builders.filler;

import buildcraftreforged.api.filler.IFillerPattern;
import buildcraftreforged.api.filler.IFillerRegistry;
import buildcraftreforged.api.gates.IAction;
import buildcraftreforged.builders.filler.pattern.FillerPattern;
import buildcraftreforged.builders.triggers.ActionFiller;
import java.util.LinkedHashSet;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class FillerRegistry implements IFillerRegistry {

    private TreeMap<String, IFillerPattern> patterns = new TreeMap<String, IFillerPattern>();
    private Set<DeferredHolder<IAction, ActionFiller>> patternActions = new LinkedHashSet<>();
    private final DeferredRegister<IAction> actions;

    /** @param actions where the pattern actions go; 4.2.2 kept them only in this registry. */
    public FillerRegistry(DeferredRegister<IAction> actions) {
        this.actions = actions;
    }

    /**
     * Adds the pattern and registers its gate action as {@code filler_<tag>} (the 4.2.2 tag for a
     * {@link FillerPattern}, else the unique tag with ':' as '_'). Call it during mod construction.
     */
    @Override
    public void addPattern(IFillerPattern pattern) {
        patterns.put(pattern.getUniqueTag(), pattern);
        patternActions.add(actions.register(actionName(pattern), () -> new ActionFiller(pattern)));
    }

    private static String actionName(IFillerPattern pattern) {
        String tag = pattern instanceof FillerPattern fillerPattern ? fillerPattern.getTag() : pattern.getUniqueTag();
        return "filler_" + tag.replace(':', '_');
    }

    @Override
    public IFillerPattern getPattern(String patternName) {
        return patterns.get(patternName);
    }

    @Override
    public IFillerPattern getNextPattern(IFillerPattern currentPattern) {
        Entry<String, IFillerPattern> pattern = patterns.higherEntry(currentPattern.getUniqueTag());
        if (pattern == null)
            pattern = patterns.firstEntry();
        return pattern.getValue();
    }

    @Override
    public IFillerPattern getPreviousPattern(IFillerPattern currentPattern) {
        Entry<String, IFillerPattern> pattern = patterns.lowerEntry(currentPattern.getUniqueTag());
        if (pattern == null)
            pattern = patterns.lastEntry();
        return pattern.getValue();
    }

    @Override
    public Set<ActionFiller> getActions() {
        Set<ActionFiller> result = new LinkedHashSet<>();
        for (DeferredHolder<IAction, ActionFiller> action : patternActions) {
            result.add(action.get());
        }
        return result;
    }
}
