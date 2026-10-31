/*
 * Modified by Buildcraft Reforged, 2026: ported from buildcraft/api/blueprints/BlueprintManager.java (no file header at
 * 4.2.2); keyed by Block instead of block id, defaults created on first use, signature lookups removed; schematic
 * families added (#76).
 */

package buildcraftreforged.api.blueprints;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.world.level.block.Block;

/**
 * The registry of blueprint schematics. Ported from 4.2.2 {@code buildcraft.api.blueprints.BlueprintManager}.
 *
 * <p>4.2.2 held an array indexed by block id and filled it with a default {@link BptBlock} for every id at class load.
 * Blocks have no ids now, so the map holds the schematics that were registered and {@link #getBptBlock} creates the
 * default for any other block on first use. The 4.2.2 class and its {@code getItemSignature}/{@code getBlockSignature}
 * were deprecated in 4.2.2 and served the id translation that plan section 13.3 removes; the signatures stay only in
 * {@link BlockSignature} and {@link ItemSignature} for the 1.6.4 importer.
 *
 * <p>Two ways to give a block its schematic (#76):
 * <ul>
 * <li>As 4.2.2: construct it, {@code new BptBlockStairs(block)}. The constructor puts it in {@link #blockBptProps};
 * the last one constructed for a block wins.</li>
 * <li>New: {@link #registerFamily} (or {@link #registerSchematic} for one block known only through a supplier) names
 * the blocks a factory serves, such as every {@code DoorBlock}. 4.2.2 listed each vanilla block id; there are now
 * dozens of doors, beds and stairs, from any mod. A family is asked only for a block that has no schematic yet, the
 * latest registered family first, and the schematic it makes is kept in {@link #blockBptProps}. So a schematic
 * constructed for one block always beats a family, whichever was registered first.</li>
 * </ul>
 * Buildcraft Reforged registers its families during mod construction ({@code BuildersSchematics}); other mods may
 * register theirs at any time before blueprints are used, usually in common setup.
 */
public class BlueprintManager {

    public static Map<Block, BptBlock> blockBptProps = new ConcurrentHashMap<Block, BptBlock>();

    private static final List<Family> families = new CopyOnWriteArrayList<Family>();

    private record Family(Predicate<Block> matches, Function<Block, ? extends BptBlock> factory) {
    }

    /** 4.2.2 code read {@code blockBptProps[blockId]}; this is the replacement. */
    public static BptBlock getBptBlock(Block block) {
        BptBlock bptBlock = blockBptProps.get(block);

        if (bptBlock == null) {
            for (int i = families.size() - 1; i >= 0 && bptBlock == null; --i) {
                Family family = families.get(i);

                if (family.matches().test(block)) {
                    bptBlock = family.factory().apply(block);
                }
            }

            if (bptBlock == null) {
                bptBlock = new BptBlock(block);
            }

            // The constructor registered it; a concurrent lookup may have made another, so return the kept one.
            blockBptProps.putIfAbsent(block, bptBlock);
            bptBlock = blockBptProps.get(block);
        }

        return bptBlock;
    }

    /**
     * New (#76): {@code factory} makes the schematic of every block {@code matches} accepts that has no schematic of
     * its own yet. The factory is expected to construct a {@link BptBlock} for the block it is given.
     */
    public static void registerFamily(Predicate<Block> matches, Function<Block, ? extends BptBlock> factory) {
        families.add(new Family(matches, factory));
    }

    /**
     * New (#76): as {@link #registerFamily}, for the one block {@code block} supplies. For blocks that are not
     * registered yet when the schematic is declared (a {@code DeferredBlock}); the supplier is read on first use.
     */
    public static void registerSchematic(Supplier<? extends Block> block, Function<Block, ? extends BptBlock> factory) {
        registerFamily(candidate -> candidate == block.get(), factory);
    }
}
