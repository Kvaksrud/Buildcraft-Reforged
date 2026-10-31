package buildcraftreforged.transport.flow;

import buildcraftreforged.api.transport.IPipeTile.PipeType;

/**
 * One pipe's flow record and its sync (docs/TRANSPORT.md 9). On the server: the {@link FlowAccumulator}, the record
 * measured at the last boundary (what the Pipe Contents trigger reads) and the last record sent to clients. On the
 * client: the last record received and when it started flowing, for the animation.
 */
public final class FlowTracker {

    public final FlowAccumulator accumulator;
    private FlowRecord record;
    private FlowRecord sent;
    private int sendCount;
    private FlowRecord client;
    private long clientSince;

    public FlowTracker(PipeType kind, Runnable onActivity) {
        accumulator = new FlowAccumulator(kind, onActivity);
        record = sent = client = FlowRecord.none(kind);
    }

    /** Server: the record of the last boundary; {@link FlowRecord#none} before the first and once flow stopped. */
    public FlowRecord record() {
        return record;
    }

    /** Server: the record clients have, which the update tag carries to players who start watching. */
    public FlowRecord sent() {
        return sent;
    }

    /** Server: how many times this pipe's record was sent since it loaded (for tests). */
    public int sendCount() {
        return sendCount;
    }

    /**
     * Server, at a boundary: measures the record and returns it when it is worth sending (docs/TRANSPORT.md 9.3), else
     * null.
     */
    FlowRecord boundary(long now, boolean overload) {
        record = accumulator.record(now, overload);
        if (!record.differsFrom(sent))
            return null;
        sent = record;
        sendCount++;
        return record;
    }

    /** Server: nothing flows and the clients know it, so the pipe needs no more boundaries until it flows again. */
    boolean isIdle() {
        return !record.isFlowing() && !sent.isFlowing();
    }

    /** Client: the record clients render. */
    public FlowRecord client() {
        return client;
    }

    /** Client: the game time the shown flow started, or changed content or sides; item icons start entering then. */
    public long clientSince() {
        return clientSince;
    }

    /** Client: a record from the server at game time {@code now}. */
    public void receive(FlowRecord received, long now) {
        if (!client.isFlowing() || received.content() != client.content() || received.sides() != client.sides())
            clientSince = now;
        client = received;
    }
}
