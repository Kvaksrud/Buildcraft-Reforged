package buildcraftreforged.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * 1.6.4 Forge's {@code Block.rotateBlock(World, x, y, z, ForgeDirection)}, which NeoForge does not have. Our blocks
 * that 4.2.2 let the wrench rotate (engines, wooden and iron pipes, ...) implement it with their 4.2.2 rotateBlock
 * body; every other block goes through {@link RotationHelper#rotateVanillaBlock}.
 */
public interface IRotatableBlock {
    /** @param axis the clicked face; 4.2.2 passed {@code ForgeDirection.getOrientation(side)} */
    boolean rotateBlock(Level level, BlockPos pos, Direction axis);

    /**
     * Blocks that answer true turn only on a sneaking wrench click; a plain click goes to the block as a normal use
     * (an engine opens its GUI or status panel).
     */
    default boolean rotatesOnSneakOnly() {
        return false;
    }
}
