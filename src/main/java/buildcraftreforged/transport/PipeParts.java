/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: the part boxes and the ray trace of 4.2.2 BlockGenericPipe (Part,
 * RaytraceResult, doRayTrace, get*BoundingBox) in a class of their own, since the pipe block is new code. The ray is a
 * short one through the clicked point along the clicked face (a UseOnContext carries the hit, not the player's eye),
 * and AABB.clip replaces Block.collisionRayTrace with changing block bounds.
 */
package buildcraftreforged.transport;

import buildcraftreforged.core.CoreConstants;
import buildcraftreforged.core.utils.MatrixTranformations;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The parts of a pipe a click can hit: the pipe itself (centre and arms), the gate, a facade or a plug. */
public final class PipeParts {

    /** 4.2.2 {@code TransportConstants.FACADE_THICKNESS}. */
    public static final float FACADE_THICKNESS = 2F / 16F;

    public enum Part {
        Pipe,
        Gate,
        Facade,
        Plug
    }

    /** 4.2.2 {@code RaytraceResult}; {@code sideHit} is null for the pipe centre (4.2.2 UNKNOWN). */
    public record RaytraceResult(Part hitPart, AABB boundingBox, @Nullable Direction sideHit) {
    }

    private PipeParts() {
    }

    /**
     * 4.2.2 {@code BlockGenericPipe.doRayTrace}: which part the click at {@code hit} on {@code face} touched. The ray
     * runs from just outside the clicked point into the block, so it meets the box whose surface was clicked first;
     * where boxes share that surface the earlier part in 4.2.2's order wins (pipe, gate, facade, plug).
     */
    public static @Nullable RaytraceResult doRayTrace(TilePipe pipe, Vec3 hit, Direction face) {
        BlockPos pos = pipe.getBlockPos();
        Vec3 local = hit.subtract(pos.getX(), pos.getY(), pos.getZ());
        Vec3 normal = face.getUnitVec3();
        Vec3 origin = local.add(normal.scale(0.01));
        Vec3 end = local.subtract(normal.scale(0.02));

        double minLengthSquared = Double.POSITIVE_INFINITY;
        RaytraceResult result = null;

        // pipe
        for (Direction side : sidesAndCentre()) {
            if (side == null || pipe.isPipeConnected(side)) {
                AABB bb = getPipeBoundingBox(side);
                double length = clip(bb, origin, end);
                if (length < minLengthSquared) {
                    minLengthSquared = length;
                    result = new RaytraceResult(Part.Pipe, bb, side);
                }
            }
        }

        // gates
        for (Direction side : Direction.values()) {
            if (pipe.gateHolder.hasGate(side)) {
                AABB bb = getGateBoundingBox(side);
                double length = clip(bb, origin, end);
                if (length < minLengthSquared) {
                    minLengthSquared = length;
                    result = new RaytraceResult(Part.Gate, bb, side);
                }
            }
        }

        // facades
        for (Direction side : Direction.values()) {
            if (pipe.hasFacade(side)) {
                AABB bb = getFacadeBoundingBox(side);
                double length = clip(bb, origin, end);
                if (length < minLengthSquared) {
                    minLengthSquared = length;
                    result = new RaytraceResult(Part.Facade, bb, side);
                }
            }
        }

        // plugs
        for (Direction side : Direction.values()) {
            if (pipe.hasPlug(side)) {
                AABB bb = getPlugBoundingBox(side);
                double length = clip(bb, origin, end);
                if (length < minLengthSquared) {
                    minLengthSquared = length;
                    result = new RaytraceResult(Part.Plug, bb, side);
                }
            }
        }

        return result;
    }

    /** The squared distance from {@code origin} to where the ray enters {@code box}, or infinity for a miss. */
    private static double clip(AABB box, Vec3 origin, Vec3 end) {
        if (box.contains(origin))
            return 0;
        Optional<Vec3> at = box.clip(origin, end);
        return at.map(v -> v.distanceToSqr(origin)).orElse(Double.POSITIVE_INFINITY);
    }

    /** null (the centre, 4.2.2 UNKNOWN) and the six sides. */
    private static Direction[] sidesAndCentre() {
        Direction[] sides = new Direction[7];
        System.arraycopy(Direction.values(), 0, sides, 1, 6);
        return sides;
    }

    public static AABB getGateBoundingBox(Direction side) {
        float min = CoreConstants.PIPE_MIN_POS + 0.05F;
        float max = CoreConstants.PIPE_MAX_POS - 0.05F;

        float[][] bounds = new float[3][2];
        // X START - END
        bounds[0][0] = min;
        bounds[0][1] = max;
        // Y START - END
        bounds[1][0] = CoreConstants.PIPE_MIN_POS - 0.10F;
        bounds[1][1] = CoreConstants.PIPE_MIN_POS;
        // Z START - END
        bounds[2][0] = min;
        bounds[2][1] = max;

        MatrixTranformations.transform(bounds, side);
        return box(bounds);
    }

    public static AABB getFacadeBoundingBox(Direction side) {
        float[][] bounds = new float[3][2];
        // X START - END
        bounds[0][0] = 0.0F;
        bounds[0][1] = 1.0F;
        // Y START - END
        bounds[1][0] = 0.0F;
        bounds[1][1] = FACADE_THICKNESS;
        // Z START - END
        bounds[2][0] = 0.0F;
        bounds[2][1] = 1.0F;

        MatrixTranformations.transform(bounds, side);
        return box(bounds);
    }

    public static AABB getPlugBoundingBox(Direction side) {
        float[][] bounds = new float[3][2];
        // X START - END
        bounds[0][0] = 0.25F;
        bounds[0][1] = 0.75F;
        // Y START - END
        bounds[1][0] = 0.125F;
        bounds[1][1] = 0.251F;
        // Z START - END
        bounds[2][0] = 0.25F;
        bounds[2][1] = 0.75F;

        MatrixTranformations.transform(bounds, side);
        return box(bounds);
    }

    /** The centre for null, else the arm from the centre to the block edge on {@code side}. */
    public static AABB getPipeBoundingBox(@Nullable Direction side) {
        float min = CoreConstants.PIPE_MIN_POS;
        float max = CoreConstants.PIPE_MAX_POS;

        if (side == null)
            return new AABB(min, min, min, max, max, max);

        float[][] bounds = new float[3][2];
        // X START - END
        bounds[0][0] = min;
        bounds[0][1] = max;
        // Y START - END
        bounds[1][0] = 0;
        bounds[1][1] = min;
        // Z START - END
        bounds[2][0] = min;
        bounds[2][1] = max;

        MatrixTranformations.transform(bounds, side);
        return box(bounds);
    }

    private static AABB box(float[][] bounds) {
        return new AABB(bounds[0][0], bounds[1][0], bounds[2][0], bounds[0][1], bounds[1][1], bounds[2][1]);
    }
}
