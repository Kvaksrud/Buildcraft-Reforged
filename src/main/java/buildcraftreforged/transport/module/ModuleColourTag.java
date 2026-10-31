package buildcraftreforged.transport.module;

import buildcraftreforged.api.gates.IAction;
import buildcraftreforged.core.utils.EnumColor;
import buildcraftreforged.transport.IPipeGateTarget;
import buildcraftreforged.transport.PipeModuleType;
import buildcraftreforged.transport.TilePipe;
import java.util.LinkedList;
import java.util.Map;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The Colour Tag (docs/TRANSPORT.md 8.8; 4.2.2 {@code PipeItemsLapis}): every item that passes is tagged with the
 * module's colour for the rest of its walk, which a Colour Sorter further on reads. The stack that arrives is unchanged.
 * The wrench steps to the next colour, a dye sets its colour, and the Pipe Color gate action sets that colour. Black
 * when new, as a fresh 4.2.2 lapis pipe.
 */
public class ModuleColourTag extends PipeModule {

    private EnumColor colour = EnumColor.BLACK;

    ModuleColourTag() {
        super(PipeModuleType.COLOUR_TAG);
    }

    public EnumColor getColour() {
        return colour;
    }

    public void setColour(TilePipe pipe, EnumColor colour) {
        if (colour != this.colour) {
            this.colour = colour;
            pipe.setChanged();
        }
    }

    /** 4.2.2 {@code eventHandler(ReachedCenter)}: {@code item.color = getColor()}. */
    @Override
    public int walkColour(TilePipe pipe, int colour) {
        return this.colour.ordinal();
    }

    /** 4.2.2 stepped back on a sneaking wrench, which now takes the module off (Q18); a dye picks any colour. */
    @Override
    public boolean onWrench(TilePipe pipe, Player player) {
        setColour(pipe, colour.getNext());
        return true;
    }

    @Override
    public boolean useItem(TilePipe pipe, Player player, ItemStack stack) {
        EnumColor dye = ModuleColours.fromDye(stack);
        if (dye == null)
            return false;
        if (pipe.getLevel() != null && !pipe.getLevel().isClientSide())
            setColour(pipe, dye);
        return true;
    }

    @Override
    public IPipeGateTarget gateTarget(TilePipe pipe) {
        return new IPipeGateTarget() {
            @Override
            public void addActions(LinkedList<IAction> list) {
                ModuleColours.addColourActions(list);
            }

            @Override
            public void actionsActivated(Map<IAction, Boolean> actions) {
                EnumColor active = ModuleColours.activeColour(actions);
                if (active != null)
                    setColour(pipe, active);
            }
        };
    }

    @Override
    public void save(ValueOutput output) {
        ModuleColours.save(output, colour);
    }

    @Override
    public void load(ValueInput input) {
        colour = ModuleColours.load(input);
    }
}
