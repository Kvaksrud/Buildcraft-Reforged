/*
 * Ported from buildcraft/core/gui/slots/IPhantomSlot.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: package name only.
 */
package buildcraftreforged.core.gui.slots;

/*
 * Phantom Slots don't "use" items, they are used for filters and various other logic slots.
 */
public interface IPhantomSlot {

    boolean canAdjust();
}
