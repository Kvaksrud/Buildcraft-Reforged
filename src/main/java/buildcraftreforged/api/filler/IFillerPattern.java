/*
 * Modified by Buildcraft Reforged, 2026: ported from buildcraft/api/filler/IFillerPattern.java (no file header at
 * 4.2.2); BlockEntity and Direction in place of TileEntity and ForgeDirection; the icon is a sprite id and the display
 * name a Component.
 */

package buildcraftreforged.api.filler;

import buildcraftreforged.api.core.IBox;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * A Filler pattern. Ported from 4.2.2 {@code buildcraft.api.filler.IFillerPattern}.
 */
public interface IFillerPattern {

    public String getUniqueTag();

    /**
     * Creates the object that does the pattern iteration. This object may be
     * state-full and will be used until the pattern is done or changes.
     *
     * @param tile the Filler
     * @param box the area to fill
     * @param orientation not currently used, but may be in the future (the filler needs some orientation code)
     * @return
     */
    public IPatternIterator createPatternIterator(BlockEntity tile, IBox box, Direction orientation);

    /**
     * The id of the pattern's icon sprite, drawn by the filler screen and the gate GUI. 4.2.2 returned a client-only
     * {@code Icon}; a sprite id keeps this interface free of client classes.
     */
    public Identifier getIcon();

    /** 4.2.2 returned a localised String; the Component is translated where it is shown. */
    public Component getDisplayName();
}
