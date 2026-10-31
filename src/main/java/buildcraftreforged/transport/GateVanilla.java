/*
 * Ported from buildcraft/transport/GateVanilla.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: the autarchic flag comes from the gate item; the gate item is one of fourteen
 * items instead of a damage value, and carries the programming as a data component; GateIconProvider indices become
 * texture ids; the GUI opens as a menu.
 */
package buildcraftreforged.transport;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.api.gates.IAction;
import buildcraftreforged.api.gates.ITrigger;
import buildcraftreforged.api.power.IPowerReceptor;
import buildcraftreforged.api.transport.IPipe;
import buildcraftreforged.core.utils.StringUtils;
import buildcraftreforged.transport.gui.ContainerGateInterface;
import buildcraftreforged.transport.triggers.ActionEnergyPulser;
import buildcraftreforged.transport.triggers.ActionSingleEnergyPulse;
import java.util.LinkedList;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class GateVanilla extends Gate {

    private @Nullable EnergyPulser pulser;

    public GateVanilla(PipeGateHolder pipe, GateKind kind, boolean autarchic) {
        super(pipe, kind);

        if (autarchic) {
            addEnergyPulser(pipe);
        }
    }

    // / SAVING & LOADING
    @Override
    public void writeToNBT(ValueOutput nbttagcompound) {
        super.writeToNBT(nbttagcompound);

        if (pulser != null) {
            pulser.writeToNBT(nbttagcompound.child("Pulser"));
        }
    }

    @Override
    public void readFromNBT(ValueInput nbttagcompound) {
        super.readFromNBT(nbttagcompound);

        // Load pulser if any
        ValueInput nbttagcompoundP = nbttagcompound.child("Pulser").orElse(null);
        if (nbttagcompoundP != null) {
            addEnergyPulser(pipe);
            pulser.readFromNBT(nbttagcompoundP);
        }

    }

    // GUI
    @Override
    public void openGui(Player player) {
        if (!player.level().isClientSide()) {
            TilePipe tile = pipe.container;
            player.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, p) -> new ContainerGateInterface(containerId, inventory, tile),
                    Component.translatable(getNameKey())), tile.getBlockPos());
        }
    }

    // / UPDATING
    @Override
    public void update() {
        if (hasPulser()) {
            pulser.update();
        }
    }

    // / INFORMATION
    public boolean hasPulser() {
        return pulser != null;
    }

    public @Nullable EnergyPulser getPulser() {
        return pulser;
    }

    @Override
    public String getName() {
        return StringUtils.localize(getNameKey());
    }

    /** 4.2.2's item names: {@code item.pipeGate.<damage>}, {@code item.pipeGateAutarchic.<damage>}. */
    public String getNameKey() {
        return ItemGate.translationKey(kind, hasPulser());
    }

    @Override
    public GateConditional getConditional() {
        if (kind == GateKind.OR_2 || kind == GateKind.OR_3 || kind == GateKind.OR_4)
            return GateConditional.OR;
        else if (kind == GateKind.AND_2 || kind == GateKind.AND_3 || kind == GateKind.AND_4)
            return GateConditional.AND;
        else
            return GateConditional.None;
    }

    /**
     * Tries to add an energy pulser to gates that accept energy. 4.2.2 pulsed every power-receiving pipe except the
     * wooden kinesis pipe; pipes need no power now (docs/TRANSPORT.md 12), so a pipe gets a pulser without a receptor.
     * A block entity that holds gates and takes power (the Basic Extractor, #58) is the receptor.
     */
    private boolean addEnergyPulser(PipeGateHolder pipe) {
        if (!(pipe.container instanceof IPowerReceptor)) {
            pulser = new EnergyPulser(null);
            return false;
        }
        pulser = new EnergyPulser((IPowerReceptor) pipe.container);

        return true;
    }

    /** The gate item of this kind, carrying the gate's programming. */
    @Override
    public ItemStack getGateItem() {
        ItemStack stack = new ItemStack(TransportRegistry.getGateItem(kind, hasPulser()));
        GateSettings settings = getSettings();
        if (!settings.isEmpty())
            stack.set(TransportRegistry.gateSettings.get(), settings);
        return stack;
    }

    // / ACTIONS
    @Override
    public void addActions(LinkedList<IAction> list) {

        if (pipe.wireSet[IPipe.WireColor.Red.ordinal()] && kind.ordinal() >= Gate.GateKind.AND_2.ordinal()) {
            list.add(TransportRegistry.actionRedSignal.get());
        }

        if (pipe.wireSet[IPipe.WireColor.Blue.ordinal()] && kind.ordinal() >= Gate.GateKind.AND_3.ordinal()) {
            list.add(TransportRegistry.actionBlueSignal.get());
        }

        if (pipe.wireSet[IPipe.WireColor.Green.ordinal()] && kind.ordinal() >= Gate.GateKind.AND_4.ordinal()) {
            list.add(TransportRegistry.actionGreenSignal.get());
        }

        if (pipe.wireSet[IPipe.WireColor.Yellow.ordinal()] && kind.ordinal() >= Gate.GateKind.AND_4.ordinal()) {
            list.add(TransportRegistry.actionYellowSignal.get());
        }

        if (hasPulser()) {
            list.add(TransportRegistry.actionEnergyPulser.get());
            list.add(TransportRegistry.actionSingleEnergyPulse.get());
        }

    }

    @Override
    public void startResolution() {
        if (hasPulser() && pulser.isActive()) {
            pulser.disablePulse();
            pipe.scheduleRenderUpdate();
        }
    }

    @Override
    public boolean resolveAction(IAction action, int count) {

        if (action instanceof ActionEnergyPulser) {
            pulser.enablePulse(count);
            pipe.scheduleRenderUpdate();
            return true;
        } else if (action instanceof ActionSingleEnergyPulse) {
            pulser.enableSinglePulse(count);
            pipe.scheduleRenderUpdate();
            return true;
        }
        return false;
    }

    // / TRIGGERS
    @Override
    public void addTrigger(LinkedList<ITrigger> list) {

        if (pipe.wireSet[IPipe.WireColor.Red.ordinal()] && kind.ordinal() >= Gate.GateKind.AND_2.ordinal()) {
            list.add(TransportRegistry.triggerRedSignalActive.get());
            list.add(TransportRegistry.triggerRedSignalInactive.get());
        }

        if (pipe.wireSet[IPipe.WireColor.Blue.ordinal()] && kind.ordinal() >= Gate.GateKind.AND_3.ordinal()) {
            list.add(TransportRegistry.triggerBlueSignalActive.get());
            list.add(TransportRegistry.triggerBlueSignalInactive.get());
        }

        if (pipe.wireSet[IPipe.WireColor.Green.ordinal()] && kind.ordinal() >= Gate.GateKind.AND_4.ordinal()) {
            list.add(TransportRegistry.triggerGreenSignalActive.get());
            list.add(TransportRegistry.triggerGreenSignalInactive.get());
        }

        if (pipe.wireSet[IPipe.WireColor.Yellow.ordinal()] && kind.ordinal() >= Gate.GateKind.AND_4.ordinal()) {
            list.add(TransportRegistry.triggerYellowSignalActive.get());
            list.add(TransportRegistry.triggerYellowSignalInactive.get());
        }

    }

    // / ICONS
    /** Whether the gate is drawn lit: a broadcast signal, or a running pulser (4.2.2 getTextureIconIndex). */
    public boolean isLit(boolean isSignalActive) {
        return isSignalActive || hasPulser() && pulser.isActive();
    }

    /**
     * 4.2.2 {@code getTextureIconIndex}: {@code [autarchic_]gate[_<material>_<and|or>]_<lit|dark>}, as the 4.2.2 block
     * icons were named.
     */
    @Override
    public final Identifier getTexture(boolean isSignalActive) {
        return texture(kind, hasPulser(), isLit(isSignalActive));
    }

    public static Identifier texture(GateKind kind, boolean autarchic, boolean lit) {
        String name = "gate" + (autarchic ? "_autarchic" : "") + ItemGate.suffix(kind)
                + (lit ? "_lit" : "_dark");
        return Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID,
                "textures/pipe/" + name.toLowerCase(Locale.ENGLISH) + ".png");
    }

    private static final Identifier TEXTURE1 = gui("gate_interface_1");
    private static final Identifier TEXTURE2 = gui("gate_interface_2");
    private static final Identifier TEXTURE3 = gui("gate_interface_3");
    private static final Identifier TEXTURE4 = gui("gate_interface_4");

    private static Identifier gui(String name) {
        return Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "textures/gui/" + name + ".png");
    }

    @Override
    public Identifier getGuiFile() {
        if (kind == GateKind.Single)
            return TEXTURE1;
        else if (kind == GateKind.AND_2 || kind == GateKind.OR_2)
            return TEXTURE2;
        else if (kind == GateKind.AND_3 || kind == GateKind.OR_3)
            return TEXTURE3;
        else
            return TEXTURE4;
    }

    @Override
    public int getGuiHeight() {
        switch (kind) {
            case Single:
                return 146;
            case AND_2:
            case OR_2:
                return 164;
            default:
                return 200;
        }
    }
}
