package buildcraftreforged.transport.route;

import buildcraftreforged.transport.PipeFlow;
import buildcraftreforged.transport.TilePipe;
import buildcraftreforged.transport.flow.FlowRecord;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * What the Pipe Contents trigger (#60) reads from an item pipe: its flow record (docs/TRANSPORT.md 9, 12). Items flow
 * while the record's rate is above zero, and the record's content is the item. Server only; on the client nothing
 * flows.
 */
public final class ItemPipeFlow implements PipeFlow {

    private final TilePipe pipe;

    public ItemPipeFlow(TilePipe pipe) {
        this.pipe = pipe;
    }

    @Override
    public boolean isFlowing() {
        return pipe.getFlowTracker().record().isFlowing();
    }

    @Override
    public ItemStack itemContent() {
        FlowRecord record = pipe.getFlowTracker().record();
        return record.isFlowing() && record.content() != null && record.content().value() instanceof Item item
                ? new ItemStack(item) : ItemStack.EMPTY;
    }
}
