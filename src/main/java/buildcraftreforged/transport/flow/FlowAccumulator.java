package buildcraftreforged.transport.flow;

import buildcraftreforged.api.transport.IPipeTile.PipeType;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * The measuring half of a pipe's flow record (docs/TRANSPORT.md 9.2): adds up what passes the pipe in 10-tick buckets
 * aligned to game time ({@code floor(gameTime / 10)}) and remembers the last content, colour and sides. Adding is part of
 * the transfer's transaction, so a simulated transfer records nothing. Buckets roll lazily, so an idle pipe costs
 * nothing.
 *
 * <p>{@link #record} at a boundary {@code T} ({@code T % 10 == 0}) reads the two buckets that closed before {@code T},
 * the 20 ticks {@code [T - 20, T)}: items per second is their sum, mB and FE per tick the sum / 20.
 *
 * <p>Not saved: a loaded pipe starts with nothing flowing.
 */
public final class FlowAccumulator extends SnapshotJournal<FlowAccumulator.State> {

    /** Ticks per bucket; a record covers two. */
    public static final int BUCKET_TICKS = 10;
    public static final int WINDOW_TICKS = 2 * BUCKET_TICKS;

    record State(long bucket, long current, long previous, long older, @Nullable Holder<?> content, byte colour,
            byte sides) {
    }

    private static final long UNSET = Long.MIN_VALUE;

    public final PipeType kind;
    private final @Nullable Runnable onActivity;
    private long bucket = UNSET;
    private long current;
    private long previous;
    private long older;
    private @Nullable Holder<?> content;
    private byte colour = FlowRecord.NO_COLOUR;
    private byte sides = FlowRecord.NO_SIDES;

    /** {@code onActivity} runs on every add, so the owner can put the pipe on the list of pipes to look at. */
    public FlowAccumulator(PipeType kind, @Nullable Runnable onActivity) {
        this.kind = kind;
        this.onActivity = onActivity;
    }

    /**
     * Adds {@code amount} (items, mB or FE) of {@code content} passing at {@code now}, entering by {@code entry} and
     * leaving by {@code exit} (null: the centre), with walk colour {@code colour}, in {@code transaction}.
     */
    public void add(long amount, @Nullable Holder<?> content, byte colour, @Nullable Direction entry,
            @Nullable Direction exit, long now, TransactionContext transaction) {
        if (amount <= 0)
            return;
        updateSnapshots(transaction);
        roll(now);
        current += amount;
        this.content = content;
        this.colour = colour;
        sides = FlowRecord.sides(index(entry), index(exit));
        if (onActivity != null)
            onActivity.run();
    }

    private static int index(@Nullable Direction side) {
        return side == null ? FlowRecord.CENTRE : side.get3DDataValue();
    }

    private void roll(long now) {
        long now10 = Math.floorDiv(now, BUCKET_TICKS);
        if (bucket == UNSET || now10 < bucket) {
            bucket = now10;
            current = previous = older = 0;
            return;
        }
        long steps = now10 - bucket;
        if (steps == 0)
            return;
        if (steps == 1) {
            older = previous;
            previous = current;
        } else if (steps == 2) {
            older = current;
            previous = 0;
        } else {
            older = previous = 0;
        }
        current = 0;
        bucket = now10;
    }

    /** What passed in the two buckets that closed before {@code now}'s bucket, in the kind's unit. */
    public long passed(long now) {
        roll(now);
        return previous + older;
    }

    /** The rate over the window before {@code now}: items per second, or mB or FE per tick. */
    public float rate(long now) {
        long passed = passed(now);
        return kind == PipeType.ITEM ? passed : passed / (float) WINDOW_TICKS;
    }

    /**
     * The flow record at {@code now}, a 10-tick boundary (docs/TRANSPORT.md 9.2). {@code overload} is the power pipe's
     * overload (7.4); it is kept only while power flows. With nothing passed, the record is {@link FlowRecord#none}.
     */
    public FlowRecord record(long now, boolean overload) {
        float rate = rate(now);
        if (rate <= 0)
            return FlowRecord.none(kind);
        return new FlowRecord(kind, kind == PipeType.POWER ? null : content, colour, sides, rate,
                kind == PipeType.POWER && overload);
    }

    @Override
    protected State createSnapshot() {
        return new State(bucket, current, previous, older, content, colour, sides);
    }

    @Override
    protected void revertToSnapshot(State snapshot) {
        bucket = snapshot.bucket();
        current = snapshot.current();
        previous = snapshot.previous();
        older = snapshot.older();
        content = snapshot.content();
        colour = snapshot.colour();
        sides = snapshot.sides();
    }
}
