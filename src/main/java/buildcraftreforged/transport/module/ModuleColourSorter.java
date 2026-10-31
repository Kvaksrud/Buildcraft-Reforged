package buildcraftreforged.transport.module;

import buildcraftreforged.api.gates.IAction;
import buildcraftreforged.core.utils.EnumColor;
import buildcraftreforged.transport.IPipeGateTarget;
import buildcraftreforged.transport.PipeModuleType;
import buildcraftreforged.transport.TilePipe;
import buildcraftreforged.transport.TransportRegistry;
import buildcraftreforged.transport.triggers.ActionPipeDirection;
import java.util.LinkedList;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.resource.Resource;
import org.jspecify.annotations.Nullable;

/**
 * The Colour Sorter (docs/TRANSPORT.md 8.9, Q10; 4.2.2 {@code PipeItemsDaizuli}): one colour and one output side.
 * Items tagged with its colour leave by the output side only (when it can take them); every other item leaves by any
 * side but that one. When the output cannot take a matching item, the item goes elsewhere, and a walk with nowhere else
 * to go backs up (4.2.2 bouncing, 4.3).
 *
 * <p>The output side is the Director's facing ({@code PipeLogicIron}: a connected endpoint or item pipe without an
 * extracting module), turned by the wrench and the Pipe Direction action, without the Director's redstone toggle. The
 * colour is set by the Pipe Color action and by a dye; 4.2.2's sneaking wrench now takes the module off (Q18).
 */
public class ModuleColourSorter extends ModuleDirector {

    private EnumColor colour = EnumColor.BLACK;

    ModuleColourSorter() {
        super(PipeModuleType.COLOUR_SORTER);
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

    /** Every side stays open; the sorting is the candidate filter. */
    @Override
    public boolean allowsOutput(TilePipe pipe, Direction side) {
        return true;
    }

    @Override
    public int orderCandidates(TilePipe pipe, int[] sides, int count, @Nullable Resource resource, int colour,
            RandomSource random) {
        Direction output = getFacing();
        return CandidateOrder.colourSorter(sides, count, output == null ? -1 : output.get3DDataValue(),
                colour == this.colour.ordinal(), random);
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

    /** 4.2.2 {@code getActions}: the sixteen Pipe Color actions and a Pipe Direction per connected side. */
    @Override
    public IPipeGateTarget gateTarget(TilePipe pipe) {
        return new IPipeGateTarget() {
            @Override
            public void addActions(LinkedList<IAction> list) {
                ModuleColours.addColourActions(list);
                for (Direction side : Direction.values()) {
                    if (pipe.isPipeConnected(side))
                        list.add(TransportRegistry.actionPipeDirection.get(side.ordinal()).get());
                }
            }

            @Override
            public void actionsActivated(Map<IAction, Boolean> actions) {
                EnumColor active = ModuleColours.activeColour(actions);
                if (active != null)
                    setColour(pipe, active);
                for (Map.Entry<IAction, Boolean> action : actions.entrySet()) {
                    if (action.getKey() instanceof ActionPipeDirection direction
                            && Boolean.TRUE.equals(action.getValue())) {
                        setFacing(pipe, direction.direction);
                        break;
                    }
                }
            }
        };
    }

    @Override
    public void save(ValueOutput output) {
        super.save(output);
        ModuleColours.save(output, colour);
    }

    @Override
    public void load(ValueInput input) {
        super.load(input);
        colour = ModuleColours.load(input);
    }
}
