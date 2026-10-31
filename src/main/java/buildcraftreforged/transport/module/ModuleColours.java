package buildcraftreforged.transport.module;

import buildcraftreforged.api.gates.IAction;
import buildcraftreforged.core.utils.EnumColor;
import buildcraftreforged.transport.TransportRegistry;
import buildcraftreforged.transport.triggers.ActionPipeColor;
import java.util.LinkedList;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** What the Colour Tag and the Colour Sorter share (docs/TRANSPORT.md 8.8, 8.9): the colour, its actions and dyes. */
final class ModuleColours {

    private ModuleColours() {
    }

    /**
     * The colour of a dye item, or null for anything else. {@code EnumColor} keeps the 4.2.2 dye damage order, the
     * reverse of {@code DyeColor}.
     */
    static @Nullable EnumColor fromDye(ItemStack stack) {
        DyeColor dye = stack.get(DataComponents.DYE);
        return dye == null ? null : EnumColor.fromId(EnumColor.VALUES.length - 1 - dye.getId());
    }

    /** The sixteen Pipe Color actions (4.2.2 {@code actionPipeColor}). */
    static void addColourActions(LinkedList<IAction> list) {
        TransportRegistry.actionPipeColor.forEach(action -> list.add(action.get()));
    }

    /** The colour of the first active Pipe Color action, or null when none is active. */
    static @Nullable EnumColor activeColour(Map<IAction, Boolean> actions) {
        for (Map.Entry<IAction, Boolean> action : actions.entrySet()) {
            if (action.getKey() instanceof ActionPipeColor color && Boolean.TRUE.equals(action.getValue()))
                return color.color;
        }
        return null;
    }

    /** Saved as the 4.2.2 byte {@code color}, the {@code EnumColor} ordinal. */
    static void save(ValueOutput output, EnumColor colour) {
        output.putByte("color", (byte) colour.ordinal());
    }

    static EnumColor load(ValueInput input) {
        int colour = input.getByteOr("color", (byte) 0);
        return colour >= 0 && colour < EnumColor.VALUES.length ? EnumColor.VALUES[colour] : EnumColor.BLACK;
    }
}
