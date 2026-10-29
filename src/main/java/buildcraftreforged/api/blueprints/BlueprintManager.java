/*
 * Modified by Buildcraft Reforged, 2026: ported from buildcraft/api/blueprints/BlueprintManager.java (no file header at
 * 4.2.2); keyed by Block instead of block id, defaults created on first use, signature lookups removed.
 */

package buildcraftreforged.api.blueprints;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.level.block.Block;

/**
 * The registry of blueprint schematics. Ported from 4.2.2 {@code buildcraft.api.blueprints.BlueprintManager}.
 *
 * <p>4.2.2 held an array indexed by block id and filled it with a default {@link BptBlock} for every id at class load.
 * Blocks have no ids now, so the map holds the schematics that were registered and {@link #getBptBlock} creates the
 * default for any other block on first use. The 4.2.2 class and its {@code getItemSignature}/{@code getBlockSignature}
 * were deprecated in 4.2.2 and served the id translation that plan section 13.3 removes; the signatures stay only in
 * {@link BlockSignature} and {@link ItemSignature} for the 1.6.4 importer.
 */
public class BlueprintManager {

    public static Map<Block, BptBlock> blockBptProps = new HashMap<Block, BptBlock>();

    /** 4.2.2 code read {@code blockBptProps[blockId]}; this is the replacement. */
    public static BptBlock getBptBlock(Block block) {
        BptBlock bptBlock = blockBptProps.get(block);

        if (bptBlock == null) {
            bptBlock = new BptBlock(block);
        }

        return bptBlock;
    }
}
