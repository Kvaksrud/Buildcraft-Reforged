/*
 * Modified by Buildcraft Reforged, 2026: ported from buildcraft/api/transport/IExtractionHandler.java (no file header
 * at 4.2.2); Level and BlockPos in place of World and i, j, k.
 */

package buildcraftreforged.api.transport;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Implement and register with the PipeManager if you want to suppress connections from wooden pipes.
 *
 * <p>Ported from 4.2.2 {@code buildcraft.api.transport.IExtractionHandler}. <b>Plan section 8:</b> the wooden pipe
 * becomes the Extractor module and the Basic Extractor (8.2); P3 decides whether they still ask registered handlers.
 * {@link PipeManager} keeps the registered handlers; the Extractor and Filtered Extractor modules ask it when they
 * choose a facing (#55). The Basic Extractor is #58.
 */
public interface IExtractionHandler {

    /**
     * Can this pipe extract items from the block located at these coordinates?
     * param extractor can be null
     */
    boolean canExtractItems(Object extractor, Level world, BlockPos pos);

    /**
     * Can this pipe extract liquids from the block located at these coordinates?
     * param extractor can be null
     */
    boolean canExtractFluids(Object extractor, Level world, BlockPos pos);
}
