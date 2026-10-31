/*
 * Buildcraft Reforged, 2026: 4.2.2 made the four pipe wires plain ItemBuildCraft items and fitted them in
 * BlockGenericPipe.addOrStripWire; here that is the wire item's use on a pipe.
 */
package buildcraftreforged.transport;

import buildcraftreforged.api.transport.IPipe;
import buildcraftreforged.core.ItemBuildCraft;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;

/** Red, Blue, Green or Yellow Pipe Wire. Use on a pipe to lay it; sneak-use on a wired pipe to take it off. */
public class ItemPipeWire extends ItemBuildCraft {

    public final IPipe.WireColor color;

    public ItemPipeWire(IPipe.WireColor color, Properties properties) {
        super(properties);
        this.color = color;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().getBlockEntity(context.getClickedPos()) instanceof TilePipe pipe
                && pipe.gateHolder.addOrStripWire(context.getPlayer(), context.getItemInHand(), color))
            return InteractionResult.SUCCESS;
        return super.useOn(context);
    }
}
