package buildcraftreforged.transport.module;

import buildcraftreforged.transport.route.PipeWalk;
import net.minecraft.util.RandomSource;

/**
 * The candidate filters of the Sorter and the Colour Sorter (docs/TRANSPORT.md 4.2 steps 5-6, 8.5, 8.9), level-free so
 * they are unit-tested. Each reorders {@code sides[0 .. count)}, side indices in side order, into the order the walk
 * tries them, and returns how many to try.
 */
public final class CandidateOrder {

    private CandidateOrder() {
    }

    /**
     * The Sorter (4.2.2 {@code PipeItemsDiamond.eventHandler(FindDest)}): {@code matches[d]} is how many filter slots of
     * row {@code d} hold the routed item, {@code filtered[d]} whether row {@code d} holds any filter. Sides with matches
     * come first, drawn by weight without replacement (an entry is drawn uniformly from the multiset of matches, its
     * side taken and all its entries removed); when no side matches, the sides whose rows have no filter, uniformly. A
     * filtered row that does not match is never tried.
     */
    public static int sorter(int[] sides, int count, boolean[] filtered, int[] matches, RandomSource random) {
        int[] weights = new int[count];
        int total = 0;
        for (int i = 0; i < count; i++) {
            weights[i] = matches[sides[i]];
            total += weights[i];
        }
        if (total > 0) {
            int[] ordered = new int[count];
            int n = 0;
            while (total > 0) {
                int draw = random.nextInt(total);
                int i = 0;
                while (draw >= weights[i]) {
                    draw -= weights[i];
                    i++;
                }
                ordered[n++] = sides[i];
                total -= weights[i];
                weights[i] = 0;
            }
            System.arraycopy(ordered, 0, sides, 0, n);
            return n;
        }
        int n = 0;
        for (int i = 0; i < count; i++) {
            if (!filtered[sides[i]])
                sides[n++] = sides[i];
        }
        PipeWalk.shuffle(sides, n, random);
        return n;
    }

    /**
     * The Colour Sorter (4.2.2 {@code PipeItemsDaizuli.eventHandler(FindDest)}): an item of the sorter's colour goes
     * out of {@code output} only, when that side is a candidate; otherwise {@code output} is removed and the rest are
     * tried in uniform order. {@code output} is a side index, or -1 for none.
     */
    public static int colourSorter(int[] sides, int count, int output, boolean matching, RandomSource random) {
        boolean hasOutput = false;
        for (int i = 0; i < count; i++) {
            hasOutput |= sides[i] == output;
        }
        if (matching && hasOutput) {
            sides[0] = output;
            return 1;
        }
        int n = 0;
        for (int i = 0; i < count; i++) {
            if (sides[i] != output)
                sides[n++] = sides[i];
        }
        PipeWalk.shuffle(sides, n, random);
        return n;
    }
}
