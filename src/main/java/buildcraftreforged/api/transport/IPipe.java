/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: BlockEntity in place of TileEntity; section 8 notes.
 */

package buildcraftreforged.api.transport;

import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * A pipe, the logic object a pipe block entity holds. Ported from 4.2.2 {@code buildcraft.api.transport.IPipe}.
 *
 * <p><b>Plan section 8:</b> P3 redesigns the pipe internals. {@link WireColor} and the wire and gate queries survive
 * (pipe wire and gates are kept, 8.5). {@link DrawingState} was the 4.2.2 multi-pass pipe renderer's state and is not
 * expected to survive the dynamic pipe model of 8.4. The pipe/container split itself is P3's call.
 */
public interface IPipe {

    /** <b>Plan section 8:</b> not expected to survive P3 (4.2.2 renderer state). */
    enum DrawingState {
        DrawingPipe, DrawingRedWire, DrawingBlueWire, DrawingGreenWire, DrawingYellowWire, DrawingGate
    }

    enum WireColor {
        Red, Blue, Green, Yellow;

        public WireColor reverse() {
            switch (this) {
            case Red:
                return Yellow;
            case Blue:
                return Green;
            case Green:
                return Blue;
            default:
                return Red;
            }
        }
    }

    public boolean isWired(WireColor color);

    public boolean hasGate();

    public BlockEntity getContainer();

    public boolean isWireConnectedTo(BlockEntity tile, WireColor color);

}
