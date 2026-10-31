/*
 * Modified by Buildcraft Reforged, 2026: ported from buildcraft/api/transport/PipeManager.java (no file header at
 * 4.2.2); Level and BlockPos in place of World and i, j, k; a thread-safe handler list.
 */

package buildcraftreforged.api.transport;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * The exclusion list of extracting pipes: registered {@link IExtractionHandler}s can forbid an Extractor or Filtered
 * Extractor module (docs/TRANSPORT.md 8.2) from pulling out of a block. A block that any handler refuses is not a valid
 * facing for the module.
 */
public abstract class PipeManager {

    public static final List<IExtractionHandler> extractionHandlers = new CopyOnWriteArrayList<>();

    public static void registerExtractionHandler(IExtractionHandler handler) {
        extractionHandlers.add(handler);
    }

    /**
     * @param extractor the module asking (4.2.2 passed the pipe, or null)
     */
    public static boolean canExtractItems(Object extractor, Level world, BlockPos pos) {
        for (IExtractionHandler handler : extractionHandlers)
            if (!handler.canExtractItems(extractor, world, pos))
                return false;

        return true;
    }

    /**
     * @param extractor the module asking (4.2.2 passed the pipe, or null)
     */
    public static boolean canExtractFluids(Object extractor, Level world, BlockPos pos) {
        for (IExtractionHandler handler : extractionHandlers)
            if (!handler.canExtractFluids(extractor, world, pos))
                return false;

        return true;
    }
}
