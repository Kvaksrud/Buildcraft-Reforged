package buildcraftreforged.transport.module;

import buildcraftreforged.core.ItemBuildCraft;
import buildcraftreforged.transport.PipeModuleType;
import buildcraftreforged.transport.TilePipe;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * A pipe module item (docs/TRANSPORT.md 8.1, Q18): used on a pipe of a kind that takes it and that has no module yet,
 * it fits the module, with the settings it carries, and is used up.
 */
public class ItemPipeModule extends ItemBuildCraft {

    public final PipeModuleType type;

    public ItemPipeModule(PipeModuleType type, Properties properties) {
        super(properties);
        this.type = type;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level.getBlockEntity(context.getClickedPos()) instanceof TilePipe pipe) || pipe.getModule() != null
                || !type.accepts(pipe.kind))
            return InteractionResult.PASS;
        if (!level.isClientSide()) {
            ItemStack stack = context.getItemInHand();
            pipe.setModule(PipeModule.fromItem(type, stack, level.registryAccess()));
            stack.consume(1, context.getPlayer());
        }
        return InteractionResult.SUCCESS;
    }
}
